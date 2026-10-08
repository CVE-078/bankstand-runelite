package com.bankstand;

/**
 * Decides whether a capture may submit: backs off across captures on failure and halts on a
 * revoked token until re-pairing. Counted in captures, the only clock it has. Client thread only.
 */
public class SubmitGate {

  /** About sixteen minutes at a sixty second capture. */
  public static final int MAX_SKIPPED_CAPTURES = 15;

  private boolean halted;
  private int consecutiveFailures;
  private int skipsRemaining;

  /** Consumes one skip when backing off. */
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

  /** Clears any backoff, but never the halt. */
  public void onSuccess() {
    consecutiveFailures = 0;
    skipsRemaining = 0;
  }

  /** Doubles the wait, to a cap. */
  public void onFailure() {
    consecutiveFailures++;
    skipsRemaining = Math.min(MAX_SKIPPED_CAPTURES, (1 << Math.min(consecutiveFailures, 5)) - 1);
  }

  /** A rejected or revoked token. Returns true only the first time, so it is announced once. */
  public boolean onAuthFailure() {
    boolean announce = !halted;
    halted = true;
    return announce;
  }

  /** Called on re-pairing, the only thing that clears a halt. */
  public void resume() {
    halted = false;
    consecutiveFailures = 0;
    skipsRemaining = 0;
  }

  public boolean isHalted() {
    return halted;
  }
}
