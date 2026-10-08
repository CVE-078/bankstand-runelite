package com.bankstand;

import java.util.LinkedHashMap;
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
 * Captures a collection log unlock from its chat announcement. Sends the raw item name, not an
 * id: names are not unique, and the server already disambiguates them.
 */
public class CollectionLogUnlockCapture extends BaseCapture {

  private static final Pattern UNLOCK_PATTERN =
      Pattern.compile("^New item added to your collection log: (.+)$");

  // Matches the server's bound. The server rejects the whole batch on one invalid event, so a
  // bad name must never reach the outbox.
  private static final int MAX_ITEM_NAME_LENGTH = 128;

  public CollectionLogUnlockCapture(
      EventOutbox outbox, BooleanSupplier enabled, LongSupplier accountHash) {
    super(outbox, enabled, accountHash);
  }

  public CollectionLogUnlockCapture(
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
    Matcher matcher = UNLOCK_PATTERN.matcher(message);
    if (!matcher.matches()) {
      return;
    }
    String itemName = matcher.group(1).trim();
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
