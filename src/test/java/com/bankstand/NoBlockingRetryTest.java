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
 * Holds the Plugin Hub's other line: a retry backs off by scheduling, never by blocking
 * a thread.
 *
 * <p>Review on PR #15400 flagged exactly this: {@code Thread.sleep} in the submit retry's
 * backoff, and the {@code Thread.currentThread().interrupt()} that came with catching its
 * {@code InterruptedException}. Fixed by moving the backoff onto the plugin's own {@code
 * ScheduledExecutorService} (see {@code BankstandClient#attempt}), which needed no blocked
 * thread and no interrupt handling at all. Source-scanning, the same shape as {@link
 * NoAutomationApiTest}, so a future retry loop reaching for the obvious-looking blocking
 * idiom fails a test instead of a review round trip.
 */
public class NoBlockingRetryTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

  private static final String[] BANNED = {
    "Thread.sleep(", "Thread.currentThread().interrupt()",
  };

  @Test
  public void noSourceFileBlocksAThreadToBackOff() throws IOException {
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
        "Plugin Hub bans blocking a thread to back off. Schedule the retry on a"
            + " ScheduledExecutorService instead:\n"
            + String.join("\n", offences),
        offences.isEmpty());
  }

  private static boolean isComment(String line) {
    String trimmed = line.trim();
    return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
  }
}
