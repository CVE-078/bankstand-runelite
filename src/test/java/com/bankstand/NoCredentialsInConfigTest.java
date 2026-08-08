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
 * Keeps the device token out of {@code ConfigManager}.
 *
 * <p>{@code ConfigManager.saveConfiguration} checks {@code ConfigProfile.isSync()} and,
 * when true, PATCHes the whole changed key set to RuneLite's own config service. There is
 * no per-key exclusion, so one {@code setConfiguration} call with the token in it uploads
 * a bearer credential for the Bankstand submit API to a third party the player never
 * authorised for it.
 *
 * <p>It also breaks multi-client accounts, which is the part a reviewer would not catch.
 * The server keys one {@code plugin_device} row per token, so two machines sharing one
 * synced token are one device: one name, one last-seen time, and revoking either revokes
 * both.
 *
 * <p>Source-scanning rather than behavioural, in the same spirit as
 * {@link NoAutomationApiTest}, because the constraint is a call that must not appear.
 * Reading the keys is still allowed, and has to be: the migration reads them once to move
 * an old pairing into the file, and then unsets them so the synced copy is deleted
 * upstream rather than merely ignored.
 */
public class NoCredentialsInConfigTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

  /** The keys that hold a credential. Never written through config, only read and unset. */
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
          // `unsetConfiguration` contains `setConfiguration`, and unsetting is the
          // opposite of the thing banned here: it is what deletes the synced copy.
          if (!line.contains(".setConfiguration")) {
            continue;
          }
          // The call and its key can sit on separate lines once formatted, so the key
          // is looked for on the same line or the next one.
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
