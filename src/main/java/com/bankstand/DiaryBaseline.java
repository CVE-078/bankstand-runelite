package com.bankstand;

import java.util.Map;

/**
 * Change gate: the digest of the last diary-state vector the server acknowledged. Advances only on
 * an ack, so a dropped submit is retried. Also keeps the acked values themselves: diary progress
 * never goes back, so a read below them is one the game has not finished sending.
 */
public class DiaryBaseline {
  /** About two minutes. A login's values arrive well within it; a lower read after it is real. */
  static final int HOLD_TICKS = 200;

  private String acked;
  private Map<String, String> flags;
  private Map<String, Integer> counts;
  private Map<String, Map<String, Integer>> vars;

  public boolean changedSince(Map<String, String> current) {
    return !CapabilityDigest.of(current).equals(acked);
  }

  public void advance(Map<String, String> ackedNow) {
    advance(ackedNow, null);
  }

  public void advance(Map<String, String> ackedFlags, Map<String, Integer> ackedCounts) {
    acked = CapabilityDigest.of(ackedFlags);
    flags = ackedFlags;
    counts = ackedCounts;
  }

  @SuppressWarnings("unchecked")
  public void advanceVars(Map<String, Object> ackedVars) {
    vars = (Map<String, Map<String, Integer>>) (Map<String, ?>) ackedVars;
  }

  /** Null means nothing is known. */
  public void restore(String digest) {
    acked = digest;
  }

  public void restore(AckedState state) {
    restore(state.getDiaries());
    flags = state.getDiaryFlags();
    counts = state.getDiaryCounts();
    vars = state.getDiaryTaskVarValues();
  }

  public void saveTo(AckedState state) {
    state.setDiaries(acked);
    state.setDiaryFloor(flags, counts, vars);
  }

  public String ackedDigest() {
    return acked;
  }

  public void reset() {
    acked = null;
    flags = null;
    counts = null;
    vars = null;
  }

  /** True when no tier is complete and no count is above zero, as before the game sends them. */
  static boolean isAllZero(Map<String, String> flags, Map<String, Integer> counts) {
    return flags != null
        && !flags.containsValue("COMPLETE")
        && (counts == null || counts.values().stream().allMatch(n -> n == 0));
  }

  /** Whether a read soon after login is below the acked values, so not all of it has arrived. */
  boolean holds(
      int ticksSinceLogin,
      Map<String, String> readFlags,
      Map<String, Integer> readCounts,
      Map<String, Object> readVars) {
    return ticksSinceLogin < HOLD_TICKS && isBelowAcked(readFlags, readCounts, readVars);
  }

  /** Whether any part of a read is below what the server took. Unknown parts never are. */
  @SuppressWarnings("unchecked")
  boolean isBelowAcked(
      Map<String, String> readFlags, Map<String, Integer> readCounts, Map<String, Object> readVars) {
    if (readFlags != null && flags != null) {
      for (Map.Entry<String, String> e : flags.entrySet()) {
        if ("COMPLETE".equals(e.getValue()) && !"COMPLETE".equals(readFlags.get(e.getKey()))) {
          return true;
        }
      }
    }
    if (readCounts != null && below(counts, readCounts, false)) {
      return true;
    }
    if (readVars == null || vars == null) {
      return false;
    }
    // Varps are task bitfields, so a lost bit is a regression; varbits are counts and flags.
    return below(vars.get("varps"), (Map<String, Integer>) readVars.get("varps"), true)
        || below(vars.get("varbits"), (Map<String, Integer>) readVars.get("varbits"), false);
  }

  private static boolean below(
      Map<String, Integer> acked, Map<String, Integer> read, boolean bits) {
    if (acked == null || read == null) {
      return false;
    }
    for (Map.Entry<String, Integer> e : acked.entrySet()) {
      int was = e.getValue();
      int now = read.getOrDefault(e.getKey(), was);
      if (bits ? (was & ~now) != 0 : now < was) {
        return true;
      }
    }
    return false;
  }
}
