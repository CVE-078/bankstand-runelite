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
    // The type is the one value the server also checks, so a typo would 400 permanently.
    assertEquals(
        TransientEvent.TYPE_COLLECTION_LOG_UNLOCK, outbox.pending().get(0).getEvent().getType());
    assertTrue(
        outbox.pending().get(0).getEvent().getPayload().get("itemName").equals("Abyssal orphan"));
  }

  /** An oversized name would 400 the whole batch and block every queued event forever. */
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

  /** A whitespace-only name would 400 the whole batch. */
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

  /** The toggle gates the read itself, not only the emit. */
  @Test
  public void touchesNothingWhenDisabled() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    CollectionLogUnlockCapture capture = new CollectionLogUnlockCapture(outbox, () -> false, () -> 1L);

    capture.handleMessage("New item added to your collection log: Abyssal orphan");

    assertTrue(outbox.pending().isEmpty());
  }
}
