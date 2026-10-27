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
