package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

public class UuidV7Test {
  @Test
  public void generatesACanonicalVersion7Uuid() {
    String s = UuidV7.generate();
    // Canonical 8-4-4-4-12 form, parseable by java.util.UUID.
    UUID parsed = UUID.fromString(s);
    assertEquals("36 chars", 36, s.length());
    assertEquals("version 7", 7, parsed.version());
    assertEquals("RFC variant", 2, parsed.variant());
    assertEquals("lowercase", s, s.toLowerCase());
  }

  @Test
  public void generatesDistinctValues() {
    assertNotEquals(UuidV7.generate(), UuidV7.generate());
  }

  @Test
  public void isTimeOrderedAcrossAShortDelay() throws Exception {
    String a = UuidV7.generate();
    Thread.sleep(2);
    String b = UuidV7.generate();
    // UUIDv7 leads with a millisecond timestamp, so lexical order tracks time.
    assertTrue("later uuid sorts after earlier", b.compareTo(a) > 0);
  }
}
