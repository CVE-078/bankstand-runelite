package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DiagnosticVarbitsTest {

  @Test
  public void probesTheAccountTypeAndAControl() {
    // The control is what makes an all-zero reading mean anything: without a varbit
    // known to be populated on a group account, zeroes cannot be told apart from a
    // family that was never loaded.
    assertEquals(
        Integer.valueOf(AccountTypes.ACCOUNT_TYPE_VARBIT), DiagnosticVarbits.PROBES.get("account type"));
    assertTrue(DiagnosticVarbits.PROBES.containsKey("group size (control)"));
  }

  @Test
  public void printsEveryProbeWithItsIdAndValue() {
    Map<String, Integer> values = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : DiagnosticVarbits.PROBES.entrySet()) {
      values.put(e.getKey(), 0);
    }
    values.put("account type", 5);
    values.put("group size (control)", 4);

    List<String> lines = DiagnosticVarbits.lines(values);
    assertEquals(DiagnosticVarbits.PROBES.size() + 1, lines.size());
    assertTrue(lines.get(0).toLowerCase(java.util.Locale.ROOT).contains("unranked"));
    assertTrue(lines.stream().anyMatch(l -> l.contains("account type (1777) = 5")));
    assertTrue(lines.stream().anyMatch(l -> l.contains("group size (control)") && l.endsWith("= 4")));
  }

  @Test
  public void saysUnreadRatherThanZeroForAProbeItCouldNotRead() {
    // Zero is a real reading. A missing one must not be printed as though the game
    // reported it, which is the whole failure this investigation is trying to avoid.
    List<String> lines = DiagnosticVarbits.lines(new LinkedHashMap<>());
    assertTrue(lines.stream().filter(l -> l.contains("unread")).count() >= 2);
    assertTrue(lines.stream().noneMatch(l -> l.endsWith("= 0")));
  }
}
