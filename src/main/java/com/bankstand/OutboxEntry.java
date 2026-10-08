package com.bankstand;

/**
 * A pending {@link TransientEvent} tagged with the account it was captured under, so a relog
 * before it is sent cannot submit it under the wrong account. The tag never goes on the wire.
 */
public final class OutboxEntry {
  private final long accountHash;
  private final TransientEvent event;

  public OutboxEntry(long accountHash, TransientEvent event) {
    this.accountHash = accountHash;
    this.event = event;
  }

  public long getAccountHash() {
    return accountHash;
  }

  public TransientEvent getEvent() {
    return event;
  }
}
