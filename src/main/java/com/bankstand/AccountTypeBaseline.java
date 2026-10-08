package com.bankstand;

/**
 * Change gate: the last account type the server acknowledged. The type rarely changes, so a
 * missed ack is never corrected by a later value; the server must name it in {@code
 * storedBlocks} exactly when it stored it. Holds the plain value, since it is one short string.
 */
public class AccountTypeBaseline {
  private String acked;

  public boolean changedSince(String current) {
    return current != null && !current.equals(acked);
  }

  public void advance(String ackedNow) {
    acked = ackedNow;
  }

  /** Null means nothing is known. */
  public void restore(String value) {
    acked = value;
  }

  public String ackedValue() {
    return acked;
  }

  public void reset() {
    acked = null;
  }
}
