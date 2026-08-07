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
 * Reads and writes what the server has already acknowledged, per character.
 *
 * <p>A file, not {@code ConfigManager}: a synced profile PATCHes its whole config to
 * RuneLite's own service, with no per-key exclusion, and what this holds is the player's
 * captured game data.
 *
 * <p>Every read failure resolves to "nothing is known", which re-sends everything once.
 * That is what the client did before any of this persisted, so the failure mode is the
 * status quo.
 *
 * <p>Not thread-safe; the plugin calls it only from its background executor.
 */
public class AckedStateStore {

  /** One file, characters keyed by account hash. */
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
  public AckedState load(long accountHash) {
    AckedState stored = read().accounts.get(Long.toString(accountHash));
    if (stored == null) {
      return AckedState.empty();
    }
    // Copied, so a caller mutating what it got cannot reach into the next read.
    AckedState copy = AckedState.empty();
    copy.setSkills(stored.getSkills());
    copy.setQuests(stored.getQuests());
    copy.setDiaries(stored.getDiaries());
    copy.setCollectionLogItems(stored.getCollectionLogItems());
    copy.setCollectionLogAcked(stored.getCollectionLogAcked());
    return copy;
  }

  /** Leaves every other character's state untouched. */
  public void save(long accountHash, AckedState state) {
    Document document = read();
    document.accounts.put(Long.toString(accountHash), state);
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
      // A failed save costs one resend next start, which is not worth throwing out of a
      // background task on the submit path.
    }
  }
}
