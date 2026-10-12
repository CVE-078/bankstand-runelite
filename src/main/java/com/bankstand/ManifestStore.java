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
 * Caches the last valid manifest in a local file (not {@code ConfigManager}, which syncs to
 * RuneLite's servers). Fallback order: fetched, else cached, else bundled. Read failures mean
 * "nothing cached"; write failures are swallowed. Background executor only.
 */
public class ManifestStore {

  private final File file;
  private final Gson gson;

  public ManifestStore(File file, Gson gson) {
    this.file = file;
    this.gson = gson;
  }

  /** Null when there is none. Re-validated on load, since the file can be edited. */
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

  /** Only for a manifest that already validated. */
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
      // Temp then move, so a crash midway leaves the previous file intact.
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
      // Caching is an optimisation; losing it costs one fetch.
    }
  }

  /** The entry point: the freshest usable manifest, never null. */
  public CapabilityManifest current(CapabilityManifest fetched) {
    if (fetched != null) {
      return fetched;
    }
    CapabilityManifest cached = load();
    return cached != null ? cached : CapabilityManifest.bundled();
  }
}
