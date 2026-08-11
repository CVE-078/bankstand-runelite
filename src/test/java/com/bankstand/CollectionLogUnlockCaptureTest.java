package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CollectionLogUnlockCaptureTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void emitsOnTheCollectionLogBroadcast() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    CollectionLogUnlockCapture capture = new CollectionLogUnlockCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("New item added to your collection log: Abyssal orphan");

    assertTrue(!outbox.pending().isEmpty());
    // A typo in TYPE_COLLECTION_LOG_UNLOCK would compile and pass every other
    // assertion here, then fail permanently in production as a 400 the server
    // rejects: the type is the one value in this diff the server also checks.
    assertEquals(
        TransientEvent.TYPE_COLLECTION_LOG_UNLOCK, outbox.pending().get(0).getEvent().getType());
    assertTrue(
        outbox.pending().get(0).getEvent().getPayload().get("itemName").equals("Abyssal orphan"));
  }

  /** The server validates a whole batch in one schema parse (128-char item-name bound)
   *  and 400s the WHOLE BATCH on any one event failing validation, with the drain
   *  acking nothing on failure: one oversized name would block every other queued
   *  event for the account, forever. */
  @Test
  public void skipsAnOversizedItemName() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    CollectionLogUnlockCapture capture = new CollectionLogUnlockCapture(outbox, () -> true, () -> 1L);
    String oversizedName = repeat("A", 129);

    capture.handleMessage("New item added to your collection log: " + oversizedName);

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void emitsAnItemNameAtExactlyTheLengthBound() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    CollectionLogUnlockCapture capture = new CollectionLogUnlockCapture(outbox, () -> true, () -> 1L);
    String boundedName = repeat("A", 128);

    capture.handleMessage("New item added to your collection log: " + boundedName);

    assertTrue(!outbox.pending().isEmpty());
  }

  private static String repeat(String s, int times) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < times; i++) {
      builder.append(s);
    }
    return builder.toString();
  }

  /** A whitespace-only capture is min(1)-valid junk on the wire, not a real item
   *  name, and one bad event 400s the whole batch (see the oversized-name test
   *  above), permanently blocking every other queued event for the account. */
  @Test
  public void doesNotEmitAWhitespaceOnlyItemName() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    CollectionLogUnlockCapture capture = new CollectionLogUnlockCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("New item added to your collection log:    ");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void trimsSurroundingWhitespaceFromTheItemName() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    CollectionLogUnlockCapture capture = new CollectionLogUnlockCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("New item added to your collection log:  Abyssal orphan  ");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals(
        "Abyssal orphan", outbox.pending().get(0).getEvent().getPayload().get("itemName"));
  }

  @Test
  public void ignoresAnUnrelatedMessage() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    CollectionLogUnlockCapture capture = new CollectionLogUnlockCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Untradeable drop: Coins");

    assertTrue(outbox.pending().isEmpty());
  }

  /** The toggle must gate the read itself, not only emit, matching NotableDropCapture's
   *  established convention (#608's own plugin-side session recorded this as the rule
   *  to follow: a stateless single-message detector gates at the very top). */
  @Test
  public void touchesNothingWhenDisabled() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    CollectionLogUnlockCapture capture = new CollectionLogUnlockCapture(outbox, () -> false, () -> 1L);

    capture.handleMessage("New item added to your collection log: Abyssal orphan");

    assertTrue(outbox.pending().isEmpty());
  }
}
