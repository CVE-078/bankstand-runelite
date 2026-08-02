package com.bankstand;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * The plugin's whole UI. Everything a player sets lives in RuneLite's own settings
 * screen, so Bankstand takes no permanent slot in the sidebar for something it is
 * interacted with roughly once per device.
 *
 * <p>Two items are actions rather than settings, because config has no button:
 * pasting into {@link #pairingCode()} performs the pairing and the field is cleared
 * afterwards, and ticking {@link #disconnect()} forgets the credentials and untick
 * itself. Both are handled in {@code BankstandPlugin.onConfigChanged}.
 *
 * <p>Key names match the storage keys on {@link BankstandKeys} exactly, so an
 * existing pairing keeps its server URL and opt-ins across this change.
 */
@ConfigGroup(BankstandKeys.GROUP)
public interface BankstandConfig extends Config {

  @ConfigItem(
      keyName = BankstandKeys.KEY_SERVER_URL,
      name = "Server URL",
      description =
          "Where to send your data. Leave this alone unless you are running Bankstand"
              + " locally. A stale address here makes every update fail silently.",
      position = 1)
  default String serverBaseUrl() {
    return BankstandKeys.DEFAULT_SERVER_URL;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_SHARE_QUESTS,
      name = "Share quest progress",
      description =
          "Sends which quests you have started and finished so you can see them on your"
              + " guides. Only you can see it, it is never public.",
      position = 2)
  default boolean shareQuests() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_SHARE_DIARIES,
      name = "Share achievement diary progress",
      description =
          "Sends which achievement diary tiers you have completed so you can see them on"
              + " your guides. Only you can see it, it is never public.",
      position = 3)
  default boolean shareDiaries() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_PAIRING_CODE,
      name = "Pairing code",
      description =
          "Generate a code at Bankstand > Account > Connect RuneLite, then paste it here."
              + " It is exchanged for a device token and cleared.",
      position = 4)
  default String pairingCode() {
    return "";
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_DISCONNECT,
      name = "Disconnect",
      description =
          "Tick to forget this device's pairing. Nothing is sent until you pair again.",
      position = 5)
  default boolean disconnect() {
    return false;
  }
}
