package com.bankstand;

/**
 * One presence value waiting to be sent: what happened, to which account, and when, on a
 * monotonic clock.
 *
 * <p><b>The account and the time are captured when the signal is created, never at send
 * time.</b> The session is cleared on every login screen and an account switch can follow
 * before a retry, so reading either later would attribute a logout to the wrong character
 * or to the moment it was finally delivered. The wire carries only {@link #elapsedMillis}:
 * how long the value waited, which the server subtracts from its own receive time, so
 * this machine's wall clock never matters.
 */
final class PresenceSignal {

  static final String LOGIN = "login";
  static final String ACTIVE = "active";
  static final String LOGOUT = "logout";
  static final String OFF = "off";

  private final String state;
  private final long accountHash;
  private final long createdAtNanos;

  PresenceSignal(String state, long accountHash, long createdAtNanos) {
    this.state = state;
    this.accountHash = accountHash;
    this.createdAtNanos = createdAtNanos;
  }

  String getState() {
    return state;
  }

  long getAccountHash() {
    return accountHash;
  }

  long getCreatedAtNanos() {
    return createdAtNanos;
  }

  boolean isTransition() {
    return !ACTIVE.equals(state);
  }

  /** How long this has waited, never negative. Grows with every retry. */
  long elapsedMillis(long nowNanos) {
    return Math.max(0L, (nowNanos - createdAtNanos) / 1_000_000L);
  }
}
