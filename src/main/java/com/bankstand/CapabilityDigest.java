package com.bankstand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * A stable SHA-256 fingerprint of a capability block, so an acked baseline can be persisted.
 * Keys are sorted first: the source maps have no stable iteration order, and an unsorted digest
 * would change on every cycle and resend forever.
 */
public final class CapabilityDigest {

  private CapabilityDigest() {}

  /** Separator and terminator keep {@code {"ab": "c"}} and {@code {"a": "bc"}} distinct. */
  public static String of(Map<String, ?> block) {
    StringBuilder canonical = new StringBuilder();
    for (Map.Entry<String, ?> entry : new TreeMap<String, Object>(block).entrySet()) {
      canonical.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
    }
    return sha256(canonical.toString());
  }

  private static String sha256(String input) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      // Unreachable: every JVM ships SHA-256.
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
