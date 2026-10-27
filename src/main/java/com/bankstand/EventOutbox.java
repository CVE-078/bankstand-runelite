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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Append-only, ordered outbox for {@link TransientEvent}s. Each event is a one-shot fact, so
 * nothing coalesces. Capped at {@link #MAX_PENDING}: overflow drops the OLDEST entry, logged.
 *
 * <p>Stored in a file, not {@code ConfigManager}, because a synced RuneLite profile uploads its
 * whole config and this is captured game data.
 *
 * <p>Synchronized: {@link #add} runs on the client thread while {@link #pending} and
 * {@link #ack} run on the executor, and each is a read-modify-write of the same file.
 */
@Slf4j
public class EventOutbox {

  static final int MAX_PENDING = 200;

  private static final Type LIST_TYPE = new TypeToken<ArrayList<OutboxEntry>>() {}.getType();

  private final File file;
  // Far above a full queue's size, so only a damaged file is ever this large.
  private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
  // For the status report: counts and states only, never content.
  private volatile boolean setAside;
  private volatile boolean unsaved;
  private volatile int dropped;
  // What should be on disk but is not yet, cleared only by a successful write. Full when built
  // from a successful read; otherwise only what changed while the file could not be read.
  private List<OutboxEntry> unwritten;
  private boolean unwrittenIsFull;
  private volatile boolean readFailing;
  private final Gson gson;
  private volatile Supplier<String> pairing = () -> null;

  public EventOutbox(File file, Gson gson) {
    this.file = file;
    // Keep null payload fields on disk: the server rejects an event missing one.
    this.gson = gson.newBuilder().serializeNulls().create();
  }

  /** Appends one event, evicting the oldest pending entry (logged) if this overflows the cap. */
  public synchronized void add(long accountHash, TransientEvent event) {
    List<OutboxEntry> entries = current();
    entries.add(new OutboxEntry(accountHash, event, pairing.get()));
    while (entries.size() > MAX_PENDING) {
      OutboxEntry dropped = entries.remove(0);
      this.dropped++;
      log.warn(
          "event outbox full ({} pending): dropped oldest entry, type={}",
          MAX_PENDING,
          dropped.getEvent().getType());
    }
    write(entries);
  }

  /** Every pending entry, oldest first. A snapshot: mutating the result does not persist. */
  public synchronized List<OutboxEntry> pending() {
    return current();
  }

  /**
   * Removes the entries whose id is in {@code ids} (stored, or judged permanently undeliverable)
   * and keeps the rest in order.
   */
  public synchronized void ack(Set<String> ids) {
    if (ids.isEmpty()) return;
    List<OutboxEntry> entries = current();
    entries.removeIf(entry -> ids.contains(entry.getEvent().getId()));
    write(entries);
  }

  /**
   * Forgets every pending event. Not called on an account switch: each entry is tagged with its
   * own account, so a relog must not lose it.
   */
  public synchronized void clear() {
    // Nothing is pending, so this is the full list whatever the last read did.
    readFailing = false;
    write(Collections.emptyList());
  }

  /** Where the current pairing id is read; entries tagged with another one are never sent. */
  public void setPairing(Supplier<String> pairing) {
    this.pairing = pairing;
  }

  // Fails closed: if a clear on re-pair could not write, the old pairing's entries still never
  // leave. They are dropped here, and from disk whenever a write succeeds.
  private List<OutboxEntry> current() {
    List<OutboxEntry> entries;
    boolean flush = unwritten != null;
    if (flush && unwrittenIsFull) {
      entries = new ArrayList<>(unwritten);
    } else {
      List<OutboxEntry> fromDisk = read();
      readFailing = fromDisk == null;
      entries = fromDisk == null ? new ArrayList<>() : fromDisk;
      if (flush) {
        entries.addAll(unwritten);
      }
    }
    String now = pairing.get();
    // An unreadable pairing is not "another pairing": nothing is purged until it is known.
    boolean purged =
        !PairingQueues.UNKNOWN.equals(now)
            && entries.removeIf(entry -> !Objects.equals(now, entry.getPairingId()));
    if (purged || flush) {
      write(entries);
    }
    return entries;
  }



  private List<OutboxEntry> read() {
    if (!file.exists()) {
      return new ArrayList<>();
    }
    if (file.length() > MAX_FILE_BYTES) {
      return setAside();
    }
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      List<OutboxEntry> stored = gson.fromJson(reader, LIST_TYPE);
      if (stored == null) {
        return new ArrayList<>();
      }
      // Valid JSON can still lack what every reader of an entry needs.
      for (OutboxEntry entry : stored) {
        if (entry == null || entry.getEvent() == null || entry.getEvent().getId() == null) {
          return setAside();
        }
      }
      return new ArrayList<>(stored);
    } catch (IOException | RuntimeException e) {
      log.debug("event outbox unreadable: {}", e.getMessage());
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
      log.debug("event outbox could not be set aside: {}", e.getMessage());
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

  private void write(List<OutboxEntry> entries) {
    unwritten = new ArrayList<>(entries);
    unwrittenIsFull = !readFailing;
    // Writing now would replace the unread file with only what is in memory.
    if (readFailing) {
      return;
    }
    Path target = file.toPath();
    Path directory = target.getParent();
    try {
      if (directory != null) {
        Files.createDirectories(directory);
      }
      // Temp then move, so a crash mid-write leaves the previous outbox intact.
      Path temp = Files.createTempFile(directory, "events", ".tmp");
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
      unwritten = null;
    } catch (IOException | RuntimeException e) {
      unsaved = true;
      // Swallowed: throwing out of a capture handler would break every detector on the tick.
      log.debug("event outbox write failed: {}", e.getMessage());
    }
  }

  /**
   * The ids the outbox should stop retrying: stored or duplicate, plus {@code "stale"}
   * rejections, which only get truer with time. {@code "not_applied"} stays pending, since the
   * capability flag can be turned on later.
   */
  static Set<String> idsToAck(List<EventAck> acks) {
    Set<String> ids = new HashSet<>();
    for (EventAck ack : acks) {
      if (ack.isStored() || isPermanentlyRejected(ack)) {
        ids.add(ack.getId());
      }
    }
    return ids;
  }

  private static boolean isPermanentlyRejected(EventAck ack) {
    // An invalid event fails the same check on every resend.
    return "rejected".equals(ack.getOutcome())
        && ("stale".equals(ack.getReason()) || "invalid".equals(ack.getReason()));
  }

  static Set<String> toIdSet(Iterable<String> ids) {
    Set<String> set = new HashSet<>();
    for (String id : ids) set.add(id);
    return set;
  }
}
