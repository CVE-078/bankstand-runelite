package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class QuestCompletionCaptureTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private EventOutbox outbox() throws IOException {
    return new EventOutbox(new File(folder.newFolder(), "events.json"), new Gson());
  }

  private QuestCompletionCapture capture(EventOutbox outbox, boolean enabled) {
    return new QuestCompletionCapture(
        outbox, () -> enabled, () -> 1L, null, BankstandPlugin::isKnownQuest, null);
  }

  @Test
  public void parsesScrollTitles() {
    assertEquals(
        "The Corsair Curse",
        QuestCompletionCapture.parseScrollTitle("You have completed The Corsair Curse!"));
    assertEquals(
        "One Small Favour",
        QuestCompletionCapture.parseScrollTitle("'One Small Favour' completed!"));
    assertEquals(
        "Doric's Quest",
        QuestCompletionCapture.parseScrollTitle("You have completed Doric's Quest!"));
    assertEquals(
        "Recipe for Disaster - Mountain Dwarf",
        QuestCompletionCapture.parseScrollTitle("You have freed the Mountain Dwarf!"));
    assertEquals(
        "Some Future Adventure",
        QuestCompletionCapture.parseScrollTitle("You have completed Some Future Adventure!"));
    assertNull(QuestCompletionCapture.parseScrollTitle(null));
  }

  @Test
  public void emitsAQuestRuneLiteDoesNotKnowYet() throws IOException {
    EventOutbox outbox = outbox();
    capture(outbox, true).handleTitle("You have completed Some Future Adventure!");

    assertEquals(1, outbox.pending().size());
    TransientEvent event = outbox.pending().get(0).getEvent();
    assertEquals(TransientEvent.TYPE_QUEST_COMPLETED, event.getType());
    assertEquals("Some Future Adventure", event.getPayload().get("questName"));
  }

  @Test
  public void skipsAQuestTheSnapshotAlreadyReports() throws IOException {
    EventOutbox outbox = outbox();
    capture(outbox, true).handleTitle("You have completed The Corsair Curse!");
    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void treatsANameMissingItsQuestSuffixAsKnown() {
    assertTrue(BankstandPlugin.isKnownQuest("Cook's Assistant"));
    assertTrue(BankstandPlugin.isKnownQuest("Olaf's"));
  }

  @Test
  public void emitsOncePerQuest() throws IOException {
    EventOutbox outbox = outbox();
    QuestCompletionCapture capture = capture(outbox, true);
    capture.handleTitle("You have completed Some Future Adventure!");
    capture.handleTitle("You have completed Some Future Adventure!");
    assertEquals(1, outbox.pending().size());
  }

  @Test
  public void emitsNothingWhileDisabled() throws IOException {
    EventOutbox outbox = outbox();
    capture(outbox, false).handleTitle("You have completed Some Future Adventure!");
    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void describesItInRecentActivity() {
    assertEquals(
        "Quests: Some Future Adventure completed",
        RecentActivityLog.describe(
            TransientEvent.TYPE_QUEST_COMPLETED,
            java.util.Map.of("questName", "Some Future Adventure")));
  }
}
