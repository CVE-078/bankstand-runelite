package com.bankstand;

import net.runelite.client.config.ConfigManager;

/**
 * Pairings made before the device file kept the token in ConfigManager. The unset matters most:
 * it is what deletes the token from RuneLite's config sync on a synced profile.
 */
final class LegacyCredentials {

  private LegacyCredentials() {}

  /**
   * Moves credentials from ConfigManager to the device file. A no-op after the first run. The file
   * wins if both exist, so a stale synced value never overwrites a newer pairing.
   */
  static void migrate(ConfigManager configManager, DeviceCredentialStore deviceStore) {
    String legacyToken =
        configManager.getConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_TOKEN);
    if (legacyToken == null || legacyToken.trim().isEmpty()) {
      return;
    }
    if (!deviceStore.load().isPaired()) {
      DeviceCredentials migrated = new DeviceCredentials();
      migrated.setToken(legacyToken);
      migrated.setDeviceId(
          configManager.getConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_ID));
      migrated.setExpiresAt(
          configManager.getConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_TOKEN_EXPIRES_AT));
      deviceStore.save(migrated);
    }
    forget(configManager);
  }

  /** Clears the pre-file credential keys, and on a synced profile the upstream copy. */
  static void forget(ConfigManager configManager) {
    configManager.unsetConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_TOKEN);
    configManager.unsetConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_ID);
    configManager.unsetConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_TOKEN_EXPIRES_AT);
  }
}
