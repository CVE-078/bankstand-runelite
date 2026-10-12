package com.bankstand;

/**
 * A failed pairing attempt. The message is generic and safe to show: the server gives one error
 * for every rejection reason, so the plugin never tells them apart or retries.
 */
public class PairingException extends Exception {
  public PairingException(String message) {
    super(message);
  }
}
