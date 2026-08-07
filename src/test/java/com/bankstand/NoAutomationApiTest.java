package com.bankstand;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Holds the Plugin Hub's line: the plugin may read the client, never drive it.
 *
 * <p>Hub PR #11371 was closed with "use of client.menuAction is not allowed". The
 * guided collection log read exists because of that ruling, and the automated version
 * it replaced was two lines long and looked entirely reasonable. Nothing else in this
 * repo would notice those two lines coming back, and the cost of finding out from a
 * reviewer is a rejected submission and another round of review latency.
 *
 * <p>Source-scanning rather than behavioural, because that is what the constraint
 * actually is: a call that must not appear. Comments are exempt, so the reasoning can
 * name the banned call without tripping over itself. {@code MenuAction.RUNELITE} is
 * untouched by this and is meant to be: it types a menu entry the player clicks, which
 * is not the same thing as invoking one on their behalf.
 */
public class NoAutomationApiTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

  /** Calls that drive the client rather than observe it. */
  private static final String[] BANNED = {
    "client.menuAction(", "client.runScript(", "client.invokeMenuAction(",
  };

  @Test
  public void noSourceFileDrivesTheClient() throws IOException {
    List<String> offences = new ArrayList<>();
    try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
      for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
          String line = lines.get(i);
          if (isComment(line)) {
            continue;
          }
          for (String banned : BANNED) {
            if (line.contains(banned)) {
              offences.add(file + ":" + (i + 1) + "  " + line.trim());
            }
          }
        }
      }
    }
    assertTrue(
        "Plugin Hub bans driving the client. Observe the event the player's own click"
            + " raises instead:\n"
            + String.join("\n", offences),
        offences.isEmpty());
  }

  private static boolean isComment(String line) {
    String trimmed = line.trim();
    return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
  }
}
