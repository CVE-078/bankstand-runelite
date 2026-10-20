package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.SubmitEventsResponse;
import com.google.gson.Gson;
import org.junit.Test;

public class EventSendPacerTest {

  @Test
  public void aRetryAfterPausesEveryAccount() {
    EventSendPacer pacer = new EventSendPacer();
    pacer.pauseFor(0L, 60_000L);
    assertFalse(pacer.maySend(1L, 59_999L));
    assertFalse(pacer.maySend(2L, 59_999L));
    assertTrue(pacer.maySend(1L, 60_000L));
  }

  @Test
  public void aShorterPauseNeverCutsALongerOneShort() {
    EventSendPacer pacer = new EventSendPacer();
    pacer.pauseFor(0L, 60_000L);
    pacer.pauseFor(0L, 1_000L);
    assertFalse(pacer.maySend(1L, 30_000L));
  }

  @Test
  public void anUnroutedAccountWaitsWhileOthersStillSend() {
    EventSendPacer pacer = new EventSendPacer();
    pacer.unrouted(1L, 0L);
    assertFalse(pacer.maySend(1L, EventSendPacer.UNROUTED_BACKOFF_MS - 1));
    assertTrue(pacer.maySend(2L, 0L));
    assertTrue(pacer.maySend(1L, EventSendPacer.UNROUTED_BACKOFF_MS));
  }

  @Test
  public void anIdentityChangeRetriesAnUnroutedAccountAtOnce() {
    EventSendPacer pacer = new EventSendPacer();
    pacer.unrouted(1L, 0L);
    pacer.forgetUnrouted();
    assertTrue(pacer.maySend(1L, 1L));
  }

  @Test
  public void recordAppliesOnlyARoutedReply() {
    EventSendPacer pacer = new EventSendPacer();
    assertTrue(pacer.record(1L, 0L, reply(true), null));
    assertTrue(pacer.maySend(1L, 0L));
    assertFalse(pacer.record(1L, 0L, reply(false), null));
    assertFalse(pacer.maySend(1L, 1L));
  }

  @Test
  public void recordPausesOnARetryAfterButNotOnAPlainFailure() {
    EventSendPacer pacer = new EventSendPacer();
    assertFalse(pacer.record(1L, 0L, null, new SubmitException("Could not reach Bankstand.", true)));
    assertTrue(pacer.maySend(1L, 0L));
    assertFalse(
        pacer.record(1L, 0L, null, new SubmitException("Bankstand is busy.", true, false, 60_000L)));
    assertFalse(pacer.maySend(2L, 59_999L));
    assertTrue(pacer.maySend(2L, 60_000L));
  }

  private static SubmitEventsResponse reply(boolean routed) {
    return new Gson()
        .fromJson("{\"routed\":" + routed + ",\"acks\":[]}", SubmitEventsResponse.class);
  }
}
