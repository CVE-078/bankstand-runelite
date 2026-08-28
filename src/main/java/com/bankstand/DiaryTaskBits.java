package com.bankstand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * The last-known value of every tracked diary varplayer, keyed by varplayer id, so a fresh
 * read can be XORed against it to find which individual bit(s) just flipped.
 *
 * <p>Unlike {@link DiaryVarbits}/{@link DiaryTaskVarbits}, whose "baseline" is really just a
 * digest (a tier is cheap to re-read whole, so only whether it changed matters), per-task
 * identity needs the actual prior bit pattern: a digest can say something changed, never
 * which bit. This is the same reason {@link CollectionLogAccumulator} persists raw observed
 * ids rather than a hash, and for the same restart-survival reason: a transition observed
 * only once, between two client sessions, cannot be re-asked of the game.
 *
 * <p><b>First observation of any one varplayer establishes its baseline and reports no
 * bits</b>, deliberately. Without this, a player who already has tasks done before this
 * ships (or before a client restart lost this class's in-memory state) would have every
 * already-set bit reported as newly flipped the moment it is first read, exactly the
 * "a first-seen player must not spam its baseline as events" rule the engine already
 * enforces, applied here to a per-varplayer baseline instead of a whole account.
 *
 * <p>Always advances to the freshly read value, whether or not any bit resolves to a task,
 * so a later read diffs from the right baseline regardless of resolution outcome.
 *
 * <p>Persisted per character, restored from {@link AckedState#getDiaryTaskBits()} the same
 * way {@link CollectionLogAccumulator} restores from {@code collectionLogItems}, and reset
 * on an account switch like every other per-character baseline in this plugin.
 */
@Slf4j
public class DiaryTaskBits {

  private final Map<Integer, Integer> lastKnown = new LinkedHashMap<>();

  /**
   * Diffs {@code newValue} against the stored value for {@code varplayerId}, advances the
   * stored value to {@code newValue} regardless of the outcome, and returns the 0-based bit
   * positions that went from unset to set. Empty when this varplayer has never been read
   * before (baseline establishes silently) or when nothing newly set.
   */
  public int[] diff(int varplayerId, int newValue) {
    Integer previous = lastKnown.put(varplayerId, newValue);
    if (previous == null) {
      return new int[0];
    }
    int newlySet = ~previous & newValue;
    int unset = previous & ~newValue;
    if (unset != 0) {
      // Should not happen for a diary task: nothing in the game un-completes one. Not
      // transmitted, not thrown; a local note for whoever reads the log, the same
      // treatment DiaryTaskCompletionCapture already gives a mismatch below.
      log.debug("varplayer {} lost bits {} (0x{}), reading a diary task backwards",
          varplayerId, Integer.toBinaryString(unset), Integer.toHexString(unset));
    }
    if (newlySet == 0) {
      return new int[0];
    }
    List<Integer> positions = new ArrayList<>();
    for (int bit = 0; bit < 32; bit++) {
      if ((newlySet & (1 << bit)) != 0) {
        positions.add(bit);
      }
    }
    int[] result = new int[positions.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = positions.get(i);
    }
    return result;
  }

  /** Forgets every varplayer's baseline. Called on an account switch: a bit pattern
   *  belongs to one character, and carrying it across would diff one account's diary
   *  against another's. */
  public void reset() {
    lastKnown.clear();
  }

  /** Restores values read back from disk, replacing whatever is currently held. */
  public void restore(Map<Integer, Integer> stored) {
    lastKnown.clear();
    lastKnown.putAll(stored);
  }

  /** Everything currently known, for persisting. */
  public Map<Integer, Integer> snapshot() {
    return new LinkedHashMap<>(lastKnown);
  }
}
