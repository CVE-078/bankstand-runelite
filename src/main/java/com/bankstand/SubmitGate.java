package com.bankstand;

/**
 * Decides whether a capture may submit: backs off across captures on failure and halts on a
 * revoked token until re-pairing. Counted in captures, the only clock it has.
 * Synchronized: executor callbacks write, the client thread reads.
 */
public class SubmitGate {

  /** About sixteen minutes at a sixty second capture. */
  public static final int MAX_SKIPPED_CAPTURES = 15;

  private boolean halted;
  private int consecutiveFailures;
  private int skipsRemaining;
  // Epoch millis before which nothing is sent: the server's Retry-After.
  private long pausedUntilMs;

  /** Consumes one skip when backing off. */
  public synchronized boolean allow() {
    if (halted || isPaused(System.currentTimeMillis())) {
      return false;
    }
    if (skipsRemaining > 0) {
      skipsRemaining--;
      return false;
    }
    return true;
  }

  /** Clears any backoff, but never the halt. */
  public synchronized void onSuccess() {
    consecutiveFailures = 0;
    skipsRemaining = 0;
  }

  /** Doubles the wait, to a cap. */
  public synchronized void onFailure() {
    consecutiveFailures++;
    skipsRemaining = Math.min(MAX_SKIPPED_CAPTURES, (1 << Math.min(consecutiveFailures, 5)) - 1);
  }

  /** A rejected or revoked token. Returns true only the first time, so it is announced once. */
  public synchronized boolean onAuthFailure() {
    boolean announce = !halted;
    halted = true;
    return announce;
  }

  /** A Retry-After: nothing is sent before it passes. Never shortens a longer pause. */
  public synchronized void pauseFor(long nowMs, long retryAfterMs) {
    pausedUntilMs = Math.max(pausedUntilMs, nowMs + retryAfterMs);
  }

  public synchronized boolean isPaused(long nowMs) {
    return nowMs < pausedUntilMs;
  }

  /** Called on re-pairing, the only thing that clears a halt. */
  public synchronized void resume() {
    halted = false;
    pausedUntilMs = 0;
    consecutiveFailures = 0;
    skipsRemaining = 0;
  }

  public synchronized boolean isHalted() {
    return halted;
  }
}
