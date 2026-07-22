package com.bankstand;

import java.util.Locale;

/**
 * Client-side handling of the short pairing code. Mirrors the Bankstand server's
 * {@code normalizePairingCode} exactly so the panel can validate input before a
 * request, and so the code sent over the wire is already canonical. The server
 * re-normalizes authoritatively, so this only ever repairs input, never mangles a
 * valid code.
 */
public final class PairingCodes {

  /**
   * Crockford base32 alphabet (digits plus letters, with I, L, O and U removed).
   * A generated code only ever uses these 32 symbols.
   */
  public static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  /** A valid code is 8 symbols once normalized. */
  public static final int CODE_LENGTH = 8;

  private PairingCodes() {}

  /**
   * Upper-cases, strips whitespace and the display dash, then folds the Crockford
   * look-alikes (O to 0, I and L to 1). Identical to the server's normalization.
   */
  public static String normalize(String input) {
    if (input == null) {
      return "";
    }
    return input
        .toUpperCase(Locale.ROOT)
        .replaceAll("[\\s-]", "")
        .replace('O', '0')
        .replace('I', '1')
        .replace('L', '1');
  }

  /** True when {@code normalized} is exactly 8 characters, all in the Crockford alphabet. */
  public static boolean isValid(String normalized) {
    if (normalized == null || normalized.length() != CODE_LENGTH) {
      return false;
    }
    for (int i = 0; i < normalized.length(); i++) {
      if (CROCKFORD_ALPHABET.indexOf(normalized.charAt(i)) < 0) {
        return false;
      }
    }
    return true;
  }
}
