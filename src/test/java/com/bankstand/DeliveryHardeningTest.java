package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.bankstand.http.HttpResponse;
import com.google.gson.Gson;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Event delivery, credentials and queue files under failure, through the real plugin. */
public class DeliveryHardeningTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private File dir;
  private PluginHarness h;
  private CollectionLogUnlockCapture unlocks;

  @Before
  public void setUp() throws Exception {
    dir = folder.getRoot();
    h = new PluginHarness(dir);
    h.toggles.put("collectSessions", false);
    unlocks = new CollectionLogUnlockCapture(h.eventOutbox, () -> true, () -> PluginHarness.MAIN);
    h.logIn();
    h.executor.runAll();
  }

  private void unlock(String item) {
    unlocks.handleMessage("New item added to your collection log: " + item);
  }

  private void drain() {
    h.plugin.drainEventOutbox();
    h.executor.runAll();
  }

  private String noticeText() throws Exception {
    return String.join("\n", h.notices());
  }

  // A: delivery

  @Test
  public void aBatchAwaitingItsRetryIsNotSentAgainByTheNextDrain() throws Exception {
    h.transport.byPath.put("/events", body -> new HttpResponse(503, ""));
    unlock("Pet snakeling");
    drain();
    assertEquals(1, h.transport.count("/events"));
    assertEquals("its retry is scheduled", 1, h.executor.scheduled.size());
    drain();
    assertEquals("one flight per queue", 1, h.transport.count("/events"));
  }

  @Test
  public void anUncheckedThrowInARetryNeverStopsDeliveryForGood() throws Exception {
    h.transport.byPath.put("/events", body -> new HttpResponse(503, ""));
    unlock("Pet snakeling");
    String id = h.eventOutbox.pending().get(0).getEvent().getId();
    drain();
    List<String> noticesBefore = h.notices();
    h.transport.byPath.put(
        "/events",
        body -> {
          throw new IllegalStateException("bug");
        });
    for (Runnable retry : new java.util.ArrayList<>(h.executor.scheduled)) {
      h.executor.scheduled.remove(retry);
      try {
        retry.run();
      } catch (RuntimeException swallowed) {
        // A real scheduled executor keeps a task's throw in its future, where nobody reads it.
      }
    }
    assertFalse(
        "the flight is released",
        ((java.util.concurrent.atomic.AtomicBoolean) h.get("eventSending")).get());
    assertEquals("kept, not acked or dropped", 1, h.eventOutbox.pending().size());
    assertFalse(((SubmitGate) h.get("submitGate")).isHalted());
    assertEquals("no chat notice for it", noticesBefore, h.notices());
    h.transport.byPath.put(
        "/events",
        body ->
            new HttpResponse(
                200, "{\"routed\":true,\"acks\":[{\"id\":\"" + id + "\",\"outcome\":\"stored\"}]}"));
    drain();
    assertEquals(3, h.transport.count("/events"));
    assertTrue("sent and acked by a later drain", h.eventOutbox.pending().isEmpty());
  }

  @Test
  public void aFailingEventDrainShowsOnThePanelUntilTheNextGoodDrain() throws Exception {
    h.transport.byPath.put("/events", body -> new HttpResponse(503, ""));
    unlock("Pet snakeling");
    String id = h.eventOutbox.pending().get(0).getEvent().getId();
    drain();
    h.transport.byPath.put(
        "/events",
        body -> {
          throw new IllegalStateException("bug");
        });
    for (Runnable retry : new java.util.ArrayList<>(h.executor.scheduled)) {
      h.executor.scheduled.remove(retry);
      retry.run();
    }
    PanelModel failing = (PanelModel) h.call("buildPanelModel");
    assertEquals(PanelPresentation.SyncDot.AMBER, failing.dot);
    assertEquals("Unexpected error.", failing.lastFailureReason);
    h.transport.byPath.put(
        "/events",
        body ->
            new HttpResponse(
                200, "{\"routed\":true,\"acks\":[{\"id\":\"" + id + "\",\"outcome\":\"stored\"}]}"));
    drain();
    assertNull(((PanelModel) h.call("buildPanelModel")).lastFailureReason);
  }

  private static final String STORED = "{\"accepted\":true,\"stored\":true,\"storedBlocks\":[\"skills\"]}";
  private int xp = 1_000;

  private static HttpResponse acked(String body) {
    String id = new Gson().fromJson(body, com.google.gson.JsonObject.class)
        .getAsJsonArray("events").get(0).getAsJsonObject().get("id").getAsString();
    return new HttpResponse(
        200, "{\"routed\":true,\"acks\":[{\"id\":\"" + id + "\",\"outcome\":\"stored\"}]}");
  }

  private void snapshot(int status) throws Exception {
    h.toggles.put("collectQuests", false);
    h.toggles.put("collectSlayer", false);
    h.transport.byPath.put("/submit", body -> new HttpResponse(status, status == 200 ? STORED : ""));
    ((SubmitGate) h.get("submitGate")).onSuccess();
    h.xp = ++xp;
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
  }

  private PanelModel panel() throws Exception {
    return (PanelModel) h.call("buildPanelModel");
  }

  @Test
  public void aSnapshotFailureSurvivesASuccessfulDrain() throws Exception {
    snapshot(400);
    String reason = panel().lastFailureReason;
    assertNotNull(reason);
    h.transport.byPath.put("/events", DeliveryHardeningTest::acked);
    unlock("Pet snakeling");
    drain();
    assertTrue(h.eventOutbox.pending().isEmpty());
    assertEquals(PanelPresentation.SyncDot.AMBER, panel().dot);
    assertEquals(reason, panel().lastFailureReason);
  }

  @Test
  public void anEventFailureSurvivesASuccessfulSnapshot() throws Exception {
    h.transport.byPath.put("/events", body -> new HttpResponse(400, ""));
    unlock("Pet snakeling");
    drain();
    snapshot(200);
    assertEquals(PanelPresentation.SyncDot.AMBER, panel().dot);
    assertEquals("Bankstand rejected the update.", panel().lastFailureReason);
  }

  @Test
  public void aDrainWithOneFailedChunkStaysAmberAndRecoveryClearsToGreen() throws Exception {
    snapshot(200);
    new CollectionLogUnlockCapture(h.eventOutbox, () -> true, () -> 77L)
        .handleMessage("New item added to your collection log: Pet snakeling");
    unlock("Pet snakeling");
    String failing = h.eventOutbox.pending().get(0).getEvent().getId();
    h.transport.byPath.put(
        "/events", body -> body.contains(failing) ? new HttpResponse(400, "") : acked(body));
    drain();
    assertEquals(2, h.transport.count("/events"));
    assertEquals(1, h.eventOutbox.pending().size());
    assertEquals(PanelPresentation.SyncDot.AMBER, panel().dot);
    h.transport.byPath.put("/events", DeliveryHardeningTest::acked);
    drain();
    assertTrue(h.eventOutbox.pending().isEmpty());
    assertEquals(PanelPresentation.SyncDot.GREEN, panel().dot);
    assertNull(panel().lastFailureReason);
  }

  @Test
  public void aDrainFinishingAfterARePairLeavesNoStaleFailure() throws Exception {
    unlock("Pet snakeling");
    new CollectionLogUnlockCapture(h.eventOutbox, () -> true, () -> 77L)
        .handleMessage("New item added to your collection log: Pet snakeling");
    String failing = h.eventOutbox.pending().get(0).getEvent().getId();
    h.transport.byPath.put(
        "/events", body -> new HttpResponse(body.contains(failing) ? 400 : 503, ""));
    drain();
    assertEquals("the slower chunk awaits its retry", 1, h.executor.scheduled.size());
    h.transport.byPath.put(
        "/pair", body -> new HttpResponse(200, "{\"deviceToken\":\"tok-b\",\"deviceId\":\"d\"}"));
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();
    for (Runnable retry : new java.util.ArrayList<>(h.executor.scheduled)) {
      h.executor.scheduled.remove(retry);
      retry.run();
    }
    assertNull(panel().lastFailureReason);
    assertFalse(PanelPresentation.SyncDot.AMBER.equals(panel().dot));
  }

  private void switchTo(long hash) throws Exception {
    h.state(net.runelite.api.GameState.LOGIN_SCREEN);
    h.executor.runAll();
    h.hash = hash;
    h.logIn();
    h.executor.runAll();
  }

  @Test
  public void anAccountSwitchDropsThePreviousAccountsSnapshotFailure() throws Exception {
    snapshot(400);
    assertNotNull(panel().lastFailureReason);
    switchTo(77L);
    assertNull(panel().lastFailureReason);
  }

  @Test
  public void aSnapshotFailureLandingAfterASwitchIsNotTheNewAccounts() throws Exception {
    // Only A's capture fails; B's own submits succeed.
    h.transport.byPath.put(
        "/submit",
        body ->
            body.contains("\"accountHash\":\"" + PluginHarness.MAIN + "\"")
                ? new HttpResponse(503, "")
                : new HttpResponse(200, STORED));
    h.toggles.put("collectQuests", false);
    h.toggles.put("collectSlayer", false);
    ((SubmitGate) h.get("submitGate")).onSuccess();
    h.xp = ++xp;
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
    assertEquals("A's retry is pending", 1, h.executor.scheduled.size());
    switchTo(77L);
    while (!h.executor.scheduled.isEmpty()) {
      Runnable retry = h.executor.scheduled.remove(0);
      retry.run();
      h.executor.runAll();
    }
    assertNull(panel().lastFailureReason);
    h.transport.byPath.put("/submit", body -> new HttpResponse(200, STORED));
    switchTo(PluginHarness.MAIN);
    assertNull("back on A, the old login's failure stays gone", panel().lastFailureReason);
  }

  @Test
  public void aLateSuccessFromThePreviousAccountLeavesTheNewOnesFailure() throws Exception {
    int[] mainCalls = {0};
    // A fails once, then its retry succeeds; B fails until told otherwise.
    h.transport.byPath.put(
        "/submit",
        body ->
            body.contains("\"accountHash\":\"" + PluginHarness.MAIN + "\"")
                ? new HttpResponse(mainCalls[0]++ == 0 ? 503 : 200, mainCalls[0] == 1 ? "" : STORED)
                : new HttpResponse(400, ""));
    h.toggles.put("collectQuests", false);
    h.toggles.put("collectSlayer", false);
    ((SubmitGate) h.get("submitGate")).onSuccess();
    h.xp = ++xp;
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
    assertEquals("A's retry is pending", 1, h.executor.scheduled.size());
    List<Runnable> aRetry = new java.util.ArrayList<>(h.executor.scheduled);
    h.executor.scheduled.clear();
    switchTo(77L);
    ((SubmitGate) h.get("submitGate")).onSuccess();
    h.xp = ++xp;
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
    String reason = panel().lastFailureReason;
    assertNotNull("B's own failure", reason);
    h.executor.scheduled.clear();
    for (Runnable retry : aRetry) {
      retry.run();
    }
    h.executor.runAll();
    assertTrue("A succeeded late", mainCalls[0] >= 2);
    assertEquals(reason, panel().lastFailureReason);
    h.transport.byPath.put("/submit", body -> new HttpResponse(200, STORED));
    snapshot(200);
    assertNull(panel().lastFailureReason);
  }

  @Test
  public void anAuthHaltStillShowsAfterAnAccountSwitch() throws Exception {
    snapshot(401);
    String reason = panel().lastFailureReason;
    assertNotNull(reason);
    switchTo(77L);
    assertEquals(reason, panel().lastFailureReason);
  }

  @Test
  public void aSnapshotSuccessRefreshesThePanel() {
    int[] refreshes = {0};
    new SyncFailures(() -> refreshes[0]++, (hash, gen) -> true).snapshotSucceeded(0L, 0);
    assertEquals(1, refreshes[0]);
  }

  @Test
  public void aFailureWithNoMessageStillShows() {
    assertEquals(SyncFailures.FALLBACK, SyncFailures.reason(new RuntimeException()));
  }

  @Test
  public void anEventCapturedDuringAnUploadSurvivesItsSuccess() throws Exception {
    unlock("Pet snakeling");
    String first = h.eventOutbox.pending().get(0).getEvent().getId();
    h.transport.byPath.put(
        "/events",
        body ->
            new HttpResponse(
                200, "{\"routed\":true,\"acks\":[{\"id\":\"" + first + "\",\"outcome\":\"stored\"}]}"));
    h.plugin.drainEventOutbox();
    unlock("Abyssal orphan");
    h.executor.runAll();
    assertEquals(1, h.eventOutbox.pending().size());
    assertTrue(h.eventOutbox.pending().get(0).getEvent().getPayload().toString().contains("Abyssal"));
  }

  @Test
  public void aRejectedTokenStopsEventDrainsUntilPairedAgain() throws Exception {
    h.transport.byPath.put("/events", body -> new HttpResponse(401, ""));
    unlock("Pet snakeling");
    drain();
    drain();
    assertEquals(1, h.transport.count("/events"));
    assertEquals(1, h.eventOutbox.pending().size());
  }

  @Test
  public void aRejectedTokenStopsLootDrainsToo() throws Exception {
    h.transport.byPath.put("/loot", body -> new HttpResponse(401, ""));
    h.lootOutbox.addAll(java.util.Collections.singletonList(lootEntry()));
    drain();
    drain();
    assertEquals(1, h.transport.count("/loot"));
    assertEquals(1, h.lootOutbox.pending().size());
  }

  private LootEntry lootEntry() throws Exception {
    LootLedger ledger = (LootLedger) h.get("lootLedger");
    lootCapture(ledger)
        .handleKill(2, "Goblin", java.util.Collections.singletonList(new net.runelite.client.game.ItemStack(526, 1)));
    return ledger.closeAll().get(0);
  }

  private LootCapture lootCapture(LootLedger ledger) {
    return new LootCapture(
            () -> true,
            () -> PluginHarness.MAIN,
            new LootCapture.Items() {
              @Override
              public int canonical(int itemId) {
                return itemId;
              }

              @Override
              public boolean isNotable(int itemId, int quantity) {
                return false;
              }
            },
            ledger,
            h.lootOutbox,
            () -> {},
            java.time.Instant::now);
  }

  @Test
  public void aClosedClientDispatchesNoScheduledRetry() throws Exception {
    h.transport.byPath.put("/events", body -> new HttpResponse(503, ""));
    unlock("Pet snakeling");
    drain();
    ((BankstandClient) h.get("pairingClient")).close();
    new java.util.ArrayList<>(h.executor.scheduled).forEach(Runnable::run);
    assertEquals(1, h.transport.count("/events"));
  }

  private void disconnectNow() {
    try {
      h.call("disconnect");
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private void addLoot(int kills) throws Exception {
    LootLedger ledger = (LootLedger) h.get("lootLedger");
    LootCapture capture = lootCapture(ledger);
    for (int npc = 1; npc <= kills; npc++) {
      capture.handleKill(npc, "Npc " + npc, java.util.Collections.singletonList(new net.runelite.client.game.ItemStack(526, 1)));
    }
    h.lootOutbox.addAll(ledger.closeAll());
  }

  @Test
  public void aLateRejectionForAReplacedTokenNeverHaltsLoot() throws Exception {
    addLoot(1);
    h.transport.byPath.put(
        "/loot",
        body -> {
          DeviceCredentials next = h.deviceStore.load();
          DeviceCredentials replaced = DeviceCredentials.none();
          replaced.setToken("tok-new");
          replaced.setServerUrl(next.getServerUrl());
          replaced.setPairingId(next.getPairingId());
          h.deviceStore.save(replaced);
          return new HttpResponse(401, "");
        });
    drain();
    assertFalse(((SubmitGate) h.get("submitGate")).isHalted());
  }

  @Test
  public void aDisconnectBetweenEventChunksSendsNoFurtherChunk() throws Exception {
    for (int i = 0; i < 51; i++) {
      unlock("Item " + i);
    }
    h.transport.byPath.put(
        "/events",
        body -> {
          disconnectNow();
          return new HttpResponse(200, "{\"routed\":true,\"acks\":[]}");
        });
    drain();
    assertEquals(1, h.transport.count("/events"));
  }

  @Test
  public void aDisconnectBeforeAScheduledRetryStopsTheRetry() throws Exception {
    h.transport.byPath.put("/events", body -> new HttpResponse(503, ""));
    unlock("Pet snakeling");
    drain();
    disconnectNow();
    new java.util.ArrayList<>(h.executor.scheduled).forEach(Runnable::run);
    assertEquals(1, h.transport.count("/events"));
  }

  @Test
  public void aDisconnectBetweenLootChunksSendsNoFurtherChunk() throws Exception {
    addLoot(51);
    h.transport.byPath.put(
        "/loot",
        body -> {
          disconnectNow();
          return new HttpResponse(200, "{\"acks\":[]}");
        });
    drain();
    assertEquals(1, h.transport.count("/loot"));
  }

  @Test
  public void aTransientReadErrorLeavesTheQueueAndItsEventsIntact() throws Exception {
    File events = new File(dir, "events.json");
    unlock("Pet snakeling");
    assumeTrue(events.setReadable(false) && !events.canRead());
    try {
      unlock("Abyssal orphan");
      assertNotNull(h.eventOutbox.problem());
    } finally {
      events.setReadable(true);
    }
    assertFalse(new File(dir, "events.json.unreadable").exists());
    assertEquals(2, h.eventOutbox.pending().size());
    assertNull(h.eventOutbox.problem());
  }

  @Test
  public void heldEventsSurviveAFailedWriteAfterTheFileIsReadableAgain() throws Exception {
    File events = new File(dir, "events.json");
    unlock("Pet snakeling");
    assumeTrue(events.setReadable(false) && !events.canRead());
    try {
      unlock("Abyssal orphan");
    } finally {
      events.setReadable(true);
    }
    assumeTrue(dir.setWritable(false));
    try {
      h.eventOutbox.pending();
      assertEquals("still held after the failed write", 2, h.eventOutbox.pending().size());
    } finally {
      dir.setWritable(true);
    }
    assertEquals(2, h.eventOutbox.pending().size());
    EventOutbox onDisk = new EventOutbox(events, new Gson());
    onDisk.setPairing(PairingQueues.pairingOf(h.deviceStore));
    assertEquals("written once the disk allows", 2, onDisk.pending().size());
  }

  @Test
  public void afterAFailedWriteTheQueueInMemoryIsTheTruth() throws Exception {
    unlock("Pet snakeling");
    unlock("Abyssal orphan");
    String acked = h.eventOutbox.pending().get(0).getEvent().getId();
    assumeTrue(dir.setWritable(false));
    try {
      h.eventOutbox.ack(java.util.Collections.singleton(acked));
      unlock("Tiny tempor");
      List<String> ids = ids(h.eventOutbox.pending());
      assertFalse("an acked entry never comes back", ids.contains(acked));
      assertEquals(2, ids.size());
    } finally {
      dir.setWritable(true);
    }
    unlock("Smol Heredit");
    EventOutbox onDisk = new EventOutbox(new File(dir, "events.json"), new Gson());
    onDisk.setPairing(PairingQueues.pairingOf(h.deviceStore));
    assertEquals(ids(h.eventOutbox.pending()), ids(onDisk.pending()));
    assertEquals(3, onDisk.pending().size());
  }

  @Test
  public void afterAFailedWriteTheLootQueueInMemoryIsTheTruth() throws Exception {
    addLoot(2);
    String acked = h.lootOutbox.pending().get(0).getEvent().getId();
    assumeTrue(dir.setWritable(false));
    try {
      h.lootOutbox.ack(java.util.Collections.singleton(acked));
      assertEquals(1, h.lootOutbox.pending().size());
    } finally {
      dir.setWritable(true);
    }
    h.lootOutbox.pending();
    LootOutbox onDisk = new LootOutbox(new File(dir, "loot.json"), new Gson());
    onDisk.setPairing(PairingQueues.pairingOf(h.deviceStore));
    assertEquals(1, onDisk.pending().size());
  }

  private static List<String> ids(List<OutboxEntry> entries) {
    List<String> ids = new java.util.ArrayList<>();
    for (OutboxEntry entry : entries) {
      ids.add(entry.getEvent().getId());
    }
    return ids;
  }

  @Test
  public void aTransientReadErrorLeavesTheLootQueueIntact() throws Exception {
    File loot = new File(dir, "loot.json");
    addLoot(1);
    assumeTrue(loot.setReadable(false) && !loot.canRead());
    try {
      addLoot(1);
      assertNotNull(h.lootOutbox.problem());
    } finally {
      loot.setReadable(true);
    }
    assertFalse(new File(dir, "loot.json.unreadable").exists());
    assertEquals(2, h.lootOutbox.pending().size());
  }

  @Test
  public void aDisconnectStopsAScheduledSnapshotRetry() throws Exception {
    h.toggles.put("collectQuests", false);
    h.transport.byPath.put("/submit", body -> new HttpResponse(503, ""));
    long before = h.transport.count("/submit");
    h.xp = 5_000;
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
    assertEquals(before + 1, h.transport.count("/submit"));
    assertFalse("a retry is scheduled", h.executor.scheduled.isEmpty());
    disconnectNow();
    new java.util.ArrayList<>(h.executor.scheduled).forEach(Runnable::run);
    h.executor.runAll();
    assertEquals(before + 1, h.transport.count("/submit"));
  }

  @Test
  public void aSnapshotRetryAfterHoldsTheNextCapture() throws Exception {
    h.toggles.put("collectQuests", false);
    h.transport.byPath.put("/submit", body -> new HttpResponse(429, "{}", "120"));
    long before = h.transport.count("/submit");
    h.xp = 5_000;
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
    h.xp = 6_000;
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
    assertEquals(before + 1, h.transport.count("/submit"));
    SubmitGate gate = (SubmitGate) h.get("submitGate");
    long now = System.currentTimeMillis();
    assertTrue(gate.isPaused(now + 110_000L));
    assertFalse(gate.isPaused(now + 125_000L));
  }

  @Test
  public void anIdentityRetryAfterHoldsTheNextTicks() throws Exception {
    h.transport.byPath.put("/submit", body -> new HttpResponse(429, "{}", "120"));
    h.state(net.runelite.api.GameState.LOGIN_SCREEN);
    h.executor.runAll();
    h.hash = 77L;
    long before = h.transport.count("/submit");
    h.logIn();
    h.executor.runAll();
    h.ticks(5);
    h.executor.runAll();
    assertEquals(before + 1, h.transport.count("/submit"));
  }

  @Test
  public void aLostResponseIsResentWithTheSameIdAndADuplicateEmptiesTheQueue() throws Exception {
    unlock("Pet snakeling");
    String id = h.eventOutbox.pending().get(0).getEvent().getId();
    int[] calls = {0};
    h.transport.byPath.put(
        "/events",
        body ->
            ++calls[0] == 1
                ? null
                : new HttpResponse(
                    200,
                    "{\"routed\":true,\"acks\":[{\"id\":\"" + id + "\",\"outcome\":\"duplicate\"}]}"));
    drain();
    new java.util.ArrayList<>(h.executor.scheduled).forEach(Runnable::run);
    h.executor.runAll();
    assertEquals(2, h.transport.count("/events"));
    for (int i = 0; i < h.transport.urls.size(); i++) {
      if (h.transport.urls.get(i).endsWith("/events")) {
        assertTrue(h.transport.bodies.get(i).contains(id));
      }
    }
    assertTrue(h.eventOutbox.pending().isEmpty());
  }

  @Test
  public void aFailedRevokeIsReportedAlongsideTheDisconnect() throws Exception {
    h.transport.byPath.put("/unpair", body -> new HttpResponse(500, ""));
    h.call("disconnect");
    h.executor.runAll();
    String said = noticeText();
    assertTrue(said.contains("Disconnected."));
    assertTrue(said.contains("Could not reach Bankstand"));
  }

  @Test
  public void aRevokeOfAnAlreadyDeadTokenIsNotAFailure() throws Exception {
    h.transport.byPath.put("/unpair", body -> new HttpResponse(403, ""));
    h.call("disconnect");
    h.executor.runAll();
    assertFalse(noticeText().contains("Could not reach Bankstand"));
  }

  /** Both the local delete and the server revoke fail: the pairing returns after a restart. */
  @Test
  public void aRestartAfterAFailedDeleteAndAFailedRevokeIsPairedAgain() throws Exception {
    h.transport.byPath.put("/unpair", body -> new HttpResponse(500, ""));
    assumeTrue(dir.setWritable(false));
    try {
      h.call("disconnect");
      h.executor.runAll();
      assertFalse("stopped for this session", h.deviceStore.load().isPaired());
    } finally {
      dir.setWritable(true);
    }
    String said = noticeText();
    assertTrue(said.contains("returns after a restart"));
    assertTrue(said.contains("Could not reach Bankstand"));
    // The token is still live on the server and still on disk: the next start is paired again.
    assertTrue(new DeviceCredentialStore(new File(dir, "device.json"), new Gson()).load().isPaired());
  }

  // B: credentials

  /** Makes {@code device.json} a non-empty directory: no save, delete or overwrite can succeed. */
  private void breakCredentialFile() throws Exception {
    File device = new File(dir, "device.json");
    assertTrue(device.delete());
    assertTrue(new File(device, "x").mkdirs());
  }

  @Test
  public void aPairingThatCannotBeSavedIsNotReportedAsConnected() throws Exception {
    h.transport.byPath.put(
        "/pair", body -> new HttpResponse(200, "{\"deviceToken\":\"tok-new\",\"deviceId\":\"d\"}"));
    breakCredentialFile();
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();
    assertEquals("the previous pairing stays in use", "device-token", h.deviceStore.load().getToken());
    assertFalse(noticeText().contains("Connected"));
    assertTrue(noticeText().contains("could not be saved"));
    assertEquals("only the unused new token is revoked", 1, h.transport.count("/unpair"));
  }

  @Test
  public void aPairingThatCannotBeSavedKeepsTheOldPairingsQueues() throws Exception {
    h.transport.byPath.put(
        "/pair", body -> new HttpResponse(200, "{\"deviceToken\":\"tok-new\",\"deviceId\":\"d\"}"));
    unlock("Pet snakeling");
    addLoot(1);
    breakCredentialFile();
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();
    assertEquals(1, h.eventOutbox.pending().size());
    assertEquals(1, h.lootOutbox.pending().size());
  }

  @Test
  public void disconnectStopsSendingAtOnceEvenWhenTheFileCannotBeRemoved() throws Exception {
    File device = new File(dir, "device.json");
    assumeTrue(dir.setWritable(false));
    try {
      unlock("Pet snakeling");
      h.call("disconnect");
      assertFalse(h.deviceStore.load().isPaired());
      drain();
      assertEquals(0, h.transport.count("/events"));
      assertTrue(noticeText().contains("could not be removed"));
      // Restart: the file still holds the token, which the revoke has already killed server-side.
      assertTrue(new DeviceCredentialStore(device, new Gson()).load().isPaired());
    } finally {
      dir.setWritable(true);
    }
  }

  // D: queue files

  @Test
  public void anUnreadableQueueIsSetAsideAndReported() throws Exception {
    File events = new File(dir, "events.json");
    Files.write(events.toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
    assertTrue(h.eventOutbox.pending().isEmpty());
    assertNotNull(h.eventOutbox.problem());
    assertTrue("kept for inspection, never overwritten", new File(dir, "events.json.unreadable").exists());
    unlock("Pet snakeling");
    assertEquals(1, h.eventOutbox.pending().size());
  }

  @Test
  public void aParseableButMalformedQueueIsSetAside() throws Exception {
    File events = new File(dir, "events.json");
    for (String body : new String[] {"[null]", "[{}]", "[{\"accountHash\":1,\"event\":{}}]"}) {
      Files.write(events.toPath(), body.getBytes(StandardCharsets.UTF_8));
      assertTrue(body, h.eventOutbox.pending().isEmpty());
      assertNotNull(body, h.eventOutbox.problem());
    }
    File loot = new File(dir, "loot.json");
    Files.write(loot.toPath(), "[null, {}]".getBytes(StandardCharsets.UTF_8));
    assertTrue(h.lootOutbox.pending().isEmpty());
    assertNotNull(h.lootOutbox.problem());
  }

  @Test
  public void anUnwritableQueueIsReportedAndRecoversWhenWritable() throws Exception {
    assumeTrue(dir.setWritable(false));
    try {
      unlock("Pet snakeling");
      assertNotNull(h.eventOutbox.problem());
    } finally {
      dir.setWritable(true);
    }
    unlock("Abyssal orphan");
    assertNull(h.eventOutbox.problem());
  }

  @Test
  public void anOverflowIsCountedForTheStatusReport() throws Exception {
    for (int i = 0; i < EventOutbox.MAX_PENDING + 3; i++) {
      unlock("Item " + i);
    }
    assertEquals(3, h.eventOutbox.dropped());
  }

  @Test
  public void clientThreadSaveCostAtAFullQueue() throws Exception {
    for (int i = 0; i < EventOutbox.MAX_PENDING; i++) {
      unlock("Item " + i);
    }
    int runs = 50;
    long start = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      unlock("Timed " + i);
    }
    long micros = (System.nanoTime() - start) / 1000 / runs;
    System.out.println("event outbox add at " + EventOutbox.MAX_PENDING + " pending: " + micros + " us");
    assertTrue(micros < 1_000_000);
  }
}
