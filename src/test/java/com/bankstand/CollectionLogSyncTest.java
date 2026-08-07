package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bankstand.CollectionLogSync.Outcome;
import org.junit.Test;

public class CollectionLogSyncTest {

  /** Ticks with the log open, the search closed and no items arriving. */
  private static void idleTicks(CollectionLogSync sync, int count) {
    for (int i = 0; i < count; i++) {
      assertNull(sync.onTick(false, true));
    }
  }

  @Test
  public void startsIdleAndIgnoresItemsUntilArmed() {
    CollectionLogSync sync = new CollectionLogSync();

    assertFalse(sync.isActive());
    // Passive browsing fires the same script. It must enrich the accumulator without
    // starting a guided sync or reporting an outcome nobody asked for.
    sync.onItemObserved();
    sync.onItemObserved();

    assertFalse(sync.isActive());
    assertEquals(0, sync.observedCount());
    assertNull(sync.onTick(false, true));
  }

  @Test
  public void armingWaitsForTheSearchWithoutCountingAnything() {
    CollectionLogSync sync = new CollectionLogSync();

    sync.arm();

    assertTrue(sync.isActive());
    assertTrue(sync.isAwaitingSearch());
    assertEquals(0, sync.observedCount());
  }

  @Test
  public void countsItemsOnceTheSearchStartsStreaming() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();

    assertNull(sync.onTick(true, true));
    sync.onItemObserved();
    sync.onItemObserved();
    sync.onItemObserved();

    assertFalse(sync.isAwaitingSearch());
    assertEquals(3, sync.observedCount());
  }

  @Test
  public void reportsCompleteWhenAReadRunUnderSearchGoesQuiet() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    assertNull(sync.onTick(true, true));
    sync.onItemObserved();
    sync.onItemObserved();

    Outcome outcome = null;
    for (int i = 0; i < CollectionLogSync.QUIET_TICKS && outcome == null; i++) {
      outcome = sync.onTick(true, true);
    }

    assertEquals(Outcome.COMPLETE, outcome);
    assertEquals(2, outcome.getObserved());
    // The outcome is reported exactly once, then the sync is done.
    assertFalse(sync.isActive());
    assertNull(sync.onTick(true, true));
  }

  @Test
  public void doesNotFinishWhileItemsAreStillArriving() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    assertNull(sync.onTick(true, true));

    // An item on every tick keeps the stream alive well past the quiet threshold.
    for (int i = 0; i < CollectionLogSync.QUIET_TICKS * 3; i++) {
      sync.onItemObserved();
      assertNull(sync.onTick(true, true));
    }

    assertTrue(sync.isActive());
    assertEquals(CollectionLogSync.QUIET_TICKS * 3, sync.observedCount());
  }

  @Test
  public void reportsPartialWhenTheLogClosesMidRead() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    assertNull(sync.onTick(true, true));
    sync.onItemObserved();
    sync.onItemObserved();
    sync.onItemObserved();

    Outcome outcome = sync.onTick(true, false);

    assertEquals(Outcome.PARTIAL, outcome);
    assertEquals(3, outcome.getObserved());
    assertFalse(sync.isActive());
  }

  /**
   * The conservative direction, and deliberately so. The search-open signal is a widget
   * read that cannot be verified outside a running client, so when it never arrives the
   * sync under-claims rather than calling an unverified read complete.
   */
  @Test
  public void reportsPartialWhenItemsArrivedWithoutTheSearchEverBeingSeen() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    sync.onItemObserved();

    Outcome outcome = null;
    for (int i = 0; i < CollectionLogSync.QUIET_TICKS && outcome == null; i++) {
      outcome = sync.onTick(false, true);
    }

    assertEquals(Outcome.PARTIAL, outcome);
    assertEquals(1, outcome.getObserved());
  }

  @Test
  public void cancelsSilentlyWhenTheLogClosesBeforeAnySearch() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();

    // Nothing was read, so there is no outcome worth telling the player about.
    assertNull(sync.onTick(false, false));
    assertFalse(sync.isActive());
  }

  @Test
  public void cancelsAnArmedSyncThePlayerNeverActedOn() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();

    idleTicks(sync, CollectionLogSync.ARM_TIMEOUT_TICKS - 1);
    assertTrue(sync.isActive());
    assertNull(sync.onTick(false, true));

    // Times out with no outcome: the player opened the menu and thought better of it.
    assertFalse(sync.isActive());
  }

  @Test
  public void theArmTimeoutDoesNotApplyOnceItemsAreStreaming() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    idleTicks(sync, CollectionLogSync.ARM_TIMEOUT_TICKS - 1);

    // A read that starts on the last possible tick still gets its full quiet window,
    // rather than being cut off by a timeout meant for a sync that never began.
    sync.onItemObserved();
    assertNull(sync.onTick(true, true));
    assertTrue(sync.isActive());
    assertEquals(1, sync.observedCount());
  }

  @Test
  public void armingTwiceRestartsTheReadRatherThanAccumulating() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    assertNull(sync.onTick(true, true));
    sync.onItemObserved();
    sync.onItemObserved();

    sync.arm();

    assertEquals(0, sync.observedCount());
    assertTrue(sync.isAwaitingSearch());
  }

  @Test
  public void resetAbandonsAnythingInFlight() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    sync.onItemObserved();

    // An account switch or a logout: the read belongs to the character that started it.
    sync.reset();

    assertFalse(sync.isActive());
    assertEquals(0, sync.observedCount());
    assertNull(sync.onTick(true, true));
  }
}
