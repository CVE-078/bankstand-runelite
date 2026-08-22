package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceCredentialsTest {

  /**
   * {@code NoSecretsInLogsTest} bans passing the literal identifiers {@code token}/{@code
   * getToken()} to a log call by scanning source text, but cannot see through an object
   * argument whose own {@code toString()} embeds the credential: {@code log.debug("...",
   * credentials)} reads as passing {@code credentials}, not the token. This is the property
   * that closes that gap rather than merely relying on nobody ever passing the object whole.
   */
  @Test
  public void toStringNeverIncludesTheToken() {
    DeviceCredentials credentials = new DeviceCredentials();
    credentials.setToken("bsd_super_secret_token_value");
    credentials.setDeviceId("dev_1");
    credentials.setExpiresAt("2027-01-01T00:00:00Z");

    String rendered = credentials.toString();

    assertFalse(rendered.contains("bsd_super_secret_token_value"));
    // The other fields are not credentials, so toString() staying useful for
    // them is a deliberate trade-off, not something this exclusion should cost.
    assertTrue(rendered.contains("dev_1"));
    assertTrue(rendered.contains("2027-01-01T00:00:00Z"));
  }
}
