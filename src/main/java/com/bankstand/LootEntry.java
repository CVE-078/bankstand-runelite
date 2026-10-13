package com.bankstand;

/** One pending {@link LootEvent}, tagged with the account it was captured under. */
public final class LootEntry {
  private final long accountHash;
  private final LootEvent event;

  public LootEntry(long accountHash, LootEvent event) {
    this.accountHash = accountHash;
    this.event = event;
  }

  public long getAccountHash() {
    return accountHash;
  }

  public LootEvent getEvent() {
    return event;
  }
}
