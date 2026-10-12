package com.bankstand;

/**
 * Change gate: the collection log item count the server acknowledged. A count suffices because
 * the observed set only grows. Advances only on an ack, so a dropped submit is retried.
 */
public class CollectionLogBaseline {
  private int acked = -1;

  public boolean changedSince(int observedCount) {
    return observedCount > 0 && observedCount != acked;
  }

  public void advance(int ackedNow) {
    acked = ackedNow;
  }

  /**
   * Minus one means nothing is known. Restore only together with the accumulator it counts, or
   * the gate churns (empty set) or swallows an unsent log (count derived from the set).
   */
  public void restore(int ackedNow) {
    acked = ackedNow;
  }

  public int ackedCount() {
    return acked;
  }

  public void reset() {
    acked = -1;
  }
}
