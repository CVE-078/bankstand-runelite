package com.bankstand;

/**
 * One pending {@link LootEvent}, tagged with the account it was captured under, for the
 * same reason {@link OutboxEntry} is: a relog to another character before the next send
 * must not move one character's loot onto another.
 */
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
