package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 * A read taken at logout cannot be verified outside a live client, so anything that looks
 * like a cleared client is discarded. The next login re-reads it anyway.
 */
public class FinalReadTest {

  private static Map<String, Integer> xp(int attack, int cooking) {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("attack", attack);
    m.put("cooking", cooking);
    return m;
  }

  @Test
  public void trustsAReadThatMatchesTheLastOne() {
    assertTrue(BankstandPlugin.isPlausibleFinalRead(xp(100, 200), xp(100, 200)));
  }

  @Test
  public void trustsAReadThatGainedXp() {
    assertTrue(BankstandPlugin.isPlausibleFinalRead(xp(100, 200), xp(140, 200)));
  }

  @Test
  public void rejectsAZeroedRead() {
    assertFalse(BankstandPlugin.isPlausibleFinalRead(xp(100, 200), xp(0, 0)));
  }

  @Test
  public void rejectsAReadWhereAnySkillWentBackwards() {
    assertFalse(BankstandPlugin.isPlausibleFinalRead(xp(100, 200), xp(100, 199)));
  }

  @Test
  public void rejectsAnEmptyRead() {
    assertFalse(BankstandPlugin.isPlausibleFinalRead(xp(100, 200), new LinkedHashMap<>()));
  }

  @Test
  public void rejectsAReadMissingSkillsTheLastOneHad() {
    Map<String, Integer> partial = new LinkedHashMap<>();
    partial.put("attack", 100);

    assertFalse(BankstandPlugin.isPlausibleFinalRead(xp(100, 200), partial));
  }

  /** With no previous read, a non-empty one is taken at face value. */
  @Test
  public void acceptsAnyNonEmptyReadWhenThereIsNoPreviousOne() {
    assertTrue(BankstandPlugin.isPlausibleFinalRead(null, xp(100, 200)));
    assertFalse(BankstandPlugin.isPlausibleFinalRead(null, new LinkedHashMap<>()));
  }

  @Test
  public void acceptsAZeroThatWasAlreadyZero() {
    assertTrue(BankstandPlugin.isPlausibleFinalRead(xp(0, 0), xp(0, 0)));
    assertTrue(BankstandPlugin.isPlausibleFinalRead(xp(0, 0), xp(5, 0)));
  }
}
