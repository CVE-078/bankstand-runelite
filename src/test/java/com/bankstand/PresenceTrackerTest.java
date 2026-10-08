package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * The hop table, row by row, plus the failed-hop timer. Presence describes whether the
 * player is in game on a standard world, and a transition goes out only when that changes.
 */
public class PresenceTrackerTest {

  private static final long A = 111L;
  private static final long B = 222L;
  private static final long SECOND = 1_000_000_000L;

  private static PresenceTracker onStandard(long hash) {
    PresenceTracker tracker = new PresenceTracker();
    tracker.onLoggedIn(true, hash, 0L);
    return tracker;
  }

  private static void assertSignals(List<PresenceSignal> got, String... states) {
    assertEquals("signal count", states.length, got.size());
    for (int i = 0; i < states.length; i++) {
      assertEquals(states[i], got.get(i).getState());
    }
  }

  @Test
  public void aLoginOnAStandardWorldSendsLogin() {
    PresenceTracker tracker = new PresenceTracker();
    List<PresenceSignal> out = tracker.onLoggedIn(true, A, 5L);
    assertSignals(out, PresenceSignal.LOGIN);
    assertEquals(A, out.get(0).getAccountHash());
    assertEquals(5L, out.get(0).getCreatedAtNanos());
  }

  @Test
  public void aRegionLoadOnAStandardWorldSendsNothing() {
    // LOADING then LOGGED_IN after every teleport: the world type has not changed.
    PresenceTracker tracker = onStandard(A);
    assertSignals(tracker.onLoggedIn(true, A, 10L));
    assertSignals(tracker.onLoggedIn(true, A, 20L));
  }

  @Test
  public void aRegionLoadOnANonStandardWorldSendsNothing() {
    PresenceTracker tracker = new PresenceTracker();
    assertSignals(tracker.onLoggedIn(false, A, 0L));
    assertSignals(tracker.onLoggedIn(false, A, 10L));
  }

  @Test
  public void aStandardToStandardHopSendsNothingIncludingItsLoginScreen() {
    PresenceTracker tracker = onStandard(A);
    tracker.onHopping();
    assertSignals(tracker.onLoginScreen(A, SECOND));
    assertSignals(tracker.onLoggedIn(true, A, 2 * SECOND));
    assertFalse(tracker.isHopTimerArmed());
    assertTrue(tracker.isOnStandardWorld());
  }

  @Test
  public void aHopWithoutALoginScreenSendsNothing() {
    PresenceTracker tracker = onStandard(A);
    tracker.onHopping();
    assertSignals(tracker.onLoggedIn(true, A, SECOND));
  }

  @Test
  public void arrivingOnANonStandardWorldSendsOneLogout() {
    PresenceTracker tracker = onStandard(A);
    tracker.onHopping();
    List<PresenceSignal> out = tracker.onLoggedIn(false, A, SECOND);
    assertSignals(out, PresenceSignal.LOGOUT);
    assertEquals(A, out.get(0).getAccountHash());
  }

  @Test
  public void returningToAStandardWorldSendsOneLogin() {
    PresenceTracker tracker = onStandard(A);
    tracker.onLoggedIn(false, A, SECOND);
    assertSignals(tracker.onLoggedIn(true, A, 2 * SECOND), PresenceSignal.LOGIN);
  }

  @Test
  public void nonStandardToNonStandardSendsNothing() {
    PresenceTracker tracker = new PresenceTracker();
    tracker.onLoggedIn(false, A, 0L);
    tracker.onHopping();
    assertSignals(tracker.onLoginScreen(A, SECOND));
    assertSignals(tracker.onLoggedIn(false, A, 2 * SECOND));
  }

  @Test
  public void loggingOutFromANonStandardWorldSendsNothing() {
    PresenceTracker tracker = onStandard(A);
    tracker.onLoggedIn(false, A, SECOND);
    assertSignals(tracker.onLoginScreen(A, 2 * SECOND));
  }

  @Test
  public void anOrdinaryLogoutSendsLogoutForTheLeavingAccount() {
    PresenceTracker tracker = onStandard(A);
    List<PresenceSignal> out = tracker.onLoginScreen(A, 7 * SECOND);
    assertSignals(out, PresenceSignal.LOGOUT);
    assertEquals(A, out.get(0).getAccountHash());
    assertEquals(7 * SECOND, out.get(0).getCreatedAtNanos());
    assertFalse(tracker.isOnStandardWorld());
  }

  @Test
  public void anAccountSwitchIsALogoutThenALoginForTheOtherAccount() {
    PresenceTracker tracker = onStandard(A);
    assertEquals(A, tracker.onLoginScreen(A, SECOND).get(0).getAccountHash());
    List<PresenceSignal> out = tracker.onLoggedIn(true, B, 2 * SECOND);
    assertSignals(out, PresenceSignal.LOGIN);
    assertEquals(B, out.get(0).getAccountHash());
  }

  @Test
  public void aFailedHopSendsALogoutBackdatedToItsLoginScreen() {
    PresenceTracker tracker = onStandard(A);
    tracker.onHopping();
    assertSignals(tracker.onLoginScreen(A, 10 * SECOND));
    assertTrue(tracker.isHopTimerArmed());

    // Not yet: the timer is checked against the login screen's own time.
    assertSignals(tracker.onHopTimer(39 * SECOND));

    List<PresenceSignal> out = tracker.onHopTimer(40 * SECOND);
    assertSignals(out, PresenceSignal.LOGOUT);
    assertEquals(A, out.get(0).getAccountHash());
    assertEquals(
        "the logout carries the login screen's time, not the timer's",
        10 * SECOND,
        out.get(0).getCreatedAtNanos());
    assertFalse(tracker.isOnStandardWorld());

    // The next fresh login is a real login.
    assertSignals(tracker.onLoggedIn(true, A, 60 * SECOND), PresenceSignal.LOGIN);
  }

  @Test
  public void aHopThatLandsCancelsTheTimer() {
    PresenceTracker tracker = onStandard(A);
    tracker.onHopping();
    tracker.onLoginScreen(A, SECOND);
    assertSignals(tracker.onLoggedIn(true, A, 20 * SECOND));
    assertFalse(tracker.isHopTimerArmed());
    assertSignals(tracker.onHopTimer(100 * SECOND));
  }

  @Test
  public void aDifferentAccountAfterAFailedHopEndsTheFirstAccountsSession() {
    PresenceTracker tracker = onStandard(A);
    tracker.onHopping();
    tracker.onLoginScreen(A, SECOND);
    List<PresenceSignal> out = tracker.onLoggedIn(true, B, 10 * SECOND);
    assertSignals(out, PresenceSignal.LOGOUT, PresenceSignal.LOGIN);
    assertEquals(A, out.get(0).getAccountHash());
    assertEquals(SECOND, out.get(0).getCreatedAtNanos());
    assertEquals(B, out.get(1).getAccountHash());
  }

  @Test
  public void turningSessionsOffInGameSendsOff() {
    PresenceTracker tracker = onStandard(A);
    List<PresenceSignal> out = tracker.onSessionsTurnedOff(SECOND);
    assertSignals(out, PresenceSignal.OFF);
    assertEquals(A, out.get(0).getAccountHash());
  }

  @Test
  public void turningSessionsOffOutOfGameSendsNothing() {
    assertSignals(new PresenceTracker().onSessionsTurnedOff(SECOND));
  }

  @Test
  public void turningSessionsOnInGameStartsASessionNow() {
    PresenceTracker tracker = onStandard(A);
    assertSignals(tracker.onSessionsTurnedOn(SECOND), PresenceSignal.LOGIN);
  }

  @Test
  public void turningSessionsOnOnANonStandardWorldSendsNothing() {
    PresenceTracker tracker = new PresenceTracker();
    tracker.onLoggedIn(false, A, 0L);
    assertSignals(tracker.onSessionsTurnedOn(SECOND));
  }
}
