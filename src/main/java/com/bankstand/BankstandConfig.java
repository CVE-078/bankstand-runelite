package com.bankstand;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BankstandConfig.GROUP)
public interface BankstandConfig extends Config {
  String GROUP = "bankstand";

  // Persisted via ConfigManager under this group but NOT declared as @ConfigItem,
  // so they never render in the config panel. The device token is a bearer
  // credential: it is stored here (RuneLite config, local and unencrypted, which
  // is acceptable for a manual dogfood build) and is never logged.
  String KEY_DEVICE_TOKEN = "deviceToken";
  String KEY_DEVICE_ID = "deviceId";
  String KEY_TOKEN_EXPIRES_AT = "tokenExpiresAt";

  @ConfigItem(
      keyName = "serverBaseUrl",
      name = "Server URL",
      description =
          "The Bankstand server to pair against. Use http://localhost:3000 for local testing.")
  default String serverBaseUrl() {
    return "https://bankstand.christiaanvaneijnsbergen.nl";
  }
}
