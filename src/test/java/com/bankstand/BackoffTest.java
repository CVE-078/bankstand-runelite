package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackoffTest {

  @Test
  public void doublesTheWindowEachAttempt() {
    assertEquals(500L, RetryTiming.backoffMillis(1, 1000L, 0.5));
    assertEquals(1000L, RetryTiming.backoffMillis(2, 1000L, 0.5));
    assertEquals(2000L, RetryTiming.backoffMillis(3, 1000L, 0.5));
  }

  @Test
  public void picksFromZeroUpToTheWindow() {
    assertEquals(0L, RetryTiming.backoffMillis(3, 1000L, 0.0));
    assertEquals(2000L, RetryTiming.backoffMillis(3, 1000L, 0.5));
  }

  @Test
  public void neverWaitsPastTheCap() {
    for (int attempt = 1; attempt <= 20; attempt++) {
      assertTrue(
          RetryTiming.backoffMillis(attempt, 1000L, 0.999999)
              <= RetryTiming.MAX_BACKOFF_MILLIS);
    }
  }

  /** The cap is on the window, not the draw, so late attempts stay spread out. */
  @Test
  public void aCappedAttemptStillJitters() {
    assertEquals(0L, RetryTiming.backoffMillis(20, 1000L, 0.0));
    assertEquals(
        RetryTiming.MAX_BACKOFF_MILLIS / 2, RetryTiming.backoffMillis(20, 1000L, 0.5));
  }

  @Test
  public void aZeroBaseNeverWaits() {
    assertEquals(0L, RetryTiming.backoffMillis(4, 0L, 0.999999));
  }
}
