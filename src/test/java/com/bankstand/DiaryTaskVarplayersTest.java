package com.bankstand;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class DiaryTaskVarplayersTest {

  @Test
  public void coversTheSame12RegionsAsTheOtherDiaryTables() {
    assertEquals(12, DiaryTaskVarplayers.ALL.size());
    Set<String> regionsFromTierKeys = new HashSet<>();
    for (String key : DiaryVarbits.ALL.keySet()) {
      regionsFromTierKeys.add(key.substring(0, key.lastIndexOf('_')));
    }
    assertEquals(regionsFromTierKeys, DiaryTaskVarplayers.ALL.keySet());
  }

  @Test
  public void totalsTwentySevenVarplayers() {
    int total = 0;
    for (int[] ids : DiaryTaskVarplayers.ALL.values()) {
      total += ids.length;
    }
    assertEquals(27, total);
  }

  @Test
  public void mostRegionsHaveExactlyTwoVarplayers() {
    for (Map.Entry<String, int[]> e : DiaryTaskVarplayers.ALL.entrySet()) {
      if (e.getKey().equals("KOUREND_KEBOS")) {
        assertEquals(3, e.getValue().length);
      } else if (e.getKey().equals("KARAMJA")) {
        assertEquals(4, e.getValue().length);
      } else {
        assertEquals(e.getKey(), 2, e.getValue().length);
      }
    }
  }

  @Test
  public void namesEachVarplayerOnce() {
    // A shared varplayer would attribute one diary's bits to two regions.
    List<Integer> all = new ArrayList<>();
    for (int[] ids : DiaryTaskVarplayers.ALL.values()) {
      for (int id : ids) {
        all.add(id);
      }
    }
    Set<Integer> seen = new HashSet<>(all);
    assertEquals(all.size(), seen.size());
  }
}
