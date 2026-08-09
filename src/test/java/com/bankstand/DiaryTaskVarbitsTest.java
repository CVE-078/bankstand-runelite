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
    // The two halves describe the same tiers. A key in one and not the other would be a
    // tier whose count could never be joined to its completion flag, and the join is the
    // whole point: 21 of 22 is only meaningful next to "not complete".
    assertEquals(DiaryVarbits.ALL.keySet(), DiaryTaskVarbits.ALL.keySet());
    assertEquals(48, DiaryTaskVarbits.ALL.size());
  }

  @Test
  public void readsADifferentVarbitFromTheCompletionFlag() {
    // The game keeps three varbits per tier. Reading the completion flag here would make
    // every tier report 0 or 1 tasks, which is worse than reporting nothing.
    for (Map.Entry<String, Integer> e : DiaryTaskVarbits.ALL.entrySet()) {
      assertNotEquals(e.getKey(), DiaryVarbits.ALL.get(e.getKey()), e.getValue());
    }
  }

  @Test
  public void namesEachVarbitOnce() {
    // A copy-paste inside a region would make two tiers share a count and report the
    // same number for both, which reads as real data.
    Set<Integer> seen = new HashSet<>(DiaryTaskVarbits.ALL.values());
    assertEquals(DiaryTaskVarbits.ALL.size(), seen.size());
  }

  @Test
  public void keepsKaramjaOutsideTheBlockWhereTheGamePutIt() {
    // Karamja's diary predates the 6288-6330 block, so its easy count is at 2423. Far
    // enough away to look like a mistake, which is why it is pinned.
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
    // Three regions are spelled differently on the wire, and the tier is MEDIUM here
    // where VarbitID says MED. Getting either wrong silently drops a whole region.
    assertTrue(DiaryTaskVarbits.ALL.containsKey("KOUREND_KEBOS_ELITE"));
    assertTrue(DiaryTaskVarbits.ALL.containsKey("LUMBRIDGE_DRAYNOR_MEDIUM"));
    assertTrue(DiaryTaskVarbits.ALL.containsKey("WESTERN_PROVINCES_HARD"));
    for (String key : DiaryTaskVarbits.ALL.keySet()) {
      assertTrue(key, key.endsWith("_EASY") || key.endsWith("_MEDIUM")
          || key.endsWith("_HARD") || key.endsWith("_ELITE"));
    }
  }
}
