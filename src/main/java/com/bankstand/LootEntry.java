package com.bankstand;

/**
 * One pending {@link LootEvent}, tagged with the account it was captured under and the capture
 * generation known when it was queued (null when none was).
 */
public final class LootEntry {
  private final long accountHash;
  private final LootEvent event;
  // Null for an entry queued before pairings were tagged.
  private final String pairingId;
  private final Integer captureGeneration;
  // What was known when the record's first kill was made; never stored. Null when queued as is.
  private final transient CaptureBoundary.Ticket ticket;

  public LootEntry(long accountHash, LootEvent event) {
    this(accountHash, event, null);
  }

  public LootEntry(long accountHash, LootEvent event, Integer captureGeneration) {
    this(accountHash, event, captureGeneration, null);
  }

  public LootEntry(long accountHash, LootEvent event, Integer captureGeneration, String pairingId) {
    this(accountHash, event, captureGeneration, pairingId, null);
  }

  LootEntry(
      long accountHash,
      LootEvent event,
      Integer captureGeneration,
      String pairingId,
      CaptureBoundary.Ticket ticket) {
    this.accountHash = accountHash;
    this.event = event;
    this.captureGeneration = captureGeneration;
    this.pairingId = pairingId;
    this.ticket = ticket;
  }

  CaptureBoundary.Ticket getTicket() {
    return ticket;
  }

  /** This record, stamped as the boundary settled it. */
  LootEntry stamped(Integer generation) {
    return new LootEntry(accountHash, event, generation, pairingId);
  }

  public long getAccountHash() {
    return accountHash;
  }

  public LootEvent getEvent() {
    return event;
  }

  public Integer getCaptureGeneration() {
    return captureGeneration;
  }

  public String getPairingId() {
    return pairingId;
  }
}
