package com.bankstand;

import java.awt.Color;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

/** Writes the plugin's chat lines. */
final class Notifier {

  // Prefix for every chat line the plugin writes. The darker light-theme gold stays readable
  // both on the parchment chat box and on a transparent one.
  private static final Color BRAND = new Color(0xB3730A);
  private static final String NOTICE_PREFIX = "Bankstand: ";

  private final Client client;
  private final ClientThread clientThread;
  private final ChatMessageManager chatMessageManager;

  Notifier(Client client, ClientThread clientThread, ChatMessageManager chatMessageManager) {
    this.client = client;
    this.clientThread = clientThread;
    this.chatMessageManager = chatMessageManager;
  }

  /**
   * Any thread. Writes a line to the chat box. Logged out, the message is dropped, not queued:
   * an outcome is only worth reporting while the player is there to read it.
   */
  void notice(String message) {
    String formatted = brandedNotice(message);
    clientThread.invoke(
        () -> {
          if (client.getGameState() == GameState.LOGGED_IN) {
            chatMessageManager.queue(
                QueuedMessage.builder()
                    .type(ChatMessageType.CONSOLE)
                    .runeLiteFormattedMessage(formatted)
                    .build());
          }
        });
  }

  /**
   * One chat line: the brand-coloured prefix, then the message in the chat's own colour. The
   * prefix is part of the text because a CONSOLE message does not render the sender name.
   */
  static String brandedNotice(String message) {
    return new ChatMessageBuilder().append(BRAND, NOTICE_PREFIX).append(message).build();
  }
}
