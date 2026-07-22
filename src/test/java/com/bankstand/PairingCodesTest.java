package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PairingCodesTest {

  @Test
  public void upperCasesAndStripsWhitespaceAndDash() {
    assertEquals("ABCDEFGH", PairingCodes.normalize("abcd-efgh"));
    assertEquals("ABCDEFGH", PairingCodes.normalize("  ABCD EFGH "));
  }

  @Test
  public void foldsCrockfordLookAlikesLikeTheServer() {
    // O -> 0, I -> 1, L -> 1, matching the server's normalizePairingCode.
    assertEquals("001111", PairingCodes.normalize("OoIiLl"));
  }

  @Test
  public void nullNormalizesToEmpty() {
    assertEquals("", PairingCodes.normalize(null));
  }

  @Test
  public void validityRequiresEightCrockfordChars() {
    assertTrue(PairingCodes.isValid("ABCD1234"));
    assertFalse(PairingCodes.isValid("ABCD123")); // too short
    assertFalse(PairingCodes.isValid("ABCD12345")); // too long
    assertFalse(PairingCodes.isValid("ABCDEFGU")); // U is not in the alphabet
    assertFalse(PairingCodes.isValid(null));
  }
}
