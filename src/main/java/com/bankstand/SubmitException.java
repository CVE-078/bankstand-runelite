package com.bankstand;

/**
 * An identity submit that failed for any reason (not paired, network, a rejected
 * or revoked token, a bad response). The message is generic and safe to surface;
 * the raw token and account hash are never logged.
 */
public class SubmitException extends Exception {
  public SubmitException(String message) {
    super(message);
  }
}
