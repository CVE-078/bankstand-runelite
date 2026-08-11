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

  /**
   * The exact defect the incident behind #608 reported: a claimed character told
   * the player to claim it, which sent them in circles across three pairing
   * attempts. A contested binding gets its own message with a different remedy.
   */
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

  /**
   * Additive-field compatibility (#753): a server this build predates never sends
   * `outcome` at all, and a future outcome this build does not recognise falls
   * back the same way. Both must keep behaving exactly as the pre-#608 client did,
   * never silently drop the notice or throw on a null/unrecognised value.
   */
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
