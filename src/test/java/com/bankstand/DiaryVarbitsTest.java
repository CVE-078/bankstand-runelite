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
  public void karamjaCarriesAllFourTiersLikeEveryOtherRegion() {
    // Karamja's flags sit outside the main block; VarbitID names them ATJUN_*_DONE.
    Set<String> karamja = new HashSet<>();
    for (Map.Entry<String, Integer> e : DiaryVarbits.ALL.entrySet()) {
      if (regionPrefix(e.getKey()).equals("KARAMJA")) {
        karamja.add(e.getKey());
      }
    }
    assertEquals(
        new HashSet<>(
            java.util.Arrays.asList(
                "KARAMJA_EASY", "KARAMJA_MEDIUM", "KARAMJA_HARD", "KARAMJA_ELITE")),
        karamja);
  }

  @Test
  public void coversAllFortyEightTiers() {
    // An omitted key reads as "not observed", so a gap is a silent blind spot.
    assertEquals(48, DiaryVarbits.ALL.size());
  }

  @Test
  public void keepsKaramjasPreBlockVarbitIds() {
    // Pinned: they look wrong next to the 4458-4498 block but are correct.
    assertEquals(Integer.valueOf(3578), DiaryVarbits.ALL.get("KARAMJA_EASY"));
    assertEquals(Integer.valueOf(3599), DiaryVarbits.ALL.get("KARAMJA_MEDIUM"));
    assertEquals(Integer.valueOf(3611), DiaryVarbits.ALL.get("KARAMJA_HARD"));
    assertEquals(Integer.valueOf(4566), DiaryVarbits.ALL.get("KARAMJA_ELITE"));
  }

  // Strips the tier suffix; the region prefix may contain underscores (KOUREND_KEBOS).
  private static String regionPrefix(String wireKey) {
    int lastUnderscore = wireKey.lastIndexOf('_');
    return wireKey.substring(0, lastUnderscore);
  }
}
