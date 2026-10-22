package com.bankstand;

/**
 * A pending {@link TransientEvent} tagged with the account it was captured under, so a relog
 * before it is sent cannot submit it under the wrong account, and with the capture generation
 * known when it was queued (null when none was). The account tag never goes on the wire.
 */
public final class OutboxEntry {
  private final long accountHash;
  private final TransientEvent event;
  // Null for an entry queued before pairings were tagged.
  private final String pairingId;
  private final Integer captureGeneration;

  public OutboxEntry(long accountHash, TransientEvent event) {
    this(accountHash, event, null);
  }

  public OutboxEntry(long accountHash, TransientEvent event, Integer captureGeneration) {
    this(accountHash, event, captureGeneration, null);
  }

  public OutboxEntry(
      long accountHash, TransientEvent event, Integer captureGeneration, String pairingId) {
    this.accountHash = accountHash;
    this.event = event;
    this.captureGeneration = captureGeneration;
    this.pairingId = pairingId;
  }

  public long getAccountHash() {
    return accountHash;
  }

  public TransientEvent getEvent() {
    return event;
  }

  public Integer getCaptureGeneration() {
    return captureGeneration;
  }

  public String getPairingId() {
    return pairingId;
  }
}
