package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.Test;

public class CollectionLogAccumulatorTest {

  @Test
  public void recordsAnItemOnceAndReportsWhetherItWasNew() {
    CollectionLogAccumulator log = new CollectionLogAccumulator();
    assertTrue(log.observe(11832));
    assertFalse(log.observe(11832));
    assertEquals(1, log.size());
  }

  @Test
  public void accumulatesAcrossReadsRatherThanReplacing() {
    // The whole point. A partial enumeration (one page browsed, a search cancelled)
    // must add to what is known, never become the new truth. Replacing here would
    // discard the rest of the player's log every time they opened a single page.
    CollectionLogAccumulator log = new CollectionLogAccumulator();
    log.observe(11832);
    log.observe(11834);

    // A later, narrower read that only sees one item.
    log.observe(4151);

    assertEquals(
        new LinkedHashSet<>(Arrays.asList(11832, 11834, 4151)), log.observed());
  }

  @Test
  public void keepsFirstSeenOrder() {
    CollectionLogAccumulator log = new CollectionLogAccumulator();
    log.observe(3);
    log.observe(1);
    log.observe(2);
    assertEquals(Arrays.asList(3, 1, 2), new java.util.ArrayList<>(log.observed()));
  }

  @Test
  public void resetForgetsEverything() {
    // Called on an account switch. A collection log belongs to one character, and
    // carrying it over would attribute one account's items to another.
    CollectionLogAccumulator log = new CollectionLogAccumulator();
    log.observe(11832);
    log.reset();
    assertTrue(log.isEmpty());
    assertEquals(0, log.size());
  }

  @Test
  public void exposesAnUnmodifiableView() {
    // Callers build a submission from this; letting them mutate it would corrupt the
    // accumulator without going through observe().
    CollectionLogAccumulator log = new CollectionLogAccumulator();
    log.observe(11832);
    try {
      log.observed().add(999);
      org.junit.Assert.fail("expected the view to be unmodifiable");
    } catch (UnsupportedOperationException expected) {
      // as intended
    }
  }
}
