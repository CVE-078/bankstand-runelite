package com.bankstand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Captures that SOME diary task completed, from its own chat broadcast:
 * tier and area only, never which specific task. The tier-completion broadcast already matched
 * elsewhere ("Congratulations, you have completed all of the &lt;tier&gt; tasks in the
 * &lt;area&gt; area...") fires once per finished tier; this one fires once per task, well
 * before the tier is complete, and starts with a different word entirely so the two can never
 * collide.
 *
 * <p>Verified live wording: "Well done! You have completed an elite task in the Western
 * Provinces area. Your Achievement Diary has been updated."
 *
 * <p><b>Per-task identity</b> (which task, not just which tier/area) is resolved here when
 * possible, per the region's own {@link DiaryTaskManifest} entry: the client exposes no
 * varbit or widget field naming an individual task, only bits packed into a region's own
 * varplayer(s) ({@link DiaryTaskVarplayers}), diffed against the last-known value
 * ({@link DiaryTaskBits}) to find which bit just flipped. Resolution is attempted only for
 * a region {@link DiaryTaskManifest#isVerified} has confirmed; every other region still
 * reads and advances its baseline the same way, so it is never corrupted for whenever it is
 * verified later, it just never attaches a name. A miss, a tier mismatch, or more than one
 * bit resolving at once all fail closed to the plain tier/area event, never a guess.
 */
@Slf4j
public class DiaryTaskCompletionCapture extends BaseCapture {

  private static final Pattern COMPLETION_PATTERN =
      Pattern.compile(
          "^Well done! You have completed an? (\\w+) task in the (.+) area\\. Your Achievement"
              + " Diary has been updated\\.$");

  // Diary tiers, not CombatAchievementVarbits.ALL's keys: despite sharing four of the same
  // words, they are a different vocabulary (that map also has "master" and "grandmaster",
  // which do not exist as diary tiers), so validating against it here would silently accept
  // two tier names the diary system has no notion of.
  private static final Set<String> DIARY_TIERS = Set.of("easy", "medium", "hard", "elite");

  // Matches the server's own bound (MAX_NAME_LENGTH in events-envelope.ts). The server
  // validates a whole batch in one schema parse and 400s the WHOLE BATCH on any one event
  // failing, so one oversized area name must never reach the outbox.
  private static final int MAX_AREA_NAME_LENGTH = 128;

  private final IntUnaryOperator varpReader;
  private final DiaryTaskBits bits;
  private final DiaryTaskManifest manifest;

  /** No task-identity resolution: reads and advances nothing, every completion stays
   *  tier/area only. What every existing caller and test uses. */
  public DiaryTaskCompletionCapture(
      EventOutbox outbox, BooleanSupplier enabled, LongSupplier accountHash) {
    this(outbox, enabled, accountHash, id -> 0, new DiaryTaskBits(), DiaryTaskManifest.shipped());
  }

  public DiaryTaskCompletionCapture(
      EventOutbox outbox,
      BooleanSupplier enabled,
      LongSupplier accountHash,
      IntUnaryOperator varpReader,
      DiaryTaskBits bits,
      DiaryTaskManifest manifest) {
    super(outbox, enabled, accountHash);
    this.varpReader = varpReader;
    this.bits = bits;
    this.manifest = manifest;
  }

  @Subscribe
  public void onChatMessage(ChatMessage event) {
    if (event.getType() != ChatMessageType.GAMEMESSAGE) {
      return;
    }
    handleMessage(Text.removeTags(event.getMessage()));
  }

  /** Package-private, not private: the test calls this directly with plain strings. */
  void handleMessage(String message) {
    if (!isEnabled()) {
      return;
    }
    Matcher matcher = COMPLETION_PATTERN.matcher(message);
    if (!matcher.matches()) {
      return;
    }
    String tier = matcher.group(1).toLowerCase(Locale.ROOT);
    if (!DIARY_TIERS.contains(tier)) {
      return;
    }
    String area = matcher.group(2).trim();
    if (area.isEmpty() || area.length() > MAX_AREA_NAME_LENGTH) {
      return;
    }
    emit(TransientEvent.TYPE_DIARY_TASK_COMPLETED, payload(tier, area, resolveTaskName(tier, area)));
  }

  /**
   * Reads and diffs the region's own varplayer(s) regardless of whether the region is
   * verified, so the baseline never goes stale for whenever it is. Only attempts a
   * manifest lookup, and only ever returns a name, for a verified region.
   *
   * <p>Never guesses: a bit the manifest does not know, a bit whose manifest tier
   * disagrees with the chat line's own tier, or more than one bit resolving from this one
   * observation all return null, exactly like an unrecognised region does.
   */
  private String resolveTaskName(String tier, String area) {
    String region = DiaryTaskRegions.forAreaText(area);
    if (region == null) {
      return null;
    }
    int[] varplayerIds = DiaryTaskVarplayers.ALL.get(region);
    if (varplayerIds == null) {
      return null;
    }
    boolean verified = manifest.isVerified(region);
    // Every bit that newly flipped, whether or not it resolves through the manifest:
    // this is the actual ambiguity signal, not how many happened to resolve. Two bits
    // flipping at once where only one has a manifest entry (a real, expected state for
    // a verified region with partial coverage) is exactly as ambiguous as two bits that
    // both resolve, because this capture cannot know which of the two the CURRENT chat
    // line refers to; counting only resolved bits let that case slip through.
    int totalNewlySet = 0;
    List<String> resolved = new ArrayList<>();
    boolean mismatch = false;
    for (int varplayerId : varplayerIds) {
      int newValue = varpReader.applyAsInt(varplayerId);
      // Always diffed, whether or not verified: this is what keeps an unverified
      // region's baseline correct for the day its own manifest ships.
      int[] newlySetBits = bits.diff(varplayerId, newValue);
      totalNewlySet += newlySetBits.length;
      if (!verified) {
        continue;
      }
      for (int bitIndex : newlySetBits) {
        DiaryTaskManifest.Entry entry = manifest.lookup(region, varplayerId, bitIndex);
        if (entry == null) {
          // An unmapped bit within an otherwise-mapped region. Expected and safe; the
          // manifest simply has not resolved this one yet.
          continue;
        }
        if (!entry.tier().equals(tier)) {
          // The manifest disagrees with the chat line about which tier this bit
          // belongs to. A stronger signal than an ordinary miss: something is wrong
          // upstream (a shifted bit, a wrong region), never a case to prefer one
          // source over the other.
          log.debug(
              "diary task manifest tier mismatch: region={} varplayer={} bit={} manifest={}"
                  + " chat={}",
              region, varplayerId, bitIndex, entry.tier(), tier);
          mismatch = true;
          continue;
        }
        resolved.add(entry.taskName());
      }
    }
    if (mismatch || totalNewlySet != 1 || resolved.size() != 1) {
      return null;
    }
    return resolved.get(0);
  }

  static Map<String, Object> payload(String tier, String area, String taskName) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tier", tier);
    payload.put("area", area);
    if (taskName != null) {
      payload.put("taskName", taskName);
    }
    return payload;
  }
}
