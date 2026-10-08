package com.bankstand;

import java.util.Map;

/**
 * Change gate: the digest of the last quest-state vector the server acknowledged. Advances only on
 * an ack, so a dropped submit is retried.
 */
public class QuestBaseline {
  private String acked;

  public boolean changedSince(Map<String, String> current) {
    return !CapabilityDigest.of(current).equals(acked);
  }

  public void advance(Map<String, String> ackedNow) {
    acked = CapabilityDigest.of(ackedNow);
  }

  /** Null means nothing is known. */
  public void restore(String digest) {
    acked = digest;
  }

  public String ackedDigest() {
    return acked;
  }

  public void reset() {
    acked = null;
  }
}
