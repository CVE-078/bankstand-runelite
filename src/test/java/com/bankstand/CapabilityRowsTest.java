package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The panel's per-capability rows: stable order, skipped when off, own last-synced value. */
public class CapabilityRowsTest {

  private static Map<String, Long> syncedAt(String key, long ms) {
    Map<String, Long> map = new LinkedHashMap<>();
    map.put(key, ms);
    return map;
  }

  @Test
  public void rowsEveryEnabledCapabilityInTheSameOrderAsCapabilityNames() {
    List<PanelModel.CapabilityRow> rows =
        BankstandPlugin.capabilityRows(
            EnumSet.allOf(Capability.class), new LinkedHashMap<>(), java.util.Set.of());

    assertEquals(
        List.of(
            "Skills",
            "Quests",
            "Diaries",
            "Collection log",
            "Combat achievements",
            "Account type",
            "Notable drops",
            "Pet drops",
            "Play sessions",
            "Full loot",
            "Slayer task"),
        rows.stream().map(row -> row.name).collect(java.util.stream.Collectors.toList()));
  }

  @Test
  public void skipsWhatIsSwitchedOff() {
    List<PanelModel.CapabilityRow> rows =
        BankstandPlugin.capabilityRows(
            EnumSet.of(Capability.SKILLS, Capability.COMBAT_ACHIEVEMENTS), new LinkedHashMap<>(), java.util.Set.of());

    assertEquals(
        List.of("Skills", "Combat achievements"),
        rows.stream().map(row -> row.name).collect(java.util.stream.Collectors.toList()));
  }

  @Test
  public void rowsNothingWhenEverythingIsOff() {
    assertTrue(
        BankstandPlugin.capabilityRows(
                EnumSet.noneOf(Capability.class), new LinkedHashMap<>(), java.util.Set.of())
            .isEmpty());
  }

  @Test
  public void carriesTheLastSyncedTimeForItsOwnKey() {
    List<PanelModel.CapabilityRow> rows =
        BankstandPlugin.capabilityRows(
            EnumSet.of(Capability.SKILLS), syncedAt("skills", 1_000L), java.util.Set.of());

    assertEquals(1, rows.size());
    assertEquals(1_000L, (long) rows.get(0).lastSyncedAtMs);
  }

  @Test
  public void aNeverSyncedCapabilityCarriesNullRatherThanAFakeTime() {
    List<PanelModel.CapabilityRow> rows =
        BankstandPlugin.capabilityRows(
            EnumSet.of(Capability.SKILLS), new LinkedHashMap<>(), java.util.Set.of());

    assertNull(rows.get(0).lastSyncedAtMs);
  }

  @Test
  public void aCapabilityOnlyReadsItsOwnKeyNeverAnotherOnes() {
    List<PanelModel.CapabilityRow> rows =
        BankstandPlugin.capabilityRows(
            EnumSet.of(Capability.SKILLS), syncedAt("notableDrops", 1_000L), java.util.Set.of());

    assertNull(rows.get(0).lastSyncedAtMs);
  }
}
