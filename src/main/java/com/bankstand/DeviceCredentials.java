package com.bankstand;

import lombok.Data;

/**
 * This client's pairing with a Bankstand account: a bearer token, the server's id for
 * this device, and when the token expires.
 *
 * <p>Deliberately a device's credentials rather than an account's. The server keys one
 * {@code plugin_device} row per token, with its own name, last-seen time and revocation,
 * so two machines holding the same token are one device as far as Bankstand is concerned:
 * revoking either revokes both, and neither one's last-seen time survives the other's
 * submit. Keeping this per install is what makes several clients on one account work.
 */
@Data
public class DeviceCredentials {

  private String token;
  private String deviceId;
  private String expiresAt;

  public static DeviceCredentials none() {
    return new DeviceCredentials();
  }

  public boolean isPaired() {
    return token != null && !token.trim().isEmpty();
  }
}
