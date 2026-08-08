package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Varbits;
import org.junit.Test;

public class CombatAchievementVarbitsTest {

  @Test
  public void coversEveryTierOnce() {
    assertEquals(
        new HashSet<>(
            Arrays.asList("easy", "medium", "hard", "elite", "master", "grandmaster")),
        CombatAchievementVarbits.ALL.keySet());
  }

  @Test
  public void keysAreTheServersWireKeys() {
    // Lowercase, matching the server's CA_TIER_KEYS. An uppercase key would be
    // rejected by the envelope's tier refine, silently dropping the whole block.
    for (String key : CombatAchievementVarbits.ALL.keySet()) {
      assertEquals(key, key.toLowerCase());
    }
  }

  @Test
  public void readsTaskCountsAndNotRewardClaims() {
    // The trap this table exists to avoid. COMBAT_ACHIEVEMENT_TIER_* sit right
    // beside these and track whether the tier's REWARDS were claimed from the
    // taskmaster, which is a different fact: a player can finish every Easy task
    // and never claim, and reading those would report them at zero.
    Set<Integer> rewardVarbits =
        new HashSet<>(
            Arrays.asList(
                Varbits.COMBAT_ACHIEVEMENT_TIER_EASY,
                Varbits.COMBAT_ACHIEVEMENT_TIER_MEDIUM,
                Varbits.COMBAT_ACHIEVEMENT_TIER_HARD,
                Varbits.COMBAT_ACHIEVEMENT_TIER_ELITE,
                Varbits.COMBAT_ACHIEVEMENT_TIER_MASTER));

    for (Integer id : CombatAchievementVarbits.ALL.values()) {
      assertFalse("read a reward-claim varbit, not a task count", rewardVarbits.contains(id));
    }
    assertEquals(
        (Integer) Varbits.COMBAT_TASK_EASY, CombatAchievementVarbits.ALL.get("easy"));
    assertEquals(
        (Integer) Varbits.COMBAT_TASK_GRANDMASTER,
        CombatAchievementVarbits.ALL.get("grandmaster"));
  }

  @Test
  public void everyTierMapsToADistinctVarbit() {
    assertEquals(
        CombatAchievementVarbits.ALL.size(),
        new HashSet<>(CombatAchievementVarbits.ALL.values()).size());
  }

  @Test
  public void baselineOnlyReportsChangeWhenACountMoves() {
    CombatAchievementBaseline baseline = new CombatAchievementBaseline();
    Map<String, Integer> counts = new LinkedHashMap<>();
    counts.put("easy", 23);

    assertTrue("nothing acknowledged yet", baseline.changedSince(counts));
    baseline.advance(counts);
    assertFalse("unchanged since the ack", baseline.changedSince(counts));

    Map<String, Integer> moved = new LinkedHashMap<>();
    moved.put("easy", 24);
    assertTrue(baseline.changedSince(moved));
  }
}
