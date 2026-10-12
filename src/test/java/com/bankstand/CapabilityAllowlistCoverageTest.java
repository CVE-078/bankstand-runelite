package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Every {@code allows("...")} call under src/main/java names a capability in
 * {@link CapabilityManifest#SUPPORTED_CAPABILITIES}, derived from source rather than a list.
 */
public class CapabilityAllowlistCoverageTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

  private static final Pattern ALLOWS_CALL = Pattern.compile("allows\\(\"([^\"]+)\"\\)");

  @Test
  public void everyAllowsCallSiteNamesAKnownCapability() throws IOException {
    List<String> gatedOn = new ArrayList<>();
    try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
      for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines) {
          if (isComment(line)) {
            continue;
          }
          Matcher matcher = ALLOWS_CALL.matcher(line);
          while (matcher.find()) {
            gatedOn.add(matcher.group(1));
          }
        }
      }
    }

    // A scan that finds nothing means the pattern broke.
    assertFalse(
        "found no manifest.allows(...) call sites under src/main/java; the scan itself is"
            + " broken, this is not a pass",
        gatedOn.isEmpty());

    for (String capability : gatedOn) {
      assertTrue(
          capability + " is gated on by a manifest.allows(...) call in src/main/java but is"
              + " missing from SUPPORTED_CAPABILITIES, so that call can never return true",
          CapabilityManifest.SUPPORTED_CAPABILITIES.contains(capability));
    }
  }

  private static boolean isComment(String line) {
    String trimmed = line.trim();
    return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
  }
}
