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

    // Best-effort guess, NOT verified against a live capture: built from the one documented
    // fragment available (RuneLite's own chat-notification highlight pattern, "has completed the
    // (\w*) tier of the Combat Achievements"). If this test starts failing against a real capture
    // later, the wording is wrong, not the test; update both together, the same way the CA_ID
    // prefix and the @ach_comp@ icon tag were both fixed only after a real live capture.
    capture.handleMessage("Congratulations, you have completed the elite tier of the Combat Achievements!");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals("elite", outbox.pending().get(0).getEvent().getPayload().get("tier"));
  }

  @Test
  public void emitsWhenTheBroadcastHasNoTrailingExclamationMark() throws IOException {
    // Same unverified guess as above; the trailing "!" is optional in the pattern because
    // nothing in the one documented fragment confirms it is always present.
    EventOutbox outbox = outboxIn(newFile());
    CombatAchievementTierCompletionCapture capture =
        new CombatAchievementTierCompletionCapture(outbox, () -> true, () -> 1L);

    capture.handleMessage("Congratulations, you have completed the hard tier of the Combat Achievements");

    assertTrue(!outbox.pending().isEmpty());
    assertEquals("hard", outbox.pending().get(0).getEvent().getPayload().get("tier"));
  }

  @Test
  public void ignoresAnUnrecognisedTierWord() throws IOException {
    // Defends against a mis-parse resolving to something outside the six real
    // tiers, which the server's own zod enum would reject anyway, but failing
    // closed here means nothing is even queued for a doomed submission.
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
