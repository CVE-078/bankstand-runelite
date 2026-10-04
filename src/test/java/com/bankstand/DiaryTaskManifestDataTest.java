package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * Structural checks on the generated data itself, independent of {@link
 * DiaryTaskManifestTest}'s generic lookup-and-gating behaviour. These exist to catch a bad
 * regeneration (a stray region key, a bit index outside a 32-bit varplayer, an entry
 * pointing at a varplayer this region never reads) mechanically, rather than relying on a
 * human re-reading several hundred generated lines.
 */
public class DiaryTaskManifestDataTest {

  // Mirrors DiaryTaskCompletionCapture.DIARY_TIERS, which is private: real diary tiers only,
  // deliberately excluding "master"/"grandmaster" (real combat-achievement tiers that are
  // not diary tiers).
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
    // Easy, Medium and Hard are varbit-based in the real game, and DiaryTaskVarplayers
    // never reads varbits for per-task identity, so only Elite (ATJUN_TASKS_4, var 1200)
    // can be represented here at all.
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> karamja = byRegion.get("KARAMJA");

    assertEquals(Set.of(1200), karamja.keySet());
    for (DiaryTaskManifest.Entry entry : karamja.get(1200).values()) {
      assertEquals("elite", entry.tier());
    }
  }

  @Test
  public void desertsIronmanVariantBitResolvesTheSameTaskAsTheStandardOne() {
    // Desert medium task 11 is tracked at two locations (1198:22 for a regular account,
    // 1199:9 for an ironman one); only one bit ever flips for a given account, but both
    // must agree on exactly which task that is.
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> desert = byRegion.get("DESERT");

    DiaryTaskManifest.Entry standard = desert.get(1198).get(22);
    DiaryTaskManifest.Entry ironman = desert.get(1199).get(9);

    assertEquals("medium", standard.tier());
    assertEquals("medium", ironman.tier());
    assertEquals(standard.taskName(), ironman.taskName());
  }

  @Test
  public void totalEntryCountMatchesTheGeneratedCount() {
    // A regression pin: if this number ever changes, it means the generated data changed
    // (a region added, a task recount, a different special case), not that the test is
    // stale. 453 real (region, varplayer, bit) task mappings, plus the one deliberate
    // extra entry for Desert's ironman-variant bit.
    int total = 0;
    for (Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> byVarplayer : byRegion.values()) {
      for (Map<Integer, DiaryTaskManifest.Entry> byBit : byVarplayer.values()) {
        total += byBit.size();
      }
    }

    assertEquals(454, total);
  }
}
