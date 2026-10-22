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

  /** Drops the old queues and swaps the stored pairing as one step. Returns what it dropped. */
  Dropped replace(Runnable swapPairing) {
    return replace(swapPairing, Collections.emptyMap());
  }

  /**
   * {@link #replace(Runnable)} that also learns the new pairing's generations. Older answers stop
   * counting first, so none lands between the drop and the swap.
   */
  synchronized Dropped replace(Runnable swapPairing, Map<Long, Integer> generationsByAccount) {
    Map<CaptureBoundary.Kind, Integer> held = boundary.replace();
    // Dropped first: a crash in between loses the queue, never sends it with the new token.
    Dropped dropped =
        new Dropped(
            events.pending().size() + held.getOrDefault(CaptureBoundary.Kind.EVENT, 0),
            loot.pending().size()
                + ledger.closeAll().size()
                + held.getOrDefault(CaptureBoundary.Kind.LOOT, 0));
    presence.clear();
    events.clear();
    loot.clear();
    swapPairing.run();
    // After the swap, so each is saved under the new pairing.
    generations.observeAll(generationsByAccount);
    return dropped;
  }

  /** Counts only, so the notice can never carry a token, device id, server URL or account. */
  static final class Dropped {
    final int events;
    final int loot;

    Dropped(int events, int loot) {
      this.events = events;
      this.loot = loot;
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
