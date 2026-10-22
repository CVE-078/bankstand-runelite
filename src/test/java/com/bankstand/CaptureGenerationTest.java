package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.EventAck;
import com.bankstand.dto.SubmitEventsResponse;
import com.bankstand.http.HttpResponse;
import com.bankstand.http.HttpTransport;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CaptureGenerationTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private File dir;
  private AckedStateStore store;
  private CaptureGenerations generations;

  @Before
  public void setUp() throws Exception {
    dir = folder.newFolder("bankstand");
    store = new AckedStateStore(new File(dir, "acked.json"), new Gson());
    generations = new CaptureGenerations();
    generations.persistTo(store, Runnable::run);
  }

  private static TransientEvent event(String id) {
    return new TransientEvent(
        id, TransientEvent.TYPE_PET_DROP, "2026-10-20T10:00:00Z", new LinkedHashMap<>());
  }

  private static LootEntry loot(String id) {
    return new LootEntry(
        1L,
        new LootEvent(
            id,
            1,
            "Rat",
            1,
            "2026-10-20T10:00:00Z",
            "2026-10-20T10:00:00Z",
            Collections.singletonList(new LootEvent.Item(526, 1))));
  }

  private static List<EventAck> acks(String json) {
    return new Gson().fromJson(json, SubmitEventsResponse.class).getAcks();
  }

  @Test
  public void anAckedGenerationIsRememberedAndPersisted() {
    assertNull(generations.get(1L));
    assertTrue(generations.observe(1L, 2));
    assertFalse(generations.observe(1L, 2));
    assertFalse(generations.observe(1L, null));
    assertEquals(Integer.valueOf(2), generations.get(1L));
    assertEquals(Integer.valueOf(2), store.load(1L).getCaptureGeneration());
    assertNull(generations.get(2L));
  }

  @Test
  public void restoringNeverOverwritesAFresherAck() {
    generations.observe(1L, 3);
    generations.restore(1L, 2);
    generations.restore(2L, 5);
    assertEquals(Integer.valueOf(3), generations.get(1L));
    assertEquals(Integer.valueOf(5), generations.get(2L));
  }

  @Test
  public void aFullStateSaveKeepsTheStoredGeneration() {
    generations.observe(1L, 4);
    AckedState state = AckedState.empty();
    state.setSkills("digest");
    store.save(1L, state);
    assertEquals(Integer.valueOf(4), store.load(1L).getCaptureGeneration());
    assertEquals("digest", store.load(1L).getSkills());
  }

  @Test
  public void eachCaptureIsStampedWhenQueuedNotWhenSent() {
    File events = new File(dir, "events.json");
    EventOutbox outbox = new EventOutbox(events, new Gson());
    outbox.setCaptureGenerations(generations);
    outbox.add(1L, event("unstamped"));
    generations.observe(1L, 1);
    outbox.add(1L, event("first"));
    generations.observe(1L, 2);
    outbox.add(1L, event("second"));

    List<OutboxEntry> pending = new EventOutbox(events, new Gson()).pending();
    assertNull(pending.get(0).getCaptureGeneration());
    assertEquals(Integer.valueOf(1), pending.get(1).getCaptureGeneration());
    assertEquals(Integer.valueOf(2), pending.get(2).getCaptureGeneration());
  }

  @Test
  public void anOutboxWrittenByAnOlderBuildReadsAsUnstamped() throws Exception {
    File events = new File(dir, "events.json");
    Files.write(
        events.toPath(),
        ("[{\"accountHash\":1,\"event\":{\"id\":\"a\",\"type\":\"pet_drop\","
                + "\"occurredAt\":\"2026-10-20T10:00:00Z\",\"payload\":{}}}]")
            .getBytes(StandardCharsets.UTF_8));
    assertNull(new EventOutbox(events, new Gson()).pending().get(0).getCaptureGeneration());
  }

  @Test
  public void lootIsStampedWhenTheKillIsRecordedNotWhenTheWindowCloses() {
    LootLedger ledger = new LootLedger();
    ledger.setCaptureGenerations(generations);
    LootOutbox outbox = new LootOutbox(new File(dir, "loot.json"), new Gson());
    Map<Integer, Integer> bones = Collections.singletonMap(526, 1);
    Instant at = Instant.parse("2026-10-20T10:00:00Z");
    generations.observe(1L, 4);
    ledger.record(1L, 1, "Rat", bones, at);
    // A removal is acked while the window is still open.
    generations.observe(1L, 5);
    outbox.addAll(ledger.record(1L, 1, "Rat", bones, at.plusSeconds(5)));
    outbox.addAll(ledger.closeAll());

    List<LootEntry> pending = new LootOutbox(new File(dir, "loot.json"), new Gson()).pending();
    assertEquals(2, pending.size());
    assertEquals(Integer.valueOf(4), pending.get(0).getCaptureGeneration());
    assertEquals(1, pending.get(0).getEvent().getKills());
    assertEquals(Integer.valueOf(5), pending.get(1).getCaptureGeneration());
  }

  @Test
  public void presenceIsStampedWhenQueued() {
    generations.observe(1L, 7);
    PresenceQueue queue = new PresenceQueue();
    queue.setCaptureGenerations(generations);
    queue.addTransition(new PresenceSignal(PresenceSignal.LOGIN, 1L, 0L));
    assertEquals(Integer.valueOf(7), queue.peek().getCaptureGeneration());
    queue.onAnswered(queue.peek());
    assertEquals(0, queue.size());
  }

  @Test
  public void aStaleGenerationRejectionIsDroppedNotRetried() {
    String json =
        "{\"acks\":[{\"id\":\"a\",\"outcome\":\"rejected\",\"reason\":\"stale_generation\"},"
            + "{\"id\":\"b\",\"outcome\":\"rejected\",\"reason\":\"not_applied\"}]}";
    assertEquals(Collections.singleton("a"), EventOutbox.idsToAck(acks(json)));
    assertEquals(Collections.singleton("a"), LootOutbox.idsToAck(acks(json)));
  }

  @Test
  public void entriesQueuedUnderDifferentGenerationsAreSentApart() {
    List<OutboxEntry> entries =
        Arrays.asList(
            new OutboxEntry(1L, event("a"), 1),
            new OutboxEntry(1L, event("b"), 2),
            new OutboxEntry(1L, event("c"), 1),
            new OutboxEntry(2L, event("d"), 1));
    Map<SendGroup, List<TransientEvent>> groups =
        SendGroup.of(
            entries,
            OutboxEntry::getAccountHash,
            OutboxEntry::getCaptureGeneration,
            OutboxEntry::getEvent);
    assertEquals(3, groups.size());
    List<TransientEvent> first = groups.get(new SendGroup(1L, 1));
    assertEquals("a", first.get(0).getId());
    assertEquals("c", first.get(1).getId());
    assertEquals(1, groups.get(new SendGroup(1L, 2)).size());
    assertEquals(1, groups.get(new SendGroup(2L, 1)).size());
  }

  @Test
  public void disconnectingDiscardsEverythingQueuedAndSurvivesARestart() {
    File events = new File(dir, "events.json");
    File lootFile = new File(dir, "loot.json");
    EventOutbox eventOutbox = new EventOutbox(events, new Gson());
    LootOutbox lootOutbox = new LootOutbox(lootFile, new Gson());
    generations.observe(1L, 3);
    eventOutbox.add(1L, event("a"));
    lootOutbox.addAll(Collections.singletonList(loot("b")));

    // What disconnect does.
    eventOutbox.clear();
    lootOutbox.clear();
    generations.forget();

    assertTrue(new EventOutbox(events, new Gson()).pending().isEmpty());
    assertTrue(new LootOutbox(lootFile, new Gson()).pending().isEmpty());
    assertNull(generations.get(1L));
    assertNull(store.load(1L).getCaptureGeneration());
  }

  /** Captures the last request body. */
  private static final class Capture implements HttpTransport {
    String body;
    final String response;

    Capture(String response) {
      this.response = response;
    }

    @Override
    public HttpResponse post(String url, String jsonBody, Map<String, String> headers) {
      body = jsonBody;
      return new HttpResponse(200, response);
    }

    @Override
    public HttpResponse get(String url, Map<String, String> headers) {
      return new HttpResponse(200, response);
    }
  }

  private static BankstandClient client(Capture capture) {
    return new BankstandClient(
        capture,
        new Gson(),
        Executors.newSingleThreadScheduledExecutor(),
        () -> Instant.parse("2026-10-20T10:00:00Z"));
  }

  private static JsonObject body(Capture capture) {
    return new JsonParser().parse(capture.body).getAsJsonObject();
  }

  @Test
  public void requestsCarryTheStampOnlyWhenOneIsKnown() throws Exception {
    Capture capture = new Capture("{\"routed\":true,\"acks\":[],\"captureGeneration\":2}");
    List<TransientEvent> one = Collections.singletonList(event("a"));

    SubmitEventsResponse res = client(capture).submitEvents("https://x.test", "tok", 1L, one, 2);
    assertEquals(2, body(capture).get("captureGeneration").getAsInt());
    assertEquals(Integer.valueOf(2), res.getCaptureGeneration());

    client(capture).submitEvents("https://x.test", "tok", 1L, one, null);
    assertFalse(body(capture).has("captureGeneration"));

    client(capture).submitLoot("https://x.test", "tok", 1L, Collections.emptyList(), 3);
    assertEquals(3, body(capture).get("captureGeneration").getAsInt());

    client(capture).submitPresence("https://x.test", "tok", 1L, "login", 0L, null, 4);
    assertEquals(4, body(capture).get("captureGeneration").getAsInt());
    client(capture).submitPresence("https://x.test", "tok", 1L, "login", 0L, null, null);
    assertFalse(body(capture).has("captureGeneration"));
  }

  @Test
  public void pairingDeclaresGenerationStamping() throws Exception {
    Capture capture =
        new Capture(
            "{\"deviceToken\":\"bsd_t\",\"deviceId\":\"d\",\"expiresAt\":\"2027-01-01T00:00:00Z\"}");
    client(capture).exchangePairingCode("https://x.test", "ABCD2345");
    assertEquals(
        "capture_generation", body(capture).getAsJsonArray("capabilities").get(0).getAsString());
  }

  @Test
  public void queuedCapturesKeepTheirStampAcrossARestartAndAreDroppedWhenRefused() {
    File events = new File(dir, "events.json");
    File lootFile = new File(dir, "loot.json");
    EventOutbox outbox = new EventOutbox(events, new Gson());
    outbox.setCaptureGenerations(generations);
    outbox.add(1L, event("unstamped"));
    generations.observe(1L, 1);
    outbox.add(1L, event("old"));
    LootOutbox lootOutbox = new LootOutbox(lootFile, new Gson());
    lootOutbox.addAll(
        Collections.singletonList(new LootEntry(1L, loot("l").getEvent(), 1, null)));

    // Restart: a fresh run knows the newer generation the removal produced.
    CaptureGenerations restarted = new CaptureGenerations();
    restarted.restore(1L, 2);
    EventOutbox reloaded = new EventOutbox(events, new Gson());
    reloaded.setCaptureGenerations(restarted);
    reloaded.add(1L, event("new"));
    Map<SendGroup, List<TransientEvent>> groups =
        SendGroup.of(
            reloaded.pending(),
            OutboxEntry::getAccountHash,
            OutboxEntry::getCaptureGeneration,
            OutboxEntry::getEvent);
    assertEquals("unstamped", groups.get(new SendGroup(1L, null)).get(0).getId());
    assertEquals("old", groups.get(new SendGroup(1L, 1)).get(0).getId());
    assertEquals("new", groups.get(new SendGroup(1L, 2)).get(0).getId());
    LootOutbox reloadedLoot = new LootOutbox(lootFile, new Gson());
    assertEquals(Integer.valueOf(1), reloadedLoot.pending().get(0).getCaptureGeneration());

    String refused =
        "{\"acks\":[{\"id\":\"unstamped\",\"outcome\":\"rejected\",\"reason\":\"stale_generation\"},"
            + "{\"id\":\"old\",\"outcome\":\"rejected\",\"reason\":\"stale_generation\"},"
            + "{\"id\":\"new\",\"outcome\":\"stored\"},"
            + "{\"id\":\"l\",\"outcome\":\"rejected\",\"reason\":\"stale_generation\"}]}";
    reloaded.ack(EventOutbox.idsToAck(acks(refused)));
    reloadedLoot.ack(LootOutbox.idsToAck(acks(refused)));
    assertTrue(new EventOutbox(events, new Gson()).pending().isEmpty());
    assertTrue(new LootOutbox(lootFile, new Gson()).pending().isEmpty());
    assertTrue(EventOutbox.anyStaleGeneration(acks(refused)));
    assertFalse(
        EventOutbox.anyStaleGeneration(acks("{\"acks\":[{\"id\":\"a\",\"outcome\":\"stored\"}]}")));
  }

  @Test
  public void aStaleRefusalIsAnnouncedOncePerRemoval() {
    assertFalse(generations.firstStaleRefusal(1L));
    generations.observe(1L, 2);
    assertTrue(generations.firstStaleRefusal(1L));
    assertFalse(generations.firstStaleRefusal(1L));
    generations.observe(1L, 3);
    assertTrue(generations.firstStaleRefusal(1L));
    assertFalse(generations.firstStaleRefusal(2L));

    List<String> notices = new java.util.ArrayList<>();
    CaptureGenerations fresh = new CaptureGenerations();
    fresh.onStaleRefusal(notices::add);
    fresh.observe(1L, 2, "stale_generation");
    fresh.observe(1L, 2, acks("{\"acks\":[{\"id\":\"a\",\"outcome\":\"rejected\",\"reason\":\"stale_generation\"}]}"));
    fresh.observe(1L, 2, "not_applied");
    assertEquals(1, notices.size());
    fresh.observe(1L, 3, "stale_generation");
    assertEquals(2, notices.size());
  }

  @Test
  public void aSnapshotIsStampedAsItIsBuilt() {
    Map<String, Object> envelope = new LinkedHashMap<>();
    generations.stamp(envelope, 1L);
    assertFalse(envelope.containsKey("captureGeneration"));
    generations.observe(1L, 5);
    generations.stamp(envelope, 1L);
    assertEquals(5, envelope.get("captureGeneration"));
  }
}
