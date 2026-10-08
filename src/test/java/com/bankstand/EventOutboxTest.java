package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EventOutboxTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private EventOutbox outboxIn(File file) {
    return new EventOutbox(file, new Gson());
  }

  private File newFile() throws IOException {
    return new File(folder.newFolder("bankstand"), "events.json");
  }

  private TransientEvent event(String id) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("itemName", "Dragon warhammer");
    return new TransientEvent(id, TransientEvent.TYPE_NOTABLE_DROP, "2026-08-10T10:00:00Z", payload);
  }

  @Test
  public void roundTripsPendingEntries() throws IOException {
    File file = newFile();
    EventOutbox outbox = outboxIn(file);
    outbox.add(123L, event("a"));
    outbox.add(123L, event("b"));

    List<OutboxEntry> reloaded = outboxIn(file).pending();

    assertEquals(2, reloaded.size());
    assertEquals("a", reloaded.get(0).getEvent().getId());
    assertEquals("b", reloaded.get(1).getEvent().getId());
    assertEquals(123L, reloaded.get(0).getAccountHash());
  }

  @Test
  public void readsAsEmptyWhenNothingWasEverWritten() throws IOException {
    assertTrue(outboxIn(newFile()).pending().isEmpty());
  }

  @Test
  public void ackRemovesExactlyTheMatchingEntriesAndLeavesTheRestInOrder() throws IOException {
    File file = newFile();
    EventOutbox outbox = outboxIn(file);
    outbox.add(1L, event("a"));
    outbox.add(1L, event("b"));
    outbox.add(1L, event("c"));

    outbox.ack(EventOutbox.toIdSet(Collections.singletonList("b")));

    List<OutboxEntry> remaining = outbox.pending();
    assertEquals(2, remaining.size());
    assertEquals("a", remaining.get(0).getEvent().getId());
    assertEquals("c", remaining.get(1).getEvent().getId());
  }

  @Test
  public void ackWithNoMatchesChangesNothing() throws IOException {
    File file = newFile();
    EventOutbox outbox = outboxIn(file);
    outbox.add(1L, event("a"));

    outbox.ack(EventOutbox.toIdSet(Collections.singletonList("not-present")));

    assertEquals(1, outbox.pending().size());
  }

  @Test
  public void clearForgetsEveryPendingEvent() throws IOException {
    File file = newFile();
    EventOutbox outbox = outboxIn(file);
    outbox.add(1L, event("a"));
    outbox.add(2L, event("b"));

    outbox.clear();

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void overflowingTheCapDropsTheOldestEntryAndKeepsTheRest() throws IOException {
    File file = newFile();
    EventOutbox outbox = outboxIn(file);
    for (int i = 0; i < EventOutbox.MAX_PENDING; i++) {
      outbox.add(1L, event("id-" + i));
    }
    outbox.add(1L, event("overflow"));

    List<OutboxEntry> pending = outbox.pending();
    assertEquals(EventOutbox.MAX_PENDING, pending.size());
    // The oldest entry is the one dropped.
    assertEquals("id-1", pending.get(0).getEvent().getId());
    assertEquals("overflow", pending.get(pending.size() - 1).getEvent().getId());
  }

  @Test
  public void readingACorruptFileResolvesToEmptyRatherThanThrowing() throws IOException {
    File file = newFile();
    java.nio.file.Files.write(file.toPath(), "not json".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertTrue(outboxIn(file).pending().isEmpty());
  }

  /**
   * {@code add} runs on the client thread and {@code ack} on the executor, both
   * read-modify-writing the same file. No add may be lost to a concurrent ack.
   */
  @Test
  public void concurrentAddAndAckDoNotLoseEntries() throws Exception {
    File file = newFile();
    EventOutbox outbox = outboxIn(file);
    int iterations = 150; // below MAX_PENDING, so no drop-oldest eviction is expected
    Set<String> neverMatches = EventOutbox.toIdSet(Collections.singletonList("no-such-id"));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);

    Thread adder =
        new Thread(
            () -> {
              ready.countDown();
              awaitUninterruptibly(go);
              for (int i = 0; i < iterations; i++) {
                outbox.add(1L, event("id-" + i));
              }
            });
    Thread acker =
        new Thread(
            () -> {
              ready.countDown();
              awaitUninterruptibly(go);
              for (int i = 0; i < iterations; i++) {
                outbox.ack(neverMatches);
              }
            });

    adder.start();
    acker.start();
    ready.await();
    go.countDown();
    adder.join(30_000);
    acker.join(30_000);

    assertEquals(iterations, outbox.pending().size());
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** A "not observed" field comes back off disk as an explicit null. */
  @Test
  public void aNullPayloadFieldSurvivesTheRoundTripToDisk() throws Exception {
    java.io.File file = new java.io.File(folder.newFolder("nulls"), "events.json");
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("petName", "Heron");
    payload.put("source", null);
    new EventOutbox(file, new com.google.gson.Gson())
        .add(1L, new TransientEvent(TransientEvent.TYPE_PET_DROP, payload));

    java.util.Map<String, Object> reloaded =
        new EventOutbox(file, new com.google.gson.Gson()).pending().get(0).getEvent().getPayload();
    org.junit.Assert.assertTrue(reloaded.containsKey("source"));
    org.junit.Assert.assertNull(reloaded.get("source"));
  }
}
