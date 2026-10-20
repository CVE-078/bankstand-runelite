package com.bankstand;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * The capture generation the server last acked, per account. The server bumps it when the
 * player removes their captured data, and refuses anything stamped with another one, so a queue
 * built before a removal cannot resend into it. Each capture is stamped with the value known
 * when it was queued. Thread-safe.
 */
public final class CaptureGenerations {
  private final Map<Long, Integer> byAccount = new ConcurrentHashMap<>();
  private volatile AckedStateStore store;
  private volatile Executor executor;

  /** Persists every change to {@code store}, written on {@code executor}. */
  public void persistTo(AckedStateStore store, Executor executor) {
    this.store = store;
    this.executor = executor;
  }

  /** The last acked generation, or null when none is known (the capture goes unstamped). */
  public Integer get(long accountHash) {
    return byAccount.get(accountHash);
  }

  /** Puts the account's generation on a request body, when one is known. */
  public void stamp(Map<String, Object> body, long accountHash) {
    Integer generation = get(accountHash);
    if (generation != null) {
      body.put("captureGeneration", generation);
    }
  }

  /** Records an acked generation. True when it changed. A null ack (an older server) is ignored. */
  public boolean observe(long accountHash, Integer generation) {
    if (generation == null) {
      return false;
    }
    Integer previous = byAccount.put(accountHash, generation);
    boolean changed = !generation.equals(previous);
    AckedStateStore target = store;
    Executor writer = executor;
    if (changed && target != null && writer != null) {
      int value = generation;
      writer.execute(() -> target.saveCaptureGeneration(accountHash, value));
    }
    return changed;
  }

  /** Restores a persisted value without overwriting one an ack already set this run. */
  public void restore(long accountHash, Integer generation) {
    if (generation != null) {
      byAccount.putIfAbsent(accountHash, generation);
    }
  }

  /**
   * Forgets every generation, here and on disk: a new pairing may be another Bankstand account,
   * so what is queued next goes unstamped until that account acks one.
   */
  public void forget() {
    byAccount.clear();
    AckedStateStore target = store;
    if (target != null) {
      target.clearCaptureGenerations();
    }
  }
}
