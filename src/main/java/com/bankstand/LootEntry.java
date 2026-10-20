package com.bankstand;

/**
 * One pending {@link LootEvent}, tagged with the account it was captured under and the capture
 * generation known when it was queued (null when none was).
 */
public final class LootEntry {
  private final long accountHash;
  private final LootEvent event;
  private final Integer captureGeneration;

  public LootEntry(long accountHash, LootEvent event) {
    this(accountHash, event, null);
  }

  public LootEntry(long accountHash, LootEvent event, Integer captureGeneration) {
    this.accountHash = accountHash;
    this.event = event;
    this.captureGeneration = captureGeneration;
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
}
