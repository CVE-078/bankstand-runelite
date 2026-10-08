package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.EventAck;
import com.google.gson.Gson;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LootOutboxTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static LootEntry entry(String id) {
    return new LootEntry(
        1L,
        new LootEvent(
            id,
            1,
            "Rat",
            1,
            "2026-10-08T12:00:00Z",
            "2026-10-08T12:00:00Z",
            Collections.singletonList(new LootEvent.Item(526, 1))));
  }

  private static List<EventAck> acks(String json) {
    return new Gson().fromJson(json, com.bankstand.dto.SubmitLootResponse.class).getAcks();
  }

  @Test
  public void survivesARestart() throws Exception {
    File file = new File(folder.newFolder("bankstand"), "loot-outbox.json");
    new LootOutbox(file, new Gson()).addAll(Collections.singletonList(entry("a")));
    List<LootEntry> reloaded = new LootOutbox(file, new Gson()).pending();
    assertEquals(1, reloaded.size());
    assertEquals("a", reloaded.get(0).getEvent().getId());
    assertEquals(526, reloaded.get(0).getEvent().getItems().get(0).getId());
  }

  @Test
  public void evictsOnlyItsOwnOldestRecordsPastTheCap() throws Exception {
    File file = new File(folder.newFolder("bankstand"), "loot-outbox.json");
    LootOutbox outbox = new LootOutbox(file, new Gson());
    List<LootEntry> many = new ArrayList<>();
    for (int i = 0; i < LootOutbox.MAX_PENDING + 3; i++) {
      many.add(entry("e" + i));
    }
    outbox.addAll(many);
    List<LootEntry> pending = outbox.pending();
    assertEquals(LootOutbox.MAX_PENDING, pending.size());
    assertEquals("e3", pending.get(0).getEvent().getId());
  }

  /** Loot can never push a pet or an achievement out of the event outbox. */
  @Test
  public void aFullLootOutboxLeavesTheEventOutboxUntouched() throws Exception {
    File dir = folder.newFolder("bankstand");
    EventOutbox events = new EventOutbox(new File(dir, "events.json"), new Gson());
    events.add(1L, new TransientEvent(TransientEvent.TYPE_PET_DROP, Collections.emptyMap()));
    LootOutbox loot = new LootOutbox(new File(dir, "loot-outbox.json"), new Gson());
    List<LootEntry> many = new ArrayList<>();
    for (int i = 0; i < LootOutbox.MAX_PENDING * 2; i++) {
      many.add(entry("e" + i));
    }
    loot.addAll(many);

    assertEquals(1, events.pending().size());
    assertEquals(TransientEvent.TYPE_PET_DROP, events.pending().get(0).getEvent().getType());
  }

  @Test
  public void acksRemoveExactlyTheNamedRecords() throws Exception {
    File file = new File(folder.newFolder("bankstand"), "loot-outbox.json");
    LootOutbox outbox = new LootOutbox(file, new Gson());
    outbox.addAll(java.util.Arrays.asList(entry("a"), entry("b"), entry("c")));
    outbox.ack(Collections.singleton("b"));
    assertEquals(2, outbox.pending().size());
    assertEquals("c", outbox.pending().get(1).getEvent().getId());
  }

  @Test
  public void storedAndTerminalRejectionsAreAckedEverythingElseStays() {
    Set<String> ids =
        LootOutbox.idsToAck(
            acks(
                "{\"acks\":["
                    + "{\"id\":\"stored\",\"outcome\":\"stored\"},"
                    + "{\"id\":\"dup\",\"outcome\":\"duplicate\"},"
                    + "{\"id\":\"invalid\",\"outcome\":\"rejected\",\"reason\":\"invalid\"},"
                    + "{\"id\":\"quota\",\"outcome\":\"rejected\",\"reason\":\"quota\"},"
                    + "{\"id\":\"stale\",\"outcome\":\"rejected\",\"reason\":\"stale\"},"
                    + "{\"id\":\"off\",\"outcome\":\"rejected\",\"reason\":\"not_applied\"},"
                    + "{\"id\":\"unknown\",\"outcome\":\"rejected\",\"reason\":\"something_new\"}"
                    + "]}"));
    assertEquals(5, ids.size());
    assertTrue(ids.contains("stored"));
    assertTrue(ids.contains("dup"));
    assertTrue(ids.contains("invalid"));
    assertTrue(ids.contains("quota"));
    assertTrue(ids.contains("stale"));
  }
}
