package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** No RuneLite or game dependency: a plain bounded list and a plain string builder,
 *  matching the "testable without a client" philosophy {@link StatusReport} already
 *  uses for its own wording. */
public class RecentActivityLogTest {

  @Test
  public void startsEmpty() {
    assertTrue(new RecentActivityLog().recent().isEmpty());
  }

  @Test
  public void newestEntryComesFirst() {
    RecentActivityLog log = new RecentActivityLog();
    log.record("first");
    log.record("second");

    assertEquals(List.of("second", "first"), log.recent());
  }

  @Test
  public void evictsTheOldestOnceFull() {
    RecentActivityLog log = new RecentActivityLog();
    for (int i = 0; i < RecentActivityLog.MAX_ENTRIES + 3; i++) {
      log.record("entry-" + i);
    }

    List<String> recent = log.recent();

    assertEquals(RecentActivityLog.MAX_ENTRIES, recent.size());
    // Newest first, and the three oldest ("entry-0".."entry-2") are gone.
    assertEquals("entry-" + (RecentActivityLog.MAX_ENTRIES + 2), recent.get(0));
    assertEquals("entry-3", recent.get(recent.size() - 1));
  }

  @Test
  public void recentIsASnapshotNotALiveView() {
    RecentActivityLog log = new RecentActivityLog();
    log.record("first");

    List<String> snapshot = log.recent();
    log.record("second");

    assertEquals(List.of("first"), snapshot);
  }

  @Test
  public void clearForgetsEverything() {
    RecentActivityLog log = new RecentActivityLog();
    log.record("first");

    log.clear();

    assertTrue(log.recent().isEmpty());
  }

  private static Map<String, Object> payload(String... keyValuePairs) {
    Map<String, Object> payload = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      payload.put(keyValuePairs[i], keyValuePairs[i + 1]);
    }
    return payload;
  }

  @Test
  public void describesACollectionLogUnlockByItsItemName() {
    assertEquals(
        "Collection log: Dragon warhammer",
        RecentActivityLog.describe(
            TransientEvent.TYPE_COLLECTION_LOG_UNLOCK, payload("itemName", "Dragon warhammer")));
  }

  @Test
  public void describesACombatAchievementByItsTaskName() {
    assertEquals(
        "Combat achievements: Reflection completed",
        RecentActivityLog.describe(
            TransientEvent.TYPE_COMBAT_ACHIEVEMENT_COMPLETED,
            payload("tier", "master", "taskName", "Reflection")));
  }

  @Test
  public void describesADiaryTaskWithATitleCasedTier() {
    assertEquals(
        "Diaries: Elite task completed in Western Provinces",
        RecentActivityLog.describe(
            TransientEvent.TYPE_DIARY_TASK_COMPLETED,
            payload("tier", "elite", "area", "Western Provinces")));
  }

  @Test
  public void describesANotableDropByItsItemName() {
    assertEquals(
        "Notable drop: Twisted bow",
        RecentActivityLog.describe(TransientEvent.TYPE_NOTABLE_DROP, payload("itemName", "Twisted bow")));
  }

  @Test
  public void describesAPetDropByItsPetName() {
    assertEquals(
        "Pet drop: Baby mole",
        RecentActivityLog.describe(TransientEvent.TYPE_PET_DROP, payload("petName", "Baby mole")));
  }

  @Test
  public void anUnknownTypeDescribesAsNullRatherThanABlankLine() {
    assertNull(RecentActivityLog.describe("some_future_event", payload()));
  }
}
