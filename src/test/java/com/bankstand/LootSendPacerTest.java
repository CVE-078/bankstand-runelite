package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LootSendPacerTest {

  @Test
  public void anEarlySendGoesAtMostOncePerThirtySeconds() {
    LootSendPacer pacer = new LootSendPacer();
    assertTrue(pacer.tryFastSend(1_000L));
    assertFalse(pacer.tryFastSend(1_000L + LootSendPacer.FAST_SEND_SPACING_MS - 1));
    assertTrue(pacer.tryFastSend(1_000L + LootSendPacer.FAST_SEND_SPACING_MS));
  }

  @Test
  public void aRetryAfterPausesEverySend() {
    LootSendPacer pacer = new LootSendPacer();
    pacer.pauseFor(0L, 60_000L);
    assertFalse(pacer.mayDrain(59_999L));
    assertFalse(pacer.tryFastSend(59_999L));
    assertTrue(pacer.mayDrain(60_000L));
    assertTrue(pacer.tryFastSend(60_000L));
  }

  @Test
  public void aShorterPauseNeverCutsALongerOneShort() {
    LootSendPacer pacer = new LootSendPacer();
    pacer.pauseFor(0L, 60_000L);
    pacer.pauseFor(1_000L, 1_000L);
    assertFalse(pacer.mayDrain(30_000L));
  }
}
