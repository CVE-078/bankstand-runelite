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
    // 77 CA_TOTAL_TASKS_COMPLETED_* constants exist in the real client. 6 are the
    // exact same varbit ids as the tier-count block (see the class doc) and 3 more
    // (COWBOSS, DOM, MAD_ANGEL) were left out for lack of a confident name, leaving
    // 68. This count moving without a matching change to the class doc's own
    // reasoning is the signal something drifted.
    assertEquals(68, CombatAchievementBossVarbits.ALL.size());
  }

  @Test
  public void everySourceMapsToADistinctVarbit() {
    // A copy-paste would attribute one boss's task count to two corpus sources.
    assertEquals(
        CombatAchievementBossVarbits.ALL.size(),
        new HashSet<>(CombatAchievementBossVarbits.ALL.values()).size());
  }

  @Test
  public void neverReadsATierCountVarbitAsABoss() {
    // The trap the class doc calls out by id: CA_TOTAL_TASKS_COMPLETED_EASY and
    // COMBAT_TASK_EASY are the same varbit (12885), so a boss entry that
    // accidentally reused one of the six tier ids would double-count a tier as a
    // fake "boss".
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
    // The wire key is the corpus's own source name, not a code, so a blank one
    // would silently fail the server's join rather than fail loudly here.
    for (String key : CombatAchievementBossVarbits.ALL.keySet()) {
      assertTrue(key, !key.trim().isEmpty());
    }
  }

  @Test
  public void gargbossIsGrotesqueGuardiansNotThePlainMonster() {
    // Caught by review: GARGBOSS reads as "Gargoyle Boss", easy to misread as the
    // plain slayer monster. The corpus itself settles it: "Gargoyle" has one task
    // (not boss-shaped), "Grotesque Guardians" has fifteen.
    assertEquals(
        (Integer) VarbitID.CA_TOTAL_TASKS_COMPLETED_GARGBOSS,
        CombatAchievementBossVarbits.ALL.get("Grotesque Guardians"));
    assertFalse(CombatAchievementBossVarbits.ALL.containsKey("Gargoyle"));
  }

  @Test
  public void gauntletModesAreNotSwapped() {
    // Caught by review: the base/hard-mode pairing was backwards on first pass.
    // The corpus's own task text is unambiguous: every Crystalline Hunllef task
    // reads "Complete the Gauntlet" (the unsuffixed, base-mode varbit); every
    // Corrupted Hunllef task reads "Complete the Corrupted Gauntlet" (_HM).
    assertEquals(
        (Integer) VarbitID.CA_TOTAL_TASKS_COMPLETED_GAUNTLET,
        CombatAchievementBossVarbits.ALL.get("Crystalline Hunllef"));
    assertEquals(
        (Integer) VarbitID.CA_TOTAL_TASKS_COMPLETED_GAUNTLET_HM,
        CombatAchievementBossVarbits.ALL.get("Corrupted Hunllef"));
  }
}
