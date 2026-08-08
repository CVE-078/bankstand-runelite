package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class StatusReportTest {

  private static boolean mentions(List<String> lines, String needle) {
    for (String line : lines) {
      if (line.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void namesWhatItActuallyCounted() {
    // The plugin counts distinct item ids; the game counts entries. On a real
    // account those were 193 and 189, and calling ids "entries" put both numbers
    // in one chat window disagreeing with each other. The plugin has no manifest
    // and cannot resolve one into the other, so it names what it counted.
    List<String> lines =
        StatusReport.lines(
            true, "https://x", "Zezima", "just now", null, Arrays.asList("skills"), 193);
    assertTrue(mentions(lines, "193 items"));
    assertFalse(mentions(lines, "193 entries"));
  }

  @Test
  public void unpairedSaysOnlyThat() {
    // Every other line describes a pairing that does not exist. Printing them
    // reads as though something is configured when nothing is.
    List<String> lines =
        StatusReport.lines(false, "https://x", "Zezima", "just now", null, Arrays.asList("skills"), 12);
    assertEquals(1, lines.size());
    assertTrue(lines.get(0).contains("Not paired"));
  }

  @Test
  public void namesTheServerBecauseAWrongOneIsOtherwiseSilent() {
    List<String> lines =
        StatusReport.lines(
            true, "https://example.test", "Zezima", "just now", null, Arrays.asList("skills"), 12);
    assertTrue(mentions(lines, "https://example.test"));
  }

  @Test
  public void saysWhenNoCharacterIsLinkedAndHowToFixIt() {
    // The exact state that cost two hours: identity submitted once, failed, and
    // nothing in the client could be asked to try again.
    List<String> lines =
        StatusReport.lines(true, "https://x", null, null, null, Arrays.asList("skills"), -1);
    assertTrue(mentions(lines, "No character linked"));
    assertTrue(mentions(lines, "::bstand link"));
  }

  @Test
  public void namesTheLinkedCharacter() {
    List<String> lines =
        StatusReport.lines(true, "https://x", "Zezima", "just now", null, Arrays.asList("skills"), 5);
    assertTrue(mentions(lines, "Zezima"));
  }

  @Test
  public void saysWhenNothingIsSwitchedOn() {
    List<String> lines =
        StatusReport.lines(true, "https://x", "Zezima", null, null, Collections.emptyList(), -1);
    assertTrue(mentions(lines, "No capabilities switched on"));
  }

  @Test
  public void reportsTheLastFailureOnlyWhenThereIsOne() {
    List<String> withFailure =
        StatusReport.lines(
            true, "https://x", "Zezima", "5m ago", "401 unauthorised", Arrays.asList("skills"), 5);
    assertTrue(mentions(withFailure, "401 unauthorised"));

    List<String> clean =
        StatusReport.lines(true, "https://x", "Zezima", "5m ago", null, Arrays.asList("skills"), 5);
    assertFalse(mentions(clean, "Last failure"));
  }

  @Test
  public void alwaysExplainsTheCollectionLog() {
    // Read or not, it gets a line. It is the one capability a manual sync cannot
    // refresh, and silence about it is what makes a working sync look broken.
    List<String> neverRead =
        StatusReport.lines(true, "https://x", "Zezima", "5m ago", null, Arrays.asList("skills"), -1);
    assertTrue(mentions(neverRead, "Search"));
    assertFalse(mentions(neverRead, "-1"));

    List<String> read =
        StatusReport.lines(true, "https://x", "Zezima", "5m ago", null, Arrays.asList("skills"), 189);
    assertTrue(mentions(read, "189"));
    assertTrue(mentions(read, "Search"));
  }

  @Test
  public void syncNamesWhatItIsSendingAndWhatItIsNot() {
    List<String> lines = StatusReport.syncLines(true, Arrays.asList("skills", "quests"));
    assertTrue(mentions(lines, "skills, quests"));
    // The trap this line exists to avoid: a player runs a sync, sees no new
    // collection log slots, and concludes it did nothing.
    assertTrue(mentions(lines, "collection log is not included"));
  }

  @Test
  public void syncRefusesWithNothingToSend() {
    assertTrue(mentions(StatusReport.syncLines(false, Arrays.asList("skills")), "Not paired"));
    assertTrue(
        mentions(StatusReport.syncLines(true, Collections.emptyList()), "nothing to sync"));
  }
}
