package com.bankstand;

import java.util.Map;
import java.util.Set;

/** Which blocks a capture puts on the wire. Skills is required, so it is not listed. */
final class SubmitPlan {
  private final boolean submit;
  private final boolean quests;
  private final boolean diaries;
  private final boolean collectionLog;
  private final boolean fullEnumeration;
  private final boolean combatAchievements;
  private final boolean accountType;
  private final boolean slayerTask;

  private SubmitPlan(
      boolean submit,
      boolean quests,
      boolean diaries,
      boolean collectionLog,
      boolean fullEnumeration,
      boolean combatAchievements,
      boolean accountType,
      boolean slayerTask) {
    this.submit = submit;
    this.quests = quests;
    this.diaries = diaries;
    this.collectionLog = collectionLog;
    this.fullEnumeration = fullEnumeration;
    this.combatAchievements = combatAchievements;
    this.accountType = accountType;
    this.slayerTask = slayerTask;
  }

  boolean shouldSubmit() {
    return submit;
  }

  boolean includesQuests() {
    return quests;
  }

  boolean includesDiaries() {
    return diaries;
  }

  boolean includesCollectionLog() {
    return collectionLog;
  }

  // True only when the flag rides on this submission's collection log block.
  boolean includesFullEnumeration() {
    return fullEnumeration;
  }

  boolean includesCombatAchievements() {
    return combatAchievements;
  }

  boolean includesAccountType() {
    return accountType;
  }

  boolean includesSlayerTask() {
    return slayerTask;
  }

  /**
   * Decides what this capture sends, per capability: a block rides along only when it changed
   * since the server last acknowledged it.
   *
   * <p>Whole blocks only, never a delta: the server merges blocks with jsonb {@code ||}, a
   * top-level replace, so a partial block would erase the fields it left out. An unacked block
   * has no baseline, so it keeps re-sending until stored. An empty block is never sent: absent
   * means "not observed", empty would assert the player has none. Null means the opt-in is off.
   */
  static SubmitPlan plan(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries, Map<String, Integer> diaryTaskCounts,
      CollectionLogBaseline collectionLogBaseline,
      Set<Integer> collectionLogItems,
      CombatAchievementBaseline combatAchievementBaseline,
      Map<String, Integer> combatAchievements,
      AccountTypeBaseline accountTypeBaseline,
      String accountType,
      boolean fullEnumerationPending,
      SlayerTaskBaseline slayerTaskBaseline,
      SlayerTask slayerTask) {
    boolean sendQuests =
        quests != null && !quests.isEmpty() && questBaseline.changedSince(quests);
    boolean sendDiaries =
        diaries != null && !diaries.isEmpty() && diaryBaseline.changedSince(diaries, diaryTaskCounts);
    // The collection log counts toward the decision on its own. A pending full enumeration also
    // forces it, since a COMPLETE read can reveal no new ids. An empty log has no block to ride on.
    boolean sendCollectionLog =
        !collectionLogItems.isEmpty()
            && (collectionLogBaseline.changedSince(collectionLogItems.size())
                || fullEnumerationPending);
    boolean sendCombatAchievements =
        combatAchievements != null
            && !combatAchievements.isEmpty()
            && combatAchievementBaseline.changedSince(combatAchievements);
    // Counts on its own: the type rarely changes, so it must not wait on xp moving.
    boolean sendAccountType =
        accountType != null
            && !accountType.isEmpty()
            && accountTypeBaseline.changedSince(accountType);
    // On its own: a finished task must replace the stored one even with no xp change.
    boolean sendSlayerTask = slayerTaskBaseline.changedSince(slayerTask);
    boolean submit =
        skillBaseline.changedSince(skills)
            || sendQuests
            || sendDiaries
            || sendCollectionLog
            || sendCombatAchievements
            || sendAccountType
            || sendSlayerTask;
    return new SubmitPlan(
        submit,
        sendQuests,
        sendDiaries,
        sendCollectionLog,
        fullEnumerationPending && sendCollectionLog,
        sendCombatAchievements,
        sendAccountType,
        sendSlayerTask);
  }
}
