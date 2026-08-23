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
 * Reads and writes this client's pairing credentials.
 *
 * <p><b>A file, not {@code ConfigManager}, and that is the whole point of this class.</b>
 * {@code ConfigManager.saveConfiguration} checks {@code ConfigProfile.isSync()} and, when
 * the profile is synced, PATCHes the whole changed key set to RuneLite's own config
 * service. There is no per-key exclusion, so a plugin cannot mark one value local-only.
 * The device token is a bearer credential for the submit API, and a player who paired
 * with Bankstand did not agree to RuneLite storing that credential.
 *
 * <p>It also keeps several clients on one account working rather than breaking them. A
 * synced token gives every machine the same credential, which collapses them into one
 * {@code plugin_device} row: one name, one last-seen time, and revoking one revokes all.
 * Per-install storage is what lets each machine be its own device.
 *
 * <p>Every read failure resolves to "not paired", which asks the player to pair again
 * rather than sending with a credential we could not read. That is the safe direction:
 * the alternative is a submit loop against a token that may not be ours.
 *
 * <p>Not thread-safe across a save/clear racing a load; the plugin calls those from the
 * client thread and its executor, never concurrently for the same file. {@link #cached}
 * is {@code volatile} for a narrower reason: a pairing written on one thread must be
 * visible to the very next {@link #load()} on the other, with no other synchronization
 * between them, the same cross-thread guarantee {@code AccountSession.submitted} already
 * needs one for.
 */
public class DeviceCredentialStore {

  private final File file;
  private final Gson gson;

  // Every gate the plugin registers (isPaired() and its callers) reads the credential on
  // nearly every in-game event, so re-reading and re-parsing the file on each call cost
  // real client-thread time for no reason: the credential only ever changes via save/clear,
  // both of which keep this field in step. Null means "not yet loaded this session", not
  // "not paired": DeviceCredentials.none() is itself a valid cached value once a real read
  // (or a clear) has established that.
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
      // Temp then move, so a crash midway leaves the previous pairing intact rather
      // than truncating the only copy of the token.
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
      // Only on the success path: a save that threw below never touched disk, so the
      // cache must keep reflecting whatever WAS there, the same "previous pairing
      // intact" guarantee the temp-then-move above already gives the file itself.
      cached = credentials;
    } catch (IOException | RuntimeException e) {
      // Swallowed like every other write on this path. A pairing that failed to persist
      // costs one re-pair; throwing out of the pairing handler costs the chat reply that
      // tells the player what happened.
    }
  }

  /** Forgets this device's pairing. A missing file is already forgotten. */
  public void clear() {
    try {
      Files.deleteIfExists(file.toPath());
      cached = DeviceCredentials.none();
    } catch (IOException | RuntimeException e) {
      // Fall back to an empty document, so a delete the filesystem refuses still leaves
      // no usable credential behind. save() sets the cache itself on its own success path.
      save(DeviceCredentials.none());
    }
  }
}
