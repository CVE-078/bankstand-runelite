package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Test;

public class DiaryVarbitsTest {
  private static final Pattern WIRE_KEY_PATTERN =
      Pattern.compile("^[A-Z]+(_[A-Z]+)*_(EASY|MEDIUM|HARD|ELITE)$");

  @Test
  public void hasExactlyFortyFiveEntries() {
    assertEquals(45, DiaryVarbits.ALL.size());
  }

  @Test
  public void everyWireKeyMatchesTheExpectedShape() {
    for (String key : DiaryVarbits.ALL.keySet()) {
      assertTrue(key + " does not match the wire key shape", WIRE_KEY_PATTERN.matcher(key).matches());
    }
  }

  @Test
  public void everyVarbitIdIsDistinct() {
    Set<Integer> ids = new HashSet<>(DiaryVarbits.ALL.values());
    assertEquals(DiaryVarbits.ALL.size(), ids.size());
  }

  @Test
  public void theRegionPrefixSetIsExactlyTheExpectedTwelve() {
    Set<String> expected =
        new HashSet<>(
            java.util.Arrays.asList(
                "ARDOUGNE",
                "DESERT",
                "FALADOR",
                "FREMENNIK",
                "KANDARIN",
                "KARAMJA",
                "KOUREND_KEBOS",
                "LUMBRIDGE_DRAYNOR",
                "MORYTANIA",
                "VARROCK",
                "WESTERN_PROVINCES",
                "WILDERNESS"));
    Set<String> actual = new HashSet<>();
    for (String key : DiaryVarbits.ALL.keySet()) {
      actual.add(regionPrefix(key));
    }
    assertEquals(expected, actual);
  }

  @Test
  public void karamjaHasOnlyElite() {
    for (Map.Entry<String, Integer> e : DiaryVarbits.ALL.entrySet()) {
      if (regionPrefix(e.getKey()).equals("KARAMJA")) {
        assertEquals("KARAMJA_ELITE", e.getKey());
      }
    }
  }

  // Strips the trailing tier suffix, leaving the region prefix (which may itself
  // contain underscores, e.g. KOUREND_KEBOS).
  private static String regionPrefix(String wireKey) {
    int lastUnderscore = wireKey.lastIndexOf('_');
    return wireKey.substring(0, lastUnderscore);
  }
}
