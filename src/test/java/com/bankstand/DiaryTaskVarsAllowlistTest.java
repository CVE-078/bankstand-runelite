package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.stream.IntStream;
import org.junit.Test;

/** The allowlist the server validates against, pinned by id. */
public class DiaryTaskVarsAllowlistTest {

  @Test
  public void varpsAreTheTwentyThreeTaskVarplayers() {
    int[] expected =
        IntStream.concat(
                IntStream.rangeClosed(1176, 1200).filter(id -> id < 1188 || id > 1191),
                IntStream.of(2085, 2086))
            .toArray();
    int[] actual = DiaryTaskVars.VARPS.clone();
    Arrays.sort(actual);
    assertEquals(23, actual.length);
    assertEquals(Arrays.toString(expected), Arrays.toString(actual));
  }

  @Test
  public void varbitsAreKaramjaTasksEveryRewardAndEveryTierCount() {
    assertEquals(39 + 48 + 48, DiaryTaskVars.VARBITS.length);
    assertEquals(39 + 48 + 48, Arrays.stream(DiaryTaskVars.VARBITS).distinct().count());
    for (int count : DiaryTaskVarbits.ALL.values()) {
      assertTrue(Arrays.stream(DiaryTaskVars.VARBITS).anyMatch(id -> id == count));
    }
    // Karamja tasks sit in 3566-3609, skipping the started, reward and done varbits.
    assertEquals(
        39,
        Arrays.stream(DiaryTaskVars.VARBITS)
            .filter(id -> id >= 3566 && id <= 3609)
            .filter(id -> id != 3576 && id != 3577 && id != 3578 && id != 3598 && id != 3599)
            .count());
  }
}
