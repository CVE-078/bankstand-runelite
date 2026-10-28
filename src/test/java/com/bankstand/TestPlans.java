package com.bankstand;

import java.util.Map;
import java.util.Set;

/** Shorter {@link BankstandPlugin#plan} forms for tests that leave the later capabilities off. */
final class TestPlans {

  private TestPlans() {}

  /** Without combat achievements. */
  static BankstandPlugin.SubmitPlan plan(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries,
      CollectionLogBaseline collectionLogBaseline,
      Set<Integer> collectionLogItems) {
    return plan(
        skillBaseline,
        skills,
        questBaseline,
        quests,
        diaryBaseline,
        diaries,
        collectionLogBaseline,
        collectionLogItems,
        new CombatAchievementBaseline(),
        null,
        new AccountTypeBaseline(),
        null,
        false);
  }

  /** Without the account type. */
  static BankstandPlugin.SubmitPlan plan(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries,
      CollectionLogBaseline collectionLogBaseline,
      Set<Integer> collectionLogItems,
      CombatAchievementBaseline combatAchievementBaseline,
      Map<String, Integer> combatAchievements) {
    return plan(
        skillBaseline,
        skills,
        questBaseline,
        quests,
        diaryBaseline,
        diaries,
        collectionLogBaseline,
        collectionLogItems,
        combatAchievementBaseline,
        combatAchievements,
        new AccountTypeBaseline(),
        null,
        false);
  }

  /** Without the full-enumeration signal. */
  static BankstandPlugin.SubmitPlan plan(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries,
      CollectionLogBaseline collectionLogBaseline,
      Set<Integer> collectionLogItems,
      CombatAchievementBaseline combatAchievementBaseline,
      Map<String, Integer> combatAchievements,
      AccountTypeBaseline accountTypeBaseline,
      String accountType) {
    return plan(
        skillBaseline,
        skills,
        questBaseline,
        quests,
        diaryBaseline,
        diaries,
        collectionLogBaseline,
        collectionLogItems,
        combatAchievementBaseline,
        combatAchievements,
        accountTypeBaseline,
        accountType,
        false);
  }

  /** Without the slayer task. */
  static BankstandPlugin.SubmitPlan plan(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries,
      CollectionLogBaseline collectionLogBaseline,
      Set<Integer> collectionLogItems,
      CombatAchievementBaseline combatAchievementBaseline,
      Map<String, Integer> combatAchievements,
      AccountTypeBaseline accountTypeBaseline,
      String accountType,
      boolean fullEnumerationPending) {
    return BankstandPlugin.plan(
        skillBaseline,
        skills,
        questBaseline,
        quests,
        diaryBaseline,
        diaries,
        null,
        collectionLogBaseline,
        collectionLogItems,
        combatAchievementBaseline,
        combatAchievements,
        accountTypeBaseline,
        accountType,
        fullEnumerationPending,
        new SlayerTaskBaseline(),
        null);
  }
}
