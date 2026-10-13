package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** Structural checks on the generated diary task data. */
public class DiaryTaskManifestDataTest {

  // Mirrors DiaryTaskCompletionCapture.DIARY_TIERS, which is private.
  private static final Set<String> DIARY_TIERS = Set.of("easy", "medium", "hard", "elite");

  private final Map<String, Map<Integer, Map<Integer, DiaryTaskManifest.Entry>>> byRegion =
      DiaryTaskManifestData.build();

  @Test
  public void everyRegionKeyIsARealDiaryRegion() {
    assertTrue(DiaryTaskVarplayers.ALL.keySet().containsAll(byRegion.keySet()));
  }

  @Test
  public void everyNonKaramjaRegionHasEntries() {
    for (String region : DiaryTaskVarplayers.ALL.keySet()) {
      if (region.equals("KARAMJA")) {
        continue;
      }
      assertTrue(region, byRegion.containsKey(region));
      assertTrue(region, !byRegion.get(region).isEmpty());
    }
  }

  @Test
  public void everyVarplayerIdBelongsToItsOwnRegion() {
    for (Map.Entry<String, Map<Integer, Map<Integer, DiaryTaskManifest.Entry>>> region :
        byRegion.entrySet()) {
      int[] declared = DiaryTaskVarplayers.ALL.get(region.getKey());
      assertTrue(region.getKey(), declared != null);
      Set<Integer> allowed = new java.util.HashSet<>();
      for (int id : declared) {
        allowed.add(id);
      }
      for (int varplayerId : region.getValue().keySet()) {
        assertTrue(
            region.getKey() + " varplayer " + varplayerId, allowed.contains(varplayerId));
      }
    }
  }

  @Test
  public void everyBitIndexFitsA32BitVarplayer() {
    for (Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> byVarplayer : byRegion.values()) {
      for (Map<Integer, DiaryTaskManifest.Entry> byBit : byVarplayer.values()) {
        for (int bitIndex : byBit.keySet()) {
          assertTrue("bit " + bitIndex, bitIndex >= 0 && bitIndex < 32);
        }
      }
    }
  }

  @Test
  public void everyEntryCarriesARealDiaryTier() {
    for (Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> byVarplayer : byRegion.values()) {
      for (Map<Integer, DiaryTaskManifest.Entry> byBit : byVarplayer.values()) {
        for (DiaryTaskManifest.Entry entry : byBit.values()) {
          assertTrue(entry.tier(), DIARY_TIERS.contains(entry.tier()));
        }
      }
    }
  }

  @Test
  public void everyEntryCarriesANonBlankTaskName() {
    for (Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> byVarplayer : byRegion.values()) {
      for (Map<Integer, DiaryTaskManifest.Entry> byBit : byVarplayer.values()) {
        for (DiaryTaskManifest.Entry entry : byBit.values()) {
          assertTrue(!entry.taskName().isBlank());
        }
      }
    }
  }

  @Test
  public void karamjaOnlyCoversItsSingleVarplayerBasedTier() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> karamja = byRegion.get("KARAMJA");

    assertEquals(Set.of(1200), karamja.keySet());
    for (DiaryTaskManifest.Entry entry : karamja.get(1200).values()) {
      assertEquals("elite", entry.tier());
    }
  }

  @Test
  public void desertsIronmanVariantBitResolvesTheSameTaskAsTheStandardOne() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> desert = byRegion.get("DESERT");

    DiaryTaskManifest.Entry standard = desert.get(1198).get(22);
    DiaryTaskManifest.Entry ironman = desert.get(1199).get(9);

    assertEquals("medium", standard.tier());
    assertEquals("medium", ironman.tier());
    assertEquals(standard.taskName(), ironman.taskName());
  }

  @Test
  public void totalEntryCountMatchesTheGeneratedCount() {
    // 453 task bits plus Desert's ironman variant.
    int total = 0;
    for (Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> byVarplayer : byRegion.values()) {
      for (Map<Integer, DiaryTaskManifest.Entry> byBit : byVarplayer.values()) {
        total += byBit.size();
      }
    }

    assertEquals(454, total);
  }
}
