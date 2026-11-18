package com.bankstand.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A successful pairing response. The device token is a bearer credential: store it via {@link
 * com.bankstand.DeviceCredentialStore}, never {@code ConfigManager}, and never log it.
 */
public class PairResponse {
  private String deviceToken;
  private String deviceId;
  private String expiresAt;
  private List<Generation> captureGenerations;

  /** A cleared character's capture generation, by its decimal account hash. */
  private static class Generation {
    private String accountHash;
    private Integer generation;
  }

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

  /** The capture generations the server named, skipping any entry it cannot read. */
  public Map<Long, Integer> captureGenerationsByAccount() {
    Map<Long, Integer> byAccount = new LinkedHashMap<>();
    if (captureGenerations != null) {
      for (Generation g : captureGenerations) {
        if (g == null || g.accountHash == null || g.generation == null) {
          continue;
        }
        try {
          byAccount.put(Long.parseLong(g.accountHash), g.generation);
        } catch (NumberFormatException ignored) {
          // Unreadable: that character learns its generation from its first ack instead.
        }
      }
    }
    return byAccount;
  }
}
