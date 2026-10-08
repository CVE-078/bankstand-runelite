package com.bankstand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Captures a finished slayer task from the game's completion line, using RuneLite's Slayer
 * plugin pattern. The game clears the task on completion, so creature and assigned count
 * come from the task as last seen; that bookkeeping runs even with the toggle off.
 */
public class SlayerTaskCompletionCapture extends BaseCapture {

  static final Pattern COMPLETE =
      Pattern.compile(
          "You've completed (?:at least )?(?<tasks>[\\d,]+) (?:Wilderness )?tasks?(?: and received"
              + " (?<awarded>[\\d,]+) points, giving you a total of (?<points>[\\d,]+)| and reached"
              + " the maximum amount of Slayer points \\((?<points2>[\\d,]+)\\))?");

  private final Supplier<SlayerTask> currentTask;
  private SlayerTask lastSeen;

  public SlayerTaskCompletionCapture(
      EventOutbox outbox,
      BooleanSupplier enabled,
      LongSupplier accountHash,
      Supplier<SlayerTask> currentTask,
      Consumer<TransientEvent> onEmit) {
    super(outbox, enabled, accountHash, onEmit);
    this.currentTask = currentTask;
  }

  /** Remembers the running task each time its kill count moves. */
  @Subscribe
  public void onVarbitChanged(VarbitChanged event) {
    if (event.getVarpId() != VarPlayerID.SLAYER_COUNT || event.getValue() <= 0) {
      return;
    }
    SlayerTask task = currentTask.get();
    if (task != null && task.active) {
      lastSeen = task;
    }
  }

  @Subscribe
  public void onChatMessage(ChatMessage event) {
    if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
      return;
    }
    onMessage(Text.removeTags(event.getMessage()));
  }

  /** Package-private so a test can feed a line without a {@link ChatMessage}. */
  void onMessage(String message) {
    if (!isCompletionLine(message)) {
      return;
    }
    SlayerTask task = lastSeen;
    lastSeen = null;
    if (!isEnabled()) {
      return;
    }
    Matcher m = COMPLETE.matcher(message);
    if (!m.find()) {
      return;
    }
    String totalPoints = m.group("points") != null ? m.group("points") : m.group("points2");
    emit(
        TransientEvent.TYPE_SLAYER_TASK_COMPLETED,
        payload(
            task == null ? null : task.creature,
            task == null ? null : task.assigned,
            parseCount(m.group("awarded")),
            parseCount(totalPoints),
            parseCount(m.group("tasks"))));
  }

  /** Forgets the remembered task, on an account switch. */
  void reset() {
    lastSeen = null;
  }

  static boolean isCompletionLine(String message) {
    return message != null
        && message.startsWith("You've completed")
        && (message.contains("Slayer master") || message.contains("Slayer Master"));
  }

  static Map<String, Object> payload(
      String creature, Integer killed, Integer pointsAwarded, Integer pointsTotal, Integer streak) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("creature", creature);
    payload.put("killed", killed);
    payload.put("master", null);
    payload.put("pointsAwarded", pointsAwarded);
    payload.put("pointsTotal", pointsTotal);
    payload.put("streak", streak);
    return payload;
  }

  private static Integer parseCount(String digits) {
    if (digits == null) {
      return null;
    }
    try {
      return Integer.parseInt(digits.replace(",", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
