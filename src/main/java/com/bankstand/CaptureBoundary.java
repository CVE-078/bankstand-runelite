package com.bankstand;

import com.bankstand.dto.EventAck;
import com.bankstand.session.AccountSession;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * Decides which capture generation each event, loot record and presence mark carries.
 *
 * <p>A capture made before the server named its character's generation for this login is held.
 * It is released stamped with the server's answer only when that answer equals the generation
 * known before the capture (or 1 with none known): generations only rise, so nothing was removed
 * in between. Otherwise it is discarded and counted, never relabelled into a later generation.
 *
 * <p>Answers apply only to the pairing and login that asked. Every method locks the pairing
 * queues, which {@link PairingQueues#replace} also holds, so a new pairing and an answer never
 * interleave.
 */
final class CaptureBoundary {
  static final int MAX_HELD = 200;
  static final long MAX_HELD_MS = 10 * 60_000L;
  static final int NO_LOGIN = -1;

  enum Kind {
    EVENT("event", "events"),
    LOOT("loot record", "loot records"),
    PRESENCE("session mark", "session marks"),
    SNAPSHOT("snapshot", "snapshots");

    final String one;
    final String many;

    Kind(String one, String many) {
      this.one = one;
      this.many = many;
    }
  }

  /** Queues a capture once its stamp is settled. Null means unstamped. */
  interface Sink {
    void queue(Integer stamp);
  }

  /** What was known for one account when something was captured or asked. */
  static final class Ticket {
    final long account;
    final int login;
    final int epoch;
    final boolean confirmed;
    // Valid only when confirmed.
    final Integer stamp;
    // The last generation the server named under this pairing, before the capture.
    final Integer remembered;
    final long atMs;

    Ticket(long account, int login, int epoch, boolean confirmed, Integer stamp, Integer remembered, long atMs) {
      this.account = account;
      this.login = login;
      this.epoch = epoch;
      this.confirmed = confirmed;
      this.stamp = stamp;
      this.remembered = remembered;
      this.atMs = atMs;
    }

    /** Whether two captures would leave with the same stamp, so may share a loot record. */
    boolean sameStamp(Ticket other) {
      if (confirmed != other.confirmed || epoch != other.epoch) {
        return false;
      }
      return confirmed
          ? java.util.Objects.equals(stamp, other.stamp)
          : login == other.login && java.util.Objects.equals(remembered, other.remembered);
    }
  }

  private static final class Held {
    final Ticket ticket;
    final Kind kind;
    final Sink sink;

    Held(Ticket ticket, Kind kind, Sink sink) {
      this.ticket = ticket;
      this.kind = kind;
      this.sink = sink;
    }
  }

  private final Object lock;
  private final CaptureGenerations generations;
  private final AccountSession session;
  private final LongSupplier clock;
  private final List<Held> held = new ArrayList<>();
  private final CaptureLosses losses = new CaptureLosses();
  private long waitingAccount;
  private int waitingLogin = NO_LOGIN;
  private int epoch;
  private volatile Consumer<String> lost = line -> {};

  CaptureBoundary(Object lock, CaptureGenerations generations, AccountSession session, LongSupplier clock) {
    this.lock = lock;
    this.generations = generations;
    this.session = session;
    this.clock = clock;
  }

  /** Where the panel line for each batch of discarded captures goes. */
  void onLost(Consumer<String> lost) {
    this.lost = lost;
  }

  /**
   * Whether {@code answer} can never be newer than the generation at capture time: it is the
   * value known before the capture, or 1 with none known. Null: the server named none, so the
   * capture goes unstamped and the server decides.
   */
  static boolean proven(Integer remembered, Integer answer) {
    return answer == null || answer.equals(remembered) || (remembered == null && answer == 1);
  }

  /** A login: until the server answers for it, captures are held. True when the account changed. */
  boolean startLogin(long accountHash) {
    synchronized (lock) {
      if (!session.onLogin(accountHash)) {
        return false;
      }
      generations.startSession(accountHash);
      return true;
    }
  }

  void endLogin() {
    synchronized (lock) {
      session.onLogout();
    }
  }

  /** What is known now: take it as a capture is made or a request leaves. */
  Ticket ticket(long accountHash) {
    synchronized (lock) {
      int login =
          session.isActive() && session.getAccountHash() == accountHash
              ? session.getGeneration()
              : NO_LOGIN;
      boolean confirmed = generations.isConfirmed(accountHash);
      return new Ticket(
          accountHash,
          login,
          epoch,
          confirmed,
          confirmed ? generations.stampFor(accountHash) : null,
          generations.get(accountHash),
          clock.getAsLong());
    }
  }

  /**
   * Reads the token, then the queue, then a ticket per account in it, as one step: a re-pair
   * waits, so an answer to this request can never be taken for the next pairing's.
   */
  <T> CaptureSend<T> send(Supplier<String> token, Supplier<List<T>> pending, ToLongFunction<T> account) {
    synchronized (lock) {
      String t = token.get();
      List<T> items = pending.get();
      Map<Long, Ticket> asked = new HashMap<>();
      for (T item : items) {
        asked.computeIfAbsent(account.applyAsLong(item), this::ticket);
      }
      return new CaptureSend<>(t, items, asked);
    }
  }

  /**
   * A snapshot was not read because the server has not answered for this login yet. It is read
   * once it has, so it counts as recovered, once per login.
   */
  void snapshotWaiting(long accountHash) {
    synchronized (lock) {
      int login = ticket(accountHash).login;
      if (login != NO_LOGIN && (accountHash != waitingAccount || login != waitingLogin)) {
        waitingAccount = accountHash;
        waitingLogin = login;
        losses.recovered(1);
      }
    }
  }

  /** Queues a capture made now. */
  void queue(long accountHash, Kind kind, Sink sink) {
    synchronized (lock) {
      queue(ticket(accountHash), kind, sink);
    }
  }

  /** Queues a capture made when {@code ticket} was taken: now, or held until an answer. */
  void queue(Ticket ticket, Kind kind, Sink sink) {
    synchronized (lock) {
      expire();
      if (ticket.epoch != epoch) {
        // Made under a pairing since replaced; that pairing's queues were already dropped.
        report(CaptureLosses.Reason.REPAIRED, CaptureLosses.single(kind, 1));
        return;
      }
      if (ticket.confirmed) {
        sink.queue(ticket.stamp);
      } else if (generations.isConfirmed(ticket.account)) {
        // Answered since the capture: settle it now against that answer.
        Map<Kind, Integer> stale = new EnumMap<>(Kind.class);
        settle(new Held(ticket, kind, sink), generations.stampFor(ticket.account), stale);
        reportStale(ticket.account, stale);
      } else {
        held.add(new Held(ticket, kind, sink));
        Map<Kind, Integer> over = new EnumMap<>(Kind.class);
        while (held.size() > MAX_HELD) {
          over.merge(held.remove(0).kind, 1, Integer::sum);
        }
        report(CaptureLosses.Reason.FULL, over);
      }
    }
  }

  /** Queues only if it can be stamped now, for a heartbeat: one is never held. */
  void queueIfConfirmed(long accountHash, Sink sink) {
    synchronized (lock) {
      if (generations.isConfirmed(accountHash)) {
        sink.queue(generations.stampFor(accountHash));
      }
    }
  }

  /**
   * Asks for the account's generation. The answer counts only for the pairing and login in
   * {@code asked}, taken when the ask was decided.
   */
  CompletableFuture<com.bankstand.dto.SubmitResponse> discover(
      Ticket asked, Supplier<CompletableFuture<com.bankstand.dto.SubmitResponse>> call) {
    return call.get()
        .whenComplete(
            (res, failure) -> {
              if (failure == null && res != null) {
                identityAnswered(asked, res.getCaptureGeneration());
              }
            });
  }

  /**
   * The identity check answered. Without a generation (an older server, or a character not
   * routed) the login is confirmed unstamped and the server decides.
   */
  void identityAnswered(Ticket asked, Integer generation) {
    synchronized (lock) {
      if (asked.epoch != epoch) {
        return;
      }
      boolean current = session.isCurrent(asked.account, asked.login);
      if (generation == null) {
        if (current) {
          generations.confirm(asked.account);
        }
      } else {
        generations.apply(asked.account, generation, null, false, current);
      }
      release(asked, generation);
    }
  }

  /**
   * An upload answered. {@code stale} is how many of its items were refused as captured under an
   * older generation: snapshots are recaptured, anything else is gone for good and counted.
   */
  void acked(Ticket asked, Kind kind, Integer generation, Integer sentWith, int stale) {
    synchronized (lock) {
      if (asked.epoch != epoch) {
        return;
      }
      boolean current = session.isCurrent(asked.account, asked.login);
      generations.apply(asked.account, generation, sentWith, stale > 0, current);
      if (stale > 0) {
        Map<Kind, Integer> refused = new EnumMap<>(Kind.class);
        if (kind == Kind.SNAPSHOT) {
          // Read again from the game under the new generation.
          losses.recovered(stale);
        } else {
          refused.put(kind, stale);
        }
        generations.staleRefused(asked.account);
        reportStale(asked.account, refused);
      }
      if (generation != null) {
        release(asked, generation);
      }
    }
  }

  /** {@link #acked} for a batch with one ack per item. */
  void acked(Ticket asked, Kind kind, Integer generation, Integer sentWith, List<EventAck> acks) {
    acked(asked, kind, generation, sentWith, EventOutbox.countStaleGeneration(acks));
  }

  /** {@link #acked} for a single item refused with {@code reason}, or null. */
  void acked(Ticket asked, Kind kind, Integer generation, Integer sentWith, String reason) {
    acked(asked, kind, generation, sentWith, EventOutbox.STALE_GENERATION.equals(reason) ? 1 : 0);
  }

  // Settles what this account held from the asking login or an earlier one.
  private void release(Ticket asked, Integer answer) {
    Map<Kind, Integer> stale = new EnumMap<>(Kind.class);
    for (Iterator<Held> it = held.iterator(); it.hasNext(); ) {
      Held h = it.next();
      if (h.ticket.account == asked.account && h.ticket.login <= asked.login) {
        it.remove();
        settle(h, answer, stale);
      }
    }
    reportStale(asked.account, stale);
  }

  private static void settle(Held h, Integer answer, Map<Kind, Integer> stale) {
    if (proven(h.ticket.remembered, answer)) {
      h.sink.queue(answer);
    } else {
      stale.merge(h.kind, 1, Integer::sum);
    }
  }

  /** Discards what was held past {@link #MAX_HELD_MS}. */
  void expire() {
    synchronized (lock) {
      long now = clock.getAsLong();
      Map<Kind, Integer> old = new EnumMap<>(Kind.class);
      for (Iterator<Held> it = held.iterator(); it.hasNext(); ) {
        Held h = it.next();
        if (now - h.ticket.atMs > MAX_HELD_MS) {
          it.remove();
          old.merge(h.kind, 1, Integer::sum);
        }
      }
      report(CaptureLosses.Reason.NO_ANSWER, old);
    }
  }

  /**
   * A new pairing or a disconnect: every older answer stops counting and what was held is
   * dropped with the other queues. Call only from {@link PairingQueues#replace}. Returns what it
   * dropped.
   */
  Map<Kind, Integer> replace() {
    synchronized (lock) {
      epoch++;
      Map<Kind, Integer> dropped = new EnumMap<>(Kind.class);
      for (Held h : held) {
        dropped.merge(h.kind, 1, Integer::sum);
      }
      held.clear();
      generations.forget();
      return dropped;
    }
  }

  /** Held now, for tests and the status line. */
  int heldCount() {
    synchronized (lock) {
      return held.size();
    }
  }

  /** Recovered and lost captures since start-up. */
  CaptureLosses losses() {
    return losses;
  }

  private void reportStale(long accountHash, Map<Kind, Integer> counts) {
    if (!counts.isEmpty()) {
      generations.staleRefused(accountHash);
    }
    report(CaptureLosses.Reason.REMOVED, counts);
  }

  private void report(CaptureLosses.Reason reason, Map<Kind, Integer> counts) {
    String line = losses.add(reason, counts);
    if (line != null) {
      lost.accept(line);
    }
  }

}
