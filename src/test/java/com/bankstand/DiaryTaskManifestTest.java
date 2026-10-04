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
    DiaryTaskManifest manifest = DiaryTaskManifest.shipped();

    for (String region : DiaryTaskVarplayers.ALL.keySet()) {
      assertFalse(region, manifest.isVerified(region));
    }
  }

  @Test
  public void shippedResolvesARealEntryButGatesItOnVerification() {
    // shipped() is populated (DiaryTaskManifestDataTest covers the data itself), but
    // isVerified("ARDOUGNE") is still false, which is what actually keeps this from
    // reaching a player: DiaryTaskCompletionCapture never calls lookup() unless
    // isVerified() says yes first.
    DiaryTaskManifest manifest = DiaryTaskManifest.shipped();

    assertFalse(manifest.isVerified("ARDOUGNE"));
    assertEquals("easy", manifest.lookup("ARDOUGNE", 1196, 0).tier());
  }

  @Test
  public void shippedResolvesNothingForAGenuinelyUnmappedBit() {
    // Bit 3 of Ardougne's first varplayer isn't any task (offsets 0,1,2,4,5,... skip it),
    // the same kind of gap real residual bits leave throughout the manifest.
    DiaryTaskManifest manifest = DiaryTaskManifest.shipped();

    assertNull(manifest.lookup("ARDOUGNE", 1196, 3));
  }

  @Test
  public void aFabricatedEntryResolvesForATestedRegion() {
    DiaryTaskManifest.Entry taskEntry =
        new DiaryTaskManifest.Entry("easy", "Enter the Yanille agility dungeon");
    DiaryTaskManifest manifest = new DiaryTaskManifest(
        Set.of("ARDOUGNE"), Map.of("ARDOUGNE", Map.of(1196, Map.of(0, taskEntry))));

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
