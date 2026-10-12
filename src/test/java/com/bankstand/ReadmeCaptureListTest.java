package com.bankstand;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/** Checks that the README names every {@code KEY_COLLECT_*} toggle label from BankstandConfig. */
public class ReadmeCaptureListTest {

  private static final Path CONFIG_FILE =
      Paths.get("src", "main", "java", "com", "bankstand", "BankstandConfig.java");
  private static final Path README_FILE = Paths.get("README.md");

  private static final Pattern NAME_ATTRIBUTE = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");

  @Test
  public void everyCaptureToggleIsNamedInReadme() throws IOException {
    String config = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
    String readme = Files.readString(README_FILE, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

    List<String> labels = new ArrayList<>();
    // Split before each @ConfigItem( so every chunk holds exactly one item's attributes.
    for (String chunk : config.split("(?=@ConfigItem\\()")) {
      if (!chunk.contains("BankstandKeys.KEY_COLLECT_")) {
        continue;
      }
      Matcher nameMatcher = NAME_ATTRIBUTE.matcher(chunk);
      assertTrue(
          "Found a KEY_COLLECT_* @ConfigItem with no parseable name = \"...\" attribute:\n"
              + chunk,
          nameMatcher.find());
      labels.add(nameMatcher.group(1));
    }

    assertTrue(
        "Expected at least one KEY_COLLECT_* @ConfigItem in BankstandConfig.java; the"
            + " naming convention this test parses may have changed.",
        !labels.isEmpty());

    List<String> missing = new ArrayList<>();
    for (String label : labels) {
      // "Collect skill XP" -> "skill xp": the README names the subject without the verb.
      String subject = label.replaceFirst("(?i)^collect\\s+", "").toLowerCase(Locale.ROOT);
      if (!readme.contains(subject)) {
        missing.add(label);
      }
    }

    assertTrue(
        "README.md's \"What it captures\" section does not name every real capture"
            + " toggle. A toggle exists in BankstandConfig.java with no matching mention"
            + " in the README, which is exactly how the plugin grew from 4 documented"
            + " toggles to 8 without anyone noticing. Missing: "
            + missing,
        missing.isEmpty());
  }
}
