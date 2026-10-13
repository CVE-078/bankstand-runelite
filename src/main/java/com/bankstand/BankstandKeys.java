package com.bankstand;

import java.util.Locale;

/**
 * Storage keys and defaults. Config keys must match {@link BankstandConfig}, since the plugin
 * also writes some back through {@code ConfigManager}.
 *
 * <p>Device credentials are never config items: {@link DeviceCredentialStore} keeps them in a
 * local file under {@code RUNELITE_DIR}, unencrypted (a key would live on the same machine).
 * Never log the token.
 */
public final class BankstandKeys {
  private BankstandKeys() {}

  public static final String GROUP = "bankstand";

  /**
   * The chat command. Never start it with "bank": the game matches its own {@code ::bank} on
   * the prefix and posts a message in public chat.
   */
  public static final String COMMAND = "bstand";

  public static final String COMMAND_ALIAS = "stand";

  // Never rename: an existing pairing depends on these keys.
  public static final String KEY_SERVER_URL = "serverBaseUrl";
  public static final String KEY_PAIRING_CODE = "pairingCode";
  public static final String KEY_DISCONNECT = "disconnect";

  // "collect", not "share": the client decides what is read; the website decides who sees it.
  public static final String KEY_COLLECT_SKILLS = "collectSkills";
  public static final String KEY_COLLECT_QUESTS = "collectQuests";
  public static final String KEY_COLLECT_DIARIES = "collectDiaries";
  public static final String KEY_COLLECT_COLLECTION_LOG = "collectCollectionLog";
  public static final String KEY_COLLECT_COMBAT_ACHIEVEMENTS = "collectCombatAchievements";
  public static final String KEY_COLLECT_ACCOUNT_TYPE = "collectAccountType";

  public static final String KEY_COLLECT_NOTABLE_DROPS = "collectNotableDrops";
  public static final String KEY_NOTABLE_DROP_THRESHOLD = "notableDropThreshold";
  public static final String KEY_COLLECT_PET_DROPS = "collectPetDrops";

  // Live sessions: three separate toggles, none turns another on.
  public static final String KEY_COLLECT_SESSIONS = "collectSessions";
  public static final String KEY_COLLECT_LOOT = "collectLoot";
  public static final String KEY_COLLECT_SLAYER = "collectSlayer";

  // Credentials, never surfaced as config items.
  public static final String KEY_DEVICE_TOKEN = "deviceToken";
  public static final String KEY_DEVICE_ID = "deviceId";
  public static final String KEY_TOKEN_EXPIRES_AT = "tokenExpiresAt";

  /** Changing this reaches new installs only; a paired client keeps its stored URL. */
  public static final String DEFAULT_SERVER_URL = "https://bankstand.gg";

  /**
   * Trimmed, or the default when unset, blank or unusable. Every caller must use this. Plain http
   * is allowed only for localhost; any other http address is upgraded to https so the token is
   * never sent in the clear.
   */
  public static String normaliseServerUrl(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
      return DEFAULT_SERVER_URL;
    }
    String url = raw.trim();
    String lower = url.toLowerCase(Locale.ROOT);
    if (lower.startsWith("https://")) {
      return url;
    }
    if (!lower.startsWith("http://")) {
      return DEFAULT_SERVER_URL;
    }
    String rest = url.substring("http://".length());
    return isLocalhost(hostOf(rest)) ? url : "https://" + rest;
  }

  // Host of "host[:port][/path]", brackets kept for IPv6.
  private static String hostOf(String rest) {
    int slash = rest.indexOf('/');
    String authority = slash < 0 ? rest : rest.substring(0, slash);
    if (authority.startsWith("[")) {
      int close = authority.indexOf(']');
      return close < 0 ? authority : authority.substring(0, close + 1);
    }
    int colon = authority.indexOf(':');
    return colon < 0 ? authority : authority.substring(0, colon);
  }

  private static boolean isLocalhost(String host) {
    String h = host.toLowerCase(Locale.ROOT);
    return h.equals("localhost") || h.equals("127.0.0.1") || h.equals("[::1]");
  }
}
