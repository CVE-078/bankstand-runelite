package com.bankstand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * A stable fingerprint of one complete capability block.
 *
 * <p>Exists so an acknowledged baseline can be written to disk. In memory the baselines
 * keep the block itself and compare it exactly, which is stronger than a digest because
 * it cannot collide; across a restart that is the wrong trade, since skills, quests and
 * diaries are all re-read live on the next capture and only the verdict "the server has
 * already seen this" needs to survive. Sixty-four characters carries that verdict for a
 * block of any size.
 *
 * <p>Canonical before hashed, and that ordering is load-bearing. The capture rebuilds
 * these maps every cycle from a {@code HashMap}, whose iteration order is not a
 * contract, so a digest taken over the block as it happens to be laid out would differ
 * on a cycle where nothing moved, and the block would be resent forever.
 *
 * <p>Pure and deterministic.
 */
public final class CapabilityDigest {

  private CapabilityDigest() {}

  /**
   * Digests a keyed block over its sorted keys.
   *
   * <p>Key and value are separated and each entry terminated, so {@code {"ab": "c"}} and
   * {@code {"a": "bc"}} cannot serialize to the same bytes.
   */
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
      // Every JVM ships SHA-256. Unreachable, and not worth a fallback that would be
      // silently weaker than the thing it stands in for.
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
