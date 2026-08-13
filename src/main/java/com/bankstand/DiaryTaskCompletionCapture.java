package com.bankstand;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Captures that SOME diary task completed, from its own chat broadcast (CVE-078/bankstand#842):
 * tier and area only, never which specific task. The tier-completion broadcast #770 already
 * matches ("Congratulations, you have completed all of the &lt;tier&gt; tasks in the
 * &lt;area&gt; area...") fires once per finished tier; this one fires once per task, well
 * before the tier is complete, and starts with a different word entirely so the two can never
 * collide.
 *
 * <p>Verified live wording: "Well done! You have completed an elite task in the Western
 * Provinces area. Your Achievement Diary has been updated."
 *
 * <p>Full per-task identity (which task, not just which tier/area) needs a varbit-to-task map
 * that does not exist publicly (CVE-078/bankstand#843, separate scope, its own design).
 */
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

  public DiaryTaskCompletionCapture(
      EventOutbox outbox, BooleanSupplier enabled, LongSupplier accountHash) {
    super(outbox, enabled, accountHash);
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
    emit(TransientEvent.TYPE_DIARY_TASK_COMPLETED, payload(tier, area));
  }

  static Map<String, Object> payload(String tier, String area) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tier", tier);
    payload.put("area", area);
    return payload;
  }
}
