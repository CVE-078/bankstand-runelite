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
 * <p>A read failure means "not paired" (fail closed) and is never cached. Called from the client
 * thread and the executor: {@link #cached} is volatile, and each read returns its own result.
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

  /** One read: the credentials, and whether the file could not be read ("not paired" is a guess). */
  public static final class Read {
    public final DeviceCredentials credentials;
    public final boolean unreadable;

    Read(DeviceCredentials credentials, boolean unreadable) {
      this.credentials = credentials;
      this.unreadable = unreadable;
    }
  }

  /** Never null. */
  public DeviceCredentials load() {
    return read().credentials;
  }

  public Read read() {
    DeviceCredentials current = cached;
    if (current != null) {
      return new Read(current, false);
    }
    Read read = readFromDisk();
    if (!read.unreadable) {
      cached = read.credentials;
    }
    return read;
  }

  private Read readFromDisk() {
    if (!file.exists()) {
      return new Read(DeviceCredentials.none(), false);
    }
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      DeviceCredentials stored = gson.fromJson(reader, DeviceCredentials.class);
      return new Read(stored == null ? DeviceCredentials.none() : stored, false);
    } catch (IOException | RuntimeException e) {
      // Not logged, at any level: this file holds a credential.
      return new Read(DeviceCredentials.none(), true);
    }
  }

  /** False when the file could not be written; the cache then keeps the previous pairing. */
  public boolean save(DeviceCredentials credentials) {
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
      return true;
    } catch (IOException | RuntimeException e) {
      // Not logged: the message can carry the path, and the caller reports the outcome.
      return false;
    }
  }

  /**
   * Unpairs this process at once, whatever the disk does. False when the file could not be
   * removed or emptied, so the pairing comes back on the next start.
   */
  public boolean clear() {
    cached = DeviceCredentials.none();
    try {
      Files.deleteIfExists(file.toPath());
      return true;
    } catch (IOException | RuntimeException e) {
      // If the delete fails, overwrite with an empty document so no credential remains.
      boolean emptied = save(DeviceCredentials.none());
      cached = DeviceCredentials.none();
      return emptied;
    }
  }
}
