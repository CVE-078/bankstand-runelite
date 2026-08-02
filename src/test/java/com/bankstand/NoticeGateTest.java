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
    // The capture cycle runs every 60s, so an unreachable server would otherwise
    // print the same line forever.
    NoticeGate gate = new NoticeGate();
    gate.onFailure("Could not reach Bankstand.");
    assertFalse(gate.onFailure("Could not reach Bankstand."));
    assertFalse(gate.onFailure("Could not reach Bankstand."));
  }

  @Test
  public void announcesAFailureThatChanges() {
    // A different reason is new information: reachable-but-rejected is a different
    // problem from unreachable, and needs a different fix.
    NoticeGate gate = new NoticeGate();
    gate.onFailure("Could not reach Bankstand.");
    assertTrue(gate.onFailure("Your device token is no longer valid."));
  }

  @Test
  public void announcesRecoveryOnlyWhenSomethingWasWrong() {
    NoticeGate gate = new NoticeGate();
    // Nothing outstanding: a healthy submit is not worth a line every 60s.
    assertFalse(gate.onSuccess());

    gate.onFailure("Could not reach Bankstand.");
    assertTrue(gate.onSuccess());
    assertFalse(gate.onSuccess());
  }

  @Test
  public void announcesTheSameFailureAgainAfterARecovery() {
    // An intermittent failure is worth reporting each time it returns: the player
    // saw it clear, so its coming back is news.
    NoticeGate gate = new NoticeGate();
    gate.onFailure("Could not reach Bankstand.");
    gate.onSuccess();
    assertTrue(gate.onFailure("Could not reach Bankstand."));
  }

  @Test
  public void treatsAMissingReasonAsItsOwnState() {
    // SubmitException.getMessage() is nullable, and a null must not blow up the
    // notice path or read as "same as the last real failure".
    NoticeGate gate = new NoticeGate();
    assertTrue(gate.onFailure(null));
    assertFalse(gate.onFailure(null));
    assertTrue(gate.onFailure("Could not reach Bankstand."));
  }
}
