package com.bankstand;

/**
 * Pauses loot sends after a 429 and spaces a notable drop's early send by {@link
 * #FAST_SEND_SPACING_MS}.
 */
final class LootSendPacer {

  static final long FAST_SEND_SPACING_MS = 30_000L;

  private long pausedUntilMs;
  private long lastFastSendMs = Long.MIN_VALUE;

  synchronized boolean mayDrain(long nowMs) {
    return nowMs >= pausedUntilMs;
  }

  /** True, and consumes the slot, when an early send may go now. */
  synchronized boolean tryFastSend(long nowMs) {
    if (!mayDrain(nowMs)) {
      return false;
    }
    if (lastFastSendMs != Long.MIN_VALUE && nowMs - lastFastSendMs < FAST_SEND_SPACING_MS) {
      return false;
    }
    lastFastSendMs = nowMs;
    return true;
  }

  synchronized void pauseFor(long nowMs, long retryAfterMs) {
    pausedUntilMs = Math.max(pausedUntilMs, nowMs + retryAfterMs);
  }
}
