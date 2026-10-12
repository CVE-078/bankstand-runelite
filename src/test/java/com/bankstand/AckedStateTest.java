package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class AckedStateTest {

  @Test
  public void aFreshStateHasNoSyncTimesRatherThanNull() {
    assertTrue(AckedState.empty().getLastSyncedAt().isEmpty());
  }

  @Test
  public void remembersWhatWasSet() {
    AckedState state = AckedState.empty();
    Map<String, Long> synced = new LinkedHashMap<>();
    synced.put("skills", 1_000L);
    synced.put("quests", 2_000L);

    state.setLastSyncedAt(synced);

    assertEquals(1_000L, (long) state.getLastSyncedAt().get("skills"));
    assertEquals(2_000L, (long) state.getLastSyncedAt().get("quests"));
  }

  /** The setter copies the map it is given. */
  @Test
  public void copiesTheMapPassedIn() {
    AckedState state = AckedState.empty();
    Map<String, Long> synced = new LinkedHashMap<>();
    synced.put("skills", 1_000L);
    state.setLastSyncedAt(synced);

    synced.put("skills", 9_999L);

    assertEquals(1_000L, (long) state.getLastSyncedAt().get("skills"));
  }

  /** An older document leaves the field null; the getter must still return a map. */
  @Test
  public void aNullFieldReadsAsEmptyNotNull() {
    AckedState state = new AckedState();

    assertTrue(state.getLastSyncedAt().isEmpty());
  }
}
