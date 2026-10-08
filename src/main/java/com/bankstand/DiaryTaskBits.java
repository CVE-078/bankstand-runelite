package com.bankstand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Last-known value of each diary varplayer, so a fresh read can be diffed to find which task bits
 * just flipped. Holds raw values, not a digest, because the diff needs the actual bits.
 *
 * <p>The first read of a varplayer only sets its baseline and reports nothing, so existing
 * progress is never reported as new.
 */
@Slf4j
public class DiaryTaskBits {

  private final Map<Integer, Integer> lastKnown = new LinkedHashMap<>();

  /**
   * Returns the 0-based bit positions that went from unset to set, and always advances the
   * baseline. Empty on a first read.
   */
  public int[] diff(int varplayerId, int newValue) {
    Integer previous = lastKnown.put(varplayerId, newValue);
    if (previous == null) {
      return new int[0];
    }
    int newlySet = ~previous & newValue;
    int unset = previous & ~newValue;
    if (unset != 0) {
      // Should not happen: nothing un-completes a diary task.
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

  /** Called on an account switch. */
  public void reset() {
    lastKnown.clear();
  }

  public void restore(Map<Integer, Integer> stored) {
    lastKnown.clear();
    lastKnown.putAll(stored);
  }

  public Map<Integer, Integer> snapshot() {
    return new LinkedHashMap<>(lastKnown);
  }
}
