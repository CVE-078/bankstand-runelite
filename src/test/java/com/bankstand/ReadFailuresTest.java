package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import org.junit.Test;

/** Against the Gson the client ships, which wraps a reader's IOException as a syntax error. */
public class ReadFailuresTest {

  private static Throwable parse(Reader reader) {
    try {
      new Gson().fromJson(reader, new TypeToken<List<OutboxEntry>>() {}.getType());
    } catch (RuntimeException e) {
      return e;
    }
    fail("expected a failure");
    return null;
  }

  /** Hands out part of a valid document, then fails like a disk or a lock would. */
  private static Reader failingAfter(String start) {
    StringReader head = new StringReader(start);
    return new Reader() {
      @Override
      public int read(char[] buf, int off, int len) throws IOException {
        int n = head.read(buf, off, len);
        if (n == -1) {
          throw new IOException("device not ready");
        }
        return n;
      }

      @Override
      public void close() {}
    };
  }

  @Test
  public void anIoErrorMidReadIsNotDamage() {
    assertFalse(ReadFailures.isDamaged(parse(failingAfter("[{\"accountHash\":"))));
  }

  @Test
  public void malformedOrTruncatedJsonIsDamage() {
    assertTrue(ReadFailures.isDamaged(parse(new StringReader("{not json"))));
    assertTrue(ReadFailures.isDamaged(parse(new StringReader("[{\"accountHash\":"))));
  }

  private static Throwable parseBytes(byte[] bytes) throws IOException {
    java.nio.file.Path file = java.nio.file.Files.createTempFile("queue", ".json");
    try {
      java.nio.file.Files.write(file, bytes);
      try (Reader reader =
          java.nio.file.Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
        return parse(reader);
      }
    } finally {
      java.nio.file.Files.delete(file);
    }
  }

  @Test
  public void undecodableBytesAreDamage() throws IOException {
    byte[] prefix = "[{\"accountHash\":\"".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    byte[] invalid = java.util.Arrays.copyOf(prefix, prefix.length + 2);
    invalid[prefix.length] = (byte) 0xC3;
    invalid[prefix.length + 1] = (byte) 0x28;
    assertTrue("not UTF-8", ReadFailures.isDamaged(parseBytes(invalid)));
    byte[] truncated = java.util.Arrays.copyOf(prefix, prefix.length + 2);
    truncated[prefix.length] = (byte) 0xE2;
    truncated[prefix.length + 1] = (byte) 0x82;
    assertTrue("cut mid-character", ReadFailures.isDamaged(parseBytes(truncated)));
  }
}
