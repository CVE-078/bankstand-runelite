package com.bankstand;

/**
 * Storage keys and defaults for the plugin's persisted state.
 *
 * <p>The keys a player edits ({@code serverBaseUrl}, {@code shareQuests}, {@code
 * shareDiaries}) are declared again as items on {@link BankstandConfig} and are read
 * through it. They are still named here because the plugin also writes some of them
 * back through {@link net.runelite.client.config.ConfigManager} (clearing the pairing
 * code, resetting the disconnect toggle), and both sides have to agree on the key.
 *
 * <p>The pairing credentials below are deliberately NOT config items: a device token
 * is a bearer credential and has no business in a settings screen. They live only
 * under this group, read and written directly. Storing them unencrypted is acceptable
 * for a dogfood build, and the token is never logged.
 */
public final class BankstandKeys {
  private BankstandKeys() {}

  public static final String GROUP = "bankstand";

  // Keep the original key names so a client that paired with an earlier version keeps
  // its settings. The URL in particular has now survived two UI redesigns.
  public static final String KEY_SERVER_URL = "serverBaseUrl";
  public static final String KEY_SHARE_QUESTS = "shareQuests";
  public static final String KEY_SHARE_DIARIES = "shareDiaries";
  public static final String KEY_PAIRING_CODE = "pairingCode";
  public static final String KEY_DISCONNECT = "disconnect";

  // Credentials, never surfaced as config items.
  public static final String KEY_DEVICE_TOKEN = "deviceToken";
  public static final String KEY_DEVICE_ID = "deviceId";
  public static final String KEY_TOKEN_EXPIRES_AT = "tokenExpiresAt";

  public static final String DEFAULT_SERVER_URL = "https://bankstand.christiaanvaneijnsbergen.nl";

  /**
   * The server URL to actually call: trimmed, or the default when unset or blank.
   *
   * <p>Every caller has to agree on this, because a present-but-blank URL is not the
   * same as an absent one to {@code ConfigManager} and would otherwise be used as-is
   * and fail every request at the socket. Surrounding whitespace matters for the same
   * reason: a pasted URL routinely carries a trailing space.
   */
  public static String normaliseServerUrl(String raw) {
    return raw == null || raw.trim().isEmpty() ? DEFAULT_SERVER_URL : raw.trim();
  }
}
