package com.bankstand;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

public class DiaryTaskBitsTest {

  @Test
  public void firstObservationEstablishesTheBaselineAndReportsNothing() {
    // Pre-existing progress must not read as freshly completed on the first read.
    DiaryTaskBits bits = new DiaryTaskBits();

    int[] newlySet = bits.diff(1196, 0b101);

    assertArrayEquals(new int[0], newlySet);
  }

  @Test
  public void reportsExactlyTheBitThatWentFromUnsetToSet() {
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(1196, 0b0001);

    int[] newlySet = bits.diff(1196, 0b0101);

    assertArrayEquals(new int[] {2}, newlySet);
  }

  @Test
  public void reportsMultipleBitsFlippedAtOnce() {
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(1196, 0b0000);

    int[] newlySet = bits.diff(1196, 0b1010);

    assertArrayEquals(new int[] {1, 3}, newlySet);
  }

  @Test
  public void reportsNothingWhenTheValueIsUnchanged() {
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(1196, 0b0101);

    int[] newlySet = bits.diff(1196, 0b0101);

    assertArrayEquals(new int[0], newlySet);
  }

  @Test
  public void doesNotReportABitGoingFromSetToUnset() {
    // Shouldn't happen (nothing un-completes a diary task), must not crash either way.
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(1196, 0b0111);

    int[] newlySet = bits.diff(1196, 0b0011);

    assertArrayEquals(new int[0], newlySet);
  }

  @Test
  public void alwaysAdvancesRegardlessOfWhatCallerDoesWithTheResult() {
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(1196, 0b0001);
    bits.diff(1196, 0b0011);

    // The baseline is now 0b0011; re-reading the same value reports nothing new.
    assertArrayEquals(new int[0], bits.diff(1196, 0b0011));
  }

  @Test
  public void tracksEachVarplayerIndependently() {
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(1196, 0b0001);
    bits.diff(1197, 0b0001);

    // A bit newly set on one varplayer must not be diffed against another's baseline.
    int[] first = bits.diff(1196, 0b0011);
    int[] second = bits.diff(1198, 0b0001);

    assertArrayEquals(new int[] {1}, first);
    assertArrayEquals(new int[0], second);
  }

  @Test
  public void resetForgetsEveryVarplayer() {
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(1196, 0b0111);

    bits.reset();

    // A reset makes this a first observation again, not three newly completed tasks.
    assertArrayEquals(new int[0], bits.diff(1196, 0b0111));
  }

  @Test
  public void restoreAndSnapshotRoundTrip() {
    DiaryTaskBits original = new DiaryTaskBits();
    original.diff(1196, 0b0001);
    original.diff(1197, 0b0010);

    Map<Integer, Integer> saved = original.snapshot();
    DiaryTaskBits restored = new DiaryTaskBits();
    restored.restore(saved);

    // Diffs against the restored baseline, not a fresh first observation.
    assertArrayEquals(new int[] {1}, restored.diff(1196, 0b0011));
    assertEquals(2, saved.size());
    assertTrue(saved.containsKey(1196));
    assertTrue(saved.containsKey(1197));
  }

  @Test
  public void snapshotIsIndependentOfLaterChanges() {
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(1196, 0b0001);

    Map<Integer, Integer> snapshot = bits.snapshot();
    bits.diff(1196, 0b0011);

    assertEquals(Integer.valueOf(0b0001), snapshot.get(1196));
  }
}
