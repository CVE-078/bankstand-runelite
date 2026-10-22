package com.bankstand;

/**
 * A pending {@link TransientEvent} tagged with the account it was captured under, so a relog
 * before it is sent cannot submit it under the wrong account. The tag never goes on the wire.
 */
public final class OutboxEntry {
  private final long accountHash;
  private final TransientEvent event;
  // Null for an entry queued before pairings were tagged.
  private final String pairingId;

  public OutboxEntry(long accountHash, TransientEvent event) {
    this(accountHash, event, null);
  }

  public OutboxEntry(long accountHash, TransientEvent event, String pairingId) {
    this.accountHash = accountHash;
    this.event = event;
    this.pairingId = pairingId;
  }

  public long getAccountHash() {
    return accountHash;
  }

  public TransientEvent getEvent() {
    return event;
  }

  public String getPairingId() {
    return pairingId;
  }
}
