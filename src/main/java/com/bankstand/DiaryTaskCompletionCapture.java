package com.bankstand;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * Captures a diary task completion (tier and area) from its per-task chat broadcast. Which task
 * it was is the server's to work out from the raw values the snapshot carries.
 */
public class DiaryTaskCompletionCapture extends BaseCapture {

  private static final Pattern COMPLETION_PATTERN =
      Pattern.compile(
          "^Well done! You have completed an? (\\w+) task in the (.+) area\\. Your Achievement"
              + " Diary has been updated\\.$");

  // Not CombatAchievementVarbits' tiers, which add "master" and "grandmaster".
  private static final Set<String> DIARY_TIERS = Set.of("easy", "medium", "hard", "elite");

  // Matches the server's bound; one oversized event makes the server reject the whole batch.
  private static final int MAX_AREA_NAME_LENGTH = 128;

  public DiaryTaskCompletionCapture(
      EventOutbox outbox, BooleanSupplier enabled, LongSupplier accountHash) {
    this(outbox, enabled, accountHash, null);
  }

  public DiaryTaskCompletionCapture(
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
