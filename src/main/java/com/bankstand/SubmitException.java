package com.bankstand;

/**
 * An identity submit that failed for any reason (not paired, network, a rejected
 * or revoked token, a bad response). The message is generic and safe to surface;
 * the raw token and account hash are never logged. {@link #isRetryable()} marks
 * transient failures (network, 429, 5xx) that a bounded retry may clear, so the
 * caller retries those and fails fast on terminal ones (a revoked token, a 4xx).
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

  /**
   * A rejected or revoked token (401/403). Distinct from an ordinary terminal failure
   * because retrying never fixes it, only re-pairing does, so the caller has to stop
   * submitting rather than try again next capture.
   */
  public boolean isAuthFailure() {
    return authFailure;
  }
}
