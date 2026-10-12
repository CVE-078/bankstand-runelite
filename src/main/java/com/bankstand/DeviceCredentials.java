package com.bankstand;

import lombok.Data;
import lombok.ToString;

/**
 * This install's pairing: bearer token, device id and expiry. Per install, not per account, since
 * the server treats each token as one device.
 */
@Data
public class DeviceCredentials {

  // Never in toString(), so logging this object cannot leak the token.
  @ToString.Exclude private String token;
  private String deviceId;
  private String expiresAt;

  public static DeviceCredentials none() {
    return new DeviceCredentials();
  }

  public boolean isPaired() {
    return token != null && !token.trim().isEmpty();
  }
}
