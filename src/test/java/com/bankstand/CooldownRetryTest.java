package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import org.junit.Test;

public class CooldownRetryTest {

  private static final long NOW = Instant.parse("2026-10-13T12:00:00Z").toEpochMilli();

  @Test
  public void noRetryWhenNotCooledDown() {
    assertEquals(-1, BankstandPlugin.cooldownRetryDelayMs(null, null, NOW));
    assertEquals(-1, BankstandPlugin.cooldownRetryDelayMs("stored", "2026-10-13T12:00:30Z", NOW));
  }

  @Test
  public void retriesJustAfterTheServerSaysItMay() {
    assertEquals(
        31_000, BankstandPlugin.cooldownRetryDelayMs("cooldown", "2026-10-13T12:00:30Z", NOW));
  }

  @Test
  public void fallsBackToAMinuteWhenTheTimeIsMissingOrUnreadable() {
    assertEquals(60_000, BankstandPlugin.cooldownRetryDelayMs("cooldown", null, NOW));
    assertEquals(60_000, BankstandPlugin.cooldownRetryDelayMs("cooldown", "soon", NOW));
  }

  @Test
  public void clampsAFarOrPastTime() {
    assertEquals(
        120_000, BankstandPlugin.cooldownRetryDelayMs("cooldown", "2026-10-13T13:00:00Z", NOW));
    assertEquals(
        1_000, BankstandPlugin.cooldownRetryDelayMs("cooldown", "2026-10-13T11:59:00Z", NOW));
  }

  @Test
  public void aNewerSubmitSupersedesTheRetry() {
    assertFalse(BankstandPlugin.isSuperseded(3, 3));
    assertTrue(BankstandPlugin.isSuperseded(3, 4));
  }
}
