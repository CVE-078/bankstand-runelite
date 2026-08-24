package com.bankstand.dto;

/**
 * The successful pairing response from {@code POST /api/plugin/v1/pair}:
 * {@code { deviceToken, deviceId, expiresAt }}. Populated by Gson. The device
 * token is a bearer credential returned exactly once; store it via {@link
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

  /** ISO-8601 instant the token expires (server sends created_at + 90 days). */
  public String getExpiresAt() {
    return expiresAt;
  }
}
