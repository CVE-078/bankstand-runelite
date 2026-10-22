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

  public LootEntry(long accountHash, LootEvent event) {
    this(accountHash, event, null);
  }

  public LootEntry(long accountHash, LootEvent event, Integer captureGeneration) {
    this(accountHash, event, captureGeneration, null);
  }

  public LootEntry(long accountHash, LootEvent event, Integer captureGeneration, String pairingId) {
    this.accountHash = accountHash;
    this.event = event;
    this.captureGeneration = captureGeneration;
    this.pairingId = pairingId;
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
