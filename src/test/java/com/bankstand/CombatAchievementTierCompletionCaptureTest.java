package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CombatAchievementTierCompletionCaptureTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private EventOutbox outboxIn(File file) {
    return new EventOutbox(file, new Gson());
  }

  private File newFile() throws IOException {
    return new File(folder.newFolder("bankstand"), "events.json");
  }

  @Test
  public void emitsOnATierCompletionBroadcast() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementTierCompletionCapture capture =
        new CombatAchievementTierCompletionCapture(outbox, () -> true, () -> 1L);

    // Unverified wording, based on RuneLite's own chat-notification pattern. If a real
    // capture disagrees, the wording is wrong; update pattern and test together.
    capture.handleMessage("Congratulations, you have completed the elite tier of the Combat Achievements!");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals("elite", outbox.pending().get(0).getEvent().getPayload().get("tier"));
  }

  @Test
  public void emitsWhenTheBroadcastHasNoTrailingExclamationMark() throws IOException {
    // The trailing "!" is optional because it is unconfirmed.
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementTierCompletionCapture capture =
        new CombatAchievementTierCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you have completed the hard tier of the Combat Achievements");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals("hard", outbox.pending().get(0).getEvent().getPayload().get("tier"));
  }

  @Test
  public void ignoresAnUnrecognisedTierWord() throws IOException {
    // Fail closed on anything outside the six real tiers.
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementTierCompletionCapture capture =
        new CombatAchievementTierCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you have completed the mythical tier of the Combat Achievements!");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void ignoresAnUnrelatedMessage() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementTierCompletionCapture capture =
        new CombatAchievementTierCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you've completed a hard combat task: Whack-a-Mole.");

    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void touchesNothingWhenDisabled() throws IOException {
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementTierCompletionCapture capture =
        new CombatAchievementTierCompletionCapture(outbox, () -> false, () -> 1L);

    capture.handleMessage("Congratulations, you have completed the elite tier of the Combat Achievements!");

    assertTrue(outbox.pending().isEmpty());
  }
}
