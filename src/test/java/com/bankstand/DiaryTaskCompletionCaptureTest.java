package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DiaryTaskCompletionCaptureTest {

  private static final String WESTERN_TASK_MESSAGE =
      "Well done! You have completed an elite task in the Western Provinces area. Your"
          + " Achievement Diary has been updated.";

  private static final String WESTERN_REGION = "WESTERN_PROVINCES";
  private static final int WESTERN_VARPLAYER = DiaryTaskVarplayers.ALL.get(WESTERN_REGION)[0];

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private EventOutbox outboxIn(File file) {
    return new EventOutbox(file, new Gson());
  }

  private File newFile() throws IOException {
    // TemporaryFolder#newFolder throws on a repeated path, and this is called in a loop.
    return new File(folder.newFolder(), "events.json");
  }

  private DiaryTaskManifest verifiedWesternManifest(String tier, String taskName) {
    return new DiaryTaskManifest(
        Set.of(WESTERN_REGION),
        Map.of(WESTERN_REGION, Map.of(
            WESTERN_VARPLAYER, Map.of(0, new DiaryTaskManifest.Entry(tier, taskName)))));
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
  public void attachesNoTaskNameForAnUnverifiedRegion() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox, () -> true, () -> 1L, id -> 0b1, new DiaryTaskBits(),
            DiaryTaskManifest.shipped(), () -> {});

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }

  @Test
  public void attachesTheResolvedTaskNameForAVerifiedRegionsSingleNewlySetBit()
      throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(WESTERN_VARPLAYER, 0); // baseline at 0 first, so the bit has to transition
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox,
            () -> true,
            () -> 1L,
            id -> id == WESTERN_VARPLAYER ? 0b1 : 0,
            bits,
            verifiedWesternManifest("elite", "Enter the Kalphite Lair"),
            () -> {});

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertEquals(
        "Enter the Kalphite Lair",
        outbox.pending().get(0).getEvent().getPayload().get("taskName"));
  }

  @Test
  public void firstObservationNeverAttachesATaskNameEvenWhenVerifiedAndAlreadySet()
      throws IOException {
    // A task done before the manifest shipped must not report as freshly completed.
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox,
            () -> true,
            () -> 1L,
            id -> id == WESTERN_VARPLAYER ? 0b1 : 0,
            new DiaryTaskBits(),
            verifiedWesternManifest("elite", "Enter the Kalphite Lair"),
            () -> {});

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }

  @Test
  public void failsClosedOnATierMismatchBetweenTheManifestAndTheChatLine() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(WESTERN_VARPLAYER, 0); // establish baseline at 0
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox,
            () -> true,
            () -> 1L,
            id -> id == WESTERN_VARPLAYER ? 0b1 : 0,
            bits,
            verifiedWesternManifest("hard", "Enter the Kalphite Lair"), // chat line says elite
            () -> {});

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }

  @Test
  public void failsClosedWhenMoreThanOneBitResolvesFromOneObservation() throws IOException {
    int otherVarplayer = DiaryTaskVarplayers.ALL.get(WESTERN_REGION)[1];
    DiaryTaskManifest manifest = new DiaryTaskManifest(
        Set.of(WESTERN_REGION),
        Map.of(WESTERN_REGION, Map.of(
            WESTERN_VARPLAYER, Map.of(0, new DiaryTaskManifest.Entry("elite", "Task A")),
            otherVarplayer, Map.of(0, new DiaryTaskManifest.Entry("elite", "Task B")))));
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(WESTERN_VARPLAYER, 0);
    bits.diff(otherVarplayer, 0);
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox, () -> true, () -> 1L, id -> 0b1, bits, manifest, () -> {});

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }

  @Test
  public void failsClosedWhenOneOfTwoSimultaneouslyFlippedBitsIsUnmapped() throws IOException {
    // Only one of two flipped bits has a manifest entry: still ambiguous.
    int otherVarplayer = DiaryTaskVarplayers.ALL.get(WESTERN_REGION)[1];
    DiaryTaskManifest manifest = new DiaryTaskManifest(
        Set.of(WESTERN_REGION),
        Map.of(WESTERN_REGION, Map.of(
            WESTERN_VARPLAYER, Map.of(0, new DiaryTaskManifest.Entry("elite", "Task A")))));
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(WESTERN_VARPLAYER, 0);
    bits.diff(otherVarplayer, 0);
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox, () -> true, () -> 1L, id -> 0b1, bits, manifest, () -> {});

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }

  @Test
  public void attachesNoTaskNameWhenTheAreaTextIsUnrecognised() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox, () -> true, () -> 1L, id -> 0b1, new DiaryTaskBits(),
            DiaryTaskManifest.shipped(), () -> {});

    capture.handleMessage(
        "Well done! You have completed an elite task in the Nowhere area. Your Achievement"
            + " Diary has been updated.");

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }

  @Test
  public void touchesTheBaselineCallbackOnceForARecognisedRegion() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    int[] touched = new int[1];
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox, () -> true, () -> 1L, id -> 0b1, new DiaryTaskBits(),
            DiaryTaskManifest.shipped(), () -> touched[0]++);

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertEquals(1, touched[0]);
  }

  @Test
  public void theBaselineCallbackObservesThisMessagesOwnDiffAlreadyApplied() throws IOException {
    // The persist callback must fire after this message's own diff, or a crash loses it.
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskBits bits = new DiaryTaskBits();
    bits.diff(WESTERN_VARPLAYER, 0); // establish baseline before the message under test
    Integer[] seenDuringCallback = new Integer[1];
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox,
            () -> true,
            () -> 1L,
            id -> id == WESTERN_VARPLAYER ? 0b1 : 0,
            bits,
            DiaryTaskManifest.shipped(),
            () -> seenDuringCallback[0] = bits.snapshot().get(WESTERN_VARPLAYER));

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertEquals(Integer.valueOf(0b1), seenDuringCallback[0]);
  }

  @Test
  public void neverTouchesTheBaselineCallbackForAnUnrecognisedRegion() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    int[] touched = new int[1];
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox, () -> true, () -> 1L, id -> 0b1, new DiaryTaskBits(),
            DiaryTaskManifest.shipped(), () -> touched[0]++);

    capture.handleMessage(
        "Well done! You have completed an elite task in the Nowhere area. Your Achievement"
            + " Diary has been updated.");

    assertEquals(0, touched[0]);
  }

  @Test
  public void neverTouchesTheBaselineCallbackWhenDisabled() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    int[] touched = new int[1];
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox, () -> false, () -> 1L, id -> 0b1, new DiaryTaskBits(),
            DiaryTaskManifest.shipped(), () -> touched[0]++);

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertEquals(0, touched[0]);
  }
}
