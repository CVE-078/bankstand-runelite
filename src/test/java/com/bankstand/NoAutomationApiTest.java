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
 * Plugin Hub rule: the plugin may read the client, never drive it. Comments are exempt.
 * {@code MenuAction.RUNELITE} is allowed: the player still clicks the entry.
 */
public class NoAutomationApiTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

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
