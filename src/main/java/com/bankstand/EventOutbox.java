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
import java.util.Set;
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
  private final Gson gson;
  private volatile CaptureGenerations generations = new CaptureGenerations();

  public EventOutbox(File file, Gson gson) {
    this.file = file;
    // Keep null payload fields on disk: the server rejects an event missing one.
    this.gson = gson.newBuilder().serializeNulls().create();
  }

  /** Where {@link #add} reads the generation each event is stamped with. */
  public void setCaptureGenerations(CaptureGenerations generations) {
    this.generations = generations;
  }

  /**
   * Appends one event, stamped with its account's current capture generation, evicting the
   * oldest pending entry (logged) if this overflows the cap.
   */
  public synchronized void add(long accountHash, TransientEvent event) {
    List<OutboxEntry> entries = read();
    entries.add(new OutboxEntry(accountHash, event, generations.get(accountHash)));
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

  /**
   * Removes the entries whose id is in {@code ids} (stored, or judged permanently undeliverable)
   * and keeps the rest in order.
   */
  public synchronized void ack(Set<String> ids) {
    if (ids.isEmpty()) return;
    List<OutboxEntry> entries = read();
    entries.removeIf(entry -> ids.contains(entry.getEvent().getId()));
    write(entries);
  }

  /**
   * Forgets every pending event. Not called on an account switch: each entry is tagged with its
   * own account, so a relog must not lose it.
   */
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
    } catch (IOException | RuntimeException e) {
      // Swallowed: throwing out of a capture handler would break every detector on the tick.
      log.debug("event outbox write failed: {}", e.getMessage());
    }
  }

  /**
   * The ids to stop retrying: stored or duplicate, plus rejections no resend can fix ({@code
   * stale} and {@code invalid}, and {@code stale_generation} for an event queued before the
   * player removed their captured data). {@code not_applied} stays pending, since the capability
   * flag can be turned on later.
   */
  static Set<String> idsToAck(List<EventAck> acks) {
    Set<String> ids = new HashSet<>();
    for (EventAck ack : acks) {
      boolean terminal =
          "rejected".equals(ack.getOutcome())
              && ("stale".equals(ack.getReason())
                  || "invalid".equals(ack.getReason())
                  || "stale_generation".equals(ack.getReason()));
      if (ack.isStored() || terminal) {
        ids.add(ack.getId());
      }
    }
    return ids;
  }

  static Set<String> toIdSet(Iterable<String> ids) {
    Set<String> set = new HashSet<>();
    for (String id : ids) set.add(id);
    return set;
  }
}
