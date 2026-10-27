package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DiaryTaskCompletionCaptureTest {

  private static final String WESTERN_TASK_MESSAGE =
      "Well done! You have completed an elite task in the Western Provinces area. Your"
          + " Achievement Diary has been updated.";

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private EventOutbox outboxIn(File file) {
    return new EventOutbox(file, new Gson());
  }

  private File newFile() throws IOException {
    // TemporaryFolder#newFolder throws on a repeated path, and this is called in a loop.
    return new File(folder.newFolder(), "events.json");
  }

  @Test
  public void emitsOnADiaryTaskCompletionBroadcast() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage(
        "Well done! You have completed an elite task in the Western Provinces area. Your"
            + " Achievement Diary has been updated.");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals(
        TransientEvent.TYPE_DIARY_TASK_COMPLETED, outbox.pending().get(0).getEvent().getType());
    assertEquals("elite", outbox.pending().get(0).getEvent().getPayload().get("tier"));
    assertEquals(
        "Western Provinces", outbox.pending().get(0).getEvent().getPayload().get("area"));
  }

  @Test
  public void emitsOnAnIndefiniteArticleAVariant() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage(
        "Well done! You have completed a hard task in the Kandarin area. Your Achievement"
            + " Diary has been updated.");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals("hard", outbox.pending().get(0).getEvent().getPayload().get("tier"));
    assertEquals("Kandarin", outbox.pending().get(0).getEvent().getPayload().get("area"));
  }

  @Test
  public void emitsForEveryRealTier() throws IOException {
    for (String tier : new String[] {"easy", "medium", "hard", "elite"}) {
      EventOutbox outbox = outboxIn(newFile());
      DiaryTaskCompletionCapture capture =
          new DiaryTaskCompletionCapture(outbox, () -> true, () -> 1L);
      String article = tier.equals("elite") || tier.equals("easy") ? "an" : "a";

      capture.handleMessage(
          "Well done! You have completed "
              + article
              + " "
              + tier
              + " task in the Varrock area. Your Achievement Diary has been updated.");

      assertTrue("expected an event for tier " + tier, !outbox.pending().isEmpty());
      assertEquals(tier, outbox.pending().get(0).getEvent().getPayload().get("tier"));
    }
  }

  @Test
  public void keepsADoubleBarrelledAreaNameIntact() throws IOException {
    // Double-barrelled region names must be captured whole.
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage(
        "Well done! You have completed a medium task in the Lumbridge & Draynor area. Your"
            + " Achievement Diary has been updated.");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals(
        "Lumbridge & Draynor", outbox.pending().get(0).getEvent().getPayload().get("area"));
  }

  @Test
  public void ignoresAnUnrecognisedTierWord() throws IOException {
    // Combat achievement tiers must never parse as diary tiers.
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage(
        "Well done! You have completed a master task in the Varrock area. Your Achievement"
            + " Diary has been updated.");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void doesNotConfuseTheTierCompletionBroadcastForThisOne() throws IOException {
    // The tier-completion broadcast must not match this pattern.
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage(
        "Congratulations, you have completed all of the Easy tasks in the Varrock area. Your"
            + " Achievement Diary has been updated.");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void ignoresAnUnrelatedMessage() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you've reached level 99 in Attack!");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void skipsAnOversizedAreaName() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(outbox, () -> true, () -> 1L);
    String oversizedArea = repeat("A", 129);

    capture.handleMessage(
        "Well done! You have completed an elite task in the "
            + oversizedArea
            + " area. Your Achievement Diary has been updated.");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void emitsAnAreaNameAtExactlyTheLengthBound() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(outbox, () -> true, () -> 1L);
    String boundedArea = repeat("A", 128);

    capture.handleMessage(
        "Well done! You have completed an elite task in the "
            + boundedArea
            + " area. Your Achievement Diary has been updated.");

    assertTrue(!outbox.pending().isEmpty());
  }

  private static String repeat(String s, int times) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < times; i++) {
      builder.append(s);
    }
    return builder.toString();
  }

  @Test
  public void touchesNothingWhenDisabled() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(outbox, () -> false, () -> 1L);

    capture.handleMessage(
        "Well done! You have completed an elite task in the Western Provinces area. Your"
            + " Achievement Diary has been updated.");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void carriesTierAndAreaOnly() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    new DiaryTaskCompletionCapture(outbox, () -> true, () -> 1L).handleMessage(WESTERN_TASK_MESSAGE);
    assertEquals(
        Set.of("tier", "area"), outbox.pending().get(0).getEvent().getPayload().keySet());
  }
}
