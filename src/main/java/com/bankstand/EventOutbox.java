package com.bankstand;

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
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * The append-only outbox for {@link TransientEvent}s (#656). Not a coalescing
 * 4-slot structure like the skills/quests/diaries snapshot outbox (#362): each
 * event is a distinct, one-shot fact, so a newer one cannot overwrite an older
 * one the way a fresher skill total can.
 *
 * <p><b>Ordered</b>: entries are appended and drained oldest-first, so a batch
 * submit reports events in the order they happened.
 *
 * <p><b>Bounded, with an explicit and logged drop policy</b>: capped at {@link
 * #MAX_PENDING}. A full outbox drops the OLDEST entry to make room for the
 * newest, logged via {@code log.warn} because a silently dropped drop is worse
 * than a noisy one. This is the honest trade-off named in the design: a client
 * offline long enough to overflow the cap loses the oldest of what it saw,
 * rather than growing without limit or losing the newest instead.
 *
 * <p><b>A file, not {@code ConfigManager}</b>, for the same reason {@link
 * DeviceCredentialStore} and {@link AckedStateStore} are: pending state IS the
 * player's captured game data, and a synced profile PATCHes its whole config to
 * RuneLite's own service with no per-key exclusion.
 *
 * <p><b>Thread-safe.</b> {@link #add} runs synchronously inside a capture's {@code
 * @Subscribe} handler, on the client thread; {@link #pending} and {@link #ack} run
 * from {@code drainEventOutbox}'s {@code @Schedule} callback and its executor
 * continuation, off the client thread (the same split {@code captureSkills}' own
 * {@code clientThread.invokeLater} hop exists to bridge). Each public method is one
 * read-modify-write against the same file, so without a lock a drop captured
 * between a drain's read and its write is silently and permanently overwritten:
 * proven by {@code EventOutboxTest#concurrentAddAndAckDoNotLoseEntries}, which
 * fails on an unsynchronized version of this class.
 */
@Slf4j
public class EventOutbox {

  static final int MAX_PENDING = 200;

  private static final Type LIST_TYPE = new TypeToken<ArrayList<OutboxEntry>>() {}.getType();

  private final File file;
  private final Gson gson;

  public EventOutbox(File file, Gson gson) {
    this.file = file;
    this.gson = gson;
  }

  /** Appends one event, evicting the oldest pending entry (logged) if this overflows the cap. */
  public synchronized void add(long accountHash, TransientEvent event) {
    List<OutboxEntry> entries = read();
    entries.add(new OutboxEntry(accountHash, event));
    while (entries.size() > MAX_PENDING) {
      OutboxEntry dropped = entries.remove(0);
      log.warn(
          "event outbox full ({} pending): dropped oldest entry, type={}",
          MAX_PENDING,
          dropped.getEvent().getType());
    }
    write(entries);
  }

  /** Every pending entry, oldest first. A snapshot: mutating the result does not persist. */
  public synchronized List<OutboxEntry> pending() {
    return read();
  }

  /** Removes exactly the entries whose event id is in {@code storedIds}. Leaves the rest,
   *  in order, for the next drain. */
  public synchronized void ack(Set<String> storedIds) {
    if (storedIds.isEmpty()) return;
    List<OutboxEntry> entries = read();
    entries.removeIf(entry -> storedIds.contains(entry.getEvent().getId()));
    write(entries);
  }

  /** Forgets every pending event. Not called on an account switch: unlike the skills
   *  baseline, an event already happened and stays true for whichever character it
   *  was tagged under (see {@link OutboxEntry}), so a relog must not lose it. */
  public synchronized void clear() {
    write(Collections.emptyList());
  }

  private List<OutboxEntry> read() {
    if (!file.exists()) {
      return new ArrayList<>();
    }
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      List<OutboxEntry> stored = gson.fromJson(reader, LIST_TYPE);
      return stored == null ? new ArrayList<>() : new ArrayList<>(stored);
    } catch (IOException | RuntimeException e) {
      // Not logged at more than debug: this file holds the player's captured drops.
      log.debug("event outbox unreadable, treating as empty: {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  private void write(List<OutboxEntry> entries) {
    Path target = file.toPath();
    Path directory = target.getParent();
    try {
      if (directory != null) {
        Files.createDirectories(directory);
      }
      // Temp then move, so a crash mid-write leaves the previous outbox intact
      // rather than truncating it.
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
    } catch (IOException | RuntimeException e) {
      // Swallowed like every other write on this path. A drop that failed to persist
      // costs one lost event on the next crash; throwing out of a capture handler
      // costs every detector behind it on the same tick.
      log.debug("event outbox write failed: {}", e.getMessage());
    }
  }

  static Set<String> toIdSet(Iterable<String> ids) {
    Set<String> set = new HashSet<>();
    for (String id : ids) set.add(id);
    return set;
  }
}
