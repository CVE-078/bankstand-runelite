package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
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

    assertEquals(List.of("second", "first"), descriptions(log.recent()));
  }

  @Test
  public void evictsTheOldestOnceFull() {
    RecentActivityLog log = new RecentActivityLog();
    for (int i = 0; i < RecentActivityLog.MAX_ENTRIES + 3; i++) {
      log.record("entry-" + i);
    }

    List<PanelModel.ActivityRow> recent = log.recent();

    assertEquals(RecentActivityLog.MAX_ENTRIES, recent.size());
    // Newest first, and the three oldest ("entry-0".."entry-2") are gone.
    assertEquals("entry-" + (RecentActivityLog.MAX_ENTRIES + 2), recent.get(0).description);
    assertEquals("entry-3", recent.get(recent.size() - 1).description);
  }

  @Test
  public void recentIsASnapshotNotALiveView() {
    RecentActivityLog log = new RecentActivityLog();
    log.record("first");

    List<PanelModel.ActivityRow> snapshot = log.recent();
    log.record("second");

    assertEquals(List.of("first"), descriptions(snapshot));
  }

  @Test
  public void stampsEachEntryWithWhenItWasRecorded() {
    RecentActivityLog log = new RecentActivityLog();
    long before = System.currentTimeMillis();
    log.record("first");
    long after = System.currentTimeMillis();

    long atMs = log.recent().get(0).atMs;
    assertTrue(atMs >= before && atMs <= after);
  }

  private static List<String> descriptions(List<PanelModel.ActivityRow> rows) {
    List<String> out = new ArrayList<>();
    for (PanelModel.ActivityRow row : rows) {
      out.add(row.description);
    }
    return out;
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
