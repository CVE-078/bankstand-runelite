package com.bankstand;

/**
 * One presence value waiting to be sent. Account and time are captured at creation, never
 * at send time, so a retry after an account switch still names the right character.
 */
final class PresenceSignal {

  static final String LOGIN = "login";
  static final String ACTIVE = "active";
  static final String LOGOUT = "logout";
  static final String OFF = "off";

  private final String state;
  private final long accountHash;
  private final long createdAtNanos;
  // Whether full loot was on. Null on logout and off, which do not carry it.
  private final Boolean loot;

  PresenceSignal(String state, long accountHash, long createdAtNanos) {
    this(state, accountHash, createdAtNanos, null);
  }

  PresenceSignal(String state, long accountHash, long createdAtNanos, Boolean loot) {
    this.state = state;
    this.accountHash = accountHash;
    this.createdAtNanos = createdAtNanos;
    this.loot = loot;
  }

  /** A copy carrying the loot flag, for {@code login} and {@code active} only. */
  PresenceSignal withLoot(boolean lootOn) {
    if (!LOGIN.equals(state) && !ACTIVE.equals(state)) {
      return this;
    }
    return new PresenceSignal(state, accountHash, createdAtNanos, lootOn);
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

  Boolean getLoot() {
    return loot;
  }

  boolean isTransition() {
    return !ACTIVE.equals(state);
  }

  /** How long this has waited, never negative. Grows with every retry. */
  long elapsedMillis(long nowNanos) {
    return Math.max(0L, (nowNanos - createdAtNanos) / 1_000_000L);
  }
}
