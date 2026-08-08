package com.bankstand;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BankstandKeysTest {

  // Every test below asserts against the default rather than a literal, which would
  // pass just as happily if the default pointed somewhere nobody deploys. This is the
  // one that reads it, and it is worth pinning: a wrong host is invisible to the
  // player, who sees only that Bankstand stopped updating.
  @Test
  public void defaultsToTheCanonicalOrigin() {
    assertEquals("https://bankstand.gg", BankstandKeys.DEFAULT_SERVER_URL);
  }

  @Test
  public void fallsBackToTheDefaultWhenUnset() {
    assertEquals(BankstandKeys.DEFAULT_SERVER_URL, BankstandKeys.normaliseServerUrl(null));
  }

  @Test
  public void treatsABlankUrlAsUnset() {
    // ConfigManager hands back a present-but-empty string once a player has cleared
    // the field. Used as-is it fails every request at the socket.
    assertEquals(BankstandKeys.DEFAULT_SERVER_URL, BankstandKeys.normaliseServerUrl(""));
    assertEquals(BankstandKeys.DEFAULT_SERVER_URL, BankstandKeys.normaliseServerUrl("   "));
  }

  @Test
  public void trimsAPastedUrl() {
    assertEquals("http://localhost:3001", BankstandKeys.normaliseServerUrl("  http://localhost:3001 "));
  }

  @Test
  public void keepsAnExplicitUrl() {
    assertEquals("http://localhost:3001", BankstandKeys.normaliseServerUrl("http://localhost:3001"));
  }
}
