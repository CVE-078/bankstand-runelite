package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * What {@code ::bstand help} prints: one line per command, in the same order the
 * dispatcher resolves them, so a player never sees a command listed here that
 * {@code actionFor} does not actually recognise, or vice versa.
 */
public class HelpLinesTest {

  private static boolean mentions(List<String> lines, String needle) {
    return lines.stream().anyMatch((line) -> line.contains(needle));
  }

  @Test
  public void namesEveryRealCommand() {
    List<String> lines = StatusReport.helpLines();
    for (String command : new String[] {
      "::bstand", "::bstand sync", "::bstand link", "::bstand log",
      "::bstand export", "::bstand repair", "::bstand help"
    }) {
      assertTrue("missing " + command, mentions(lines, command));
    }
  }

  /**
   * The guard against the next one. A command added to {@code actionFor} without a
   * matching line here leaves a real command nobody can discover; a line added here
   * for a command that does not exist tells a player to run something that fails.
   * Counting pins the list rather than trusting each name check alone.
   */
  @Test
  public void hasExactlyOneLinePerCommandPlusAHeader() {
    assertEquals(8, StatusReport.helpLines().size());
  }

  @Test
  public void firstLineIsAHeaderNotACommand() {
    assertTrue(StatusReport.helpLines().get(0).toLowerCase().contains("command"));
  }
}
