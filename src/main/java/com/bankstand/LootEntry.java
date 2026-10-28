package com.bankstand;

/** One pending {@link LootEvent}, tagged with the account it was captured under. */
public final class LootEntry {
  private final long accountHash;
  private final LootEvent event;
  // Null for an entry queued before pairings were tagged.
  private final String pairingId;

  public LootEntry(long accountHash, LootEvent event) {
    this(accountHash, event, null);
  }

  public LootEntry(long accountHash, LootEvent event, String pairingId) {
    this.accountHash = accountHash;
    this.event = event;
    this.pairingId = pairingId;
  }

  public long getAccountHash() {
    return accountHash;
  }

  public LootEvent getEvent() {
    return event;
  }

  public String getPairingId() {
    return pairingId;
  }
}
