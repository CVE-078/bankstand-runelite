package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bit-to-task lookup for diary task completions, gated per region. A wrong entry misattributes a
 * completion, so a region is resolved only once verified against a live account.
 */
public final class DiaryTaskManifest {

  /** Task text is read off the in-game diary journal, never copied from third-party data. */
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

  /** No verified regions yet. */
  public static DiaryTaskManifest shipped() {
    return new DiaryTaskManifest(Set.of(), Map.of());
  }

  public boolean isVerified(String region) {
    return verifiedRegions.contains(region);
  }

  /** Null on a miss, which is expected. */
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
