package com.bankstand;

import com.bankstand.dto.EventAck;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * The on-disk loot queue, separate from the event outbox so loot can never evict a pet or
 * achievement. Capped at {@link #MAX_PENDING}, oldest evicted first.
 */
@Slf4j
public class LootOutbox {

  static final int MAX_PENDING = 500;

  /** Rejections that resending the same record can never fix, so it is dropped. */
  private static final Set<String> TERMINAL_REJECTIONS =
      new HashSet<>(Arrays.asList("invalid", "quota", "stale"));

  private static final Type LIST_TYPE = new TypeToken<ArrayList<LootEntry>>() {}.getType();

  private final File file;
  private final Gson gson;

  public LootOutbox(File file, Gson gson) {
    this.file = file;
    this.gson = gson;
  }

  /** Appends closed records, evicting the oldest pending ones past the cap. */
  public synchronized void addAll(Collection<LootEntry> added) {
    if (added.isEmpty()) {
      return;
    }
    List<LootEntry> entries = read();
    entries.addAll(added);
    int evicted = 0;
    while (entries.size() > MAX_PENDING) {
      entries.remove(0);
      evicted++;
    }
    if (evicted > 0) {
      log.warn("loot outbox full ({} pending): dropped {} oldest records", MAX_PENDING, evicted);
    }
    write(entries);
  }

  /** Every pending entry, oldest first. A snapshot: mutating it does not persist. */
  public synchronized List<LootEntry> pending() {
    return read();
  }

  /** Removes exactly the entries whose record id is in {@code ids}. */
  public synchronized void ack(Set<String> ids) {
    if (ids.isEmpty()) {
      return;
    }
    List<LootEntry> entries = read();
    entries.removeIf(entry -> ids.contains(entry.getEvent().getId()));
    write(entries);
  }

  /** Stored, duplicate and unfixable rejections are dropped; anything else is resent. */
  static Set<String> idsToAck(List<EventAck> acks) {
    Set<String> ids = new HashSet<>();
    for (EventAck ack : acks) {
      if (ack.getId() == null) {
        continue;
      }
      boolean terminal =
          "rejected".equals(ack.getOutcome()) && TERMINAL_REJECTIONS.contains(ack.getReason());
      if (ack.isStored() || terminal) {
        ids.add(ack.getId());
      }
    }
    return ids;
  }

  private List<LootEntry> read() {
    if (!file.exists()) {
      return new ArrayList<>();
    }
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      List<LootEntry> stored = gson.fromJson(reader, LIST_TYPE);
      return stored == null ? new ArrayList<>() : new ArrayList<>(stored);
    } catch (IOException | RuntimeException e) {
      log.debug("loot outbox unreadable, treating as empty: {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  private void write(List<LootEntry> entries) {
    Path target = file.toPath();
    Path directory = target.getParent();
    try {
      if (directory != null) {
        Files.createDirectories(directory);
      }
      // Temp then move, so a crash mid-write leaves the previous file intact.
      Path temp = Files.createTempFile(directory, "loot", ".tmp");
      try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
        gson.toJson(entries, LIST_TYPE, writer);
      }
      try {
        Files.move(
            temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException | RuntimeException e) {
      log.debug("loot outbox write failed: {}", e.getMessage());
    }
  }
}
