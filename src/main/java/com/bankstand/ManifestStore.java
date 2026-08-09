package com.bankstand;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Keeps the last manifest that validated, so a client that cannot reach the server still
 * knows what it was told last time.
 *
 * <p>Three layers, and the point of all three is that the plugin always has a usable
 * answer: what the server just sent, else what it sent last time, else what this build was
 * compiled with. There is no state in which a manifest problem stops a paired client
 * working.
 *
 * <p>A file rather than {@code ConfigManager}, for the same reason the acknowledged-state
 * store is one: a synced RuneLite profile PATCHes its whole config to RuneLite's own
 * service with no per-key exclusion. Nothing here is secret, but a manifest is server
 * state rather than a user preference and has no business travelling with a profile.
 *
 * <p>Every read failure resolves to "nothing cached", which falls through to the bundled
 * manifest. Every write failure is swallowed: failing to cache is not worth a symptom.
 *
 * <p>Not thread-safe; the plugin calls it only from its background executor.
 */
public class ManifestStore {

  private final File file;
  private final Gson gson;

  public ManifestStore(File file, Gson gson) {
    this.file = file;
    this.gson = gson;
  }

  /**
   * The last manifest that validated, or null when there is none to be had.
   *
   * <p>Re-validated on the way out, never trusted because it was once trusted. The file is
   * on disk where anything can edit it, and a build whose allowlist has since narrowed
   * must not honour a cached manifest naming a capability it no longer supports.
   */
  public CapabilityManifest load() {
    if (!file.isFile()) {
      return null;
    }
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      return CapabilityManifest.validate(
          gson.fromJson(reader, CapabilityManifest.RawManifest.class));
    } catch (IOException | JsonSyntaxException e) {
      return null;
    }
  }

  /** Records a manifest that has already validated. Silent on failure. */
  public void save(CapabilityManifest.RawManifest raw) {
    if (raw == null) {
      return;
    }
    try {
      Path target = file.toPath();
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      // Written beside the target and moved into place, so a crash mid-write leaves the
      // previous manifest intact rather than a truncated file that parses to nothing.
      Path temp = Files.createTempFile(parent, "manifest", ".tmp");
      try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
        gson.toJson(raw, writer);
      }
      try {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      // Caching is an optimisation. Losing it costs one fetch, and saying so would be
      // noise in a chat box about something the player cannot act on.
    }
  }

  /**
   * The manifest to work from right now: the freshest thing available, always usable.
   *
   * <p>The one entry point the plugin should call, so the fallback order lives here rather
   * than being reassembled correctly at each call site.
   */
  public CapabilityManifest current(CapabilityManifest fetched) {
    if (fetched != null) {
      return fetched;
    }
    CapabilityManifest cached = load();
    return cached != null ? cached : CapabilityManifest.bundled();
  }
}
