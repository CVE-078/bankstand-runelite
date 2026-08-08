package com.bankstand;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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
 * <p>The sections separate the two questions a player is actually answering, which are
 * not the same question: <b>Connection</b> is which Bankstand account this client is
 * tied to, and <b>Collect</b> is what this client reads and sends. Who may then SEE any
 * of it is a third question, answered on the website and deliberately not here: a
 * setting in the game client cannot be the source of truth for a server-side audience.
 *
 * <p>Every toggle in Collect names what it sends. That is the RuneLite Plugin Hub's
 * rule for a plugin that talks to a third-party server (the warning belongs on the
 * plugin or on the option that enables the sending), and it is why {@link
 * #collectSkills()} exists as an option at all rather than being implied by pairing.
 */
@ConfigGroup(BankstandKeys.GROUP)
public interface BankstandConfig extends Config {

  @ConfigSection(
      name = "Connection",
      description =
          "Which Bankstand account this client is paired with. Type ::bstand in game for"
              + " status, ::bstand sync to send now, or ::bstand link to re-link this"
              + " character.",
      position = 10)
  String connectionSection = "connection";

  @ConfigSection(
      name = "Collect",
      description =
          "What this client reads from the game and sends to Bankstand. Everything is"
              + " private to your own account by default. You choose who else can see"
              + " it, per capability, in your Bankstand privacy settings.",
      position = 20)
  String collectSection = "collect";

  @ConfigItem(
      keyName = BankstandKeys.KEY_SERVER_URL,
      name = "Server URL",
      description =
          "Where to send your data. Leave this alone unless you are running Bankstand"
              + " locally. A stale address here makes every update fail silently.",
      section = connectionSection,
      position = 1)
  default String serverBaseUrl() {
    return BankstandKeys.DEFAULT_SERVER_URL;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_PAIRING_CODE,
      name = "Pairing code",
      description =
          "Generate a code at Bankstand > Account > Connect RuneLite, then paste it here."
              + " It is exchanged for a device token and cleared.",
      section = connectionSection,
      position = 2)
  default String pairingCode() {
    return "";
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_DISCONNECT,
      name = "Disconnect",
      description =
          "Tick to forget this device's pairing. Nothing is sent until you pair again.",
      section = connectionSection,
      position = 3)
  default boolean disconnect() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_SKILLS,
      name = "Collect skill XP",
      description =
          "Sends your XP in each skill, your account hash and your display name while you"
              + " are paired. This is what identifies your character to Bankstand, so"
              + " turning it off stops the connection doing anything useful.",
      section = collectSection,
      position = 1)
  default boolean collectSkills() {
    return true;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_QUESTS,
      name = "Collect quest progress",
      description =
          "Sends which quests you have not started, started and finished, so your guides"
              + " can read from the game rather than inferring it.",
      section = collectSection,
      position = 2)
  default boolean collectQuests() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_DIARIES,
      // "Collect achievement diary progress" is too long for the config panel and
      // renders truncated. The description carries the full name instead.
      name = "Collect diary progress",
      description =
          "Sends which achievement diary tiers you have completed. Tier level only: a tier"
              + " you are part way through is not yet distinguishable from an untouched one.",
      section = collectSection,
      position = 3)
  default boolean collectDiaries() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_COLLECTION_LOG,
      name = "Collect collection log",
      description =
          "Sends which collection log slots you have filled. The game only reveals the log"
              + " while it is on screen, so browsing adds what you see. To read the whole"
              + " log in one go, open it and click Search.",
      section = collectSection,
      position = 4)
  default boolean collectCollectionLog() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_COMBAT_ACHIEVEMENTS,
      name = "Collect combat achievements",
      description =
          "Sends how many combat achievement tasks you have completed in each tier. Counts"
              + " only: the client does not expose which tasks, so a tier at 23 of 41 cannot"
              + " say which 23.",
      section = collectSection,
      position = 5)
  default boolean collectCombatAchievements() {
    return false;
  }
}
