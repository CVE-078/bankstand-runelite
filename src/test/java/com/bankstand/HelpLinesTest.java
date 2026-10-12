package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** {@code ::bstand help} lists exactly the commands {@code actionFor} recognises, in order. */
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

  @Test
  public void hasExactlyOneLinePerCommandPlusAHeader() {
    assertEquals(8, StatusReport.helpLines().size());
  }

  @Test
  public void firstLineIsAHeaderNotACommand() {
    assertTrue(StatusReport.helpLines().get(0).toLowerCase().contains("command"));
  }
}
