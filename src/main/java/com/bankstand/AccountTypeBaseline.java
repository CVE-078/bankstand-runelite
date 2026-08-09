package com.bankstand;

/**
 * The last account type the server acknowledged, used as a change gate so the capture loop
 * only submits when the answer differs from what has already been accepted.
 *
 * <p><b>The one capability where a missed acknowledgement is permanent.</b> Every other
 * block describes something that moves: xp ticks, a diary tier completes, a log slot
 * fills, and a baseline that failed to advance is corrected by the next real change. An
 * account's type changes once if ever, so there is no later value to force a resend. A
 * server that stored the value without naming it in {@code storedBlocks} would leave this
 * client re-sending the same word on every capture, forever, and a server that named it
 * without storing it would lose the fact outright.
 *
 * <p>Holds the value rather than a digest, unlike the collection-based baselines: it is one
 * short string, so hashing it would cost the ability to read the acked state in a bug
 * report and buy nothing.
 */
public class AccountTypeBaseline {
  private String acked;

  /** True when {@code current} differs from what the server last acknowledged. */
  public boolean changedSince(String current) {
    return current != null && !current.equals(acked);
  }

  public void advance(String ackedNow) {
    acked = ackedNow;
  }

  /** Restores a value read back from disk. Null means nothing is known. */
  public void restore(String value) {
    acked = value;
  }

  /** The acknowledged type, or null when nothing has been. */
  public String ackedValue() {
    return acked;
  }

  public void reset() {
    acked = null;
  }
}
