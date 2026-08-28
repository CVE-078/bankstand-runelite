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
    // A region present in the completion/count tables but missing here would be one
    // DiaryTaskCompletionCapture can never read varplayers for, silently.
    assertEquals(12, DiaryTaskVarplayers.ALL.size());
    Set<String> regionsFromTierKeys = new HashSet<>();
    for (String key : DiaryVarbits.ALL.keySet()) {
      regionsFromTierKeys.add(key.substring(0, key.lastIndexOf('_')));
    }
    assertEquals(regionsFromTierKeys, DiaryTaskVarplayers.ALL.keySet());
  }

  @Test
  public void totalsTwentySevenVarplayers() {
    // 10 regions x 2, plus Kourend & Kebos's extra MULTISTAGE, plus Karamja's four.
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
    // A copy-paste across regions would make two regions share a varplayer and diff
    // the same bits twice, attributing one account's diary to two regions.
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
