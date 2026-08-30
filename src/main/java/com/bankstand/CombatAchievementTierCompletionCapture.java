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
 * Captures a whole combat achievement TIER completing (all of that tier's tasks done), from
 * the game's own chat broadcast. Distinct from {@link CombatAchievementCompletionCapture}, which
 * captures a single task finishing; this one fires once per tier, when the last task in it is
 * done, and never carries a task identity.
 *
 * <p><b>The regex below is an unverified, best-effort guess, not a confirmed live capture.</b>
 * It is built from the one documented fragment available: RuneLite's own chat-notification
 * highlight pattern for this broadcast, "has completed the (\w*) tier of the Combat
 * Achievements". Two prior guesses in this exact file family, {@link
 * CombatAchievementCompletionCapture}'s CA_ID prefix handling and its leading icon-tag handling,
 * were both found to be wrong on the first guess and only fixed after a real live capture; treat
 * this one the same way. If a live capture later disagrees with this wording, the wording is
 * wrong, not the test: update both together.
 */
public class CombatAchievementTierCompletionCapture extends BaseCapture {

  // Unverified, best-effort guess (see the class javadoc): the trailing "!" is made optional
  // because nothing in the one documented fragment confirms it is always present.
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

  /** Package-private, not private: the test calls this directly with plain strings. */
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
