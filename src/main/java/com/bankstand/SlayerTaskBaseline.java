package com.bankstand;

/** The last stored slayer task. In memory, reset on an account switch. */
final class SlayerTaskBaseline {
  private SlayerTask acked;

  boolean changedSince(SlayerTask current) {
    return current != null && !current.equals(acked);
  }

  /** The read, or null while no task may be varps not yet sent: as {@link AccountTypeBaseline}. */
  SlayerTask unlessHeld(int ticksSinceLogin, SlayerTask read) {
    boolean held =
        ticksSinceLogin < DiaryBaseline.HOLD_TICKS
            && read != null
            && !read.active
            && (acked == null || acked.active);
    return held ? null : read;
  }

  void advance(SlayerTask ackedNow) {
    acked = ackedNow;
  }

  void reset() {
    acked = null;
  }
}
