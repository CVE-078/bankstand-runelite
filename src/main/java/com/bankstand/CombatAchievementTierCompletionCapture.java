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
 * Captures a whole combat achievement tier completing, from its chat broadcast. Carries no task
 * identity; single tasks are {@link CombatAchievementCompletionCapture}'s job.
 *
 * <p>The pattern is unverified against a live broadcast. If a real capture disagrees, the
 * pattern is wrong: update it and its test together.
 */
public class CombatAchievementTierCompletionCapture extends BaseCapture {

  // Trailing "!" optional: unconfirmed.
  private static final Pattern TIER_COMPLETION_PATTERN =
      Pattern.compile(
          "^Congratulations, you have completed the (\\w+) tier of the Combat Achievements!?$");

  public CombatAchievementTierCompletionCapture(
      EventOutbox outbox, BooleanSupplier enabled, LongSupplier accountHash) {
    super(outbox, enabled, accountHash);
  }

  public CombatAchievementTierCompletionCapture(
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
    Matcher matcher = TIER_COMPLETION_PATTERN.matcher(message);
    if (!matcher.matches()) {
      return;
    }
    String tier = matcher.group(1).toLowerCase(Locale.ROOT);
    if (!CombatAchievementVarbits.ALL.containsKey(tier)) {
      return;
    }
    emit(TransientEvent.TYPE_COMBAT_ACHIEVEMENT_TIER_COMPLETED, payload(tier));
  }

  static Map<String, Object> payload(String tier) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tier", tier);
    return payload;
  }
}
