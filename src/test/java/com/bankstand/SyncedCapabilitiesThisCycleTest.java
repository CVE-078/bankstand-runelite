package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

/** Which capabilities count as synced this cycle, for the panel's sync times and activity. */
public class SyncedCapabilitiesThisCycleTest {

  private static Map<String, String> synced(
      boolean skillsAdvanced,
      boolean skillsChanged,
      boolean questsAdvanced,
      boolean diariesAdvanced,
      boolean collectionLogAdvanced,
      boolean combatAchievementsAdvanced,
      boolean accountTypeAdvanced) {
    return BankstandPlugin.syncedCapabilitiesThisCycle(
        skillsAdvanced,
        skillsChanged,
        questsAdvanced,
        diariesAdvanced,
        collectionLogAdvanced,
        combatAchievementsAdvanced,
        accountTypeAdvanced);
  }

  @Test
  public void nothingAdvancedMeansNothingSynced() {
    assertTrue(synced(false, false, false, false, false, false, false).isEmpty());
  }

  /** Skills ride along on every submission, so a stored skills block alone is not "synced". */
  @Test
  public void skillsAdvancedWithNoActualChangeIsNotReportedAsSynced() {
    Map<String, String> result = synced(true, false, false, false, false, false, false);

    assertFalse(result.containsKey("skills"));
  }

  @Test
  public void skillsAdvancedWithARealChangeIsReportedWithItsOwnLine() {
    Map<String, String> result = synced(true, true, false, false, false, false, false);

    assertEquals("Skills synced", result.get("skills"));
  }

  /** A non-null quests, diaries or accountType value already means that capability changed. */
  @Test
  public void questsDiariesAndAccountTypeEachGetTheirOwnLineWhenAdvanced() {
    Map<String, String> result = synced(false, false, true, true, false, false, true);

    assertEquals("Quests synced", result.get("quests"));
    assertEquals("Diaries synced", result.get("diaries"));
    assertEquals("Account type synced", result.get("accountType"));
  }

  /** The collection log and combat achievements log their own more specific lines. */
  @Test
  public void collectionLogAndCombatAchievementsGetATimeButNoLine() {
    Map<String, String> result = synced(false, false, false, false, true, true, false);

    assertTrue(result.containsKey("collectionLog"));
    assertNull(result.get("collectionLog"));
    assertTrue(result.containsKey("combatAchievements"));
    assertNull(result.get("combatAchievements"));
  }

  @Test
  public void everythingAdvancingReportsAllSixCapabilities() {
    Map<String, String> result = synced(true, true, true, true, true, true, true);

    assertEquals(6, result.size());
  }
}
