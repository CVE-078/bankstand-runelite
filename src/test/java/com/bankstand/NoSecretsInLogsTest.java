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
 * Keeps credentials and identity out of the log file.
 *
 * <p>The plugin logs at DEBUG so a bug report from a stranger carries something
 * to diagnose. That is only safe while four things never appear in a log line:
 * the device token (a bearer credential for the submit API), the account hash
 * and display name (they identify a real account), and a raw request or response
 * body (it contains all three plus the whole capture).
 *
 * <p>Source-scanning, like {@link NoAutomationApiTest} and
 * {@link NoCredentialsInConfigTest}, because the constraint is an argument that
 * must not be passed. A behavioural test would have to provoke each log line to
 * find out, and the lines that matter are the ones on the failure paths nobody
 * exercises by accident.
 */
public class NoSecretsInLogsTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

  /** Identifiers that hold something a log must never carry. */
  private static final String[] FORBIDDEN_ARGUMENTS = {
    "token", "accountHash", "displayName", "body", "getToken()",
  };

  @Test
  public void noLogLineTakesACredentialOrAnIdentity() throws IOException {
    List<String> offences = new ArrayList<>();
    try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
      for (Path file :
          (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
          String line = lines.get(i);
          if (isComment(line) || !line.contains("log.")) {
            continue;
          }
          // A log call can wrap across lines, so the whole statement is read up
          // to its closing `);` rather than just the line the call starts on.
          StringBuilder statement = new StringBuilder(line);
          for (int j = i + 1; j < lines.size() && statement.indexOf(");") < 0; j++) {
            if (!isComment(lines.get(j))) {
              statement.append(lines.get(j));
            }
          }
          // String literals are stripped first. The ban is on passing the value,
          // not on naming it: "token rejected" is exactly the kind of message a
          // log SHOULD carry, and matching it would push authors towards vaguer
          // wording to satisfy a test.
          String args = withoutStringLiterals(statement.toString());
          for (String forbidden : FORBIDDEN_ARGUMENTS) {
            if (args.contains(forbidden)) {
              offences.add(file + ":" + (i + 1) + "  " + line.trim());
            }
          }
        }
      }
    }
    assertTrue(
        "A log line must never carry the device token, the account hash, the display"
            + " name, or a raw body. Log the outcome instead: a status, a reason, or"
            + " which capability blocks the server acknowledged.\n"
            + String.join("\n", offences),
        offences.isEmpty());
  }

  /** Everything between double quotes removed, escapes included. */
  private static String withoutStringLiterals(String statement) {
    return statement.replaceAll("\\\\.", "").replaceAll("\"[^\"]*\"", "\"\"");
  }

  private static boolean isComment(String line) {
    String trimmed = line.trim();
    return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
  }
}
