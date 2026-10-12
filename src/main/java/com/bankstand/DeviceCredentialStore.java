package com.bankstand;

import com.google.gson.Gson;
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
 * Stores the pairing credentials in a local file, never {@code ConfigManager}: a synced profile
 * uploads all config to RuneLite's servers, and the token is a bearer credential. Per-install
 * storage also keeps each machine its own device.
 *
 * <p>A read failure means "not paired" (fail closed). Save/clear and load are never concurrent;
 * {@link #cached} is volatile so a save on one thread is seen by the next load on another.
 */
public class DeviceCredentialStore {

  private final File file;
  private final Gson gson;

  // Read on nearly every game event, so cached. Null means "not loaded yet", not "not paired".
  private volatile DeviceCredentials cached;

  public DeviceCredentialStore(File file, Gson gson) {
    this.file = file;
    this.gson = gson;
  }

  /** Never null. */
  public DeviceCredentials load() {
    DeviceCredentials current = cached;
    if (current != null) {
      return current;
    }
    DeviceCredentials loaded = readFromDisk();
    cached = loaded;
    return loaded;
  }

  private DeviceCredentials readFromDisk() {
    if (!file.exists()) {
      return DeviceCredentials.none();
    }
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      DeviceCredentials stored = gson.fromJson(reader, DeviceCredentials.class);
      return stored == null ? DeviceCredentials.none() : stored;
    } catch (IOException | RuntimeException e) {
      // Not logged, at any level: this file holds a credential.
      return DeviceCredentials.none();
    }
  }

  public void save(DeviceCredentials credentials) {
    Path target = file.toPath();
    Path directory = target.getParent();
    try {
      if (directory != null) {
        Files.createDirectories(directory);
      }
      // Temp then move, so a crash midway leaves the previous pairing intact.
      Path temp = Files.createTempFile(directory, "device", ".tmp");
      try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
        gson.toJson(credentials, writer);
      }
      try {
        Files.move(
            temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
      // Only on success, so the cache always matches disk.
      cached = credentials;
    } catch (IOException | RuntimeException e) {
      // Swallowed: a failed save costs one re-pair.
    }
  }

  public void clear() {
    try {
      Files.deleteIfExists(file.toPath());
      cached = DeviceCredentials.none();
    } catch (IOException | RuntimeException e) {
      // If the delete fails, overwrite with an empty document so no credential remains.
      save(DeviceCredentials.none());
    }
  }
}
