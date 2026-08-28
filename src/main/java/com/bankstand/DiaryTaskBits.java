package com.bankstand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Last-known value of every tracked diary varplayer, keyed by varplayer id, so a fresh
 * read can be XORed against it to find which bit(s) just flipped.
 *
 * <p>{@link DiaryVarbits}/{@link DiaryTaskVarbits} get away with a digest because a tier is
 * cheap to re-read whole; per-task identity needs the real prior bit pattern, since a
 * digest only says something changed, not which bit. Same reason
 * {@link CollectionLogAccumulator} persists raw ids instead of a hash.
 *
 * <p>First read of a varplayer sets its baseline and reports nothing, on purpose: without
 * that, a player with pre-existing progress (or a client that just restarted and lost this
 * in-memory state) would have every already-set bit reported as freshly completed.
 *
 * <p>Always advances to the value it just read, whether or not anything resolved, so the
 * next diff has the right baseline regardless.
 *
 * <p>Persisted per character via {@link AckedState#getDiaryTaskBits()}, reset on account
 * switch like every other baseline here.
 */
@Slf4j
public class DiaryTaskBits {

  private final Map<Integer, Integer> lastKnown = new LinkedHashMap<>();

  /**
   * Diffs {@code newValue} against the stored value, advances the baseline regardless, and
   * returns the 0-based bit positions that went from unset to set. Empty on a first read of
   * this varplayer, or when nothing newly set.
   */
  public int[] diff(int varplayerId, int newValue) {
    Integer previous = lastKnown.put(varplayerId, newValue);
    if (previous == null) {
      return new int[0];
    }
    int newlySet = ~previous & newValue;
    int unset = previous & ~newValue;
    if (unset != 0) {
      // Nothing un-completes a diary task, so this shouldn't happen. Log it, don't crash.
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

  /** Forgets every varplayer's baseline. Called on an account switch, so one account's
   *  diary is never diffed against another's. */
  public void reset() {
    lastKnown.clear();
  }

  /** Restores values read back from disk, replacing whatever is currently held. */
  public void restore(Map<Integer, Integer> stored) {
    lastKnown.clear();
    lastKnown.putAll(stored);
  }

  /** For persisting. */
  public Map<Integer, Integer> snapshot() {
    return new LinkedHashMap<>(lastKnown);
  }
}
