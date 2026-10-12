package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class CombatAchievementBossVarbitsTest {

  @Test
  public void covers68VerifiedSources() {
    // 77 constants exist; 6 duplicate the tier-count varbits and 3 lack a confident name.
    assertEquals(68, CombatAchievementBossVarbits.ALL.size());
  }

  @Test
  public void everySourceMapsToADistinctVarbit() {
    assertEquals(
        CombatAchievementBossVarbits.ALL.size(),
        new HashSet<>(CombatAchievementBossVarbits.ALL.values()).size());
  }

  @Test
  public void neverReadsATierCountVarbitAsABoss() {
    // Tier totals share varbit ids with boss totals (12885 is both), so none may be reused.
    Set<Integer> tierVarbits =
        new HashSet<>(
            Arrays.asList(
                Varbits.COMBAT_TASK_EASY,
                Varbits.COMBAT_TASK_MEDIUM,
                Varbits.COMBAT_TASK_HARD,
                Varbits.COMBAT_TASK_ELITE,
                Varbits.COMBAT_TASK_MASTER,
                Varbits.COMBAT_TASK_GRANDMASTER));
    for (Integer id : CombatAchievementBossVarbits.ALL.values()) {
      assertFalse("read a tier-count varbit as a boss", tierVarbits.contains(id));
    }
  }

  @Test
  public void everyKeyIsNonEmpty() {
    // The wire key is the corpus source name; a blank one would fail the server join silently.
    for (String key : CombatAchievementBossVarbits.ALL.keySet()) {
      assertTrue(key, !key.trim().isEmpty());
    }
  }

  @Test
  public void gargbossIsGrotesqueGuardiansNotThePlainMonster() {
    // GARGBOSS is Grotesque Guardians, not the slayer Gargoyle.
    assertEquals(
        (Integer) VarbitID.CA_TOTAL_TASKS_COMPLETED_GARGBOSS,
        CombatAchievementBossVarbits.ALL.get("Grotesque Guardians"));
    assertFalse(CombatAchievementBossVarbits.ALL.containsKey("Gargoyle"));
  }

  @Test
  public void gauntletModesAreNotSwapped() {
    // The unsuffixed varbit is the base Gauntlet; _HM is Corrupted.
    assertEquals(
        (Integer) VarbitID.CA_TOTAL_TASKS_COMPLETED_GAUNTLET,
        CombatAchievementBossVarbits.ALL.get("Crystalline Hunllef"));
    assertEquals(
        (Integer) VarbitID.CA_TOTAL_TASKS_COMPLETED_GAUNTLET_HM,
        CombatAchievementBossVarbits.ALL.get("Corrupted Hunllef"));
  }
}
