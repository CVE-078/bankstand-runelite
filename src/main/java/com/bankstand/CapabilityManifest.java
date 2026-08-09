package com.bankstand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the server says it will ingest, after this client has decided how much of it to
 * believe.
 *
 * <p><b>The refusal is structural, not a policy.</b> This type has four fields: a schema
 * version, a minimum plugin version, a list of capability <i>names</i> and an interval.
 * There is no field that can carry a varbit id, a varp, a script, a widget, a URL, a class
 * name or an expression, so a fully compromised Bankstand server cannot ask this plugin to
 * read a bank, an inventory, worn equipment, chat or a location. Not because it would be
 * refused, but because there is no way to say it. The plugin owns the mapping from a
 * capability name to game values and always has.
 *
 * <p>That is the property a Plugin Hub reviewer needs to be able to check in one file, so
 * the enforcement is deliberately small, ordered and readable rather than clever. The
 * Hub's stated position is that if it is difficult for them to be sure a plugin is not
 * against the rules, they will not merge it.
 *
 * <p>Every rejection resolves to a usable manifest rather than to a failure: the last one
 * that validated, or the compiled-in {@link #bundled()}. A manifest outage, or a server
 * that starts sending nonsense, must never stop a paired client working.
 */
public final class CapabilityManifest {

  /**
   * The only capabilities this build can capture at all.
   *
   * <p>An allowlist, so a name the server invents is dropped and the rest of the manifest
   * is still used. Adding a name here is a code change with a Hub review attached, which
   * is exactly the friction that should exist around what the plugin reads.
   */
  public static final Set<String> SUPPORTED_CAPABILITIES =
      Collections.unmodifiableSet(
          new LinkedHashSet<>(
              Arrays.asList(
                  "skills", "quests", "diaries", "collectionLog", "combatAchievements")));

  /** The contract version this build speaks. A manifest declaring anything else is not ours. */
  public static final int SUPPORTED_SCHEMA_VERSION = 1;

  /**
   * The fastest this client will ever submit, whatever the server asks for.
   *
   * <p>A server that could drive the interval down could turn every paired client into a
   * load generator against itself and against Wise Old Man, so the client keeps the floor.
   * The server's value is only ever able to make submissions <i>less</i> frequent.
   */
  public static final int MIN_UPLOAD_INTERVAL_SECONDS = 60;

  /** And the slowest, so a bad value cannot silently park a client forever. */
  public static final int MAX_UPLOAD_INTERVAL_SECONDS = 6 * 60 * 60;

  /** More names than exist today would mean the list had stopped being a capability list. */
  private static final int MAX_CAPABILITIES = 32;

  /** Long enough for every real name, short enough that the field cannot smuggle a payload. */
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
   * The manifest compiled into this build.
   *
   * <p>Used before the first successful fetch and after a total failure, so a client that
   * has never reached the server still captures what it was built to capture. Deliberately
   * everything this build supports: the server's copy can only ever narrow it.
   */
  public static CapabilityManifest bundled() {
    return new CapabilityManifest(
        SUPPORTED_SCHEMA_VERSION,
        new ArrayList<>(SUPPORTED_CAPABILITIES),
        MIN_UPLOAD_INTERVAL_SECONDS * 5);
  }

  /**
   * Validates a manifest the server sent, or returns null to keep whatever is in use.
   *
   * <p>Null, never an exception and never a partly-applied manifest. The caller's job on a
   * null is to do nothing, which leaves the last good copy or the bundled one in place.
   *
   * <p>The schema version gates the whole document rather than a field: a manifest written
   * against a contract this build does not speak cannot be partly understood, and guessing
   * at the parts that look familiar is how a client ends up honouring half of something.
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

    // An unknown name is dropped and the rest is kept, which is the opposite of the
    // version gate above and deliberately so: a new capability is an additive change the
    // server is entitled to make, and rejecting the whole manifest for one would mean a
    // server could never introduce anything without stranding every older client.
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

  /** One line for the debug output, so the active manifest is never a guess. */
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
   * The wire shape, exactly as Gson fills it.
   *
   * <p>Primitives and a list of strings, matching the server's own document. Unknown fields
   * on the wire are ignored rather than rejected, which is what lets the server add one
   * without stranding older clients; Gson does that by simply having nowhere to put them.
   *
   * <p><b>Do not add a field here that is not a primitive.</b> The guarantee at the top of
   * this file is only true while this class cannot express anything else.
   */
  public static final class RawManifest {
    int schemaVersion;
    String minPluginVersion;
    List<String> capabilities;
    int uploadIntervalSeconds;
  }
}
