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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists what the server acknowledged, per character, in a local file (not {@code
 * ConfigManager}, which syncs to RuneLite's servers). A read failure means "nothing known", which
 * re-sends everything once. Background executor only.
 */
public class AckedStateStore {

  private static class Document {
    Map<String, AckedState> accounts;
  }

  private final File file;
  // Whether the last read hit a file it could not read, as opposed to no file at all.
  private volatile boolean lastReadFailed;
  // While the file cannot be read, saves wait here: writing would erase every other character.
  private final Map<String, AckedState> held = new LinkedHashMap<>();
  private boolean forgetHeld;
  private final Gson gson;

  public AckedStateStore(File file, Gson gson) {
    this.file = file;
    this.gson = gson;
  }

  /** Never null. */
  public synchronized AckedState load(long accountHash) {
    Document document = document();
    AckedState stored =
        document == null
            ? held.get(Long.toString(accountHash))
            : document.accounts.get(Long.toString(accountHash));
    if (stored == null) {
      return AckedState.empty();
    }
    // A copy, so a caller's mutation cannot leak into the next read.
    AckedState copy = AckedState.empty();
    copy.setSkills(stored.getSkills());
    copy.setQuests(stored.getQuests());
    copy.setDiaries(stored.getDiaries());
    copy.setAccountType(stored.getAccountType());
    copy.setDiaryTaskVars(stored.getDiaryTaskVars());
    copy.setCombatAchievements(stored.getCombatAchievements());
    copy.setCollectionLogItems(stored.getCollectionLogItems());
    copy.setCollectionLogAcked(stored.getCollectionLogAcked());
    copy.setLastSyncedAt(stored.getLastSyncedAt());
    return copy;
  }

  /** Leaves every other character's state untouched. */
  public synchronized void save(long accountHash, AckedState state) {
    Document document = document();
    if (document == null) {
      held.put(Long.toString(accountHash), state);
      return;
    }
    document.accounts.put(Long.toString(accountHash), state);
    write(document);
  }

  /** For a new pairing, which may be another account or server: keeps the browsed log, not acks. */
  public synchronized void forgetAcks() {
    Document document = document();
    if (document == null) {
      forget(held);
      forgetHeld = true;
      return;
    }
    forget(document.accounts);
    write(document);
  }

  private static void forget(Map<String, AckedState> accounts) {
    for (Map.Entry<String, AckedState> entry : accounts.entrySet()) {
      AckedState kept = AckedState.empty();
      kept.setCollectionLogItems(entry.getValue().getCollectionLogItems());
      entry.setValue(kept);
    }
  }

  /** The file with anything held merged in, or null while it cannot be read. */
  private Document document() {
    Document document = read();
    if (document == null || (held.isEmpty() && !forgetHeld)) {
      return document;
    }
    if (forgetHeld) {
      forget(document.accounts);
    }
    document.accounts.putAll(held);
    // Cleared by write() on success, so a failed write cannot lose them.
    write(document);
    return document;
  }

  /** One read's state and whether that read failed, taken under one lock. */
  static final class Loaded {
    final AckedState state;
    final boolean failed;

    Loaded(AckedState state, boolean failed) {
      this.state = state;
      this.failed = failed;
    }
  }

  synchronized Loaded loadChecked(long accountHash) {
    AckedState state = load(accountHash);
    return new Loaded(state, lastReadFailed);
  }

  private Document read() {
    Document document = null;
    lastReadFailed = false;
    if (file.exists()) {
      try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
        document = gson.fromJson(reader, Document.class);
      } catch (IOException | RuntimeException e) {
        lastReadFailed = true;
        // Maybe passing: never treated as empty, or a save would erase every other character.
        if (!ReadFailures.isDamaged(e)) {
          return null;
        }
        // Damaged for good: start again, which re-sends everything once.
        document = null;
      }
    }
    if (document == null) {
      document = new Document();
    }
    if (document.accounts == null) {
      document.accounts = new HashMap<>();
    }
    return document;
  }

  private void write(Document document) {
    Path target = file.toPath();
    Path directory = target.getParent();
    try {
      if (directory != null) {
        Files.createDirectories(directory);
      }
      // Temp then move, so a crash midway leaves the previous document intact.
      Path temp = Files.createTempFile(directory, "acked", ".tmp");
      try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
        gson.toJson(document, writer);
      }
      try {
        Files.move(
            temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
      // Every write carries what was held, merged in by document().
      held.clear();
      forgetHeld = false;
    } catch (IOException | RuntimeException e) {
      // A failed save costs one resend next start.
    }
  }
}
