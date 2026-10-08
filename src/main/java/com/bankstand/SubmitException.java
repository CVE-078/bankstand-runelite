package com.bankstand;

/**
 * A failed submit. The message is generic and safe to show; never put the token or account hash
 * in it. {@link #isRetryable()} marks transient failures (network, 429, 5xx).
 */
public class SubmitException extends Exception {
  private final boolean retryable;
  private final boolean authFailure;
  private final long retryAfterMillis;

  public SubmitException(String message) {
    this(message, false);
  }

  public SubmitException(String message, boolean retryable) {
    this(message, retryable, false);
  }

  public SubmitException(String message, boolean retryable, boolean authFailure) {
    this(message, retryable, authFailure, 0L);
  }

  public SubmitException(
      String message, boolean retryable, boolean authFailure, long retryAfterMillis) {
    super(message);
    this.retryable = retryable;
    this.authFailure = authFailure;
    this.retryAfterMillis = retryAfterMillis;
  }

  public boolean isRetryable() {
    return retryable;
  }

  /** A rejected or revoked token (401/403): only re-pairing fixes it, so stop submitting. */
  public boolean isAuthFailure() {
    return authFailure;
  }

  /** A 429's {@code Retry-After}, or zero. */
  public long getRetryAfterMillis() {
    return retryAfterMillis;
  }
}
