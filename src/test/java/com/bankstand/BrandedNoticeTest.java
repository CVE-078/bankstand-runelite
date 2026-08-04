package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BrandedNoticeTest {

  @Test
  public void namesBankstandInBrandGoldAndLeavesTheBodyAlone() {
    // The colour tag closes after the prefix, so the body renders in whatever colour
    // the player's chat already uses. That is the part they have to read, and it stays
    // legible across every chat mode and transparency setting.
    assertEquals(
        "<col=b3730a>Bankstand: </col>Verified as Crusty Jobby.",
        BankstandPlugin.brandedNotice("Verified as Crusty Jobby."));
  }

  @Test
  public void usesTheLightSurfaceAccent() {
    // b3730a, not the site's brighter f0a830: the chat box is pale parchment by
    // default, where the dark-theme gold washes out.
    assertTrue(BankstandPlugin.brandedNotice("Connected.").contains("b3730a"));
  }

  @Test
  public void coloursNothingButThePrefix() {
    String line = BankstandPlugin.brandedNotice("Could not sync your progress.");
    assertEquals(1, line.split("<col=", -1).length - 1);
  }
}
