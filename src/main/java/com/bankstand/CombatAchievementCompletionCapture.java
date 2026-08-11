package com.bankstand;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Captures which specific combat achievement task was just completed, from the
 * game's own chat broadcast (#770). The only source that can ever name a
 * specific task: RuneLite exposes a per-tier completed count and nothing per
 * task, and Jagex publishes no per-task identity anywhere (#618, #662).
 *
 * <p>Wording verified against Dink's own {@code CombatTaskNotifier} and its test
 * fixtures, a Hub-approved plugin whose combat achievement notifier is
 * live-tested at scale: "Congratulations, you've completed a/an &lt;tier&gt;
 * combat task: &lt;name&gt;." A grandmaster task additionally appends a
 * "(N points)" suffix to the name, which is stripped before emit.
 */
public class CombatAchievementCompletionCapture extends BaseCapture {

  private static final Pattern COMPLETION_PATTERN =
      Pattern.compile(
          "^Congratulations, you've completed an? (\\w+) combat task: (.+)\\.$");

  // Strips a trailing "(N points)" from a task name, matching Dink's own approach of a
  // dedicated strip pattern applied with replaceFirst(""). taskName becomes a permanent
  // primary key server-side (player_combat_achievement_task is append-only, with no
  // later sync to correct it), so the suffix must never reach the outbox: a future fix
  // would then record the same task a second time under the clean name with no way to
  // tell which row is the copy.
  private static final Pattern POINTS_SUFFIX_PATTERN =
      Pattern.compile("\\s+\\(\\d+ points?\\)$");

  // Matches the server's own bound (MAX_NAME_LENGTH in events-envelope.ts). The server
  // validates a whole batch in one schema parse and 400s the WHOLE BATCH on any one
  // event failing, so one oversized name must never reach the outbox: it would
  // permanently block every other queued event for the account, not just itself.
  private static final int MAX_TASK_NAME_LENGTH = 128;

  public CombatAchievementCompletionCapture(
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
    if (!CombatAchievementVarbits.ALL.containsKey(tier)) {
      return;
    }
    String taskName = POINTS_SUFFIX_PATTERN.matcher(matcher.group(2)).replaceFirst("");
    if (taskName.length() > MAX_TASK_NAME_LENGTH) {
      return;
    }
    emit(TransientEvent.TYPE_COMBAT_ACHIEVEMENT_COMPLETED, payload(tier, taskName));
  }

  static Map<String, Object> payload(String tier, String taskName) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tier", tier);
    payload.put("taskName", taskName);
    return payload;
  }
}
