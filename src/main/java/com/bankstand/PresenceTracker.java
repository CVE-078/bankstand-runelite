package com.bankstand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decides which presence transitions to send, from game-state changes.
 *
 * <p><b>Presence describes one thing: whether the player is in game on a standard
 * world.</b> A transition is sent only when that changes, never on every {@code
 * LOGGED_IN}: the client posts one after every teleport and region load as well as after
 * a real login, and with the world type unchanged nothing happened that a session cares
 * about.
 *
 * <ul>
 *   <li>Standard to standard, a world hop: nothing, including the login screen the hop may
 *       pass through and the {@code LOGGED_IN} that ends it.
 *   <li>Standard to non-standard (Leagues, Deadman, tournament, PvP arena): one {@code
 *       logout} on arrival, since that play is not the account's own.
 *   <li>Non-standard to standard: one {@code login}.
 *   <li>Non-standard to non-standard, or logging out from a non-standard world: nothing,
 *       the {@code logout} already went on arrival.
 * </ul>
 *
 * <p><b>A hop that fails.</b> A login screen during a hop arms a {@link
 * #FAILED_HOP_TIMEOUT_NANOS} timer instead of sending {@code logout}. A {@code LOGGED_IN}
 * before it fires means the hop worked and nothing is sent. If it fires, the {@code
 * logout} carries the account and time captured at that login screen, so it lands at the
 * moment the player actually left, not when the timer gave up.
 *
 * <p>Pure: no client, no clock, no I/O. Every method takes the account and a monotonic
 * time, and returns what to send. The bookkeeping runs whether or not sessions are
 * switched on; the caller decides whether a returned signal leaves the client.
 */
final class PresenceTracker {

  static final long FAILED_HOP_TIMEOUT_NANOS = 30_000_000_000L;

  enum Where {
    OUT,
    STANDARD,
    NON_STANDARD
  }

  private Where where = Where.OUT;
  private long accountHash = -1L;
  private boolean hopping;
  private PresenceSignal pendingHopLogout;

  synchronized Where where() {
    return where;
  }

  synchronized boolean isOnStandardWorld() {
    return where == Where.STANDARD;
  }

  /** The account presence currently describes, or -1 when out of game. */
  synchronized long accountHash() {
    return where == Where.OUT ? -1L : accountHash;
  }

  synchronized boolean isHopTimerArmed() {
    return pendingHopLogout != null;
  }

  /** {@code HOPPING}: a hop is in progress until the next {@code LOGGED_IN}. */
  synchronized void onHopping() {
    hopping = true;
  }

  /** {@code LOGGED_IN}, after a real login, a hop, a teleport or a region load alike. */
  synchronized List<PresenceSignal> onLoggedIn(boolean standardWorld, long hash, long nowNanos) {
    List<PresenceSignal> out = new ArrayList<>();
    hopping = false;
    PresenceSignal abandoned = pendingHopLogout;
    pendingHopLogout = null;
    if (abandoned != null && abandoned.getAccountHash() != hash) {
      // The hop failed and a different account logged in before the timer fired: the
      // first account did leave, and its session must not stay open.
      out.add(abandoned);
      where = Where.OUT;
    }
    Where now = standardWorld ? Where.STANDARD : Where.NON_STANDARD;
    if (now != where) {
      if (now == Where.STANDARD) {
        out.add(new PresenceSignal(PresenceSignal.LOGIN, hash, nowNanos));
      } else if (where == Where.STANDARD) {
        out.add(new PresenceSignal(PresenceSignal.LOGOUT, accountHash, nowNanos));
      }
      where = now;
    }
    accountHash = hash;
    return out;
  }

  /**
   * {@code LOGIN_SCREEN}. Called before the session is cleared, so {@code hash} is still
   * the account that is leaving.
   */
  synchronized List<PresenceSignal> onLoginScreen(long hash, long nowNanos) {
    if (where != Where.STANDARD) {
      where = Where.OUT;
      hopping = false;
      return Collections.emptyList();
    }
    if (hopping) {
      if (pendingHopLogout == null) {
        pendingHopLogout = new PresenceSignal(PresenceSignal.LOGOUT, hash, nowNanos);
      }
      return Collections.emptyList();
    }
    where = Where.OUT;
    return Collections.singletonList(new PresenceSignal(PresenceSignal.LOGOUT, hash, nowNanos));
  }

  /** The failed-hop timer fired. Sends the deferred {@code logout} if the hop never landed. */
  synchronized List<PresenceSignal> onHopTimer(long nowNanos) {
    PresenceSignal pending = pendingHopLogout;
    if (pending == null || nowNanos - pending.getCreatedAtNanos() < FAILED_HOP_TIMEOUT_NANOS) {
      return Collections.emptyList();
    }
    pendingHopLogout = null;
    hopping = false;
    where = Where.OUT;
    return Collections.singletonList(pending);
  }

  /** The player turned session capture off: an open session ends now. */
  synchronized List<PresenceSignal> onSessionsTurnedOff(long nowNanos) {
    if (where != Where.STANDARD) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new PresenceSignal(PresenceSignal.OFF, accountHash, nowNanos));
  }

  /** The player turned session capture on while already in game: a session starts now. */
  synchronized List<PresenceSignal> onSessionsTurnedOn(long nowNanos) {
    if (where != Where.STANDARD) {
      return Collections.emptyList();
    }
    return Collections.singletonList(
        new PresenceSignal(PresenceSignal.LOGIN, accountHash, nowNanos));
  }

  synchronized void reset() {
    where = Where.OUT;
    accountHash = -1L;
    hopping = false;
    pendingHopLogout = null;
  }
}
