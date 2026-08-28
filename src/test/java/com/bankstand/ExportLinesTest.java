package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * What {@code ::bstand export} prints: the current Collect/Events toggle state, plain
 * values so the wording is testable without a live config.
 */
public class ExportLinesTest {

  private static boolean mentions(List<String> lines, String needle) {
    return lines.stream().anyMatch((line) -> line.contains(needle));
  }

  @Test
  public void marksAnEnabledToggleOn() {
    List<String> lines =
        StatusReport.exportLines(true, false, false, false, false, false, false, 1_000_000, false);
    assertTrue(mentions(lines, "Skill XP: on"));
  }

  @Test
  public void marksADisabledToggleOff() {
    List<String> lines =
        StatusReport.exportLines(false, false, false, false, false, false, false, 1_000_000, false);
    assertTrue(mentions(lines, "Skill XP: off"));
  }

  @Test
  public void namesOnePerToggle() {
    // The same guard CapabilityNamesTest already keeps for the status line: a toggle
    // added to the config without being added here silently omits it from the export.
    List<String> lines =
        StatusReport.exportLines(true, true, true, true, true, true, true, 1_000_000, true);
    assertTrue(mentions(lines, "Skill XP"));
    assertTrue(mentions(lines, "Quest progress"));
    assertTrue(mentions(lines, "Diary progress"));
    assertTrue(mentions(lines, "Collection log"));
    assertTrue(mentions(lines, "Combat achievements"));
    assertTrue(mentions(lines, "Account type"));
    assertTrue(mentions(lines, "Notable drops"));
    assertTrue(mentions(lines, "Pet drops"));
  }

  @Test
  public void includesTheNotableDropThreshold() {
    List<String> lines =
        StatusReport.exportLines(false, false, false, false, false, false, false, 250_000, false);
    assertTrue(mentions(lines, "250000"));
  }

  @Test
  public void neverMentionsThePairingCodeDeviceTokenOrServerUrl() {
    // The one hard rule this command exists under: none of the three secrets/identity
    // fields this plugin holds may ever appear in an export meant to be pasted
    // somewhere else.
    List<String> lines =
        StatusReport.exportLines(true, true, true, true, true, true, true, 1_000_000, true);
    for (String line : lines) {
      String lower = line.toLowerCase();
      assertFalse(lower.contains("pairing"));
      assertFalse(lower.contains("token"));
      assertFalse(lower.contains("server"));
    }
  }
}
