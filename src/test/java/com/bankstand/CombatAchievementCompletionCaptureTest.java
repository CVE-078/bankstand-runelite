package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CombatAchievementCompletionCaptureTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private EventOutbox outboxIn(File file) {
    return new EventOutbox(file, new Gson());
  }

  private File newFile() throws IOException {
    return new File(folder.newFolder("bankstand"), "events.json");
  }

  @Test
  public void emitsOnACombatTaskCompletionBroadcast() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you've completed a hard combat task: Whack-a-Mole.");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals("hard", outbox.pending().get(0).getEvent().getPayload().get("tier"));
    assertEquals(
        "Whack-a-Mole", outbox.pending().get(0).getEvent().getPayload().get("taskName"));
  }

  @Test
  public void emitsOnAnIndefiniteArticleAnVariant() throws IOException {
    // "an easy"/"an elite" use the "an" article; the pattern must accept both a/an.
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you've completed an easy combat task: A Slow Death.");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals("easy", outbox.pending().get(0).getEvent().getPayload().get("tier"));
    assertEquals(
        "A Slow Death", outbox.pending().get(0).getEvent().getPayload().get("taskName"));
  }

  @Test
  public void stripsAPointsSuffixFromTheTaskName() throws IOException {
    // The clean name is a permanent server-side primary key with no later sync to fix
    // it, so the "(N points)" suffix the game appends must never reach the outbox.
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage(
        "Congratulations, you've completed a grandmaster combat task: No Pressure (6 points).");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals(
        "grandmaster", outbox.pending().get(0).getEvent().getPayload().get("tier"));
    assertEquals(
        "No Pressure", outbox.pending().get(0).getEvent().getPayload().get("taskName"));
  }

  @Test
  public void keepsAPeriodOrParenthesesInsideTheTaskName() throws IOException {
    // Pins the greedy capture group: everything up to the final ". " terminator stays
    // part of the task name, including an internal period or parenthetical aside.
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage(
        "Congratulations, you've completed a master combat task: Mr. Ex-Diner (No Seconds).");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals(
        "Mr. Ex-Diner (No Seconds)",
        outbox.pending().get(0).getEvent().getPayload().get("taskName"));
  }

  /** The server validates a whole batch in one schema parse (128-char task-name bound)
   *  and 400s the WHOLE BATCH on any one event failing validation, with the drain
   *  acking nothing on failure: one oversized name would block every other queued
   *  event for the account, forever. Same fix as CollectionLogUnlockCapture's. */
  @Test
  public void skipsAnOversizedTaskName() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);
    String oversizedName = repeat("A", 129);

    capture.handleMessage(
        "Congratulations, you've completed a hard combat task: " + oversizedName + ".");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void emitsATaskNameAtExactlyTheLengthBound() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);
    String boundedName = repeat("A", 128);

    capture.handleMessage(
        "Congratulations, you've completed a hard combat task: " + boundedName + ".");

    assertTrue(!outbox.pending().isEmpty());
  }

  private static String repeat(String s, int times) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < times; i++) {
      builder.append(s);
    }
    return builder.toString();
  }

  /** The strip can consume the entire captured group (a double space before the
   *  suffix leaves nothing behind), and an empty name is worse than useless: the
   *  server's min(1) rejects it, and one rejected event in a batch 400s the whole
   *  batch, permanently blocking every other queued event for the account. */
  @Test
  public void doesNotEmitWhenTheTaskNameStripsToEmpty() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you've completed a hard combat task:  (1 point).");

    assertTrue(outbox.pending().isEmpty());
  }

  /** A whitespace-only name is min(1)-valid junk, not a real task name, and must be
   *  caught the same way an empty one is. */
  @Test
  public void doesNotEmitAWhitespaceOnlyTaskName() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you've completed a hard combat task:    .");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void ignoresAnUnrecognisedTierWord() throws IOException {
    // Defends against a mis-parse resolving to something outside the six real
    // tiers, which the server's own zod enum would reject anyway, but failing
    // closed here means nothing is even queued for a doomed submission.
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you've completed a mythical combat task: Nonsense.");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void ignoresAnUnrelatedMessage() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you've reached level 99 in Attack!");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void touchesNothingWhenDisabled() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> false, () -> 1L);

    capture.handleMessage("Congratulations, you've completed a hard combat task: Whack-a-Mole.");

    assertTrue(outbox.pending().isEmpty());
  }
}
