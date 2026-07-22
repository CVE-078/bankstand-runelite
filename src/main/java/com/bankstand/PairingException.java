package com.bankstand;

/**
 * A pairing attempt that failed for any reason. The message is always generic and
 * safe to show the user: the server returns one indistinguishable error for a
 * wrong, expired, consumed or rate-limited code, so the plugin never tries to
 * distinguish them and never retry-loops on a rejection.
 */
public class PairingException extends Exception {
  public PairingException(String message) {
    super(message);
  }
}
