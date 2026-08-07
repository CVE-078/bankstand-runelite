package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.bankstand.CollectionLogSync.Outcome;
import org.junit.Test;

public class SyncOutcomeMessageTest {

  private static final LogTotals REAL = LogTotals.fromTitle("Collection Log - 189/1712");

  /**
   * The game's figure wins. Measured: our item-id matching said 193 where the log title
   * said 189, so quoting ours told the player a number their own game contradicts.
   */
  @Test
  public void quotesTheGamesOwnFigureWhenTheTitleWasReadable() {
    assertEquals(
        "Collection log synced. 189 of 1712 logged.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 193, REAL));
  }

  @Test
  public void fallsBackToOurOwnCountWhenTheTitleWasNot() {
    assertEquals(
        "Collection log synced. 193 entries logged.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 193, null));
  }

  /** A partial read is not a failed one: everything it saw is already stored. */
  @Test
  public void saysWhatIsLoggedSoFarOnAPartialRead() {
    String message = BankstandPlugin.syncOutcomeMessage(Outcome.PARTIAL, 312, null);

    assertEquals("Partial read of your collection log. 312 entries logged so far.", message);
    assertFalse(message.toLowerCase().contains("fail"));
  }

  @Test
  public void countsOneEntryInTheSingular() {
    assertEquals(
        "Collection log synced. 1 entry logged.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 1, null));
  }

  @Test
  public void carriesNoEmDash() {
    for (Outcome kind : Outcome.values()) {
      assertFalse(BankstandPlugin.syncOutcomeMessage(kind, 7, REAL).contains("—"));
    }
  }
}
