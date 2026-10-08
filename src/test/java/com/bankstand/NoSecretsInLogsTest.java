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
 * Log calls never pass the device token, account hash, display name or a raw request or
 * response body.
 */
public class NoSecretsInLogsTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

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
          // A log call can wrap across lines, so read the whole statement up to `);`.
          StringBuilder statement = new StringBuilder(line);
          for (int j = i + 1; j < lines.size() && statement.indexOf(");") < 0; j++) {
            if (!isComment(lines.get(j))) {
              statement.append(lines.get(j));
            }
          }
          // String literals are stripped: the ban is on passing the value, not naming it.
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

  private static String withoutStringLiterals(String statement) {
    return statement.replaceAll("\\\\.", "").replaceAll("\"[^\"]*\"", "\"\"");
  }

  private static boolean isComment(String line) {
    String trimmed = line.trim();
    return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
  }
}
