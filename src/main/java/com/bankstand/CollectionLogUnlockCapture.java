package com.bankstand;

import java.util.LinkedHashMap;
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
 * Captures a collection log unlock the moment the game announces it in chat (#770),
 * rather than waiting for the player to open the log and click Search.
 *
 * <p>Carries the raw item NAME, not a resolved item id. The log's own display name is
 * not unique ("Graceful hood" names three separate entries across three sources), so
 * resolving it to the correct one is a disambiguation the server already does
 * correctly for the guided sync; duplicating that logic here would re-solve an
 * already-solved problem, with a real chance of getting it wrong.
 */
public class CollectionLogUnlockCapture extends BaseCapture {

  private static final Pattern UNLOCK_PATTERN =
      Pattern.compile("^New item added to your collection log: (.+)$");

  // Matches the server's own bound (MAX_NAME_LENGTH in events-envelope.ts). The server
  // validates a whole batch in one schema parse and 400s the WHOLE BATCH on any one
  // event failing, so one oversized or malformed name must never reach the outbox: it
  // would permanently block every other queued event for the account, not just itself.
  private static final int MAX_ITEM_NAME_LENGTH = 128;

  public CollectionLogUnlockCapture(
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

  /** Package-private, not private: CollectionLogUnlockCaptureTest calls this directly
   *  with plain strings, to avoid constructing a live ChatMessage. */
  void handleMessage(String message) {
    if (!isEnabled()) {
      return;
    }
    Matcher matcher = UNLOCK_PATTERN.matcher(message);
    if (!matcher.matches()) {
      return;
    }
    String itemName = matcher.group(1).trim();
    // A whitespace-only capture is min(1)-valid junk on the server's own schema, the
    // same way an empty one is, and one bad event 400s the whole batch (see
    // MAX_ITEM_NAME_LENGTH's comment for why that is never acceptable here).
    if (itemName.isEmpty() || itemName.length() > MAX_ITEM_NAME_LENGTH) {
      return;
    }
    emit(TransientEvent.TYPE_COLLECTION_LOG_UNLOCK, payload(itemName));
  }

  static Map<String, Object> payload(String itemName) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("itemName", itemName);
    return payload;
  }
}
