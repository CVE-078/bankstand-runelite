package com.bankstand;

import com.bankstand.dto.SubmitSnapshotResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Blocks the server received but kept back, because they would lower progress it already has.
 * Not an ack: a held read is never a baseline. It only stops the same read being uploaded on
 * every capture. Memory only, per login, so a restart, relog or re-pair offers it again.
 * Client thread.
 */
final class HeldBlocks {

  /** The diary task vars rider, which is not a capability of its own. */
  static final String DIARY_TASK_VARS = "diaryTaskVars";

  // Bounds stale suppression after a removal on the server the plugin cannot see.
  static final long EXPIRY_MS = 30 * 60 * 1000L;

  private static final class Held {
    final long accountHash;
    final int generation;
    final String digest;
    final long atMs;

    Held(long accountHash, int generation, String digest, long atMs) {
      this.accountHash = accountHash;
      this.generation = generation;
      this.digest = digest;
      this.atMs = atMs;
    }
  }

  private final Map<String, Held> held = new HashMap<>();
  private final LongSupplier clock;

  HeldBlocks(LongSupplier clock) {
    this.clock = clock;
  }

  /** The capability keys (and the vars rider) a response held. A wider block wins over stored. */
  static Set<String> heldKeys(SubmitSnapshotResponse res) {
    List<String> blocks = res.getHeldBlocks();
    Set<String> keys = new HashSet<>();
    if (blocks.contains("quests")) {
      keys.add(Capability.QUESTS.key);
    }
    // The diaries capability carries the flags and counts together.
    if (blocks.contains("diaries") || blocks.contains("diaryTasks")) {
      keys.add(Capability.DIARIES.key);
    }
    if (blocks.contains(DIARY_TASK_VARS)) {
      keys.add(DIARY_TASK_VARS);
    }
    if (blocks.contains("combatAchievementLampClaimed")) {
      keys.add(Capability.COMBAT_ACHIEVEMENTS.key);
    }
    return keys;
  }

  /** Whether this read is the one last held for this login, so sending it again is pointless. */
  @SafeVarargs
  final boolean suppresses(String key, long accountHash, int generation, Map<String, ?>... read) {
    Held h = held.get(key);
    if (h == null || h.accountHash != accountHash || h.generation != generation) {
      return false;
    }
    if (clock.getAsLong() - h.atMs >= EXPIRY_MS) {
      held.remove(key);
      return false;
    }
    return h.digest.equals(digest(read));
  }

  /**
   * Applies the server's verdict on one block and returns whether it may be acked: held wins over
   * stored, a held block records what was sent, and a stored one clears its hold.
   */
  @SafeVarargs
  final boolean settle(
      String key,
      SubmitSnapshotResponse res,
      boolean stored,
      long accountHash,
      int generation,
      Map<String, ?>... sent) {
    if (sent[0] != null && heldKeys(res).contains(key)) {
      held.put(key, new Held(accountHash, generation, digest(sent), clock.getAsLong()));
      return false;
    }
    if (stored) {
      held.remove(key);
    }
    return stored;
  }

  /** The keys held for this login, for the panel. */
  Set<String> current(long accountHash, int generation) {
    Set<String> keys = new HashSet<>();
    held.forEach(
        (key, h) -> {
          if (h.accountHash == accountHash && h.generation == generation) {
            keys.add(key);
          }
        });
    return keys;
  }

  void clear() {
    held.clear();
  }

  private static String digest(Map<String, ?>[] blocks) {
    StringBuilder all = new StringBuilder();
    for (Map<String, ?> block : blocks) {
      all.append(block == null ? "-" : CapabilityDigest.of(block)).append('/');
    }
    return all.toString();
  }
}
