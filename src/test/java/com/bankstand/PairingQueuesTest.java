package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PairingQueuesTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final long ACCOUNT = 7L;

  @Test
  public void dropLeavesNothingFromTheOldPairingToSendUnderTheNextOne() throws IOException {
    File dir = folder.newFolder("bankstand");
    Gson gson = new Gson();
    CaptureGenerations generations = new CaptureGenerations();
    generations.observe(ACCOUNT, 3);
    PresenceQueue presence = new PresenceQueue();
    presence.addTransition(new PresenceSignal(PresenceSignal.LOGIN, ACCOUNT, 1L));
    EventOutbox events = new EventOutbox(new File(dir, "events.json"), gson);
    events.add(
        ACCOUNT,
        new TransientEvent(
            "e1", TransientEvent.TYPE_NOTABLE_DROP, "2026-10-22T10:00:00Z", Collections.emptyMap()));
    LootLedger ledger = new LootLedger();
    ledger.setCaptureGenerations(generations);
    ledger.record(ACCOUNT, 1, "Rat", Collections.singletonMap(526, 1), Instant.now());
    LootOutbox loot = new LootOutbox(new File(dir, "loot.json"), gson);
    loot.addAll(ledger.closeAll());
    ledger.record(ACCOUNT, 2, "Goblin", Collections.singletonMap(526, 1), Instant.now());
    assertEquals(1, loot.pending().size());

    PairingQueues queues =
        new PairingQueues(presence, events, ledger, loot, generations, () -> null);
    boolean[] droppedFirst = {false};
    PairingQueues.Dropped dropped =
        queues.replace(() -> droppedFirst[0] = events.pending().isEmpty());
    assertEquals(1, dropped.events);
    assertEquals("one queued and one still open", 2, dropped.loot);
    assertEquals(
        "Re-paired: 1 queued event and 2 loot drops from the previous pairing were discarded.",
        dropped.repairNotice());
    assertTrue("the queues are dropped before the pairing is swapped", droppedFirst[0]);

    assertEquals(0, presence.size());
    assertTrue(events.pending().isEmpty());
    assertTrue("an open loot window is not closed into the next pairing", ledger.closeAll().isEmpty());
    assertTrue(loot.pending().isEmpty());
    assertNull(generations.get(ACCOUNT));
    // On disk too, so a restart does not bring them back.
    assertTrue(new EventOutbox(new File(dir, "events.json"), gson).pending().isEmpty());
    assertTrue(new LootOutbox(new File(dir, "loot.json"), gson).pending().isEmpty());
  }

  // The plugin needs a live client to run, so its re-pairing is pinned on the source.
  @Test
  public void aNewPairingDropsTheOldQueueAndRevokesTheOldTokenWhereItWasPaired()
      throws IOException {
    String source =
        new String(
            Files.readAllBytes(
                Paths.get("src", "main", "java", "com", "bankstand", "BankstandPlugin.java")),
            StandardCharsets.UTF_8);
    int start = source.indexOf("private void storeToken(");
    String body = source.substring(start, source.indexOf("\n  }\n", start));

    int previous = body.indexOf("DeviceCredentials previous = deviceStore.load();");
    int replace =
        body.indexOf(
            "pairingQueues.replace(() -> deviceStore.save(DeviceCredentials.of(res, url)));");
    int revoke = body.indexOf("previous.pairedServerUrl(), previous.getToken()");
    assertTrue("reads the old pairing before replacing it", previous >= 0 && previous < replace);
    assertTrue("revokes the old token at the server it was paired with", revoke > replace);
  }

  @Test
  public void aSendHoldingTheLockKeepsThePairingFromChangingUnderIt() throws Exception {
    PairingQueues queues =
        new PairingQueues(
            new PresenceQueue(),
            new EventOutbox(new File(folder.newFolder("q"), "events.json"), new Gson()),
            new LootLedger(),
            new LootOutbox(new File(folder.getRoot(), "q/loot.json"), new Gson()),
            new CaptureGenerations(),
            () -> null);
    java.util.concurrent.CountDownLatch swapped = new java.util.concurrent.CountDownLatch(1);
    Thread pairing;
    synchronized (queues) {
      pairing = new Thread(() -> queues.replace(swapped::countDown));
      pairing.start();
      assertTrue(
          "the swap waits for the send's read",
          !swapped.await(200, java.util.concurrent.TimeUnit.MILLISECONDS));
    }
    assertTrue(swapped.await(5, java.util.concurrent.TimeUnit.SECONDS));
    pairing.join();
  }

  @Test
  public void theRepairNoticeCountsWhatWasDroppedAndIsSilentWhenNothingWas() {
    assertNull(new PairingQueues.Dropped(0, 0).repairNotice());
    assertEquals(
        "Re-paired: 12 queued events and 3 loot drops from the previous pairing were discarded.",
        new PairingQueues.Dropped(12, 3).repairNotice());
    assertEquals(
        "Re-paired: 1 loot drop from the previous pairing was discarded.",
        new PairingQueues.Dropped(0, 1).repairNotice());
    assertEquals(
        "Re-paired: 2 queued events from the previous pairing were discarded.",
        new PairingQueues.Dropped(2, 0).repairNotice());
  }

  @Test
  public void aNewPairingTellsThePlayerWhatItDiscarded() throws IOException {
    String source =
        new String(
            Files.readAllBytes(
                Paths.get("src", "main", "java", "com", "bankstand", "BankstandPlugin.java")),
            StandardCharsets.UTF_8);
    int start = source.indexOf("private void storeToken(");
    String body = source.substring(start, source.indexOf("\n  }\n", start));
    assertTrue(body.contains(".repairNotice();"));
    assertTrue(body.contains("notice(discarded);"));
  }
}
