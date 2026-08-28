package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The bit-to-task lookup {@link DiaryTaskCompletionCapture} resolves a newly-set bit
 * against, and the per-region gate on whether it is even attempted.
 *
 * <p><b>Correctness here is a different kind of risk than every other capability's
 * disclosure flag</b>: a wrong entry does not leak data, it silently misattributes one
 * task's completion to another. The tool for that is this per-region allowlist
 * ({@link #isVerified}), not a second environment variable: a region ships the moment its
 * own manifest is verified against a live account, independent of every other region's
 * state, and a bad region can be pulled from the list without touching the
 * {@code PLUGIN_DIARIES_INGEST_ENABLED} disclosure everyone else's data depends on.
 *
 * <p><b>{@link #shipped()} starts with no verified regions and no entries at all.</b> This
 * class only exists so the mechanics (varplayer read, bit diff, lookup, tier
 * cross-check) can be built, wired and unit-tested now; the actual per-region content is
 * live-account verification work, region by region, tracked separately. Every lookup
 * against the shipped instance misses until a region's own PR adds its entries and adds
 * that region to {@code VERIFIED_REGIONS}, which is the same "falls through to the plain
 * tier/area event" behaviour as an unmapped bit within an otherwise-mapped region.
 *
 * <p>Not a static utility like {@link DiaryTaskVarplayers}: a test constructs its own
 * instance with fabricated entries to exercise the resolution logic without needing a
 * single real, live-verified entry.
 */
public final class DiaryTaskManifest {

  /** One resolved task: which tier it belongs to, and its own text, read directly off the
   *  achievement diary journal by whoever verified this entry. Never derived from a third
   *  party's own reverse-engineered data; see the design doc's licensing note. */
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

  /** The real, in-production manifest. No verified regions yet; see the class javadoc. */
  public static DiaryTaskManifest shipped() {
    return new DiaryTaskManifest(Set.of(), Map.of());
  }

  /** Whether a region's manifest has been verified against a live account and is safe to
   *  resolve identity from. {@link DiaryTaskCompletionCapture} still reads and diffs an
   *  unverified region's varplayers (so the baseline stays fresh for whenever it is
   *  verified later); it just never attempts a lookup while this is false. */
  public boolean isVerified(String region) {
    return verifiedRegions.contains(region);
  }

  /** The task at this bit, or null when the region, varplayer or bit is not in the
   *  manifest. A miss here is the normal, expected state for every region before its own
   *  manifest ships, and for any residual bit an otherwise-mapped region has not resolved
   *  yet; it is never treated as an error. */
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
