package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class IdentityNoticeTest {

  @Test
  public void confirmsTheLinkedCharacterWhenVerified() {
    assertEquals(
        "Verified as B0aty.",
        BankstandPlugin.identityNoticeFor(true, "B0aty", "bound"));
  }

  @Test
  public void saysNotClaimedWhenTheNameMatchesNoClaim() {
    assertEquals(
        "This character is not claimed on Bankstand yet, so nothing will sync."
            + " Claim it on your account page.",
        BankstandPlugin.identityNoticeFor(false, null, "no_claim"));
  }

  /** A contested binding gets its own message, not "claim this character". */
  @Test
  public void saysAlreadyLinkedRatherThanNotClaimedWhenAnotherAccountHoldsIt() {
    String message = BankstandPlugin.identityNoticeFor(false, null, "held_by_other");
    assertEquals(
        "This character is already linked, so there is nothing to claim here."
            + " If that looks wrong, contact support.",
        message);
    assertFalse(message.toLowerCase().contains("not claimed"));
  }

  @Test
  public void saysAlreadyLinkedWhenThisAccountHashBindsADifferentCharacter() {
    assertEquals(
        "This character is already linked, so there is nothing to claim here."
            + " If that looks wrong, contact support.",
        BankstandPlugin.identityNoticeFor(false, null, "hash_bound_elsewhere"));
  }

  /** A missing or unknown {@code outcome} falls back to the generic notice, never throws. */
  @Test
  public void fallsBackToNotClaimedForAMissingOrUnrecognisedOutcome() {
    assertEquals(
        "This character is not claimed on Bankstand yet, so nothing will sync."
            + " Claim it on your account page.",
        BankstandPlugin.identityNoticeFor(false, null, null));
    assertEquals(
        "This character is not claimed on Bankstand yet, so nothing will sync."
            + " Claim it on your account page.",
        BankstandPlugin.identityNoticeFor(false, null, "a_future_outcome_this_build_predates"));
  }

  @Test
  public void carriesNoEmDash() {
    for (String outcome :
        new String[] {"bound", "no_account", "no_display_name", "no_claim", "held_by_other",
            "hash_bound_elsewhere", null, "unknown"}) {
      assertFalse(
          BankstandPlugin.identityNoticeFor(false, "B0aty", outcome).contains("—"));
    }
    assertFalse(BankstandPlugin.identityNoticeFor(true, "B0aty", "bound").contains("—"));
  }
}
