package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The panel's per-capability rows: same stable order and same skip-when-off rule as
 * {@link CapabilityNamesTest}, plus the row's own last-synced value.
 */
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
            true, true, true, true, true, true, true, true, new LinkedHashMap<>());

    assertEquals(
        List.of(
            "Skills",
            "Quests",
            "Diaries",
            "Collection log",
            "Combat achievements",
            "Account type",
            "Notable drops",
            "Pet drops"),
        rows.stream().map(row -> row.name).collect(java.util.stream.Collectors.toList()));
  }

  @Test
  public void skipsWhatIsSwitchedOff() {
    List<PanelModel.CapabilityRow> rows =
        BankstandPlugin.capabilityRows(
            true, false, false, false, true, false, false, false, new LinkedHashMap<>());

    assertEquals(
        List.of("Skills", "Combat achievements"),
        rows.stream().map(row -> row.name).collect(java.util.stream.Collectors.toList()));
  }

  @Test
  public void rowsNothingWhenEverythingIsOff() {
    assertTrue(
        BankstandPlugin.capabilityRows(
                false, false, false, false, false, false, false, false, new LinkedHashMap<>())
            .isEmpty());
  }

  @Test
  public void carriesTheLastSyncedTimeForItsOwnKey() {
    List<PanelModel.CapabilityRow> rows =
        BankstandPlugin.capabilityRows(
            true, false, false, false, false, false, false, false, syncedAt("skills", 1_000L));

    assertEquals(1, rows.size());
    assertEquals(1_000L, (long) rows.get(0).lastSyncedAtMs);
  }

  @Test
  public void aNeverSyncedCapabilityCarriesNullRatherThanAFakeTime() {
    List<PanelModel.CapabilityRow> rows =
        BankstandPlugin.capabilityRows(
            true, false, false, false, false, false, false, false, new LinkedHashMap<>());

    assertNull(rows.get(0).lastSyncedAtMs);
  }

  @Test
  public void aCapabilityOnlyReadsItsOwnKeyNeverAnotherOnes() {
    // notableDrops and skills share nothing; a row must not pick up a stray value
    // stored under a different capability's key.
    List<PanelModel.CapabilityRow> rows =
        BankstandPlugin.capabilityRows(
            true, false, false, false, false, false, false, false, syncedAt("notableDrops", 1_000L));

    assertNull(rows.get(0).lastSyncedAtMs);
  }
}
