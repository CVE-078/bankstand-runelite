package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackoffTest {

  @Test
  public void doublesTheWindowEachAttempt() {
    assertEquals(500L, BankstandClient.backoffMillis(1, 1000L, 0.5));
    assertEquals(1000L, BankstandClient.backoffMillis(2, 1000L, 0.5));
    assertEquals(2000L, BankstandClient.backoffMillis(3, 1000L, 0.5));
  }

  @Test
  public void picksFromZeroUpToTheWindow() {
    assertEquals(0L, BankstandClient.backoffMillis(3, 1000L, 0.0));
    assertEquals(2000L, BankstandClient.backoffMillis(3, 1000L, 0.5));
  }

  @Test
  public void neverWaitsPastTheCap() {
    for (int attempt = 1; attempt <= 20; attempt++) {
      assertTrue(
          BankstandClient.backoffMillis(attempt, 1000L, 0.999999)
              <= BankstandClient.MAX_BACKOFF_MILLIS);
    }
  }

  /** The cap is on the window, not the draw, so late attempts stay spread out. */
  @Test
  public void aCappedAttemptStillJitters() {
    assertEquals(0L, BankstandClient.backoffMillis(20, 1000L, 0.0));
    assertEquals(
        BankstandClient.MAX_BACKOFF_MILLIS / 2, BankstandClient.backoffMillis(20, 1000L, 0.5));
  }

  @Test
  public void aZeroBaseNeverWaits() {
    assertEquals(0L, BankstandClient.backoffMillis(4, 0L, 0.999999));
  }
}
