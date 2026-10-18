package com.bankstand;

import com.bankstand.session.AccountSession;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import net.runelite.api.Client;

/**
 * When the current slayer task started, as far as this client saw it. Only a change seen live (no
 * task, or another task, then this one) stamps a start; a task already running when the client
 * first looked has none. Kept per character in a local file (not {@code ConfigManager}) so a
 * restart keeps it. {@link #read} runs on the client thread; file IO goes to {@code io}.
 */
final class SlayerTaskStart {

  static final class Record {
    String creature;
    Integer assigned;
    String startedAt;
  }

  private static class Document {
    Map<String, Record> accounts;
  }

  private static final String NONE = "none";

  private final File file;
  private final Gson gson;
  private final Executor io;
  private final Client client;
  private final AccountSession session;
  private final Map<String, Record> records = new HashMap<>();
  private long lastSeenAccount = -1;
  private int lastSeenGeneration = -1;
  private String lastSeen;

  SlayerTaskStart(File file, Gson gson, Executor io, Client client, AccountSession session) {
    this.file = file;
    this.gson = gson;
    this.io = io;
    this.client = client;
    this.session = session;
    io.execute(this::load);
  }

  /**
   * The current task. {@code observable}: capture is on and the values have arrived, so this
   * read counts as seeing the task.
   */
  SlayerTask read(boolean observable) {
    SlayerTask task = SlayerTaskReader.read(client);
    if (!observable || !session.isActive()) {
      // Whatever changes while capture is off is not seen live; the next read is a baseline.
      forgetLive();
      return task;
    }
    return observe(session.getAccountHash(), session.getGeneration(), task, Instant.now());
  }

  private synchronized void forgetLive() {
    lastSeen = null;
  }

  /** Reads the file. A read failure means nothing is known. Never on the client thread. */
  synchronized void load() {
    if (!file.isFile()) {
      return;
    }
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      Document document = gson.fromJson(reader, Document.class);
      if (document != null && document.accounts != null) {
        // What this session already saw wins over the file.
        document.accounts.forEach(records::putIfAbsent);
      }
    } catch (IOException | RuntimeException e) {
      // Not logged: the file holds account hashes.
    }
  }

  /** The task with its start, or with none when it was not seen starting. */
  synchronized SlayerTask observe(long accountHash, int generation, SlayerTask task, Instant now) {
    // A task whose name has not arrived yet is a partial read, not a new task.
    if (task.active && task.creature == null) {
      return task;
    }
    String seen = identity(task);
    // A new login (or account) starts unseen: the task can change while the client is away.
    boolean changedLive =
        accountHash == lastSeenAccount
            && generation == lastSeenGeneration
            && lastSeen != null
            && !lastSeen.equals(seen);
    lastSeenAccount = accountHash;
    lastSeenGeneration = generation;
    lastSeen = seen;
    String key = Long.toString(accountHash);
    Record stored = records.get(key);
    if (!task.active) {
      if (records.remove(key) != null) {
        save();
      }
      return task;
    }
    if (changedLive) {
      Record record = new Record();
      record.creature = task.creature;
      record.assigned = task.assigned;
      record.startedAt = now.toString();
      records.put(key, record);
      save();
      return task.withStartedAt(record.startedAt);
    }
    if (stored != null && seen.equals(identity(stored))) {
      return task.withStartedAt(stored.startedAt);
    }
    // A task the client did not see starting; an older record no longer applies.
    if (records.remove(key) != null) {
      save();
    }
    return task;
  }

  // shortcut: two back-to-back tasks of the same creature and count look like one when the end
  // between them was missed (client closed); add the task's varps to the identity if that matters.
  private static String identity(SlayerTask task) {
    return task.active ? task.creature + "|" + task.assigned : NONE;
  }

  private static String identity(Record record) {
    return record.creature + "|" + record.assigned;
  }

  private void save() {
    Document document = new Document();
    document.accounts = new HashMap<>(records);
    io.execute(() -> write(document));
  }

  private void write(Document document) {
    Path target = file.toPath();
    Path directory = target.getParent();
    try {
      if (directory != null) {
        Files.createDirectories(directory);
      }
      // Temp then move, so a crash midway leaves the previous file intact.
      Path temp = Files.createTempFile(directory, "slayer", ".tmp");
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
      // A failed save loses the start after a restart, nothing more.
    }
  }
}
