package com.bankstand;

/**
 * When a loot send may go: never while a 429 asked this client to wait, and a notable
 * drop's early send at most once per {@link #FAST_SEND_SPACING_MS}, so a low notable
 * threshold at a fast-kill NPC cannot spend the route's hourly limit.
 *
 * <p>Takes the time rather than reading a clock, so the spacing is testable.
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
