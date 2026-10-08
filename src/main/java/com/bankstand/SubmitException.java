package com.bankstand;

/**
 * A failed submit. The message is generic and safe to show; never put the token or account hash
 * in it. {@link #isRetryable()} marks transient failures (network, 429, 5xx).
 */
public class SubmitException extends Exception {
  private final boolean retryable;
  private final boolean authFailure;

  public SubmitException(String message) {
    this(message, false);
  }

  public SubmitException(String message, boolean retryable) {
    this(message, retryable, false);
  }

  public SubmitException(String message, boolean retryable, boolean authFailure) {
    super(message);
    this.retryable = retryable;
    this.authFailure = authFailure;
  }

  public boolean isRetryable() {
    return retryable;
  }

  /** A rejected or revoked token (401/403): only re-pairing fixes it, so stop submitting. */
  public boolean isAuthFailure() {
    return authFailure;
  }
}
