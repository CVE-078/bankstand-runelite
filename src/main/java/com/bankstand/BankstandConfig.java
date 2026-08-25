package com.bankstand;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

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
 * <p>One section per capability, not a shared "Collect" checkbox list: each toggle's
 * own threshold or filter field (where it has one) lives right beside it instead of in
 * a separate catch-all section, and a future capability gets its own section rather
 * than growing an already-long list. Who may then SEE any of it is a separate
 * question, answered on the website and deliberately not here: a setting in the game
 * client cannot be the source of truth for a server-side audience.
 *
 * <p>Every toggle names what it sends. That is the RuneLite Plugin Hub's rule for a
 * plugin that talks to a third-party server (the warning belongs on the plugin or on
 * the option that enables the sending), and it is why {@link #collectSkills()} exists
 * as an option at all rather than being implied by pairing.
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
      name = "Skill XP",
      description =
          "Everything is private to your own account by default. You choose who else"
              + " can see it, per capability, in your Bankstand privacy settings.",
      position = 20)
  String skillsSection = "skills";

  @ConfigSection(
      name = "Quests",
      description = "Whether this device sends your quest completion state.",
      position = 30)
  String questsSection = "quests";

  @ConfigSection(
      name = "Diary progress",
      description = "Whether this device sends your achievement diary progress.",
      position = 40)
  String diariesSection = "diaries";

  @ConfigSection(
      name = "Collection log",
      description = "Whether this device sends your collection log.",
      position = 50)
  String collectionLogSection = "collectionLog";

  @ConfigSection(
      name = "Combat achievements",
      description = "Whether this device sends your combat achievement progress.",
      position = 60)
  String combatAchievementsSection = "combatAchievements";

  @ConfigSection(
      name = "Account type",
      description = "Whether this device sends your account type.",
      position = 70)
  String accountTypeSection = "accountType";

  @ConfigSection(
      name = "Notable drops",
      description = "Whether, and above what value, this device sends a notable drop.",
      position = 80)
  String notableDropsSection = "notableDrops";

  @ConfigSection(
      name = "Pet drops",
      description = "Whether this device sends a pet drop.",
      position = 90)
  String petDropsSection = "petDrops";

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
      section = skillsSection,
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
      section = questsSection,
      position = 1)
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
      section = diariesSection,
      position = 1)
  default boolean collectDiaries() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_COLLECTION_LOG,
      name = "Collect collection log",
      description =
          "Sends which collection log slots you have filled. The game only reveals the log"
              + " while it is on screen, so browsing adds what you see. To read the whole"
              + " log in one go, open it and click Search. New unlocks are also sent as the"
              + " game announces them in chat, so you do not have to open the log to have"
              + " them captured.",
      section = collectionLogSection,
      position = 1)
  default boolean collectCollectionLog() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_COMBAT_ACHIEVEMENTS,
      name = "Collect combat achievements",
      description =
          "Sends how many combat achievement tasks you have completed in each tier, plus"
              + " which task as the game announces its completion in chat. The count comes"
              + " from what the client exposes for a tier as a whole; the task name only"
              + " covers tasks completed while this is on, so a tier at 23 of 41 cannot say"
              + " which 23 from before you turned it on.",
      section = combatAchievementsSection,
      position = 1)
  default boolean collectCombatAchievements() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_ACCOUNT_TYPE,
      name = "Collect account type",
      description =
          "Sends whether this account is a main, an ironman, or one of the group types."
              + " The hiscores cannot show a Group Ironman at all, so without this"
              + " Bankstand has to take your word for it. Your own answer still wins:"
              + " Bankstand shows you both and asks.",
      section = accountTypeSection,
      position = 1)
  default boolean collectAccountType() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_NOTABLE_DROPS,
      name = "Collect notable drops",
      description =
          "Sends unique, untradeable or high-value drops as they happen: the item, its"
              + " value where it has one, and where it came from.",
      section = notableDropsSection,
      position = 1)
  default boolean collectNotableDrops() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_NOTABLE_DROP_THRESHOLD,
      name = "Notable drop value threshold",
      description =
          "A tradeable drop is sent once its total GE value clears this many gp."
              + " Example: 1,000,000 only sends a drop worth 1m gp or more."
              + " Untradeable items are judged by name instead, not this number.",
      section = notableDropsSection,
      position = 2)
  @Range(min = 0)
  @Units(" gp")
  default int notableDropThreshold() {
    return 1_000_000;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_PET_DROPS,
      name = "Collect pet drops",
      description = "Sends which pet you received and when, as it happens.",
      section = petDropsSection,
      position = 1)
  default boolean collectPetDrops() {
    return false;
  }
}
