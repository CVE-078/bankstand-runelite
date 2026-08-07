package com.bankstand;

/**
 * The number of collection log items the server has acknowledged, used as the change
 * gate so a capture only submits when the log actually grew.
 *
 * <p>A COUNT is sufficient where the other baselines need the whole vector, because
 * this capability only ever accumulates: the game has no un-obtain, so the observed
 * set cannot shrink and cannot change without growing. Comparing sizes is therefore
 * exactly as strong as comparing sets here, and far cheaper on a set of ~1700.
 *
 * <p>Reset on an account switch and advanced only on the server's per-block
 * acknowledgement, so a dropped submit is retried on the next capture.
 */
public class CollectionLogBaseline {
  private int acked = -1;

  /** True when more items are known than the server has acknowledged. */
  public boolean changedSince(int observedCount) {
    return observedCount > 0 && observedCount != acked;
  }

  public void advance(int ackedNow) {
    acked = ackedNow;
  }

  /**
   * Restores a count read back from disk. Minus one means nothing is known.
   *
   * <p><b>Only ever alongside the accumulator it counts.</b> A count works as a gate
   * because the observed set grows monotonically, which holds across a restart only if
   * the set is restored too. Restore the count against an empty accumulator and the next
   * partial browse reads as a change, sends, acks, and churns every session after.
   * Derive the count from a restored set instead and a log whose submit failed is treated
   * as delivered and never sent again. A baseline and what it measures need the same
   * lifetime.
   */
  public void restore(int ackedNow) {
    acked = ackedNow;
  }

  /** The acknowledged count, or -1 when nothing has been. */
  public int ackedCount() {
    return acked;
  }

  public void reset() {
    acked = -1;
  }
}
