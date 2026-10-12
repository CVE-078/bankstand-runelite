package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class DiaryTaskVarbitsTest {

  @Test
  public void coversTheSame48TiersAsTheCompletionTable() {
    // Both tables must cover the same tiers so a count always joins to its completion flag.
    assertEquals(DiaryVarbits.ALL.keySet(), DiaryTaskVarbits.ALL.keySet());
    assertEquals(48, DiaryTaskVarbits.ALL.size());
  }

  @Test
  public void readsADifferentVarbitFromTheCompletionFlag() {
    // Each tier has three varbits; the completion flag would report 0 or 1 tasks.
    for (Map.Entry<String, Integer> e : DiaryTaskVarbits.ALL.entrySet()) {
      assertNotEquals(e.getKey(), DiaryVarbits.ALL.get(e.getKey()), e.getValue());
    }
  }

  @Test
  public void namesEachVarbitOnce() {
    // A shared varbit would report the same count for two tiers.
    Set<Integer> seen = new HashSet<>(DiaryTaskVarbits.ALL.values());
    assertEquals(DiaryTaskVarbits.ALL.size(), seen.size());
  }

  @Test
  public void keepsKaramjaOutsideTheBlockWhereTheGamePutIt() {
    // Karamja predates the 6288-6330 block, so its easy count really is 2423.
    assertEquals(Integer.valueOf(2423), DiaryTaskVarbits.ALL.get("KARAMJA_EASY"));
    for (Map.Entry<String, Integer> e : DiaryTaskVarbits.ALL.entrySet()) {
      if (e.getKey().equals("KARAMJA_EASY")) {
        continue;
      }
      assertTrue(e.getKey() + " = " + e.getValue(), e.getValue() >= 6288);
    }
  }

  @Test
  public void usesTheWireKeysAndNotTheRuneliteNames() {
    // Wire names differ from VarbitID (MEDIUM, not MED); a mismatch drops a whole region.
    assertTrue(DiaryTaskVarbits.ALL.containsKey("KOUREND_KEBOS_ELITE"));
    assertTrue(DiaryTaskVarbits.ALL.containsKey("LUMBRIDGE_DRAYNOR_MEDIUM"));
    assertTrue(DiaryTaskVarbits.ALL.containsKey("WESTERN_PROVINCES_HARD"));
    for (String key : DiaryTaskVarbits.ALL.keySet()) {
      assertTrue(key, key.endsWith("_EASY") || key.endsWith("_MEDIUM")
          || key.endsWith("_HARD") || key.endsWith("_ELITE"));
    }
  }
}
