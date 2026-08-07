package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LogTotalsTest {

  /** The real title, as read off a live client. */
  @Test
  public void readsTheLogTitle() {
    LogTotals t = LogTotals.fromTitle("Collection Log - 189/1712");

    assertEquals(189, t.getObtained());
    assertEquals(1712, t.getTotal());
  }

  @Test
  public void toleratesColourTagsAndSpacing() {
    LogTotals t = LogTotals.fromTitle("<col=ff981f>Collection Log - 189 / 1712</col>");

    assertEquals(189, t.getObtained());
    assertEquals(1712, t.getTotal());
  }

  @Test
  public void readsAnEmptyLog() {
    assertEquals(0, LogTotals.fromTitle("Collection Log - 0/1712").getObtained());
  }

  @Test
  public void readsACompletedLog() {
    LogTotals t = LogTotals.fromTitle("Collection Log - 1712/1712");

    assertEquals(1712, t.getObtained());
    assertEquals(1712, t.getTotal());
  }

  /**
   * The detail panel shows per-source pairs like "Obtained: 1/9". Anything carrying two
   * pairs is not the title, and picking one of them is how a single boss's progress
   * becomes the whole log's.
   */
  @Test
  public void refusesTextHoldingMoreThanOnePair() {
    assertNull(LogTotals.fromTitle("Collection Log - 189/1712 Obtained: 1/9"));
    assertNull(LogTotals.fromTitle("1/9 2/9"));
  }

  @Test
  public void refusesTextWithNoPair() {
    assertNull(LogTotals.fromTitle(null));
    assertNull(LogTotals.fromTitle(""));
    assertNull(LogTotals.fromTitle("Collection Log"));
    assertNull(LogTotals.fromTitle("Abyssal Sire"));
  }

  /** Nonsense rather than a log, and a zero denominator would render as a divide by it. */
  @Test
  public void refusesATotalOfZero() {
    assertNull(LogTotals.fromTitle("Collection Log - 0/0"));
  }

  @Test
  public void refusesMoreObtainedThanExist() {
    assertNull(LogTotals.fromTitle("Collection Log - 1713/1712"));
  }
}
