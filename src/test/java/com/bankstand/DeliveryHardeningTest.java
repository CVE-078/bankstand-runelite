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
    new LootCapture(
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
            java.time.Instant::now)
        .handleKill(2, "Goblin", java.util.Collections.singletonList(new net.runelite.client.game.ItemStack(526, 1)));
    return ledger.closeAll().get(0);
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
