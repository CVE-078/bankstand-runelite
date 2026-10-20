package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.Test;

public class DeviceCredentialsTest {

  /** toString() must not leak the token, which the source-scanning log test cannot see. */
  @Test
  public void toStringNeverIncludesTheToken() {
    DeviceCredentials credentials = new DeviceCredentials();
    credentials.setToken("bsd_super_secret_token_value");
    credentials.setDeviceId("dev_1");
    credentials.setExpiresAt("2027-01-01T00:00:00Z");

    String rendered = credentials.toString();

    assertFalse(rendered.contains("bsd_super_secret_token_value"));
    assertTrue(rendered.contains("dev_1"));
    assertTrue(rendered.contains("2027-01-01T00:00:00Z"));
  }

  private static DeviceCredentials pairedWith(String serverUrl) {
    DeviceCredentials credentials = new DeviceCredentials();
    credentials.setToken("bsd_tok");
    credentials.setServerUrl(serverUrl);
    return credentials;
  }

  @Test
  public void givesTheTokenOnlyToTheOriginItWasPairedWith() {
    DeviceCredentials paired = pairedWith("https://bankstand.example");

    assertEquals("bsd_tok", paired.tokenFor("https://bankstand.example"));
    assertEquals("bsd_tok", paired.tokenFor("https://BANKSTAND.example:443/api"));
    assertNull("another host", paired.tokenFor("https://other.example"));
    assertNull("another port", paired.tokenFor("https://bankstand.example:8443"));
    assertNull("another scheme", paired.tokenFor("http://bankstand.example"));
    assertNull("a lookalike host", paired.tokenFor("https://bankstand.example.evil.test"));
    assertNull("userinfo", paired.tokenFor("https://bankstand.example@evil.test"));
    assertNull("unusable", paired.tokenFor("not a url"));
  }

  @Test
  public void anOlderPairingWithNoServerBelongsToTheDefaultServerOnly() {
    DeviceCredentials legacy = pairedWith(null);

    assertEquals("bsd_tok", legacy.tokenFor(BankstandKeys.DEFAULT_SERVER_URL));
    assertNull(legacy.tokenFor("http://localhost:3000"));
  }

  @Test
  public void anUnpairedRecordHasNoTokenForAnyServer() {
    assertNull(DeviceCredentials.none().tokenFor(BankstandKeys.DEFAULT_SERVER_URL));
  }

  @Test
  public void theServerSurvivesTheFileRoundTrip() {
    Gson gson = new Gson();
    DeviceCredentials read =
        gson.fromJson(gson.toJson(pairedWith("http://localhost:3000")), DeviceCredentials.class);
    assertEquals("bsd_tok", read.tokenFor("http://localhost:3000"));
    assertNull(read.tokenFor(BankstandKeys.DEFAULT_SERVER_URL));
  }
}
