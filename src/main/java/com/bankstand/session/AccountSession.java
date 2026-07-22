package com.bankstand.session;

/**
 * Tracks the currently logged-in account across game-state changes. Two invariants
 * from the ingestion review are enforced here: the logged-out sentinel is never
 * adopted or submitted, and state is never carried across a change of account hash.
 * In this slice there are no capture caches yet; {@link #reset()} is the seam a
 * later gameplay-capture slice hooks into so a switch clears everything.
 */
public class AccountSession {

  /** RuneLite's {@code client.getAccountHash()} returns this when logged out. */
  public static final long LOGGED_OUT = -1L;

  private long accountHash = LOGGED_OUT;
  private boolean submitted = false;

  public long getAccountHash() {
    return accountHash;
  }

  public boolean isActive() {
    return accountHash != LOGGED_OUT;
  }

  /** True once this account's identity has been submitted, so it is not resent every tick. */
  public boolean isSubmitted() {
    return submitted;
  }

  /** Mark the current account's identity as submitted for this session. */
  public void markSubmitted() {
    submitted = true;
  }

  /**
   * Handle a login for {@code hash}. The logged-out sentinel is ignored. A hash
   * that differs from the current one resets the session (clearing the submitted
   * flag so the new account is submitted afresh) and adopts the new hash; the same
   * hash is a no-op. Returns true when a fresh session was started.
   */
  public boolean onLogin(long hash) {
    if (hash == LOGGED_OUT || hash == accountHash) {
      return false;
    }
    reset();
    accountHash = hash;
    submitted = false;
    return true;
  }

  /**
   * Clear the transient session on logout. The device token is account-independent
   * and kept elsewhere (ConfigManager), so it is not touched here.
   */
  public void onLogout() {
    reset();
    accountHash = LOGGED_OUT;
    submitted = false;
  }

  private void reset() {
    // No capture caches in this slice. A later gameplay slice clears them here so a
    // change of account can never leak one character's data into another's session.
  }
}
