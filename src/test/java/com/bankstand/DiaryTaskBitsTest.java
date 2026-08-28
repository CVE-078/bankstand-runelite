package com.bankstand;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

public class DiaryTaskBitsTest {

  @Test
  public void firstObservationEstablishesTheBaselineAndReportsNothing() {
    // A player who already has tasks done before this ever ran (or before a client
    // restart lost the in-memory baseline) must not have every already-set bit reported
    // as newly completed the moment it is first read.
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
    // Should not happen for a diary task (nothing un-completes one), and must not crash
    // or be misreported as a newly-set bit.
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

    // Re-reading the same already-set bits after a reset is a first observation again,
    // not a report of three newly completed tasks: an account switch must not attribute
    // one character's diary progress to the next.
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

    // A value already known before restore must diff against the restored baseline, not
    // be treated as a fresh first observation, which is exactly what a lost restart
    // baseline would otherwise cause.
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
