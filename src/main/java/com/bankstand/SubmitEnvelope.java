package com.bankstand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the v1 submit envelope. The shape is a frozen server contract, pinned by the
 * fixtures under src/test/resources/contracts.
 *
 * <p>Every block is omitted when empty: absent means "not observed", and the server
 * treats a present-but-empty block as an erase.
 */
public final class SubmitEnvelope {
  private SubmitEnvelope() {}

  public static final int SCHEMA_VERSION = 1;

  public static Map<String, Object> body(
      String submissionId,
      int schemaVersion,
      String pluginVersion,
      String capturedAt,
      long accountHash,
      String displayName,
      Map<String, Integer> skillXp) {
    return body(
        submissionId, schemaVersion, pluginVersion, capturedAt, accountHash, displayName, skillXp,
        null);
  }

  public static Map<String, Object> body(
      String submissionId,
      int schemaVersion,
      String pluginVersion,
      String capturedAt,
      long accountHash,
      String displayName,
      Map<String, Integer> skillXp,
      Map<String, String> questStates) {
    return body(
        submissionId, schemaVersion, pluginVersion, capturedAt, accountHash, displayName, skillXp,
        questStates, null);
  }

  public static Map<String, Object> body(
      String submissionId,
      int schemaVersion,
      String pluginVersion,
      String capturedAt,
      long accountHash,
      String displayName,
      Map<String, Integer> skillXp,
      Map<String, String> questStates,
      Map<String, String> diaryStates) {
    return body(
        submissionId, schemaVersion, pluginVersion, capturedAt, accountHash, displayName, skillXp,
        questStates, diaryStates, null, null, null, null);
  }

  public static Map<String, Object> body(
      String submissionId,
      int schemaVersion,
      String pluginVersion,
      String capturedAt,
      long accountHash,
      String displayName,
      Map<String, Integer> skillXp,
      Map<String, String> questStates,
      Map<String, String> diaryStates,
      Collection<Integer> collectionLogItems) {
    return body(
        submissionId, schemaVersion, pluginVersion, capturedAt, accountHash, displayName, skillXp,
        questStates, diaryStates, collectionLogItems, null, null, null);
  }

  public static Map<String, Object> body(
      String submissionId,
      int schemaVersion,
      String pluginVersion,
      String capturedAt,
      long accountHash,
      String displayName,
      Map<String, Integer> skillXp,
      Map<String, String> questStates,
      Map<String, String> diaryStates,
      Collection<Integer> collectionLogItems,
      Map<String, Integer> combatAchievementCounts,
      Map<String, Integer> diaryTaskCounts,
      String accountType) {
    return body(
        submissionId, schemaVersion, pluginVersion, capturedAt, accountHash, displayName, skillXp,
        questStates, diaryStates, collectionLogItems, combatAchievementCounts, diaryTaskCounts,
        accountType, false);
  }

  public static Map<String, Object> body(
      String submissionId,
      int schemaVersion,
      String pluginVersion,
      String capturedAt,
      long accountHash,
      String displayName,
      Map<String, Integer> skillXp,
      Map<String, String> questStates,
      Map<String, String> diaryStates,
      Collection<Integer> collectionLogItems,
      Map<String, Integer> combatAchievementCounts,
      Map<String, Integer> diaryTaskCounts,
      String accountType,
      boolean fullEnumeration) {
    return body(
        submissionId, schemaVersion, pluginVersion, capturedAt, accountHash, displayName, skillXp,
        questStates, diaryStates, collectionLogItems, combatAchievementCounts, diaryTaskCounts,
        accountType, fullEnumeration, null);
  }

  /** Without the slayer task. */
  public static Map<String, Object> body(
      String submissionId,
      int schemaVersion,
      String pluginVersion,
      String capturedAt,
      long accountHash,
      String displayName,
      Map<String, Integer> skillXp,
      Map<String, String> questStates,
      Map<String, String> diaryStates,
      Collection<Integer> collectionLogItems,
      Map<String, Integer> combatAchievementCounts,
      Map<String, Integer> diaryTaskCounts,
      String accountType,
      boolean fullEnumeration,
      Map<String, Integer> combatAchievementBossCounts) {
    return body(
        submissionId, schemaVersion, pluginVersion, capturedAt, accountHash, displayName, skillXp,
        questStates, diaryStates, collectionLogItems, combatAchievementCounts, diaryTaskCounts,
        accountType, fullEnumeration, combatAchievementBossCounts, null);
  }

  public static Map<String, Object> body(
      String submissionId,
      int schemaVersion,
      String pluginVersion,
      String capturedAt,
      long accountHash,
      String displayName,
      Map<String, Integer> skillXp,
      Map<String, String> questStates,
      Map<String, String> diaryStates,
      Collection<Integer> collectionLogItems,
      Map<String, Integer> combatAchievementCounts,
      Map<String, Integer> diaryTaskCounts,
      String accountType,
      boolean fullEnumeration,
      Map<String, Integer> combatAchievementBossCounts,
      Map<String, Object> slayerTask) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("submissionId", submissionId);
    body.put("schemaVersion", schemaVersion);
    body.put("pluginVersion", pluginVersion);
    body.put("capturedAt", capturedAt);
    body.put("accountHash", Long.toString(accountHash));
    if (displayName != null && !displayName.trim().isEmpty()) {
      body.put("displayName", displayName);
    }
    Map<String, Object> skills = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : skillXp.entrySet()) {
      Map<String, Object> stat = new LinkedHashMap<>();
      stat.put("xp", e.getValue());
      skills.put(e.getKey(), stat);
    }
    body.put("skills", skills);
    if (questStates != null && !questStates.isEmpty()) {
      body.put("quests", new LinkedHashMap<>(questStates));
    }
    if (diaryStates != null && !diaryStates.isEmpty()) {
      body.put("diaries", new LinkedHashMap<>(diaryStates));
    }
    if (diaryTaskCounts != null && !diaryTaskCounts.isEmpty()) {
      body.put("diaryTasks", new LinkedHashMap<>(diaryTaskCounts));
    }
    if (collectionLogItems != null && !collectionLogItems.isEmpty()) {
      body.put("collectionLog", new ArrayList<>(collectionLogItems));
      // Only after a complete guided read (Search open). Never sent as false.
      if (fullEnumeration) {
        body.put("collectionLogFullyEnumerated", true);
      }
    }
    if (combatAchievementCounts != null && !combatAchievementCounts.isEmpty()) {
      body.put("combatAchievements", new LinkedHashMap<>(combatAchievementCounts));
    }
    if (combatAchievementBossCounts != null && !combatAchievementBossCounts.isEmpty()) {
      body.put("combatAchievementBossCounts", new LinkedHashMap<>(combatAchievementBossCounts));
    }
    // Null for an unknown varbit value: omit rather than send a wrong type.
    if (accountType != null && !accountType.isEmpty()) {
      body.put("accountType", accountType);
    }
    // {"active": false} is an observed state; absent means not read this time.
    if (slayerTask != null && !slayerTask.isEmpty()) {
      body.put("slayerTask", new LinkedHashMap<>(slayerTask));
    }
    return body;
  }
}
