package com.bankstand;

import com.bankstand.dto.EventAck;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The capture generation the server last acked, per account. The server bumps it when the
 * player removes their captured data, and refuses anything stamped with another one, so a queue
 * built before a removal cannot resend into it. Each capture is stamped with the value known
 * when it was queued. Thread-safe.
 */
public final class CaptureGenerations {
  private final Map<Long, Integer> byAccount = new ConcurrentHashMap<>();
  private final Map<Long, Integer> staleNoticeAt = new ConcurrentHashMap<>();
  private volatile Consumer<String> staleNotice = message -> {};
  private volatile BooleanSupplier canNotify = () -> true;
  private volatile AckedStateStore store;
  private volatile Executor executor;

  /**
   * Persists every change to {@code store}, written on {@code executor}, after restoring what it
   * holds. Read here, at start-up, so nothing queued after a restart goes out unstamped.
   */
  public void persistTo(AckedStateStore store, Executor executor) {
    store.captureGenerations().forEach(this::restore);
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

  /**
   * Where the once-per-removal message about refused captures goes. While {@code canNotify} is
   * false (logged out, so the line would be lost) the next refusal tries again.
   */
  public void onStaleRefusal(Consumer<String> notice, BooleanSupplier canNotify) {
    this.staleNotice = notice;
    this.canNotify = canNotify;
  }

  /** {@link #observe(long, Integer)} for an ack whose refusal reason may be stale. */
  public void observe(long accountHash, Integer generation, String reason) {
    observe(accountHash, generation);
    if (EventOutbox.STALE_GENERATION.equals(reason)) {
      staleRefused(accountHash);
    }
  }

  /** {@link #observe(long, Integer)} for per-item acks, any of which may be stale. */
  public void observe(long accountHash, Integer generation, List<EventAck> acks) {
    observe(accountHash, generation);
    if (EventOutbox.anyStaleGeneration(acks)) {
      staleRefused(accountHash);
    }
  }

  /**
   * Captures made under an older generation were refused and dropped, never restamped, so data the
   * player removed is not uploaded again. Said once per removal, not once per request.
   */
  private void staleRefused(long accountHash) {
    if (canNotify.getAsBoolean() && firstStaleRefusal(accountHash)) {
      staleNotice.accept(
          "Bankstand refused some captures for a character whose data was removed."
              + " New captures sync as normal.");
    }
  }

  /**
   * True the first time a capture is refused as stale under the account's current generation, so
   * the player is told once per removal rather than once per request.
   */
  public boolean firstStaleRefusal(long accountHash) {
    Integer current = byAccount.get(accountHash);
    return current != null && !current.equals(staleNoticeAt.put(accountHash, current));
  }

  /** Restores a persisted value without overwriting one an ack already set this run. */
  public void restore(long accountHash, Integer generation) {
    if (generation != null) {
      byAccount.putIfAbsent(accountHash, generation);
    }
  }

  /**
   * Forgets every generation, here and on disk: a new pairing may be another Bankstand account,
   * so what is queued next goes unstamped until that account acks one. The disk write is queued
   * behind any pending generation write, so none of those can put an old value back.
   *
   * <p>shortcut: an ack still in flight when this runs can re-teach the old pairing's value;
   * captures stamped with it are refused once and the refusal teaches the right one. Tag each
   * request with a pairing epoch if that ever matters.
   */
  public void forget() {
    byAccount.clear();
    staleNoticeAt.clear();
    AckedStateStore target = store;
    Executor writer = executor;
    if (target != null && writer != null) {
      writer.execute(target::clearCaptureGenerations);
    }
  }
}
