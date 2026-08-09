package com.bankstand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the v1 skills-submit envelope as an ordered map, ready for Gson. The
 * account hash is emitted as a decimal string because a 64-bit value does not fit
 * a JSON number safely, matching the server's contract. The shape is frozen; see
 * the mirrored contract fixtures under src/test/resources/contracts.
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
    // How many of each tier's tasks are done, which is what makes a tier at 21 of 22
    // expressible at all: the completion flag above is true only when every task is
    // done, so a partly finished tier has been reporting as nothing. Counts, never
    // task identities. Omitted when empty like every other block, and for the same
    // reason: empty means "not observed", not "none done".
    if (diaryTaskCounts != null && !diaryTaskCounts.isEmpty()) {
      body.put("diaryTasks", new LinkedHashMap<>(diaryTaskCounts));
    }
    // An array, not a map: this capability records presence only. Omitted when empty
    // for the same reason as every other block, and the reason is load-bearing here
    // too: an empty block means "not observed", and the server would otherwise have
    // to distinguish that from "owns nothing".
    if (collectionLogItems != null && !collectionLogItems.isEmpty()) {
      body.put("collectionLog", new ArrayList<>(collectionLogItems));
    }
    // Per-tier completed COUNTS, which is all the game exposes. Omitted when empty
    // for the same reason as every other block: an empty block means "not observed",
    // and the server treats a present-but-empty one as an erase.
    if (combatAchievementCounts != null && !combatAchievementCounts.isEmpty()) {
      body.put("combatAchievements", new LinkedHashMap<>(combatAchievementCounts));
    }
    // One word, and the only block that is not a collection: the game has exactly one
    // answer. Sent because the hiscores cannot answer it at all for a Group Ironman,
    // who is absent from the ironman boards and so reads as a main everywhere public.
    //
    // Null when the varbit held a value this build has no name for, per
    // AccountTypes.keyFor, which is the same "omit rather than guess" the blocks
    // above use for empty. A wrong type is worse than no type: it is the badge on
    // the player's own profile.
    if (accountType != null && !accountType.isEmpty()) {
      body.put("accountType", accountType);
    }
    return body;
  }
}
