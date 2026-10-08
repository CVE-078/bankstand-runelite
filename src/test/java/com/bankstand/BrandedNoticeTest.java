package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BrandedNoticeTest {

  @Test
  public void namesBankstandInBrandGoldAndLeavesTheBodyAlone() {
    // Only the prefix is coloured, so the body stays legible in every chat mode.
    assertEquals(
        "<col=b3730a>Bankstand: </col>Verified as Crusty Jobby.",
        BankstandPlugin.brandedNotice("Verified as Crusty Jobby."));
  }

  @Test
  public void usesTheLightSurfaceAccent() {
    // b3730a, not f0a830: the default chat box is pale and the brighter gold washes out.
    assertTrue(BankstandPlugin.brandedNotice("Connected.").contains("b3730a"));
  }

  @Test
  public void coloursNothingButThePrefix() {
    String line = BankstandPlugin.brandedNotice("Could not sync your progress.");
    assertEquals(1, line.split("<col=", -1).length - 1);
  }
}
