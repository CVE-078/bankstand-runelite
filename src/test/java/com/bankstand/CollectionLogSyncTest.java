package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bankstand.CollectionLogSync.Outcome;
import org.junit.Test;

public class CollectionLogSyncTest {

  private static void idleTicks(CollectionLogSync sync, int count) {
    for (int i = 0; i < count; i++) {
      assertNull(sync.onTick(false, true));
    }
  }

  @Test
  public void ignoresOrdinaryPageBrowsing() {
    CollectionLogSync sync = new CollectionLogSync();

    assertFalse(sync.isActive());
    // The same script fires on a page turn; it must not report a whole-log sync.
    sync.onItemObserved(1, false);
    sync.onItemObserved(2, false);

    assertFalse(sync.isActive());
    assertEquals(0, sync.observedCount());
    assertNull(sync.onTick(false, true));
  }

  /** The Hub bans driving the Search, so the player clicking the game's own Search must suffice. */
  @Test
  public void aSearchStartsAReadWithNothingArmed() {
    CollectionLogSync sync = new CollectionLogSync();

    sync.onItemObserved(1, true);
    sync.onItemObserved(2, true);

    assertTrue(sync.isActive());
    assertFalse(sync.isAwaitingSearch());
    assertEquals(2, sync.observedCount());
  }

  @Test
  public void anUnarmedSearchStillReportsComplete() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.onItemObserved(1, true);

    Outcome outcome = null;
    for (int i = 0; i < CollectionLogSync.QUIET_TICKS && outcome == null; i++) {
      outcome = sync.onTick(true, true);
    }

    assertEquals(Outcome.COMPLETE, outcome);
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
    sync.onItemObserved(3, true);
    sync.onItemObserved(4, true);
    sync.onItemObserved(5, true);

    assertFalse(sync.isAwaitingSearch());
    assertEquals(3, sync.observedCount());
  }

  @Test
  public void reportsCompleteWhenAReadRunUnderSearchGoesQuiet() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    assertNull(sync.onTick(true, true));
    sync.onItemObserved(6, true);
    sync.onItemObserved(7, true);

    Outcome outcome = null;
    for (int i = 0; i < CollectionLogSync.QUIET_TICKS && outcome == null; i++) {
      outcome = sync.onTick(true, true);
    }

    assertEquals(Outcome.COMPLETE, outcome);
    assertEquals(2, outcome.getObserved());
    assertFalse(sync.isActive());
    assertNull(sync.onTick(true, true));
  }

  /** Eight Prospector ids fill four slots, and the count says four. */
  @Test
  public void countsAVariantAsTheSlotItFills() {
    CollectionLogSync sync = new CollectionLogSync();
    for (int id : new int[] {12013, 12014, 12015, 12016, 29472, 29474, 29476, 29478}) {
      sync.onItemObserved(id, true);
    }

    assertEquals(4, sync.observedCount());
  }

  @Test
  public void countsDistinctEntriesRatherThanScriptFires() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    assertNull(sync.onTick(true, true));

    for (int repeat = 0; repeat < 2; repeat++) {
      for (int id = 1; id <= 211; id++) {
        sync.onItemObserved(id, true);
      }
    }

    assertEquals(211, sync.observedCount());
  }

  @Test
  public void doesNotFinishWhileItemsAreStillArriving() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    assertNull(sync.onTick(true, true));

    for (int i = 0; i < CollectionLogSync.QUIET_TICKS * 3; i++) {
      sync.onItemObserved(100 + i, true);
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
    sync.onItemObserved(9, true);
    sync.onItemObserved(10, true);
    sync.onItemObserved(11, true);

    Outcome outcome = sync.onTick(true, false);

    assertEquals(Outcome.PARTIAL, outcome);
    assertEquals(3, outcome.getObserved());
    assertFalse(sync.isActive());
  }

  /**
   * The search-open signal cannot be verified outside a live client, so when it never
   * arrives the sync under-claims rather than calling the read complete.
   */
  @Test
  public void reportsPartialWhenItemsArrivedWithoutTheSearchEverBeingSeen() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    sync.onItemObserved(12, true);

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

    assertFalse(sync.isActive());
  }

  @Test
  public void theArmTimeoutDoesNotApplyOnceItemsAreStreaming() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    idleTicks(sync, CollectionLogSync.ARM_TIMEOUT_TICKS - 1);

    // A read starting on the last tick still gets its full quiet window.
    sync.onItemObserved(13, true);
    assertNull(sync.onTick(true, true));
    assertTrue(sync.isActive());
    assertEquals(1, sync.observedCount());
  }

  @Test
  public void armingTwiceRestartsTheReadRatherThanAccumulating() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    assertNull(sync.onTick(true, true));
    sync.onItemObserved(14, true);
    sync.onItemObserved(15, true);

    sync.arm();

    assertEquals(0, sync.observedCount());
    assertTrue(sync.isAwaitingSearch());
  }

  @Test
  public void resetAbandonsAnythingInFlight() {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    sync.onItemObserved(16, true);

    sync.reset();

    assertFalse(sync.isActive());
    assertEquals(0, sync.observedCount());
    assertNull(sync.onTick(true, true));
  }
}
