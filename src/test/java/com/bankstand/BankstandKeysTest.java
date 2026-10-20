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

  @Test
  public void keepsHttpsAndLocalHttp() {
    assertEquals("https://bankstand.gg", BankstandKeys.normaliseServerUrl("https://bankstand.gg"));
    assertEquals("http://127.0.0.1:3001", BankstandKeys.normaliseServerUrl("http://127.0.0.1:3001"));
    assertEquals("http://[::1]:3001", BankstandKeys.normaliseServerUrl("http://[::1]:3001"));
  }

  @Test
  public void upgradesRemoteHttpToHttps() {
    assertEquals("https://example.com", BankstandKeys.normaliseServerUrl("http://example.com"));
    assertEquals("https://example.com", BankstandKeys.normaliseServerUrl("HTTP://example.com"));
    assertEquals(
        "https://localhost.example.com",
        BankstandKeys.normaliseServerUrl("http://localhost.example.com"));
  }

  @Test
  public void fallsBackToTheDefaultForAnUnusableUrl() {
    assertEquals(BankstandKeys.DEFAULT_SERVER_URL, BankstandKeys.normaliseServerUrl("ftp://example.com"));
    assertEquals(BankstandKeys.DEFAULT_SERVER_URL, BankstandKeys.normaliseServerUrl("bankstand.gg"));

  }

  @Test
  public void reducesAUrlToItsOrigin() {
    assertEquals("https://bankstand.gg:443", BankstandKeys.originOf("https://BankStand.gg/x/?q#f"));
    assertEquals("http://localhost:3001", BankstandKeys.originOf(" http://localhost:3001/ "));
    assertEquals("https://my_app:3000", BankstandKeys.originOf("https://my_app:3000/x"));
    // The host a request really goes to, not the text before an "@".
    assertEquals("https://evil.test:443", BankstandKeys.originOf("https://bankstand.gg@evil.test"));
    org.junit.Assert.assertNull(BankstandKeys.originOf("ftp://bankstand.gg"));
    org.junit.Assert.assertNull(BankstandKeys.originOf(null));
  }

  @Test
  public void keepsAPortAndPathWhenUpgrading() {
    assertEquals("https://my_app:3000/x", BankstandKeys.normaliseServerUrl("http://my_app:3000/x"));
    assertEquals("http://localhost:3001/", BankstandKeys.normaliseServerUrl("http://localhost:3001/"));
  }
}
