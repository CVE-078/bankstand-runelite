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
  public void emitsWhenTheGameTagsTheBroadcastWithACaId() throws IOException {
    // The real broadcast carries a "CA_ID:<n>|" prefix.
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage(
        "CA_ID:632|Congratulations, you've completed an easy combat task: Brutus Novice (1"
            + " point).");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals("easy", outbox.pending().get(0).getEvent().getPayload().get("tier"));
    assertEquals(
        "Brutus Novice", outbox.pending().get(0).getEvent().getPayload().get("taskName"));
  }

  @Test
  public void stripsALeadingIconTagFromTheTaskName() throws IOException {
    // The real broadcast can carry a raw "@word@" icon tag before the task name.
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage(
        "Congratulations, you've completed a medium combat task: @ach_comp@Perfect"
            + " Shellbane.");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals("medium", outbox.pending().get(0).getEvent().getPayload().get("tier"));
    assertEquals(
        "Perfect Shellbane", outbox.pending().get(0).getEvent().getPayload().get("taskName"));
  }

  @Test
  public void emitsOnAnIndefiniteArticleAnVariant() throws IOException {
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
    // The name is a permanent server-side key, so the "(N points)" suffix must never reach it.
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
    // Greedy capture: everything up to the final ". " stays part of the task name.
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

  /** An oversized name would 400 the whole batch and block every queued event forever. */
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

  /** An empty name fails the server's min(1) and would block the whole batch. */
  @Test
  public void doesNotEmitWhenTheTaskNameStripsToEmpty() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementCompletionCapture capture =
        new CombatAchievementCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you've completed a hard combat task:  (1 point).");

    assertTrue(outbox.pending().isEmpty());
  }

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
    // Fail closed on anything outside the six real tiers.
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
