package com.bankstand;

import java.util.ArrayList;
import java.util.List;

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

  PairingQueues(PresenceQueue presence, EventOutbox events, LootLedger ledger, LootOutbox loot) {
    this.presence = presence;
    this.events = events;
    this.ledger = ledger;
    this.loot = loot;
  }

  /** Drops the old queues and swaps the stored pairing as one step. Returns what it dropped. */
  synchronized Dropped replace(Runnable swapPairing) {
    // Dropped before the swap: a crash in between loses the queue, never sends it under a new token.
    Dropped dropped =
        new Dropped(events.pending().size(), loot.pending().size() + ledger.closeAll().size());
    presence.clear();
    events.clear();
    loot.clear();
    swapPairing.run();
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
