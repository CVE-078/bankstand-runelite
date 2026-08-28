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

  // The wire region key DiaryTaskRegions resolves "Western Provinces" to, and the first
  // of DiaryTaskVarplayers.ALL's two ids for it (WESTERN_ACHIEVEMENT_DIARY).
  private static final String WESTERN_REGION = "WESTERN_PROVINCES";
  private static final int WESTERN_VARPLAYER = DiaryTaskVarplayers.ALL.get(WESTERN_REGION)[0];

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private EventOutbox outboxIn(File file) {
    return new EventOutbox(file, new Gson());
  }

  private File newFile() throws IOException {
    // A unique subfolder per call: TemporaryFolder#newFolder throws if the same
    // relative path is requested twice within one test (see emitsForEveryRealTier,
    // which calls this in a loop).
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
    // "a hard"/"a medium" use the "a" article, not "an"; the pattern must accept both.
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
    // Three regions are double-barrelled (Kourend & Kebos, Lumbridge & Draynor, Western
    // Provinces); the greedy capture group must keep the whole phrase, not stop at the
    // first word.
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
    // "master"/"grandmaster" are real combat-achievement tiers, not diary tiers; a
    // mis-parse resolving to one of those must never reach the outbox.
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
    // The #770 early-trigger's tier-completion broadcast starts with "Congratulations"
    // and names the tier and area differently; this capture's pattern must not match it.
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
    // DiaryTaskManifest.shipped() has no verified regions, matching production today:
    // the region resolves and its varplayer is read and diffed, but nothing is ever
    // looked up while unverified.
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox, () -> true, () -> 1L, id -> 0b1, new DiaryTaskBits(),
            DiaryTaskManifest.shipped());

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }

  @Test
  public void attachesTheResolvedTaskNameForAVerifiedRegionsSingleNewlySetBit()
      throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskBits bits = new DiaryTaskBits();
    // Establish the baseline at 0 first: the manifest bit must be observed transitioning
    // from unset to set, not merely present on the very first read.
    bits.diff(WESTERN_VARPLAYER, 0);
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox,
            () -> true,
            () -> 1L,
            id -> id == WESTERN_VARPLAYER ? 0b1 : 0,
            bits,
            verifiedWesternManifest("elite", "Enter the Kalphite Lair"));

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertEquals(
        "Enter the Kalphite Lair",
        outbox.pending().get(0).getEvent().getPayload().get("taskName"));
  }

  @Test
  public void firstObservationNeverAttachesATaskNameEvenWhenVerifiedAndAlreadySet()
      throws IOException {
    // The most important fail-safe: a player who already had this task done before the
    // manifest shipped (or before a restart lost the in-memory baseline) must not have
    // it reported as freshly completed the instant it is first read.
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox,
            () -> true,
            () -> 1L,
            id -> id == WESTERN_VARPLAYER ? 0b1 : 0,
            new DiaryTaskBits(),
            verifiedWesternManifest("elite", "Enter the Kalphite Lair"));

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
            // The manifest says this bit is a hard task; the chat line says elite.
            verifiedWesternManifest("hard", "Enter the Kalphite Lair"));

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }

  @Test
  public void failsClosedWhenMoreThanOneBitResolvesFromOneObservation() throws IOException {
    // Ambiguous: this capture fires once per chat line and cannot safely guess which of
    // two simultaneously-flipped, independently resolvable bits the line refers to.
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
            outbox, () -> true, () -> 1L, id -> 0b1, bits, manifest);

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }

  @Test
  public void failsClosedWhenOneOfTwoSimultaneouslyFlippedBitsIsUnmapped() throws IOException {
    // The gap counting only resolved bits missed: two bits flip at once, but only one
    // has a manifest entry (a real, expected state for a verified region with partial
    // coverage). This capture cannot know whether THIS chat line refers to the bit that
    // resolved or the other, unmapped one that flipped in the same window, so it must
    // fail closed exactly as it would if both had resolved.
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
            outbox, () -> true, () -> 1L, id -> 0b1, bits, manifest);

    capture.handleMessage(WESTERN_TASK_MESSAGE);

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }

  @Test
  public void attachesNoTaskNameWhenTheAreaTextIsUnrecognised() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    DiaryTaskCompletionCapture capture =
        new DiaryTaskCompletionCapture(
            outbox, () -> true, () -> 1L, id -> 0b1, new DiaryTaskBits(),
            DiaryTaskManifest.shipped());

    capture.handleMessage(
        "Well done! You have completed an elite task in the Nowhere area. Your Achievement"
            + " Diary has been updated.");

    assertFalse(outbox.pending().get(0).getEvent().getPayload().containsKey("taskName"));
  }
}
