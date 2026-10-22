package com.bankstand;

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

  /** Swaps the stored pairing and drops the old queues as one step. */
  synchronized void replace(Runnable swapPairing) {
    swapPairing.run();
    drop();
  }

  private void drop() {
    presence.clear();
    events.clear();
    ledger.discard();
    loot.clear();
  }
}
