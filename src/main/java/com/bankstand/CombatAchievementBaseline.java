package com.bankstand;

import java.util.Map;

/**
 * The last combat-achievement count vector the server acknowledged, used as a change gate so the
 * capture loop only submits when something moved. The plugin resets this on an
 * account switch and advances it only when a submit is acknowledged, so a dropped
 * submit is retried on the next capture (the baseline never moved).
 *
 * <p>Held as a digest rather than a copy of the vector, so the same value can be
 * written to disk and restored on the next client start. See {@link SkillBaseline} for
 * why one representation across memory and disk is worth a hash.
 */
public class CombatAchievementBaseline {
  private String acked;

  /** True when {@code current} differs from what the server last acknowledged. */
  public boolean changedSince(Map<String, Integer> current) {
    return !CapabilityDigest.of(current).equals(acked);
  }

  public void advance(Map<String, Integer> ackedNow) {
    acked = CapabilityDigest.of(ackedNow);
  }

  /** Restores a digest read back from disk. Null means nothing is known. */
  public void restore(String digest) {
    acked = digest;
  }

  /** The acknowledged digest, or null when nothing has been. */
  public String ackedDigest() {
    return acked;
  }

  public void reset() {
    acked = null;
  }
}
