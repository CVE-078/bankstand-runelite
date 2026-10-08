package com.bankstand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decides which presence transitions to send. A transition goes out only when "in game on
 * a standard world" changes: a hop or region load sends nothing, a non-standard world counts
 * as logged out. A login screen during a hop waits {@link #FAILED_HOP_TIMEOUT_NANOS} before
 * its {@code logout} is sent, stamped with the moment the player left.
 *
 * <p>Pure: no client, clock or I/O. The caller decides whether a signal leaves the client.
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
      // A failed hop followed by a different account: the first one did leave.
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

  /** {@code LOGIN_SCREEN}, called while {@code hash} is still the leaving account. */
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
