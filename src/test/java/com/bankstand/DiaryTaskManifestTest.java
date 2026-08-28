package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class DiaryTaskManifestTest {

  @Test
  public void shippedHasNoVerifiedRegions() {
    // The mechanics ship now; the per-region content is live-account verification work,
    // tracked separately. Every region must miss until its own PR adds it.
    DiaryTaskManifest manifest = DiaryTaskManifest.shipped();

    for (String region : DiaryTaskVarplayers.ALL.keySet()) {
      assertFalse(region, manifest.isVerified(region));
    }
  }

  @Test
  public void shippedResolvesNothing() {
    DiaryTaskManifest manifest = DiaryTaskManifest.shipped();

    assertNull(manifest.lookup("ARDOUGNE", 1196, 0));
  }

  @Test
  public void aFabricatedEntryResolvesForATestedRegion() {
    DiaryTaskManifest manifest = new DiaryTaskManifest(
        Set.of("ARDOUGNE"),
        Map.of("ARDOUGNE", Map.of(1196, Map.of(0, new DiaryTaskManifest.Entry("easy", "Enter the Yanille agility dungeon")))));

    assertEquals(true, manifest.isVerified("ARDOUGNE"));
    DiaryTaskManifest.Entry entry = manifest.lookup("ARDOUGNE", 1196, 0);
    assertEquals("easy", entry.tier());
    assertEquals("Enter the Yanille agility dungeon", entry.taskName());
  }

  @Test
  public void missesAnUnknownBitWithinAKnownVarplayer() {
    DiaryTaskManifest manifest = new DiaryTaskManifest(
        Set.of("ARDOUGNE"),
        Map.of("ARDOUGNE", Map.of(1196, Map.of(0, new DiaryTaskManifest.Entry("easy", "Task")))));

    assertNull(manifest.lookup("ARDOUGNE", 1196, 5));
  }

  @Test
  public void missesAnUnknownVarplayerWithinAKnownRegion() {
    DiaryTaskManifest manifest = new DiaryTaskManifest(
        Set.of("ARDOUGNE"),
        Map.of("ARDOUGNE", Map.of(1196, Map.of(0, new DiaryTaskManifest.Entry("easy", "Task")))));

    assertNull(manifest.lookup("ARDOUGNE", 1197, 0));
  }

  @Test
  public void missesAnUnknownRegionEntirely() {
    DiaryTaskManifest manifest = new DiaryTaskManifest(Set.of(), Map.of());

    assertNull(manifest.lookup("DESERT", 1198, 0));
  }
}
