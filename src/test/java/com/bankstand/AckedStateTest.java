package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/** {@link AckedState}'s own getter/setter behaviour, isolated from the file it persists
 *  through (see {@link AckedStateStoreTest} for the round-trip). */
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

  /** A caller mutating the map it passed in must not reach back into the state, the same
   *  defensive-copy rule {@code setCollectionLogItems} already follows. */
  @Test
  public void copiesTheMapPassedIn() {
    AckedState state = AckedState.empty();
    Map<String, Long> synced = new LinkedHashMap<>();
    synced.put("skills", 1_000L);
    state.setLastSyncedAt(synced);

    synced.put("skills", 9_999L);

    assertEquals(1_000L, (long) state.getLastSyncedAt().get("skills"));
  }

  /** A document read back from an older on-disk shape has no field at all, which Gson
   *  leaves null rather than an empty map; the getter must still hand out something
   *  iterable. */
  @Test
  public void aNullFieldReadsAsEmptyNotNull() {
    AckedState state = new AckedState();

    assertTrue(state.getLastSyncedAt().isEmpty());
  }
}
