package com.bankstand;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BankstandKeysTest {

  // A wrong default host is invisible to the player, so pin it.
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
    // A cleared field comes back as an empty string, not null.
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
