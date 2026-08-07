package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/**
 * Parsing the counts the collection log overview already shows.
 *
 * <p>Measured against a real account: the game reported 189 logged where our own item-id
 * matching produced 193, so the game's figure is the one to show. These are the numbers
 * that make that possible.
 */
public class OverviewCountsTest {

  @Test
  public void readsAnObtainedOverTotalPair() {
    OverviewCounts.Progress p = OverviewCounts.parseProgress("35/340");

    assertEquals(35, p.getObtained());
    assertEquals(340, p.getTotal());
  }

  @Test
  public void toleratesSurroundingSpaceAndColourTags() {
    assertEquals(119, OverviewCounts.parseProgress("  <col=ffff00>119</col>/437 ").getObtained());
    assertEquals(437, OverviewCounts.parseProgress("  <col=ffff00>119</col>/437 ").getTotal());
  }

  @Test
  public void readsAZeroObtained() {
    OverviewCounts.Progress p = OverviewCounts.parseProgress("0/67");

    assertEquals(0, p.getObtained());
    assertEquals(67, p.getTotal());
  }

  /** Anything that is not a pair of numbers is not a count, and must not become one. */
  @Test
  public void refusesTextThatIsNotAPair() {
    assertNull(OverviewCounts.parseProgress(null));
    assertNull(OverviewCounts.parseProgress(""));
    assertNull(OverviewCounts.parseProgress("Bosses"));
    assertNull(OverviewCounts.parseProgress("35"));
    assertNull(OverviewCounts.parseProgress("35/"));
    assertNull(OverviewCounts.parseProgress("/340"));
    assertNull(OverviewCounts.parseProgress("Collections Logged: 189/300"));
  }

  /** The real five, as read off the owner's own overview screen. */
  @Test
  public void sumsTheCategoriesIntoTheFigureTheGameShows() {
    OverviewCounts counts =
        OverviewCounts.of(Arrays.asList("35/340", "2/67", "15/611", "18/257", "119/437"));

    assertTrue(counts.isComplete());
    assertEquals(189, counts.getObtained());
    assertEquals(1712, counts.getTotal());
    assertEquals(5, counts.getCategories());
  }

  /**
   * A partly built interface reads as not complete rather than as a smaller log. Summing
   * whatever happened to be on screen would report a player's log shrinking every time
   * they opened it mid-render.
   */
  @Test
  public void isNotCompleteWhenACategoryIsMissing() {
    OverviewCounts counts = OverviewCounts.of(Arrays.asList("35/340", "2/67"));

    assertFalse(counts.isComplete());
    assertEquals(2, counts.getCategories());
  }

  @Test
  public void isNotCompleteWhenNothingWasReadable() {
    OverviewCounts counts = OverviewCounts.of(Collections.emptyList());

    assertFalse(counts.isComplete());
    assertEquals(0, counts.getObtained());
    assertEquals(0, counts.getTotal());
  }

  @Test
  public void ignoresUnparseableEntriesRatherThanCountingThemAsZero() {
    OverviewCounts counts =
        OverviewCounts.of(Arrays.asList("35/340", "Bosses", "2/67", "", "15/611", "18/257", "119/437"));

    assertTrue(counts.isComplete());
    assertEquals(189, counts.getObtained());
  }

  /** A sixth tab would be a game update, not a parse error, and must not be dropped. */
  @Test
  public void acceptsMoreCategoriesThanTheFiveThatExistToday() {
    OverviewCounts counts =
        OverviewCounts.of(Arrays.asList("1/2", "1/2", "1/2", "1/2", "1/2", "1/2"));

    assertTrue(counts.isComplete());
    assertEquals(6, counts.getObtained());
    assertEquals(6, counts.getCategories());
  }
}
