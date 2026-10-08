package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** What {@code ::bstand export} prints: the current Collect/Events toggle state. */
public class ExportLinesTest {

  private static boolean mentions(List<String> lines, String needle) {
    return lines.stream().anyMatch((line) -> line.contains(needle));
  }

  @Test
  public void marksAnEnabledToggleOn() {
    List<String> lines =
        StatusReport.exportLines(true, false, false, false, false, false, false, 1_000_000, false, false, false, false);
    assertTrue(mentions(lines, "Skill XP: on"));
  }

  @Test
  public void marksADisabledToggleOff() {
    List<String> lines =
        StatusReport.exportLines(false, false, false, false, false, false, false, 1_000_000, false, false, false, false);
    assertTrue(mentions(lines, "Skill XP: off"));
  }

  @Test
  public void namesOnePerToggle() {
    // A toggle missing here is silently omitted from the export.
    List<String> lines =
        StatusReport.exportLines(true, true, true, true, true, true, true, 1_000_000, true, true, true, true);
    assertTrue(mentions(lines, "Skill XP"));
    assertTrue(mentions(lines, "Quest progress"));
    assertTrue(mentions(lines, "Diary progress"));
    assertTrue(mentions(lines, "Collection log"));
    assertTrue(mentions(lines, "Combat achievements"));
    assertTrue(mentions(lines, "Account type"));
    assertTrue(mentions(lines, "Notable drops"));
    assertTrue(mentions(lines, "Pet drops"));
    assertTrue(mentions(lines, "Play sessions"));
    assertTrue(mentions(lines, "Full loot"));
    assertTrue(mentions(lines, "Slayer task"));
  }

  @Test
  public void includesTheNotableDropThreshold() {
    List<String> lines =
        StatusReport.exportLines(false, false, false, false, false, false, false, 250_000, false, false, false, false);
    assertTrue(mentions(lines, "250000"));
  }

  @Test
  public void neverMentionsThePairingCodeDeviceTokenOrServerUrl() {
    // No token, account hash or display name may appear in an export.
    List<String> lines =
        StatusReport.exportLines(true, true, true, true, true, true, true, 1_000_000, true, true, true, true);
    for (String line : lines) {
      String lower = line.toLowerCase();
      assertFalse(lower.contains("pairing"));
      assertFalse(lower.contains("token"));
      assertFalse(lower.contains("server"));
    }
  }
}
