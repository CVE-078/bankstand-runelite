package com.bankstand;

import java.security.SecureRandom;
import java.util.UUID;

/** A dependency-free UUIDv7 generator: time-ordered ids for use as idempotency keys. */
public final class UuidV7 {
  private UuidV7() {}

  private static final SecureRandom RANDOM = new SecureRandom();

  public static String generate() {
    long ts = System.currentTimeMillis();
    byte[] b = new byte[16];
    RANDOM.nextBytes(b);
    // 48-bit big-endian millisecond timestamp in bytes 0..5.
    b[0] = (byte) (ts >>> 40);
    b[1] = (byte) (ts >>> 32);
    b[2] = (byte) (ts >>> 24);
    b[3] = (byte) (ts >>> 16);
    b[4] = (byte) (ts >>> 8);
    b[5] = (byte) ts;
    // Version 7 in the high nibble of byte 6.
    b[6] = (byte) ((b[6] & 0x0f) | 0x70);
    // RFC 4122 variant (10xx) in the high bits of byte 8.
    b[8] = (byte) ((b[8] & 0x3f) | 0x80);
    long msb = 0;
    long lsb = 0;
    for (int i = 0; i < 8; i++) {
      msb = (msb << 8) | (b[i] & 0xff);
    }
    for (int i = 8; i < 16; i++) {
      lsb = (lsb << 8) | (b[i] & 0xff);
    }
    return new UUID(msb, lsb).toString();
  }
}
