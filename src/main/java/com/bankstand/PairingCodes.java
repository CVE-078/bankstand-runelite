package com.bankstand;

import java.util.Locale;

/** Pairing code normalization and validation. Must match the server's normalization exactly. */
public final class PairingCodes {

  /** Crockford base32 (I, L, O and U removed). */
  public static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  public static final int CODE_LENGTH = 8;

  private PairingCodes() {}

  /** Upper-cases, strips whitespace and dashes, folds O to 0 and I/L to 1. */
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
