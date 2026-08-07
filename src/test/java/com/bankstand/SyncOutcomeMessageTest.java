package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.CollectionLogSync.Outcome;
import org.junit.Test;

public class SyncOutcomeMessageTest {

  private static Outcome outcome(Outcome kind, int observed) {
    CollectionLogSync sync = new CollectionLogSync();
    sync.arm();
    boolean searchOpen = kind == Outcome.COMPLETE;
    sync.onTick(searchOpen, true);
    for (int i = 0; i < observed; i++) {
      sync.onItemObserved(i);
    }
    Outcome result = null;
    for (int i = 0; i < CollectionLogSync.QUIET_TICKS && result == null; i++) {
      result = sync.onTick(searchOpen, true);
    }
    assertEquals(kind, result);
    return result;
  }

  @Test
  public void statesTheCountOnASuccessfulRead() {
    assertEquals(
        "Synced 1432 entries from your collection log.",
        BankstandPlugin.syncOutcomeMessage(outcome(Outcome.COMPLETE, 1432)));
  }

  /**
   * A partial read is not a failed one. Every entry it saw is already stored, so the
   * wording says what was kept and what to do next rather than implying the sync broke
   * and the player has to start over.
   */
  @Test
  public void saysWhatWasKeptAndWhatToDoOnAPartialRead() {
    String message = BankstandPlugin.syncOutcomeMessage(outcome(Outcome.PARTIAL, 312));

    assertEquals("Partial sync, 312 entries kept. Run Search again to finish.", message);
    assertFalse(message.toLowerCase().contains("fail"));
    assertTrue(message.contains("kept"));
  }

  @Test
  public void countsOneEntryInTheSingular() {
    assertEquals(
        "Synced 1 entry from your collection log.",
        BankstandPlugin.syncOutcomeMessage(outcome(Outcome.COMPLETE, 1)));
  }

  @Test
  public void carriesNoEmDash() {
    for (Outcome kind : Outcome.values()) {
      assertFalse(BankstandPlugin.syncOutcomeMessage(outcome(kind, 7)).contains("—"));
    }
  }
}
