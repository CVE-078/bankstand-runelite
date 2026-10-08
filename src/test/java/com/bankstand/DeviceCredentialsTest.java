package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
