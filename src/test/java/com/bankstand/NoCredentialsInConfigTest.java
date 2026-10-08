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
 * The device token is never written through {@code ConfigManager}, which syncs config to
 * RuneLite's servers. Keys may still be read and unset.
 */
public class NoCredentialsInConfigTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

  private static final String[] CREDENTIAL_KEYS = {
    "KEY_DEVICE_TOKEN", "KEY_DEVICE_ID", "KEY_TOKEN_EXPIRES_AT",
  };

  @Test
  public void noCredentialIsWrittenThroughConfigManager() throws IOException {
    List<String> offences = new ArrayList<>();
    try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
      for (Path file :
          (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
          String line = lines.get(i);
          if (isComment(line)) {
            continue;
          }
          // `unsetConfiguration` contains `setConfiguration`, and unsetting is allowed.
          if (!line.contains(".setConfiguration")) {
            continue;
          }
          // The call and its key can sit on separate lines once formatted.
          String window = line + (i + 1 < lines.size() ? lines.get(i + 1) : "");
          for (String key : CREDENTIAL_KEYS) {
            if (window.contains(key)) {
              offences.add(file + ":" + (i + 1) + "  " + line.trim());
            }
          }
        }
      }
    }
    assertTrue(
        "A device credential must never reach ConfigManager: a synced profile uploads it"
            + " to RuneLite, and a shared token collapses several clients into one"
            + " device. Use DeviceCredentialStore.\n"
            + String.join("\n", offences),
        offences.isEmpty());
  }

  private static boolean isComment(String line) {
    String trimmed = line.trim();
    return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
  }
}
