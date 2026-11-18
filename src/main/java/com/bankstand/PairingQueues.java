package com.bankstand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Everything queued under one pairing. A new pairing can be another Bankstand account or another
 * server, and the plugin cannot tell, so on a new pairing or a disconnect it is all dropped, never
 * sent under the next token. Senders read their token and queue while holding this object's
 * lock, and {@link #replace} swaps the pairing under it, so a send never mixes the two.
 */
final class PairingQueues {
  private final PresenceQueue presence;
  private final EventOutbox events;
  private final LootLedger ledger;
  private final LootOutbox loot;
  private final CaptureGenerations generations;
  private final CaptureBoundary boundary;

  PairingQueues(
      PresenceQueue presence,
      EventOutbox events,
      LootLedger ledger,
      LootOutbox loot,
      CaptureGenerations generations,
      Supplier<String> pairing,
      com.bankstand.session.AccountSession session,
      LongSupplier clock) {
    events.setPairing(pairing);
    ledger.setPairing(pairing);
    loot.setPairing(pairing);
    this.presence = presence;
    this.events = events;
    this.ledger = ledger;
    this.loot = loot;
    this.generations = generations;
    // Locks on this object, so an answer never interleaves with replace.
    this.boundary = new CaptureBoundary(this, generations, session, clock);
    events.setBoundary(boundary);
    presence.setBoundary(boundary);
    ledger.setBoundary(boundary);
    loot.setBoundary(boundary);
  }

  CaptureBoundary boundary() {
    return boundary;
  }

  // Bumped by every pairing request and every replace, so a slow pairing reply is told apart.
  private long tickets;
  // Bumped when a new pairing's acks are forgotten, on the client thread in the same step.
  private long epoch;

  /** Read while the credential file cannot be: the queues then keep everything as it is. */
  static final String UNKNOWN = "unknown";

  /** The current pairing id, as the queues read it. */
  static Supplier<String> pairingOf(DeviceCredentialStore store) {
    return () -> {
      DeviceCredentialStore.Read read = store.read();
      return read.unreadable ? UNKNOWN : read.credentials.getPairingId();
    };
  }

  /** Changes only when acks are forgotten for a new pairing. */
  synchronized long epoch() {
    return epoch;
  }

  synchronized void acksForgotten() {
    epoch++;
  }

  /** Taken before a pairing request; hand it back to {@link #replaceIfCurrent}. */
  synchronized long beginPairing() {
    return ++tickets;
  }

  /** Like {@link #replace}, but null when a disconnect or a newer pairing came after the ticket. */
  synchronized Dropped replaceIfCurrent(
      long ticket,
      java.util.function.BooleanSupplier swapPairing,
      Map<Long, Integer> generationsByAccount) {
    return ticket == tickets ? replace(swapPairing, generationsByAccount) : null;
  }

  /** Drops the old queues and swaps the stored pairing as one step. Returns what it dropped. */
  Dropped replace(java.util.function.BooleanSupplier swapPairing) {
    return replace(swapPairing, Collections.emptyMap());
  }

  /** {@link #replace(java.util.function.BooleanSupplier)} that also learns the new pairing's generations. */
  synchronized Dropped replace(
      java.util.function.BooleanSupplier swapPairing, Map<Long, Integer> generationsByAccount) {
    tickets++;
    int eventCount = events.pending().size();
    int lootCount = loot.pending().size();
    // Swapped first: if it fails, the old pairing and its queues stay as they were. A crash
    // before the clear is safe too: entries are tagged with their pairing and never sent under
    // another one.
    if (!swapPairing.getAsBoolean()) {
      return new Dropped(0, 0, false);
    }
    // Under this lock, so no older answer lands between the swap and the drop.
    Map<CaptureBoundary.Kind, Integer> held = boundary.replace();
    eventCount += held.getOrDefault(CaptureBoundary.Kind.EVENT, 0);
    lootCount += ledger.closeAll().size() + held.getOrDefault(CaptureBoundary.Kind.LOOT, 0);
    presence.clear();
    events.clear();
    loot.clear();
    // After the swap, so each is saved under the new pairing.
    generations.observeAll(generationsByAccount);
    return new Dropped(eventCount, lootCount, true);
  }

  /** Counts only, so the notice can never carry a token, device id, server URL or account. */
  static final class Dropped {
    final int events;
    final int loot;
    // False when the new pairing could not be stored; nothing was dropped.
    final boolean swapped;

    Dropped(int events, int loot, boolean swapped) {
      this.events = events;
      this.loot = loot;
      this.swapped = swapped;
    }

    /** The chat line for a re-pair, or null when nothing was dropped. */
    String repairNotice() {
      List<String> parts = new ArrayList<>();
      if (events > 0) {
        parts.add(events + (events == 1 ? " queued event" : " queued events"));
      }
      if (loot > 0) {
        parts.add(loot + (loot == 1 ? " loot drop" : " loot drops"));
      }
      if (parts.isEmpty()) {
        return null;
      }
      return "Re-paired: "
          + String.join(" and ", parts)
          + " from the previous pairing "
          + (events + loot == 1 ? "was" : "were")
          + " discarded.";
    }
  }
}
