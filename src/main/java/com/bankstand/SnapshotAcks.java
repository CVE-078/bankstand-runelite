package com.bankstand;

import com.bankstand.dto.SubmitSnapshotResponse;
import java.time.Instant;
import java.util.Map;

/** Whether a snapshot submission's answer may advance a baseline, and when a refusal retries. */
final class SnapshotAcks {
  // A submit refused by that cooldown is retried once, within these bounds.
  private static final long COOLDOWN_RETRY_FALLBACK_MS = 60_000L;
  private static final long COOLDOWN_RETRY_MAX_MS = 120_000L;

  private SnapshotAcks() {}

  /**
   * Whether a read taken during logout is worth submitting. A cleared client reads as zeroes or
   * nothing, which would be an XP regression, so the read is checked against the last trusted
   * one. Discarding is free: the next login re-reads.
   */
  static boolean isPlausibleFinalRead(
      Map<String, Integer> previous, Map<String, Integer> fresh) {
    if (fresh == null || fresh.isEmpty()) {
      return false;
    }
    // No trusted read this session: a cleared client cannot be told apart, so skip it.
    if (previous == null) {
      return false;
    }
    for (Map.Entry<String, Integer> before : previous.entrySet()) {
      Integer now = fresh.get(before.getKey());
      if (now == null || now < before.getValue()) {
        return false;
      }
    }
    return true;
  }

  // Baselines advance only on the per-block ack. The server answers 200 accepted=true even for
  // outcomes that store nothing (stale, regression, unclaimed, not_applied).
  static boolean shouldAdvanceSkills(SubmitSnapshotResponse res) {
    return res.isBlockStored("skills");
  }

  // Optional blocks must also have been sent. The whole-submission verdict is not enough: a
  // one-shot fact like a diary tier, falsely acked, would never be resent.
  static boolean shouldAdvanceQuests(SubmitSnapshotResponse res, boolean questsIncluded) {
    return questsIncluded && res.isBlockStored("quests");
  }

  static boolean shouldAdvanceDiaries(SubmitSnapshotResponse res, boolean diariesIncluded) {
    return diariesIncluded && res.isBlockStored("diaries");
  }

  static boolean shouldAdvanceCollectionLog(SubmitSnapshotResponse res, boolean included) {
    return included && res.isBlockStored("collectionLog");
  }

  static boolean shouldAdvanceCombatAchievements(
      SubmitSnapshotResponse res, boolean included) {
    return included && res.isBlockStored("combatAchievements");
  }

  // A false ack here never self-heals: an account type changes once if ever.
  static boolean shouldAdvanceAccountType(SubmitSnapshotResponse res, boolean included) {
    return included && res.isBlockStored("accountType");
  }

  static boolean shouldAdvanceSlayerTask(SubmitSnapshotResponse res, boolean included) {
    return included && res.isBlockStored("slayerTask");
  }

  /** Milliseconds until a cooldown-refused submit may retry, or -1 when it was not refused. */
  static long cooldownRetryDelayMs(String reason, String nextSubmitAfter, long nowMs) {
    if (!"cooldown".equals(reason)) {
      return -1;
    }
    long delay;
    try {
      // A second of margin, so the retry does not land on the window's edge.
      delay = Instant.parse(nextSubmitAfter).toEpochMilli() - nowMs + 1000L;
    } catch (RuntimeException e) {
      delay = COOLDOWN_RETRY_FALLBACK_MS;
    }
    return Math.max(1000L, Math.min(COOLDOWN_RETRY_MAX_MS, delay));
  }

  static boolean isSuperseded(long seq, long latestSeq) {
    return seq != latestSeq;
  }
}
