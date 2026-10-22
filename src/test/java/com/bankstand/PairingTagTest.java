package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.PairResponse;
import com.google.gson.Gson;
import java.io.File;
import java.time.Instant;
import java.util.Collections;
import java.util.function.Supplier;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PairingTagTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final long ACCOUNT = 7L;
  private final Gson gson = new Gson();

  private static TransientEvent event(String id) {
    return new TransientEvent(
        id, TransientEvent.TYPE_NOTABLE_DROP, "2026-10-22T10:00:00Z", Collections.emptyMap());
  }

  private EventOutbox events(File dir, Supplier<String> pairing) {
    EventOutbox outbox = new EventOutbox(new File(dir, "events.json"), gson);
    outbox.setPairing(pairing);
    return outbox;
  }

  private LootOutbox loot(File dir, Supplier<String> pairing) {
    LootOutbox outbox = new LootOutbox(new File(dir, "loot.json"), gson);
    outbox.setPairing(pairing);
    return outbox;
  }

  // The server already named this account's generation, so captures queue at once.
  private static CaptureGenerations confirmed() {
    CaptureGenerations generations = new CaptureGenerations();
    generations.observe(ACCOUNT, 1);
    return generations;
  }

  @Test
  public void aFailedClearOnRepairNeverSendsTheOldQueueAfterARestart() throws Exception {
    File dir = folder.newFolder("bankstand");
    String[] pairing = {"pairing-a"};
    EventOutbox events = events(dir, () -> pairing[0]);
    LootLedger ledger = new LootLedger();
    LootOutbox loot = loot(dir, () -> pairing[0]);
    PairingQueues queues =
        new PairingQueues(
            new PresenceQueue(),
            events,
            ledger,
            loot,
            confirmed(),
            () -> pairing[0],
            new com.bankstand.session.AccountSession(),
            () -> 0L);
    events.add(ACCOUNT, event("e1"));
    ledger.record(ACCOUNT, 1, "Rat", Collections.singletonMap(526, 1), Instant.now());
    loot.addAll(ledger.closeAll());

    // The disk refuses the clear: the old entries stay in the files.
    Assume.assumeTrue(dir.setWritable(false));
    try {
      queues.replace(() -> pairing[0] = "pairing-b");
    } finally {
      dir.setWritable(true);
    }
    assertEquals("the clear really failed", 1, events(dir, () -> "pairing-a").pending().size());
    assertEquals("the clear really failed", 1, loot(dir, () -> "pairing-a").pending().size());

    // Restart under the new pairing: nothing from the old one is handed to a sender.
    assertTrue(events(dir, () -> "pairing-b").pending().isEmpty());
    assertTrue(loot(dir, () -> "pairing-b").pending().isEmpty());
    // And it is gone from disk once a write succeeds.
    assertTrue(events(dir, () -> "pairing-a").pending().isEmpty());
    assertTrue(loot(dir, () -> "pairing-a").pending().isEmpty());
  }

  @Test
  public void aLootWindowOpenedUnderTheOldPairingIsNeverSentUnderTheNewOne() throws Exception {
    File dir = folder.newFolder("bankstand");
    String[] pairing = {"pairing-a"};
    LootLedger ledger = new LootLedger();
    ledger.setPairing(() -> pairing[0]);
    ledger.record(ACCOUNT, 1, "Rat", Collections.singletonMap(526, 1), Instant.now());
    // Closed before the swap, added to the outbox after it.
    java.util.List<LootEntry> closed = ledger.closeAll();
    pairing[0] = "pairing-b";
    LootOutbox loot = loot(dir, () -> pairing[0]);
    loot.addAll(closed);
    assertTrue(loot.pending().isEmpty());
  }

  @Test
  public void entriesFromTheSamePairingAreKeptAndSent() throws Exception {
    File dir = folder.newFolder("bankstand");
    events(dir, () -> "pairing-a").add(ACCOUNT, event("e1"));
    assertEquals(1, events(dir, () -> "pairing-a").pending().size());
  }

  @Test
  public void aQueueFromBeforePairingsWereTaggedStaysWithThatPairingOnly() throws Exception {
    File dir = folder.newFolder("bankstand");
    // Both untagged: written by an older version, still paired the old way.
    events(dir, () -> null).add(ACCOUNT, event("e1"));
    assertEquals(1, events(dir, () -> null).pending().size());
    assertTrue(events(dir, () -> "pairing-b").pending().isEmpty());
  }

  @Test
  public void everyPairingGetsItsOwnLocalId() {
    PairResponse res =
        new Gson().fromJson("{\"deviceToken\":\"t\",\"deviceId\":\"d\"}", PairResponse.class);
    String first = DeviceCredentials.of(res, BankstandKeys.DEFAULT_SERVER_URL).getPairingId();
    String second = DeviceCredentials.of(res, BankstandKeys.DEFAULT_SERVER_URL).getPairingId();
    assertTrue(first != null && !first.isEmpty());
    assertNotEquals("even the same server and device id get a new one", first, second);
  }
}
