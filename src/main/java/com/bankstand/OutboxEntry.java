package com.bankstand;

/**
 * One pending {@link TransientEvent}, tagged with the account it was captured
 * under. The tag never travels on the wire (a batch's one top-level {@code
 * accountHash} covers every event in it); it exists so the outbox can still
 * attribute an event correctly if the player relogs to a different character
 * before it is sent, rather than submitting stale entries under whichever
 * account happens to be active when the outbox next drains.
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
