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
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * The on-disk loot queue, separate from the event outbox so loot can never evict a pet or
 * achievement. Capped at {@link #MAX_PENDING}, oldest evicted first.
 */
@Slf4j
public class LootOutbox {

  static final int MAX_PENDING = 500;

  /** Rejections that resending the same record can never fix, so it is dropped. A {@code quota}
   *  rejection stays pending: the daily quota resets at UTC midnight. */
  private static final Set<String> TERMINAL_REJECTIONS =
      new HashSet<>(Arrays.asList("invalid", "stale", "stale_generation"));

  private static final Type LIST_TYPE = new TypeToken<ArrayList<LootEntry>>() {}.getType();

  private final File file;
  private final Gson gson;
  private volatile Supplier<String> pairing = () -> null;
  private volatile CaptureBoundary boundary;

  public LootOutbox(File file, Gson gson) {
    this.file = file;
    this.gson = gson;
  }

  /** Settles each record's stamp from what was known when its window opened. */
  void setBoundary(CaptureBoundary boundary) {
    this.boundary = boundary;
  }

  /**
   * Queues closed records, each stamped as {@link CaptureBoundary} settles it from what was known
   * when its window opened (held first if need be). Not synchronized: the boundary's lock is taken
   * before this one, never after.
   */
  public void addAll(Collection<LootEntry> added) {
    CaptureBoundary decides = boundary;
    List<LootEntry> now = new ArrayList<>();
    for (LootEntry entry : added) {
      if (decides == null || entry.getTicket() == null) {
        now.add(entry);
      } else {
        decides.queue(
            entry.getTicket(),
            CaptureBoundary.Kind.LOOT,
            stamp -> append(java.util.Collections.singletonList(entry.stamped(stamp))));
      }
    }
    append(now);
  }

  /** Appends stamped records, evicting the oldest pending ones past the cap. */
  synchronized void append(Collection<LootEntry> added) {
    if (added.isEmpty()) {
      return;
    }
    List<LootEntry> entries = current();
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
    return current();
  }

  /** Removes exactly the entries whose record id is in {@code ids}. */
  public synchronized void ack(Set<String> ids) {
    if (ids.isEmpty()) {
      return;
    }
    List<LootEntry> entries = current();
    entries.removeIf(entry -> ids.contains(entry.getEvent().getId()));
    write(entries);
  }

  /** Forgets every pending record, on disk too, so a restart cannot send them either. */
  public synchronized void clear() {
    write(new ArrayList<>());
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

  /** Where the current pairing id is read; entries tagged with another one are never sent. */
  public void setPairing(Supplier<String> pairing) {
    this.pairing = pairing;
  }

  // Fails closed: if a clear on re-pair could not write, the old pairing's entries still never
  // leave. They are dropped here, and from disk whenever a write succeeds.
  private List<LootEntry> current() {
    List<LootEntry> entries = read();
    String now = pairing.get();
    if (entries.removeIf(entry -> !Objects.equals(now, entry.getPairingId()))) {
      write(entries);
    }
    return entries;
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
