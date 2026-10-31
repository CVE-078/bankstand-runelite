package com.bankstand;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The capture generation the server last acked, per account. The server bumps it when the
 * player removes their captured data, and refuses anything stamped with another one, so a queue
 * built before a removal cannot resend into it. Each capture is stamped with the value known
 * when it was queued. Thread-safe.
 */
public final class CaptureGenerations {
  /** Said once per removal. Never claims the removal is final while capture can refill it. */
  static final String STALE_NOTICE =
      "Captured data for one of your characters was removed on the Bankstand website. Its history"
          + " stays deleted. Drops, loot and session marks captured before this plugin learned of"
          + " the removal were discarded and will not be sent. While this plugin stays connected"
          + " with capture on, its current progress syncs again. Turn capture off or disconnect to"
          + " stop that.";

  /** The same, short enough for a panel row. */
  static final String STALE_ACTIVITY = "Data removed on the site: progress resyncs";

  private final Map<Long, Integer> byAccount = new ConcurrentHashMap<>();
  private final Map<Long, Integer> staleNoticeAt = new ConcurrentHashMap<>();
  // Accounts the server answered for since their login: only their generation is a stamp.
  private final Set<Long> confirmed = ConcurrentHashMap.newKeySet();
  private volatile Consumer<String> staleNotice = message -> {};
  private volatile BiConsumer<Long, Integer> changed = (account, generation) -> {};
  private volatile BooleanSupplier canNotify = () -> true;
  private volatile AckedStateStore store;
  private volatile Executor executor;
  private volatile Supplier<String> pairing = () -> null;

  /**
   * Persists every change to {@code store}, written on {@code executor} and tagged with the
   * pairing it was learned under, after restoring what that pairing stored. Another pairing's
   * values are never restored: they may belong to another Bankstand account.
   */
  public void persistTo(AckedStateStore store, Executor executor, Supplier<String> pairing) {
    store.captureGenerations(pairing.get()).forEach(this::restore);
    this.store = store;
    this.executor = executor;
    this.pairing = pairing;
  }

  /** The last acked generation, or null when none is known (the capture goes unstamped). */
  public Integer get(long accountHash) {
    return byAccount.get(accountHash);
  }

  /**
   * The stamp for a capture queued now: the generation the server named since this login, else
   * null. Unstamped, the server takes it only for a character never cleared, so a capture made
   * before the answer can never land under the wrong generation.
   */
  public Integer stampFor(long accountHash) {
    return confirmed.contains(accountHash) ? byAccount.get(accountHash) : null;
  }

  /** Whether the server has answered for this account since its login or this pairing. */
  public boolean isConfirmed(long accountHash) {
    return confirmed.contains(accountHash);
  }

  /**
   * A new login: a remembered generation may be stale until the server answers again. Called
   * through {@link CaptureBoundary#startLogin}, so no answer interleaves.
   */
  void startSession(long accountHash) {
    confirmed.remove(accountHash);
  }

  /**
   * The server answered without a generation (an older server, or a character not routed), so a
   * remembered one is not a stamp: captures go unstamped and the server decides.
   */
  public synchronized void confirm(long accountHash) {
    byAccount.remove(accountHash);
    confirmed.add(accountHash);
  }

  /** Puts the account's confirmed generation on a request body, when one is known. */
  public void stamp(Map<String, Object> body, long accountHash) {
    Integer generation = stampFor(accountHash);
    if (generation != null) {
      body.put("captureGeneration", generation);
    }
  }

  /**
   * Records an acked generation. True when it changed. A null ack (an older server) is ignored, and
   * so is a lower value: generations only rise, so a lower one is a late ack from before a removal.
   */
  public boolean observe(long accountHash, Integer generation) {
    return apply(accountHash, generation, null, false, true);
  }

  /** Every generation the server named when pairing, learned before anything is queued. */
  public void observeAll(Map<Long, Integer> generations) {
    generations.forEach(this::observe);
  }

  /** Told of every change, on the thread that observed it. A restore is not a change. */
  public void onChange(BiConsumer<Long, Integer> listener) {
    this.changed = listener;
  }

  /**
   * Records a generation the server named. {@code confirm}: it answered a request from this
   * login, so captures may now be stamped with it. A lower value is taken only when the server
   * refused the value held: then it really went back. Synchronized: answers arrive on several
   * threads, and a check-then-put could go backwards.
   */
  synchronized boolean apply(
      long accountHash, Integer generation, Integer sentWith, boolean staleRefusal, boolean confirm) {
    if (generation == null) {
      return false;
    }
    if (confirm) {
      confirmed.add(accountHash);
    }
    Integer previous = byAccount.get(accountHash);
    boolean mayLower = staleRefusal && sentWith != null && sentWith.equals(previous);
    if (previous != null && generation < previous && !mayLower) {
      return false;
    }
    byAccount.put(accountHash, generation);
    boolean changed = !generation.equals(previous);
    AckedStateStore target = store;
    Executor writer = executor;
    if (changed && target != null && writer != null) {
      int value = generation;
      String tag = pairing.get();
      writer.execute(() -> target.saveCaptureGeneration(accountHash, value, tag));
    }
    if (changed) {
      this.changed.accept(accountHash, generation);
    }
    return changed;
  }

  /**
   * Whether baselines acked under {@code ackedUnder} still describe what the server holds. Unknown
   * now: keep them, the first ack settles it. Saved before generations existed: that was 1.
   */
  static boolean baselinesHold(Integer ackedUnder, Integer current) {
    return current == null || current.equals(ackedUnder == null ? 1 : ackedUnder);
  }

  /** Whether a stored ack may advance a baseline: it was stored under the generation known now. */
  static boolean ackedUnder(Integer ackGeneration, Integer current) {
    return ackGeneration == null || ackGeneration.equals(current);
  }

  /**
   * Where the once-per-removal message about refused captures goes. While {@code canNotify} is
   * false (logged out, so the line would be lost) the next refusal tries again.
   */
  public void onStaleRefusal(Consumer<String> notice, BooleanSupplier canNotify) {
    this.staleNotice = notice;
    this.canNotify = canNotify;
  }

  /**
   * Captures made under an older generation were refused and dropped, never restamped, so data the
   * player removed is not uploaded again. Said once per removal, not once per request.
   */
  void staleRefused(long accountHash) {
    if (canNotify.getAsBoolean() && firstStaleRefusal(accountHash)) {
      staleNotice.accept(STALE_NOTICE);
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
   * Forgets every generation, here and on disk: a new pairing may be another Bankstand account.
   * Called through {@link CaptureBoundary#replace}, which stops older answers from counting.
   */
  synchronized void forget() {
    byAccount.clear();
    staleNoticeAt.clear();
    confirmed.clear();
    AckedStateStore target = store;
    Executor writer = executor;
    if (target != null && writer != null) {
      writer.execute(target::clearCaptureGenerations);
    }
  }
}
