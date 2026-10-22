package com.bankstand;

import com.bankstand.dto.PresenceResponse;
import com.bankstand.dto.SubmitEventsResponse;
import com.bankstand.dto.SubmitLootResponse;
import com.bankstand.dto.SubmitResponse;
import com.bankstand.session.AccountSession;
import com.google.gson.Gson;
import java.io.File;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The plugin's capture queues and boundary wired as the plugin wires them, against a {@link
 * FakeServer}, with a hand-run executor and clock. Sends mirror the plugin's drains.
 */
final class BoundaryRig {
  static final String URL = "https://x.test";

  final AtomicLong clock = new AtomicLong(1_000_000L);
  final Deque<Runnable> executor = new ArrayDeque<>();
  final FakeServer server = new FakeServer();
  final AccountSession session = new AccountSession();
  final AckedStateStore store;
  final CaptureGenerations generations = new CaptureGenerations();
  final EventOutbox events;
  final LootLedger ledger = new LootLedger();
  final LootOutbox loot;
  final PresenceQueue presence = new PresenceQueue();
  final PairingQueues queues;
  final CaptureBoundary boundary;
  final BankstandClient api;
  final List<String> lost = new ArrayList<>();
  final List<String> notices = new ArrayList<>();
  String pairing;
  String token = "tok-1";

  BoundaryRig(File dir) {
    this(dir, "pairing-1");
  }

  BoundaryRig(File dir, String pairing) {
    this.pairing = pairing;
    store = new AckedStateStore(new File(dir, "acked.json"), new Gson());
    generations.persistTo(store, Runnable::run, () -> this.pairing);
    generations.onStaleRefusal(notices::add, () -> true);
    events = new EventOutbox(new File(dir, "events.json"), new Gson());
    loot = new LootOutbox(new File(dir, "loot.json"), new Gson());
    queues =
        new PairingQueues(
            presence, events, ledger, loot, generations, () -> this.pairing, session, clock::get);
    boundary = queues.boundary();
    boundary.onLost(lost::add);
    api =
        new BankstandClient(
            server,
            new Gson(),
            Executors.newSingleThreadScheduledExecutor(),
            () -> Instant.parse("2026-10-20T10:00:00Z"));
  }

  /** A restart: a fresh rig over the same files. */
  static BoundaryRig restart(File dir, BoundaryRig before) {
    BoundaryRig after = new BoundaryRig(dir, before.pairing);
    after.server.generation.putAll(before.server.generation);
    after.token = before.token;
    return after;
  }

  void login(long account) {
    boundary.startLogin(account);
  }

  void logout() {
    boundary.endLogin();
  }

  /** What the plugin's tick does: decides to ask now, sends when the executor runs. */
  CompletableFuture<SubmitResponse>[] askIdentity(long account) {
    @SuppressWarnings("unchecked")
    CompletableFuture<SubmitResponse>[] out = new CompletableFuture[1];
    CaptureBoundary.Ticket asked = boundary.ticket(account);
    String tok = token;
    executor.add(
        () ->
            out[0] =
                boundary.discover(
                    asked, () -> api.submitIdentityWithRetry(URL, tok, account, "Name", 1, 1)));
    return out;
  }

  void runExecutor() {
    while (!executor.isEmpty()) {
      executor.poll().run();
    }
  }

  void event(long account, String id) {
    events.add(
        account,
        new TransientEvent(id, TransientEvent.TYPE_PET_DROP, "2026-10-20T10:00:00Z", new LinkedHashMap<>()));
  }

  void kill(long account, int npc) {
    loot.addAll(
        ledger.record(
            account, npc, "Rat", Collections.singletonMap(526, 1), Instant.ofEpochMilli(clock.get())));
  }

  void presence(long account, String state) {
    presence.addTransition(new PresenceSignal(state, account, 0L));
  }

  /** The event drain: one request per account and stamp. */
  void sendEvents() throws Exception {
    Map<SendGroup, List<TransientEvent>> groups =
        SendGroup.of(
            events.pending(),
            OutboxEntry::getAccountHash,
            OutboxEntry::getCaptureGeneration,
            OutboxEntry::getEvent);
    for (Map.Entry<SendGroup, List<TransientEvent>> group : groups.entrySet()) {
      long account = group.getKey().accountHash;
      Integer stamp = group.getKey().captureGeneration;
      CaptureBoundary.Ticket asked = boundary.ticket(account);
      SubmitEventsResponse res = api.submitEvents(URL, token, account, group.getValue(), stamp);
      boundary.acked(
          asked,
          CaptureBoundary.Kind.EVENT,
          res.getCaptureGeneration(),
          stamp,
          EventOutbox.countStaleGeneration(res.getAcks()));
      events.ack(EventOutbox.idsToAck(res.getAcks()));
    }
  }

  void sendLoot() throws Exception {
    loot.addAll(ledger.closeAll());
    Map<SendGroup, List<LootEvent>> groups =
        SendGroup.of(
            loot.pending(),
            LootEntry::getAccountHash,
            LootEntry::getCaptureGeneration,
            LootEntry::getEvent);
    for (Map.Entry<SendGroup, List<LootEvent>> group : groups.entrySet()) {
      long account = group.getKey().accountHash;
      Integer stamp = group.getKey().captureGeneration;
      CaptureBoundary.Ticket asked = boundary.ticket(account);
      SubmitLootResponse res = api.submitLoot(URL, token, account, group.getValue(), stamp);
      boundary.acked(
          asked,
          CaptureBoundary.Kind.LOOT,
          res.getCaptureGeneration(),
          stamp,
          EventOutbox.countStaleGeneration(res.getAcks()));
      loot.ack(LootOutbox.idsToAck(res.getAcks()));
    }
  }

  void sendPresence() throws Exception {
    PresenceSignal next;
    while ((next = presence.peek()) != null) {
      CaptureBoundary.Ticket asked = boundary.ticket(next.getAccountHash());
      PresenceResponse res =
          api.submitPresence(
              URL, token, next.getAccountHash(), next.getState(), 0L, null, next.getCaptureGeneration());
      presence.onAnswered(next);
      boundary.acked(
          asked,
          CaptureBoundary.Kind.PRESENCE,
          res.getCaptureGeneration(),
          next.getCaptureGeneration(),
          EventOutbox.STALE_GENERATION.equals(res.getReason()) ? 1 : 0);
    }
  }

  void sendAll() throws Exception {
    sendEvents();
    sendLoot();
    sendPresence();
  }

  /** What a successful pairing does. */
  PairingQueues.Dropped repair(String newPairing, Map<Long, Integer> learned) {
    return queues.replace(
        () -> {
          pairing = newPairing;
          token = "tok-" + newPairing;
        },
        learned);
  }

  List<Integer> eventStamps() {
    List<Integer> stamps = new ArrayList<>();
    for (OutboxEntry entry : events.pending()) {
      stamps.add(entry.getCaptureGeneration());
    }
    return stamps;
  }
}
