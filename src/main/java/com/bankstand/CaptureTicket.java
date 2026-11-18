package com.bankstand;

/** What was known for one account when something was captured or asked. */
final class CaptureTicket {
  final long account;
  final int login;
  final int epoch;
  final boolean confirmed;
  // Valid only when confirmed.
  final Integer stamp;
  // The last generation the server named under this pairing, before the capture.
  final Integer remembered;
  final long atMs;

  CaptureTicket(
      long account,
      int login,
      int epoch,
      boolean confirmed,
      Integer stamp,
      Integer remembered,
      long atMs) {
    this.account = account;
    this.login = login;
    this.epoch = epoch;
    this.confirmed = confirmed;
    this.stamp = stamp;
    this.remembered = remembered;
    this.atMs = atMs;
  }

  /** Whether two captures would leave with the same stamp, so may share a loot record. */
  boolean sameStamp(CaptureTicket other) {
    if (confirmed != other.confirmed || epoch != other.epoch) {
      return false;
    }
    return confirmed
        ? java.util.Objects.equals(stamp, other.stamp)
        : login == other.login && java.util.Objects.equals(remembered, other.remembered);
  }
}
