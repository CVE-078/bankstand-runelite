package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bit-to-task lookup {@link DiaryTaskCompletionCapture} resolves a newly-set bit against,
 * plus the per-region gate on whether that's even attempted.
 *
 * <p>A wrong entry here doesn't leak data, it misattributes one task's completion to
 * another, so the gate is a per-region allowlist ({@link #isVerified}) rather than a second
 * env var: one region ships the moment it's verified, and a bad one gets pulled without
 * touching {@code PLUGIN_DIARIES_INGEST_ENABLED}.
 *
 * <p>{@link #shipped()} has no verified regions and no entries. The mechanics are built
 * and tested now; the actual per-region content is live-account work, tracked separately.
 * A future region's PR adds its entries and adds itself to {@code VERIFIED_REGIONS}.
 *
 * <p>Not a static utility like {@link DiaryTaskVarplayers}: tests construct their own
 * instance with fabricated entries.
 */
public final class DiaryTaskManifest {

  /** One resolved task: its tier, and its text as read off the diary journal by whoever
   *  verified it. Never copied from a third party's own reverse-engineered data. */
  public static final class Entry {
    private final String tier;
    private final String taskName;

    public Entry(String tier, String taskName) {
      this.tier = tier;
      this.taskName = taskName;
    }

    public String tier() {
      return tier;
    }

    public String taskName() {
      return taskName;
    }
  }

  private final Set<String> verifiedRegions;
  // region -> varplayer id -> bit index -> entry.
  private final Map<String, Map<Integer, Map<Integer, Entry>>> byRegion;

  public DiaryTaskManifest(
      Set<String> verifiedRegions, Map<String, Map<Integer, Map<Integer, Entry>>> byRegion) {
    this.verifiedRegions = Set.copyOf(verifiedRegions);
    Map<String, Map<Integer, Map<Integer, Entry>>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Map<Integer, Map<Integer, Entry>>> region : byRegion.entrySet()) {
      copy.put(region.getKey(), Map.copyOf(region.getValue()));
    }
    this.byRegion = Collections.unmodifiableMap(copy);
  }

  /** The real, in-production manifest. No verified regions yet. */
  public static DiaryTaskManifest shipped() {
    return new DiaryTaskManifest(Set.of(), Map.of());
  }

  /** Whether a region is safe to resolve identity from. An unverified region still gets
   *  read and diffed (see {@link DiaryTaskCompletionCapture}), just never looked up. */
  public boolean isVerified(String region) {
    return verifiedRegions.contains(region);
  }

  /** The task at this bit, or null when the region, varplayer or bit isn't in the manifest.
   *  A miss is the expected state, not an error, until a region ships or a residual bit
   *  gets resolved. */
  public Entry lookup(String region, int varplayerId, int bitIndex) {
    Map<Integer, Map<Integer, Entry>> byVarplayer = byRegion.get(region);
    if (byVarplayer == null) {
      return null;
    }
    Map<Integer, Entry> byBit = byVarplayer.get(varplayerId);
    if (byBit == null) {
      return null;
    }
    return byBit.get(bitIndex);
  }
}
