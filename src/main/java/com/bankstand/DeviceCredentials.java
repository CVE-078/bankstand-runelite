package com.bankstand;

import com.bankstand.dto.PairResponse;
import lombok.Data;
import lombok.ToString;

/**
 * This install's pairing: bearer token, device id, expiry and the server it was paired with. Per
 * install, not per account, since the server treats each token as one device.
 */
@Data
public class DeviceCredentials {

  // Never in toString(), so logging this object cannot leak the token.
  @ToString.Exclude private String token;
  private String deviceId;
  private String expiresAt;
  // Null in a pairing stored before this was recorded; those are taken as the default server.
  private String serverUrl;

  public static DeviceCredentials none() {
    return new DeviceCredentials();
  }

  public static DeviceCredentials of(PairResponse res, String serverUrl) {
    DeviceCredentials credentials = new DeviceCredentials();
    credentials.setToken(res.getDeviceToken());
    credentials.setDeviceId(res.getDeviceId());
    credentials.setExpiresAt(res.getExpiresAt());
    credentials.setServerUrl(serverUrl);
    return credentials;
  }

  public boolean isPaired() {
    return token != null && !token.trim().isEmpty();
  }

  public String pairedServerUrl() {
    return serverUrl == null ? BankstandKeys.DEFAULT_SERVER_URL : serverUrl;
  }

  /**
   * The token, but only for the origin it was paired with, so changing the Server URL never
   * sends it to another host. Null when unpaired or when the origins differ.
   */
  public String tokenFor(String serverUrl) {
    String origin = BankstandKeys.originOf(serverUrl);
    boolean same = origin != null && origin.equals(BankstandKeys.originOf(pairedServerUrl()));
    return isPaired() && same ? token : null;
  }
}
