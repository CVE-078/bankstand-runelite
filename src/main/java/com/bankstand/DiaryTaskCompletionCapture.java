package com.bankstand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
 * Captures a diary task completion (tier and area) from its per-task chat broadcast.
 *
 * <p>The task name is resolved only for a verified region, by diffing the region's
 * varplayer bits against the last-known value. A miss, a tier mismatch or more than one
 * flipped bit fails closed to no name.
 */
@Slf4j
public class DiaryTaskCompletionCapture extends BaseCapture {

  private static final Pattern COMPLETION_PATTERN =
      Pattern.compile(
          "^Well done! You have completed an? (\\w+) task in the (.+) area\\. Your Achievement"
              + " Diary has been updated\\.$");

  // Not CombatAchievementVarbits' tiers, which add "master" and "grandmaster".
  private static final Set<String> DIARY_TIERS = Set.of("easy", "medium", "hard", "elite");

  // Matches the server's bound; one oversized event makes the server reject the whole batch.
  private static final int MAX_AREA_NAME_LENGTH = 128;

  private final IntUnaryOperator varpReader;
  private final DiaryTaskBits bits;
  private final DiaryTaskManifest manifest;
  private final Runnable onBaselineTouched;

  /** No task-name resolution, tier and area only. */
  public DiaryTaskCompletionCapture(
      EventOutbox outbox, BooleanSupplier enabled, LongSupplier accountHash) {
    this(
        outbox,
        enabled,
        accountHash,
        id -> 0,
        new DiaryTaskBits(),
        DiaryTaskManifest.shipped(),
        () -> {});
  }

  /** @param onBaselineTouched asks the caller to persist {@code bits} after each diff. */
  public DiaryTaskCompletionCapture(
      EventOutbox outbox,
      BooleanSupplier enabled,
      LongSupplier accountHash,
      IntUnaryOperator varpReader,
      DiaryTaskBits bits,
      DiaryTaskManifest manifest,
      Runnable onBaselineTouched) {
    this(outbox, enabled, accountHash, varpReader, bits, manifest, onBaselineTouched, null);
  }

  public DiaryTaskCompletionCapture(
      EventOutbox outbox,
      BooleanSupplier enabled,
      LongSupplier accountHash,
      IntUnaryOperator varpReader,
      DiaryTaskBits bits,
      DiaryTaskManifest manifest,
      Runnable onBaselineTouched,
      Consumer<TransientEvent> onEmit) {
    super(outbox, enabled, accountHash, onEmit);
    this.varpReader = varpReader;
    this.bits = bits;
    this.manifest = manifest;
    this.onBaselineTouched = onBaselineTouched;
  }

  @Subscribe
  public void onChatMessage(ChatMessage event) {
    if (event.getType() != ChatMessageType.GAMEMESSAGE) {
      return;
    }
    handleMessage(Text.removeTags(event.getMessage()));
  }

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
    emit(
        TransientEvent.TYPE_DIARY_TASK_COMPLETED,
        payload(tier, area, resolveTaskName(tier, area)));
  }

  // Diffs every region, verified or not, so the baseline never goes stale.
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
    // Count every flipped bit, mapped or not: two flips are ambiguous either way.
    int totalNewlySet = 0;
    List<String> resolved = new ArrayList<>();
    boolean mismatch = false;
    for (int varplayerId : varplayerIds) {
      int newValue = varpReader.applyAsInt(varplayerId);
      int[] newlySetBits = bits.diff(varplayerId, newValue);
      totalNewlySet += newlySetBits.length;
      if (!verified) {
        continue;
      }
      for (int bitIndex : newlySetBits) {
        DiaryTaskManifest.Entry entry = manifest.lookup(region, varplayerId, bitIndex);
        if (entry == null) {
          continue; // unmapped bit in an otherwise-mapped region, expected and safe
        }
        if (!entry.tier().equals(tier)) {
          // Manifest and chat disagree on tier: trust neither.
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
    // Only after every varplayer is diffed, or the persisted copy lags one message behind.
    onBaselineTouched.run();
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
