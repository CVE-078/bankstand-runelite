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
  private final Gson gson;

  public AckedStateStore(File file, Gson gson) {
    this.file = file;
    this.gson = gson;
  }

  /** Never null. */
  public synchronized AckedState load(long accountHash) {
    AckedState stored = read().accounts.get(Long.toString(accountHash));
    if (stored == null) {
      return AckedState.empty();
    }
    // A copy, so a caller's mutation cannot leak into the next read.
    AckedState copy = AckedState.empty();
    copy.setSkills(stored.getSkills());
    copy.setQuests(stored.getQuests());
    copy.setDiaries(stored.getDiaries());
    copy.setAccountType(stored.getAccountType());
    copy.setCombatAchievements(stored.getCombatAchievements());
    copy.setCollectionLogItems(stored.getCollectionLogItems());
    copy.setCollectionLogAcked(stored.getCollectionLogAcked());
    copy.setLastSyncedAt(stored.getLastSyncedAt());
    copy.setDiaryTaskBits(stored.getDiaryTaskBits());
    copy.setCaptureGeneration(stored.getCaptureGeneration());
    return copy;
  }

  /**
   * Leaves every other character's state untouched. Always keeps the stored capture generation:
   * only {@link #saveCaptureGeneration} and {@link #clearCaptureGenerations} write it, so a full
   * save snapshotted before an ack can never put an older value back.
   */
  public synchronized void save(long accountHash, AckedState state) {
    Document document = read();
    String key = Long.toString(accountHash);
    AckedState previous = document.accounts.get(key);
    state.setCaptureGeneration(previous == null ? null : previous.getCaptureGeneration());
    document.accounts.put(key, state);
    write(document);
  }

  /** Forgets every account's capture generation, keeping the rest of each state. */
  public synchronized void clearCaptureGenerations() {
    Document document = read();
    for (AckedState state : document.accounts.values()) {
      if (state != null) {
        state.setCaptureGeneration(null);
      }
    }
    write(document);
  }

  /** Records only the capture generation, for an ack that arrives for any account. */
  public synchronized void saveCaptureGeneration(long accountHash, int generation) {
    Document document = read();
    String key = Long.toString(accountHash);
    AckedState state = document.accounts.get(key);
    if (state == null) {
      state = AckedState.empty();
      document.accounts.put(key, state);
    }
    state.setCaptureGeneration(generation);
    write(document);
  }

  private Document read() {
    Document document = null;
    if (file.exists()) {
      try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
        document = gson.fromJson(reader, Document.class);
      } catch (IOException | RuntimeException e) {
        // Not logged: the document holds an account hash and a collection log.
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
    } catch (IOException | RuntimeException e) {
      // A failed save costs one resend next start.
    }
  }
}
