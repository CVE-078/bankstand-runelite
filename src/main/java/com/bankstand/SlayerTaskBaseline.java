package com.bankstand;

/**
 * The last slayer task the server acknowledged, as a change gate like the other
 * baselines: the block rides along only when it differs from what was last stored, and
 * the baseline only advances on the server's own per-block acknowledgement.
 *
 * <p>Session-scoped and reset on an account switch. Not persisted: a restart costs one
 * resend of a small block, and the task changes with almost every kill anyway.
 */
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
