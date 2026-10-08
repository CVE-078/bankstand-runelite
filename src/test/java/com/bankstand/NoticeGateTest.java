package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NoticeGateTest {

  @Test
  public void announcesTheFirstFailure() {
    NoticeGate gate = new NoticeGate();
    assertTrue(gate.onFailure("Could not reach Bankstand."));
  }

  @Test
  public void suppressesTheSameFailureRepeating() {
    // The cycle runs every 60s, so an unreachable server would repeat the line forever.
    NoticeGate gate = new NoticeGate();
    gate.onFailure("Could not reach Bankstand.");
    assertFalse(gate.onFailure("Could not reach Bankstand."));
    assertFalse(gate.onFailure("Could not reach Bankstand."));
  }

  @Test
  public void announcesAFailureThatChanges() {
    // A different reason is new information.
    NoticeGate gate = new NoticeGate();
    gate.onFailure("Could not reach Bankstand.");
    assertTrue(gate.onFailure("Your device token is no longer valid."));
  }

  @Test
  public void announcesRecoveryOnlyWhenSomethingWasWrong() {
    NoticeGate gate = new NoticeGate();
    assertFalse(gate.onSuccess());

    gate.onFailure("Could not reach Bankstand.");
    assertTrue(gate.onSuccess());
    assertFalse(gate.onSuccess());
  }

  @Test
  public void announcesTheSameFailureAgainAfterARecovery() {
    // A failure returning after it cleared is news.
    NoticeGate gate = new NoticeGate();
    gate.onFailure("Could not reach Bankstand.");
    gate.onSuccess();
    assertTrue(gate.onFailure("Could not reach Bankstand."));
  }

  @Test
  public void treatsAMissingReasonAsItsOwnState() {
    // getMessage() is nullable; null must not throw or match a real failure.
    NoticeGate gate = new NoticeGate();
    assertTrue(gate.onFailure(null));
    assertFalse(gate.onFailure(null));
    assertTrue(gate.onFailure("Could not reach Bankstand."));
  }
}
