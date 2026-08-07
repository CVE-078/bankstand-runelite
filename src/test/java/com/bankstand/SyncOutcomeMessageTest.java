package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.bankstand.CollectionLogSync.Outcome;
import org.junit.Test;

public class SyncOutcomeMessageTest {

  @Test
  public void statesHowManySlotsAreFilled() {
    assertEquals(
        "Collection log synced. 189 entries logged.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 189));
  }

  /** A partial read is not a failed one: everything it saw is already stored. */
  @Test
  public void saysHowManyAreFilledSoFarOnAPartialRead() {
    String message = BankstandPlugin.syncOutcomeMessage(Outcome.PARTIAL, 312);

    assertEquals("Partial read of your collection log. 312 entries logged so far.", message);
    assertFalse(message.toLowerCase().contains("fail"));
  }

  @Test
  public void countsOneEntryInTheSingular() {
    assertEquals(
        "Collection log synced. 1 entry logged.",
        BankstandPlugin.syncOutcomeMessage(Outcome.COMPLETE, 1));
  }

  @Test
  public void carriesNoEmDash() {
    for (Outcome kind : Outcome.values()) {
      assertFalse(BankstandPlugin.syncOutcomeMessage(kind, 7).contains("—"));
    }
  }
}
