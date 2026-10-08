package com.bankstand;

/** The last stored slayer task. In memory, reset on an account switch. */
final class SlayerTaskBaseline {
  private SlayerTask acked;

  boolean changedSince(SlayerTask current) {
    return current != null && !current.equals(acked);
  }

  void advance(SlayerTask ackedNow) {
    acked = ackedNow;
  }

  void reset() {
    acked = null;
  }
}
