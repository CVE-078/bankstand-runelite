package com.bankstand;

/** How long a failed request waits: a server's Retry-After, or jittered backoff. */
final class RetryTiming {
  // The pause for a 429 without a Retry-After.
  static final long DEFAULT_RETRY_AFTER_MILLIS = 60_000L;

  private RetryTiming() {}

  /** A {@code Retry-After} in seconds as millis, capped at an hour, else the default. */
  static long retryAfterMillis(String header) {
    if (header == null) {
      return DEFAULT_RETRY_AFTER_MILLIS;
    }
    try {
      long seconds = Long.parseLong(header.trim());
      if (seconds <= 0) {
        return DEFAULT_RETRY_AFTER_MILLIS;
      }
      return Math.min(seconds, 3600L) * 1000L;
    } catch (NumberFormatException e) {
      return DEFAULT_RETRY_AFTER_MILLIS;
    }
  }

  static final long MAX_BACKOFF_MILLIS = 8_000L;

  /**
   * Full jitter over a doubling window, so clients that failed together do not retry
   * together. The randomness is a parameter so the backoff is testable.
   *
   * @param randomFraction a value in {@code [0, 1)}
   */
  static long backoffMillis(int attempt, long baseDelayMillis, double randomFraction) {
    long window = Math.min(MAX_BACKOFF_MILLIS, baseDelayMillis << (attempt - 1));
    return (long) (window * randomFraction);
  }
}
