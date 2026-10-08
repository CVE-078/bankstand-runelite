package com.bankstand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the server says it will ingest, after this client has validated it.
 *
 * <p>The manifest carries only a schema version, a minimum plugin version, capability
 * <i>names</i> and an interval. It has no field that could name a varbit, widget, script, URL or
 * anything else to read, so the server cannot ask the plugin to read new game data. The plugin
 * owns the mapping from name to game values.
 *
 * <p>Every rejection falls back to a usable manifest (the last valid one, or {@link #bundled()}),
 * so a bad or missing manifest never stops a paired client.
 */
public final class CapabilityManifest {

  /** Allowlist of capabilities this build can capture. Unknown server names are dropped. */
  public static final Set<String> SUPPORTED_CAPABILITIES =
      Collections.unmodifiableSet(
          new LinkedHashSet<>(
              Arrays.asList(
                  "skills", "quests", "diaries", "collectionLog", "combatAchievements",
                  "accountType", "notableDrops", "petDrops")));

  /** The contract version this build speaks. A manifest declaring anything else is not ours. */
  public static final int SUPPORTED_SCHEMA_VERSION = 1;

  /** Client-side floor: the server can only make submissions less frequent. */
  public static final int MIN_UPLOAD_INTERVAL_SECONDS = 60;

  /** Ceiling, so a bad value cannot park a client forever. */
  public static final int MAX_UPLOAD_INTERVAL_SECONDS = 6 * 60 * 60;

  private static final int MAX_CAPABILITIES = 32;

  private static final int MAX_CAPABILITY_LENGTH = 40;

  private final int schemaVersion;
  private final List<String> capabilities;
  private final int uploadIntervalSeconds;

  private CapabilityManifest(int schemaVersion, List<String> capabilities, int interval) {
    this.schemaVersion = schemaVersion;
    this.capabilities = Collections.unmodifiableList(capabilities);
    this.uploadIntervalSeconds = interval;
  }

  /**
   * The manifest compiled into this build: everything it supports. The server's copy can only
   * narrow it.
   */
  public static CapabilityManifest bundled() {
    return new CapabilityManifest(
        SUPPORTED_SCHEMA_VERSION,
        new ArrayList<>(SUPPORTED_CAPABILITIES),
        MIN_UPLOAD_INTERVAL_SECONDS * 5);
  }

  /**
   * Validates a server manifest, or returns null to keep the one in use. Never throws and never
   * applies part of a manifest. An unknown schema version rejects the whole document.
   */
  public static CapabilityManifest validate(RawManifest raw) {
    if (raw == null) {
      return null;
    }
    if (raw.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
      return null;
    }
    if (raw.capabilities == null || raw.capabilities.size() > MAX_CAPABILITIES) {
      return null;
    }

    // Unknown names are dropped, not fatal, so the server can add capabilities.
    List<String> accepted = new ArrayList<>();
    for (String name : raw.capabilities) {
      if (name == null || name.length() > MAX_CAPABILITY_LENGTH) {
        continue;
      }
      if (SUPPORTED_CAPABILITIES.contains(name) && !accepted.contains(name)) {
        accepted.add(name);
      }
    }

    return new CapabilityManifest(
        raw.schemaVersion, accepted, clampInterval(raw.uploadIntervalSeconds));
  }

  /** The server's interval, held between this client's own floor and ceiling. */
  public static int clampInterval(int requested) {
    if (requested < MIN_UPLOAD_INTERVAL_SECONDS) {
      return MIN_UPLOAD_INTERVAL_SECONDS;
    }
    return Math.min(requested, MAX_UPLOAD_INTERVAL_SECONDS);
  }

  /** Whether the server is currently ingesting this capability. */
  public boolean allows(String capability) {
    return capabilities.contains(capability);
  }

  public List<String> capabilities() {
    return capabilities;
  }

  public int uploadIntervalSeconds() {
    return uploadIntervalSeconds;
  }

  public int schemaVersion() {
    return schemaVersion;
  }

  public String describe() {
    return "manifest v"
        + schemaVersion
        + ", "
        + (capabilities.isEmpty() ? "no capabilities" : String.join(", ", capabilities))
        + ", every "
        + uploadIntervalSeconds
        + "s";
  }

  /**
   * The wire shape as Gson fills it. Unknown wire fields are ignored. Keep every field a primitive
   * or a list of strings, or the guarantee in the class doc no longer holds.
   */
  public static final class RawManifest {
    int schemaVersion;
    String minPluginVersion;
    List<String> capabilities;
    int uploadIntervalSeconds;
  }
}
