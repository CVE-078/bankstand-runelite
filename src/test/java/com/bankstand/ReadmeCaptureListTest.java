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

/**
 * Keeps README.md's "What it captures" section naming every real capture toggle.
 *
 * <p>The disclosure text bankstand#1143 fixed existed because the opposite happened
 * silently: the plugin grew from 4 capture toggles to 8 and nobody updated the text
 * describing them, so it claimed combat achievements were "deliberately absent" for
 * months after they shipped. This test is the mechanical guard against that recurring:
 * every {@code @ConfigItem} whose key is named {@code KEY_COLLECT_*} must have its human
 * label (the item's own {@code name =} value, minus the leading "Collect") named
 * somewhere in the README, so a ninth toggle added without touching the README fails the
 * build instead of shipping silent drift.
 *
 * <p>Source-scanning rather than behavioural, in the same spirit as {@link
 * NoCredentialsInConfigTest}: the constraint is two files agreeing, not a runtime
 * behaviour. It derives the toggle list from {@code BankstandConfig.java} itself rather
 * than a hardcoded list here, so it does not need updating by hand every time a toggle is
 * added; only the README does.
 */
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
    // Split right before each @ConfigItem( so every chunk holds exactly one item's own
    // attributes and nothing past the next item's, however many lines (or comments)
    // separate its keyName from its name.
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
      // "Collect skill XP" -> "skill xp": the toggle's own subject, stripped of the verb
      // every label starts with, since the README states each subject on its own
      // ("Skill XP.", not "Collect skill XP.").
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
