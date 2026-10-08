package com.bankstand;

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

  /** Trimmed, or the default when unset or blank. Every caller must use this. */
  public static String normaliseServerUrl(String raw) {
    return raw == null || raw.trim().isEmpty() ? DEFAULT_SERVER_URL : raw.trim();
  }
}
