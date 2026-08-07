package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Whether a read taken as the player logs out can be trusted.
 *
 * <p>The final minute of a session is otherwise never captured, which is what #643 is
 * about. The catch is that this read happens after the client has left the world, and
 * whether skill XP is still readable there cannot be verified outside a running client.
 * So the read is checked instead of trusted: anything that looks like a cleared client
 * is discarded, and discarding costs nothing because the next login re-reads live state
 * that already includes those minutes.
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

  /** A cleared client reads as zeroes, and submitting those is an XP regression. */
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

  /**
   * With no previous read there is nothing to compare against, so a non-empty one is
   * taken at face value. It still goes through the same submit path, where the server's
   * own regression guard is the backstop.
   */
  @Test
  public void acceptsAnyNonEmptyReadWhenThereIsNoPreviousOne() {
    assertTrue(BankstandPlugin.isPlausibleFinalRead(null, xp(100, 200)));
    assertFalse(BankstandPlugin.isPlausibleFinalRead(null, new LinkedHashMap<>()));
  }

  /** A genuinely zero skill is real for a fresh account, and must not be mistaken. */
  @Test
  public void acceptsAZeroThatWasAlreadyZero() {
    assertTrue(BankstandPlugin.isPlausibleFinalRead(xp(0, 0), xp(0, 0)));
    assertTrue(BankstandPlugin.isPlausibleFinalRead(xp(0, 0), xp(5, 0)));
  }
}
