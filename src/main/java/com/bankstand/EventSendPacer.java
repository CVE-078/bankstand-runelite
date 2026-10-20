package com.bankstand;

import com.bankstand.dto.SubmitEventsResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Pauses every event send after a 429, and holds back an account whose batch came back
 * unrouted (no saved character) until the identity changes or {@link #UNROUTED_BACKOFF_MS}
 * passes. The events stay queued either way.
 */
final class EventSendPacer {

  static final long UNROUTED_BACKOFF_MS = 30 * 60_000L;

  private long pausedUntilMs;
  private final Map<Long, Long> unroutedUntilMs = new HashMap<>();

  synchronized boolean maySend(long accountHash, long nowMs) {
    return nowMs >= pausedUntilMs && nowMs >= unroutedUntilMs.getOrDefault(accountHash, 0L);
  }

  /** Records one chunk's outcome; true when its acks should be applied to the outbox. */
  synchronized boolean record(
      long accountHash, long nowMs, SubmitEventsResponse response, Throwable failure) {
    if (failure != null) {
      if (failure instanceof SubmitException) {
        pauseFor(nowMs, ((SubmitException) failure).getRetryAfterMillis());
      }
      return false;
    }
    if (!response.isRouted()) {
      unrouted(accountHash, nowMs);
      return false;
    }
    return true;
  }

  synchronized void pauseFor(long nowMs, long retryAfterMs) {
    pausedUntilMs = Math.max(pausedUntilMs, nowMs + retryAfterMs);
  }

  synchronized void unrouted(long accountHash, long nowMs) {
    unroutedUntilMs.put(accountHash, nowMs + UNROUTED_BACKOFF_MS);
  }

  /** A relog or re-pair may route what did not before. */
  synchronized void forgetUnrouted() {
    unroutedUntilMs.clear();
  }
}
