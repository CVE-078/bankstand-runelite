package com.bankstand.dto;

/**
 * A successful pairing response. The device token is a bearer credential: store it via {@link
 * com.bankstand.DeviceCredentialStore}, never {@code ConfigManager}, and never log it.
 */
public class PairResponse {
  private String deviceToken;
  private String deviceId;
  private String expiresAt;

  public String getDeviceToken() {
    return deviceToken;
  }

  public String getDeviceId() {
    return deviceId;
  }

  /** ISO-8601 instant. */
  public String getExpiresAt() {
    return expiresAt;
  }
}
