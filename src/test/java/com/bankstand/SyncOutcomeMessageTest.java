package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.bankstand.CollectionLogSync.Outcome;
import org.junit.Test;

public class SyncOutcomeMessageTest {

  @Test
  public void statesWhatBankstandNowKnows() {
    assertEquals(
        "Collection log synced. 1432 entries known.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 1432));
  }

  /**
   * A partial read is not a failed one. Everything it saw is already stored, so the
   * wording says what is known rather than implying the sync broke.
   */
  @Test
  public void saysWhatIsKnownSoFarOnAPartialRead() {
    String message = BankstandPlugin.syncOutcomeMessage(Outcome.PARTIAL, 312);

    assertEquals("Partial read of your collection log. 312 entries known so far.", message);
    assertFalse(message.toLowerCase().contains("fail"));
  }

  /**
   * The running total, not this read's count. A search the player filtered enumerates
   * only the matches, so a per-read figure would report "12 entries" for a log holding
   * hundreds. The total cannot be wrong that way, and it answers what the player is
   * actually asking: what does Bankstand have.
   */
  @Test
  public void reportsTheTotalKnownRatherThanWhatOneReadSaw() {
    assertEquals(
        "Collection log synced. 211 entries known.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 211));
  }

  @Test
  public void countsOneEntryInTheSingular() {
    assertEquals(
        "Collection log synced. 1 entry known.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 1));
  }

  @Test
  public void carriesNoEmDash() {
    for (Outcome kind : Outcome.values()) {
      assertFalse(BankstandPlugin.syncOutcomeMessage(kind, 7).contains("—"));
    }
  }
}
