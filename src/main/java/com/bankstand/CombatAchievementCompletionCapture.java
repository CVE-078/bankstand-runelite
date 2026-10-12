package com.bankstand;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Captures which combat achievement task was completed, from its chat broadcast: the only source
 * that names a specific task.
 */
public class CombatAchievementCompletionCapture extends BaseCapture {

  // The live broadcast has an optional "CA_ID:<n>|" prefix, matched and discarded.
  private static final Pattern COMPLETION_PATTERN =
      Pattern.compile(
          "^(?:CA_ID:\\d+\\|)?Congratulations, you've completed an? (\\w+) combat task: (.+)\\.$");

  // The task name is a permanent server-side key, so a trailing "(N points)" must be stripped.
  private static final Pattern POINTS_SUFFIX_PATTERN =
      Pattern.compile("\\s+\\(\\d+ points?\\)$");

  // A leading "@word@" icon tag (seen live, not removed by Text.removeTags), stripped for the
  // same reason.
  private static final Pattern ICON_TAG_PREFIX_PATTERN = Pattern.compile("^@\\w+@");

  // Matches the server's bound. The server rejects the whole batch on one invalid event, so a
  // bad name must never reach the outbox.
  private static final int MAX_TASK_NAME_LENGTH = 128;

  public CombatAchievementCompletionCapture(
      EventOutbox outbox, BooleanSupplier enabled, LongSupplier accountHash) {
    super(outbox, enabled, accountHash);
  }

  public CombatAchievementCompletionCapture(
      EventOutbox outbox,
      BooleanSupplier enabled,
      LongSupplier accountHash,
      Consumer<TransientEvent> onEmit) {
    super(outbox, enabled, accountHash, onEmit);
  }

  @Subscribe
  public void onChatMessage(ChatMessage event) {
    if (event.getType() != ChatMessageType.GAMEMESSAGE) {
      return;
    }
    handleMessage(Text.removeTags(event.getMessage()));
  }

  // Package-private for tests.
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
    String rawName = ICON_TAG_PREFIX_PATTERN.matcher(matcher.group(2)).replaceFirst("");
    String taskName = POINTS_SUFFIX_PATTERN.matcher(rawName).replaceFirst("").trim();
    // Stripping can leave nothing.
    if (taskName.isEmpty() || taskName.length() > MAX_TASK_NAME_LENGTH) {
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
