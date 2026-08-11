package com.bankstand;

/**
 * Storage keys and defaults for the plugin's persisted state.
 *
 * <p>The keys a player edits ({@code serverBaseUrl}, {@code collectQuests}, {@code
 * collectDiaries}) are declared again as items on {@link BankstandConfig} and are read
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

  /**
   * The chat command, and its shorter alias.
   *
   * **Not "bankstand".** The game matches its own {@code ::bank} on the prefix, so
   * every {@code ::bankstand ...} made the player say "Hey, everyone, I just tried
   * to do something very silly!" in PUBLIC chat. That is the exact outcome {@code ::}
   * was chosen over {@code !} to avoid: these are private actions about one
   * player's own account. Anything beginning with "bank" is unusable here.
   *
   * Both forms are accepted so nobody has to remember which we picked; {@code bstand}
   * is the one the config and the unknown-command line name.
   */
  public static final String COMMAND = "bstand";

  public static final String COMMAND_ALIAS = "stand";

  // The connection keys keep their original names, so an existing pairing survives a
  // rename untouched. The URL in particular has now survived three UI redesigns.
  public static final String KEY_SERVER_URL = "serverBaseUrl";
  public static final String KEY_PAIRING_CODE = "pairingCode";
  public static final String KEY_DISCONNECT = "disconnect";

  // The capture keys are named collect, not share, because collection and visibility
  // are separate decisions: this client decides what is READ, and the website decides
  // who may SEE it. A key called share in a game client names the wrong one of the
  // two, at exactly the moment a player is making the choice.
  //
  // Renamed from shareQuests, shareDiaries and shareCollectionLog with no migration,
  // deliberately. A client that paired before the rename keeps its pairing and its
  // server URL, and reverts these three opt-ins to off. Off is the safe direction to
  // be wrong in, and the alternative was carrying legacy-key handling into a public
  // release to spare a handful of dogfood clients one re-tick.
  public static final String KEY_COLLECT_SKILLS = "collectSkills";
  public static final String KEY_COLLECT_QUESTS = "collectQuests";
  public static final String KEY_COLLECT_DIARIES = "collectDiaries";
  public static final String KEY_COLLECT_COLLECTION_LOG = "collectCollectionLog";
  public static final String KEY_COLLECT_COMBAT_ACHIEVEMENTS = "collectCombatAchievements";
  public static final String KEY_COLLECT_ACCOUNT_TYPE = "collectAccountType";

  // Events (#658): each transient-event detector gets its own enable toggle,
  // matching the pattern above, plus its own threshold/filter setting where
  // one applies.
  public static final String KEY_COLLECT_NOTABLE_DROPS = "collectNotableDrops";
  public static final String KEY_NOTABLE_DROP_THRESHOLD = "notableDropThreshold";
  public static final String KEY_COLLECT_PET_DROPS = "collectPetDrops";

  // Credentials, never surfaced as config items.
  public static final String KEY_DEVICE_TOKEN = "deviceToken";
  public static final String KEY_DEVICE_ID = "deviceId";
  public static final String KEY_TOKEN_EXPIRES_AT = "tokenExpiresAt";

  /**
   * The canonical origin, and the one a fresh install pairs against.
   *
   * A stale value here is not cosmetic: pairing and every submit go to a host that
   * may redirect, refuse, or answer for a different deployment, and the player sees
   * only that Bankstand stopped updating. An already-paired client keeps whatever it
   * stored, so changing this reaches new installs only.
   */
  public static final String DEFAULT_SERVER_URL = "https://bankstand.gg";

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
