package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SubmitGateTest {

  private static void failAndSkipPast(SubmitGate gate) {
    gate.onFailure();
    while (!gate.allow()) {
      // Burn the backoff.
    }
  }

  @Test
  public void allowsACaptureWhenNothingHasGoneWrong() {
    SubmitGate gate = new SubmitGate();

    assertTrue(gate.allow());
    assertTrue(gate.allow());
  }

  /**
   * The gap this exists for. A revoked token fails fast inside a submit, and without a
   * latch the next capture tries again 60 seconds later, forever, while NoticeGate keeps
   * the player from ever hearing about it twice.
   */
  @Test
  public void stopsForeverOnARevokedToken() {
    SubmitGate gate = new SubmitGate();

    gate.onAuthFailure();

    assertTrue(gate.isHalted());
    for (int capture = 0; capture < 100; capture++) {
      assertFalse(gate.allow());
    }
  }

  @Test
  public void aRevokedTokenIsAnnouncedOnceRatherThanEveryCapture() {
    SubmitGate gate = new SubmitGate();

    assertTrue(gate.onAuthFailure());
    assertFalse(gate.onAuthFailure());
  }

  @Test
  public void resumingAfterAReRepairLetsCapturesThrough() {
    SubmitGate gate = new SubmitGate();
    gate.onAuthFailure();

    gate.resume();

    assertFalse(gate.isHalted());
    assertTrue(gate.allow());
  }

  @Test
  public void backsOffAfterAFailureRatherThanRetryingEveryCapture() {
    SubmitGate gate = new SubmitGate();

    gate.onFailure();

    assertFalse(gate.allow());
  }

  @Test
  public void backsOffFurtherTheLongerItKeepsFailing() {
    SubmitGate gate = new SubmitGate();
    int previous = 0;
    for (int round = 1; round <= 4; round++) {
      gate.onFailure();
      int skipped = 0;
      while (!gate.allow()) {
        skipped++;
      }
      assertTrue(
          "round " + round + " skipped " + skipped + ", previous " + previous, skipped > previous);
      previous = skipped;
    }
  }

  /** Bounded, so an all-night outage does not turn into an all-week one. */
  @Test
  public void neverBacksOffPastTheCap() {
    SubmitGate gate = new SubmitGate();
    for (int round = 0; round < 50; round++) {
      gate.onFailure();
    }
    int skipped = 0;
    while (!gate.allow()) {
      skipped++;
    }

    assertEquals(SubmitGate.MAX_SKIPPED_CAPTURES, skipped);
  }

  @Test
  public void oneSuccessClearsTheBackoffCompletely() {
    SubmitGate gate = new SubmitGate();
    for (int round = 0; round < 5; round++) {
      gate.onFailure();
    }

    gate.onSuccess();

    assertTrue(gate.allow());
    assertTrue(gate.allow());
  }

  @Test
  public void aFailureAfterARecoveryStartsFromTheShortestBackoffAgain() {
    SubmitGate gate = new SubmitGate();
    for (int round = 0; round < 5; round++) {
      gate.onFailure();
    }
    gate.onSuccess();
    failAndSkipPast(gate);

    // Back to the first rung, not the fifth: the server proved it was reachable.
    SubmitGate fresh = new SubmitGate();
    fresh.onFailure();
    int freshSkips = 0;
    while (!fresh.allow()) {
      freshSkips++;
    }
    gate.onSuccess();
    gate.onFailure();
    int afterRecoverySkips = 0;
    while (!gate.allow()) {
      afterRecoverySkips++;
    }

    assertEquals(freshSkips, afterRecoverySkips);
  }

  /** A halt outranks a backoff: re-pairing is the only thing that clears it. */
  @Test
  public void aBackoffElapsingDoesNotUnhaltARevokedToken() {
    SubmitGate gate = new SubmitGate();
    gate.onFailure();
    gate.onAuthFailure();

    for (int capture = 0; capture < 50; capture++) {
      assertFalse(gate.allow());
    }
  }

  @Test
  public void aSuccessDoesNotSilentlyUnhaltARevokedToken() {
    SubmitGate gate = new SubmitGate();
    gate.onAuthFailure();

    // Nothing can succeed while halted, but if the caller ever ordered these wrongly a
    // silent unhalt would resume submitting with a token the server already rejected.
    gate.onSuccess();

    assertTrue(gate.isHalted());
    assertFalse(gate.allow());
  }
}
