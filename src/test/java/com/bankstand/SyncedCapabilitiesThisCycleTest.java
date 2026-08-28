package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

/**
 * The decision behind the panel's per-capability sync times and recent-activity
 * lines, kept separate from the live submit wiring for the same reason {@code
 * shouldAdvanceX} is: it is testable here, on plain flags, without a live client.
 */
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

  /**
   * The case this method exists for. Skills rides along on every submission whatever
   * triggered it, so the server storing the block again is not itself proof that xp
   * moved: a diary tier completing alone can advance skills too, and that must not
   * read as "Skills synced".
   */
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

  /** Unlike skills, an advanced quests/diaries/accountType flag already means "this
   *  specific capability changed": submitSnapshot only receives a non-null value for
   *  one of those when plan() decided it had, so no separate changed flag is needed. */
  @Test
  public void questsDiariesAndAccountTypeEachGetTheirOwnLineWhenAdvanced() {
    Map<String, String> result = synced(false, false, true, true, false, false, true);

    assertEquals("Quests synced", result.get("quests"));
    assertEquals("Diaries synced", result.get("diaries"));
    assertEquals("Account type synced", result.get("accountType"));
  }

  /** The collection log and combat achievements still get a fresh sync time, just no
   *  generic line: their own chat-triggered captures already contribute a more
   *  specific one the moment an item or task is observed. */
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
