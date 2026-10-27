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
      new HashSet<>(Arrays.asList("invalid", "stale"));

  private static final Type LIST_TYPE = new TypeToken<ArrayList<LootEntry>>() {}.getType();

  private final File file;
  // Far above a full queue's size, so only a damaged file is ever this large.
  private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
  // For the status report: counts and states only, never content.
  private volatile boolean setAside;
  private volatile boolean unsaved;
  private volatile int dropped;
  // While the file cannot be read, changes are kept here and merged in once it can.
  private final List<LootEntry> held = new ArrayList<>();
  private volatile boolean readFailing;
  private final Gson gson;
  private volatile Supplier<String> pairing = () -> null;

  public LootOutbox(File file, Gson gson) {
    this.file = file;
    this.gson = gson;
  }

  /** Appends closed records, evicting the oldest pending ones past the cap. */
  public synchronized void addAll(Collection<LootEntry> added) {
    if (added.isEmpty()) {
      return;
    }
    List<LootEntry> entries = current();
    entries.addAll(added);
    int evicted = 0;
    while (entries.size() > MAX_PENDING) {
      entries.remove(0);
      evicted++;
      dropped++;
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
    boolean changed = false;
    if (entries == null) {
      readFailing = true;
      entries = new ArrayList<>(held);
    } else {
      readFailing = false;
      // Cleared by the next successful write, so a failed one cannot lose them.
      changed = !held.isEmpty();
      entries.addAll(held);
    }
    String now = pairing.get();
    // An unreadable pairing is not "another pairing": nothing is purged until it is known.
    boolean purged =
        !PairingQueues.UNKNOWN.equals(now)
            && entries.removeIf(entry -> !Objects.equals(now, entry.getPairingId()));
    if (purged || changed) {
      write(entries);
    }
    return entries;
  }


  private List<LootEntry> read() {
    if (!file.exists()) {
      return new ArrayList<>();
    }
    if (file.length() > MAX_FILE_BYTES) {
      return setAside();
    }
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      List<LootEntry> stored = gson.fromJson(reader, LIST_TYPE);
      return stored == null ? new ArrayList<>() : new ArrayList<>(stored);
    } catch (IOException | RuntimeException e) {
      log.debug("loot outbox unreadable: {}", e.getMessage());
      // Damaged for good: kept aside. Maybe passing (a lock, a permission): left in place.
      return ReadFailures.isDamaged(e) ? setAside() : null;
    }
  }

  // Moved aside, never overwritten, so a damaged queue can still be inspected or recovered.
  private <T> List<T> setAside() {
    setAside = true;
    try {
      Files.move(
          file.toPath(),
          file.toPath().resolveSibling(file.getName() + ".unreadable"),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException e) {
      log.debug("loot outbox could not be set aside: {}", e.getMessage());
    }
    return new ArrayList<>();
  }

  /** What is wrong with the queue file, or null. Never its content. */
  String problem() {
    if (readFailing) {
      return "could not be read right now; it is kept and read again later";
    }
    if (unsaved) {
      return "could not be saved, so new items may be lost";
    }
    return setAside ? "was unreadable and was set aside" : null;
  }

  /** Oldest items dropped this session because the queue was full. */
  int dropped() {
    return dropped;
  }

  private void write(List<LootEntry> entries) {
    if (readFailing) {
      // Writing now would replace the unread file with only what is in memory.
      held.clear();
      held.addAll(entries);
      return;
    }
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
      unsaved = false;
      held.clear();
    } catch (IOException | RuntimeException e) {
      unsaved = true;
      log.debug("loot outbox write failed: {}", e.getMessage());
    }
  }
}
