package com.bankstand.session;

/**
 * Tracks the logged-in account across game-state changes. The logged-out sentinel is never
 * adopted or submitted, and no state carries across a change of account hash.
 */
public class AccountSession {

  /** RuneLite's {@code client.getAccountHash()} returns this when logged out. */
  public static final long LOGGED_OUT = -1L;

  // Volatile: written on the client thread, read on the submit executor, and vice versa.
  private volatile long accountHash = LOGGED_OUT;
  private volatile int generation = 0;
  private volatile boolean submitted = false;

  public long getAccountHash() {
    return accountHash;
  }

  public boolean isActive() {
    return accountHash != LOGGED_OUT;
  }

  /**
   * Bumped on every session change (switch, logout, relog). A submit captures it at dispatch so a
   * stale result is dropped via {@link #isCurrent}.
   */
  public int getGeneration() {
    return generation;
  }

  /** True when {@code hash}/{@code gen} still identify the current login. */
  public boolean isCurrent(long hash, int gen) {
    return isActive() && accountHash == hash && generation == gen;
  }

  /** True while this account's identity submit is in flight or has succeeded. */
  public boolean isSubmitted() {
    return submitted;
  }

  /**
   * Marks a submit in flight so the next tick does not fire a second one. In flight, not done: a
   * failure must clear it via {@link #markSubmitFailed} so it can be retried.
   */
  public void markSubmitInFlight() {
    submitted = true;
  }

  /**
   * Allows a retry after a failed submit, only if the same login is still current.
   */
  public void markSubmitFailed(long hash, int gen) {
    if (isCurrent(hash, gen)) {
      submitted = false;
    }
  }

  /**
   * Starts a fresh session for a new hash and returns true. The logged-out sentinel and the
   * current hash are no-ops.
   */
  public boolean onLogin(long hash) {
    if (hash == LOGGED_OUT || hash == accountHash) {
      return false;
    }
    reset();
    accountHash = hash;
    submitted = false;
    generation++;
    return true;
  }

  /** Clears the session on logout. The device token is account-independent and kept. */
  public void onLogout() {
    reset();
    accountHash = LOGGED_OUT;
    submitted = false;
    generation++;
  }

  private void reset() {
    // Hook point: clear any per-account cache here so data never leaks across accounts.
  }
}
