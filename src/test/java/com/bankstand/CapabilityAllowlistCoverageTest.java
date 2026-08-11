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
 * Derives the gated capability set from the actual call sites, instead of trusting a second
 * hand-written list to stay in sync with them.
 *
 * <p>The literal list in {@code CapabilityManifestTest} has gone stale twice already:
 * {@code accountType} shipped without it being added there, then {@code notableDrops} and
 * {@code petDrops} shipped without it either. A hand-maintained list only catches a gap its
 * own author remembered to update, which is exactly the failure that recurred. This scans
 * every {@code .java} file under {@code src/main/java} for a {@code allows("...")} call and
 * asserts each name it finds is in {@link CapabilityManifest#SUPPORTED_CAPABILITIES}, so a
 * ninth call site added anywhere fails this test the moment it is written rather than
 * shipping a capability that can never be switched on.
 */
public class CapabilityAllowlistCoverageTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

  /** What a gate call looks like on the wire: {@code manifest.allows("someCapability")}. */
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

    // A scan that finds nothing is not a passing test, it is a broken one: the pattern
    // stopped matching real call sites and silently stopped protecting anything.
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
