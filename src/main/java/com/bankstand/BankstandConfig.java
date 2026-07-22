package com.bankstand;

/**
 * Storage keys and defaults for the plugin's persisted state. Everything is kept
 * via RuneLite's ConfigManager under {@link #GROUP} and managed from the side
 * panel, so there is no separate config screen to visit: the whole pairing flow
 * lives in one place. The device token is a bearer credential (local, unencrypted,
 * acceptable for a dogfood build) and is never logged.
 */
public final class BankstandConfig {
  private BankstandConfig() {}

  public static final String GROUP = "bankstand";

  // Keep the original key name so a client that paired with the first version (which
  // stored the URL under "serverBaseUrl") keeps its setting after the panel redesign.
  public static final String KEY_SERVER_URL = "serverBaseUrl";
  public static final String KEY_DEVICE_TOKEN = "deviceToken";
  public static final String KEY_DEVICE_ID = "deviceId";
  public static final String KEY_TOKEN_EXPIRES_AT = "tokenExpiresAt";

  public static final String DEFAULT_SERVER_URL =
      "https://bankstand.christiaanvaneijnsbergen.nl";
}
