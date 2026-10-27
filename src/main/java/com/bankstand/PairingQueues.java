package com.bankstand;

import java.util.ArrayList;
import java.util.List;
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

  PairingQueues(
      PresenceQueue presence,
      EventOutbox events,
      LootLedger ledger,
      LootOutbox loot,
      Supplier<String> pairing) {
    events.setPairing(pairing);
    ledger.setPairing(pairing);
    loot.setPairing(pairing);
    this.presence = presence;
    this.events = events;
    this.ledger = ledger;
    this.loot = loot;
  }

  // Bumped by every pairing request and every replace, so a slow pairing reply is told apart.
  private long tickets;
  // Bumped only when the stored pairing really changed, so a reader knows its acks were forgotten.
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

  /** Changes only when the stored pairing changes. */
  synchronized long epoch() {
    return epoch;
  }

  /** Taken before a pairing request; hand it back to {@link #replaceIfCurrent}. */
  synchronized long beginPairing() {
    return ++tickets;
  }

  /** Like {@link #replace}, but null when a disconnect or a newer pairing came after the ticket. */
  synchronized Dropped replaceIfCurrent(long ticket, java.util.function.BooleanSupplier swapPairing) {
    return ticket == tickets ? replace(swapPairing) : null;
  }

  /** Drops the old queues and swaps the stored pairing as one step. Returns what it dropped. */
  synchronized Dropped replace(java.util.function.BooleanSupplier swapPairing) {
    tickets++;
    int eventCount = events.pending().size();
    int lootCount = loot.pending().size();
    // Swapped first: if it fails, the old pairing and its queues stay as they were. A crash
    // before the clear is safe too: entries are tagged with their pairing and never sent under
    // another one.
    if (!swapPairing.getAsBoolean()) {
      return new Dropped(0, 0, false);
    }
    epoch++;
    lootCount += ledger.closeAll().size();
    presence.clear();
    events.clear();
    loot.clear();
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
