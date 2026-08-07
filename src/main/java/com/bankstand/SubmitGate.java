package com.bankstand;

/**
 * Decides whether a capture is allowed to submit at all.
 *
 * <p>The inner HTTP retry handles a blip within one submit. This handles the outer loop,
 * where the two real problems live. A revoked token fails fast inside the submit and
 * then the next capture tries again sixty seconds later, forever, with {@link
 * NoticeGate} making sure the player only ever hears about it once: a permanent,
 * invisible retry loop. And a server that has been down an hour is still being hit every
 * sixty seconds.
 *
 * <p>Counted in captures rather than milliseconds, because the capture schedule is the
 * only clock this has, and a duration-based backoff shorter than the schedule can never
 * elapse while one longer than it is just a skip count in disguise.
 *
 * <p>Not thread-safe; touched only from the client thread.
 */
public class SubmitGate {

  /** About sixteen minutes at a sixty second capture. */
  public static final int MAX_SKIPPED_CAPTURES = 15;

  private boolean halted;
  private int consecutiveFailures;
  private int skipsRemaining;

  /** True when this capture may submit. Consumes one skip when backing off. */
  public boolean allow() {
    if (halted) {
      return false;
    }
    if (skipsRemaining > 0) {
      skipsRemaining--;
      return false;
    }
    return true;
  }

  /** A reached server. Clears any backoff, but never the halt. */
  public void onSuccess() {
    consecutiveFailures = 0;
    skipsRemaining = 0;
  }

  /** A failure worth waiting out. Doubles the wait, to a cap. */
  public void onFailure() {
    consecutiveFailures++;
    skipsRemaining = Math.min(MAX_SKIPPED_CAPTURES, (1 << Math.min(consecutiveFailures, 5)) - 1);
  }

  /**
   * A rejected or revoked token. Returns true the first time, so the caller announces it
   * once rather than on every capture that then gets refused.
   */
  public boolean onAuthFailure() {
    boolean announce = !halted;
    halted = true;
    return announce;
  }

  /** Re-pairing, the only thing that clears a halt. */
  public void resume() {
    halted = false;
    consecutiveFailures = 0;
    skipsRemaining = 0;
  }

  public boolean isHalted() {
    return halted;
  }
}
