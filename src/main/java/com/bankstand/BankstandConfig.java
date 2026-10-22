package com.bankstand;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/** Bankstand settings. Each toggle names what it sends, per the Plugin Hub rule. */
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
      name = "Progress",
      description =
          "Everything is private to your own account by default. You choose who else"
              + " can see it, per capability, in your Bankstand privacy settings.",
      position = 20)
  String progressSection = "progress";

  @ConfigSection(
      name = "Drops",
      description = "Notable drops and pets, sent as they happen.",
      position = 30)
  String dropsSection = "drops";

  @ConfigSection(
      name = "Play tracking",
      description = "Sessions, loot per monster and your Slayer task.",
      position = 40)
  String playSection = "play";

  @ConfigSection(
      name = "Advanced",
      description = "Only needed for testing against another Bankstand server.",
      position = 50,
      closedByDefault = true)
  String advancedSection = "advanced";

  @ConfigItem(
      keyName = BankstandKeys.KEY_SERVER_URL,
      name = "Server URL",
      description =
          "Where to send your data. Leave this alone unless you are running Bankstand"
              + " locally. Must be https; plain http only works for localhost. A stale"
              + " address here makes every update fail silently.",
      section = advancedSection,
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
      position = 1)
  default String pairingCode() {
    return "";
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_DISCONNECT,
      name = "Disconnect",
      description =
          "Tick to forget this device's pairing. Nothing is sent until you pair again.",
      section = connectionSection,
      position = 2)
  default boolean disconnect() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_SKILLS,
      name = "Skill XP",
      description =
          "Sends your XP in each skill while you are paired. Quest, diary, combat achievement,"
              + " collection log, account type and Slayer task progress ride on it. While any"
              + " capture is on, each login also sends your account hash and display name.",
      section = progressSection,
      position = 1)
  default boolean collectSkills() {
    return true;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_QUESTS,
      name = "Quest progress",
      description =
          "Sends which quests you have not started, started and finished, so your guides"
              + " can read from the game rather than inferring it.",
      section = progressSection,
      position = 2)
  default boolean collectQuests() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_DIARIES,
      // The full name renders truncated in the config panel.
      name = "Diary progress",
      description =
          "Sends which achievement diary tiers you have completed and how many tasks you"
              + " have done in each tier. When the game announces a finished diary task in"
              + " chat, it also sends that task's tier and area.",
      section = progressSection,
      position = 3)
  default boolean collectDiaries() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_COLLECTION_LOG,
      name = "Collection log",
      description =
          "Sends which collection log slots you have filled. The game only reveals the log"
              + " while it is on screen, so browsing adds what you see. To read the whole"
              + " log in one go, open it and click Search. New unlocks are also sent as the"
              + " game announces them in chat, so you do not have to open the log to have"
              + " them captured.",
      section = progressSection,
      position = 4)
  default boolean collectCollectionLog() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_COMBAT_ACHIEVEMENTS,
      name = "Combat achievements",
      description =
          "Sends how many combat achievement tasks you have completed in each tier, plus"
              + " which task as the game announces its completion in chat. The count comes"
              + " from what the client exposes for a tier as a whole; the task name only"
              + " covers tasks completed while this is on, so a tier at 23 of 41 cannot say"
              + " which 23 from before you turned it on.",
      section = progressSection,
      position = 5)
  default boolean collectCombatAchievements() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_ACCOUNT_TYPE,
      name = "Account type",
      description =
          "Sends whether this account is a main, an ironman, or one of the group types."
              + " The hiscores cannot show a Group Ironman at all, so without this"
              + " Bankstand has to take your word for it. Your own answer still wins:"
              + " Bankstand shows you both and asks.",
      section = progressSection,
      position = 6)
  default boolean collectAccountType() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_NOTABLE_DROPS,
      name = "Notable drops",
      description =
          "Sends unique, untradeable or high-value drops as they happen: the item, its"
              + " value where it has one, and where it came from.",
      section = dropsSection,
      position = 1)
  default boolean collectNotableDrops() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_NOTABLE_DROP_THRESHOLD,
      name = "Notable drop value",
      description =
          "A tradeable drop is sent once its total GE value clears this many gp."
              + " Example: 1,000,000 only sends a drop worth 1m gp or more."
              + " Untradeable items are judged by name instead, not this number.",
      section = dropsSection,
      position = 2)
  @Range(min = 0)
  @Units(" gp")
  default int notableDropThreshold() {
    return 1_000_000;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_PET_DROPS,
      name = "Pet drops",
      description = "Sends which pet you received and when, as it happens.",
      section = dropsSection,
      position = 3)
  default boolean collectPetDrops() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_SESSIONS,
      name = "Session log",
      description =
          "Sends when you log in and log out, and an online mark every minute while you"
              + " play, with whether the Loot log is on. Never your world, your location or"
              + " whether you are idle.",
      section = playSection,
      position = 1)
  default boolean collectSessions() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_LOOT,
      name = "Loot log",
      description =
          "Sends every item you receive from a kill, with the monster's name, grouped about"
              + " once a minute. Never loot from another player, never your bank, inventory"
              + " or equipment, and never the game's own kill counts.",
      section = playSection,
      position = 2)
  default boolean collectLoot() {
    return false;
  }

  @ConfigItem(
      keyName = BankstandKeys.KEY_COLLECT_SLAYER,
      name = "Slayer tracker",
      description =
          "Sends your current slayer task (the creature, how many are left, the master, your"
              + " points and streaks, and when you got it) and when you finish one. Never the"
              + " task's location.",
      section = playSection,
      position = 3)
  default boolean collectSlayer() {
    return false;
  }
}
