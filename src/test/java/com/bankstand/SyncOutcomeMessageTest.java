package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.bankstand.CollectionLogSync.Outcome;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SyncOutcomeMessageTest {

  /** The five real categories, as read off a live overview screen. */
  private static OverviewCounts realOverview() {
    return OverviewCounts.of(Arrays.asList("35/340", "2/67", "15/611", "18/257", "119/437"));
  }

  private static OverviewCounts unreadable() {
    return OverviewCounts.of(Collections.emptyList());
  }

  /**
   * The game's figure wins. Measured: our item-id matching said 193 where the overview
   * said 189, so quoting ours told the player a number their own game contradicts.
   */
  @Test
  public void quotesTheGamesOwnFigureWhenTheOverviewWasReadable() {
    assertEquals(
        "Collection log synced. 189 of 1712 logged.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 193, realOverview()));
  }

  @Test
  public void fallsBackToOurOwnCountWhenTheOverviewWasNot() {
    assertEquals(
        "Collection log synced. 193 entries logged.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 193, unreadable()));
  }

  @Test
  public void fallsBackWhenTheOverviewWasOnlyHalfBuilt() {
    OverviewCounts partial = OverviewCounts.of(Arrays.asList("35/340", "2/67"));

    assertEquals(
        "Collection log synced. 193 entries logged.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 193, partial));
  }

  @Test
  public void fallsBackWhenThereIsNoOverviewAtAll() {
    assertEquals(
        "Collection log synced. 193 entries logged.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 193, null));
  }

  /** A partial read is not a failed one: everything it saw is already stored. */
  @Test
  public void saysWhatIsLoggedSoFarOnAPartialRead() {
    String message = BankstandPlugin.syncOutcomeMessage(Outcome.PARTIAL, 312, unreadable());

    assertEquals("Partial read of your collection log. 312 entries logged so far.", message);
    assertFalse(message.toLowerCase().contains("fail"));
  }

  @Test
  public void countsOneEntryInTheSingular() {
    assertEquals(
        "Collection log synced. 1 entry logged.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 1, unreadable()));
  }

  @Test
  public void carriesNoEmDash() {
    for (Outcome kind : Outcome.values()) {
      assertFalse(BankstandPlugin.syncOutcomeMessage(kind, 7, realOverview()).contains("—"));
    }
  }
}
