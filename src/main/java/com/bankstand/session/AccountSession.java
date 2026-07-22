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

  // accountHash and generation are read from the submit executor thread (see
  // isCurrent) while written on the game thread, so both are volatile for visibility
  // across the two. submitted is touched only on the game thread, so it is not.
  private volatile long accountHash = LOGGED_OUT;
  private volatile int generation = 0;
  private boolean submitted = false;

  public long getAccountHash() {
    return accountHash;
  }

  public boolean isActive() {
    return accountHash != LOGGED_OUT;
  }

  /**
   * A monotonic marker of the current login instance, bumped on every session change
   * (a switch, a logout, or a relog). A submit captures it at dispatch; the callback
   * compares it via {@link #isCurrent} so a stale result cannot overwrite a later one.
   */
  public int getGeneration() {
    return generation;
  }

  /**
   * True when {@code hash}/{@code gen} still identify the current login instance. A
   * submit is dispatched asynchronously with the account and generation it started
   * for; its callback uses this to drop a superseded result once the session has
   * moved on (a switch to another character, a logout, or a relog to the same
   * account, which advances the generation). The logged-out sentinel is never current.
   */
  public boolean isCurrent(long hash, int gen) {
    return isActive() && accountHash == hash && generation == gen;
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
    generation++;
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
    generation++;
  }

  private void reset() {
    // No capture caches in this slice. A later gameplay slice clears them here so a
    // change of account can never leak one character's data into another's session.
  }
}
