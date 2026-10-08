package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.util.Text;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The completion lines below are RuneLite's own Slayer plugin test fixtures, verbatim,
 * which that plugin matched against the live game. Never edited to suit the pattern.
 */
public class SlayerTaskCompletionCaptureTest {

  private static final String TASK_ONE =
      "<col=ef1020>You've completed </col>1 task<col=ef1020> and will need</col> 4 more"
          + " <col=ef1020>before you start receiving Slayer points; return to a Slayer"
          + " master.</col>";
  private static final String TASK_COMPLETE_NO_POINTS =
      "<col=ef1020>You've completed </col>3 tasks<col=ef1020> and will need </col>2"
          + " more<col=ef1020> before you start receiving Slayer points; return to a Slayer"
          + " master.";
  private static final String TASK_POINTS =
      "<col=ef1020>You've completed </col>9 tasks <col=ef1020>and received </col>10"
          + " points<col=ef1020>, giving you a total of </col>18,000<col=ef1020>; return to a"
          + " Slayer master.";
  private static final String TASK_LARGE_STREAK =
      "<col=ef1020>You've completed </col>2,465 tasks <col=ef1020>and received </col>15"
          + " points<col=ef1020>, giving you a total of </col>131,071<col=ef1020>; return to a"
          + " Slayer master.";
  private static final String TASK_COMPETE_TURAEL =
      "<col=ef1020>You've completed </col>104 tasks <col=ef1020>. You'll be eligible to earn"
          + " reward points if you complete tasks from a more advanced Slayer Master.";
  private static final String TASK_MAX_STREAK =
      "<col=ef1020>You've completed at least </col>16,000 tasks <col=ef1020>and received"
          + " </col>15 points<col=ef1020>, giving you a total of </col>131,071<col=ef1020>;"
          + " return to a Slayer master.";
  private static final String TASK_MAX_POINTS =
      "<col=ef1020>You've completed </col>9 tasks <col=ef1020>and reached the maximum amount"
          + " of Slayer points </col>(131,071)<col=ef1020>; return to a Slayer master.";
  private static final String TASK_WILDERNESS =
      "<col=ef1020>You've completed </col>9 Wilderness tasks <col=ef1020>and received </col>10"
          + " points<col=ef1020>, giving you a total of </col>18,000<col=ef1020>; return to a"
          + " Slayer master.";
  // Not a completion: the slayer gem's own reply once a task is done.
  private static final String TASK_COMPLETE = "You need something new to hunt.";

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final SlayerTask RUNNING =
      new SlayerTask(true, "Cave horrors", 3, 120, null, 1200, 55);

  private EventOutbox outbox;
  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final AtomicReference<SlayerTask> current = new AtomicReference<>(RUNNING);

  private SlayerTaskCompletionCapture capture() throws Exception {
    outbox = new EventOutbox(new File(folder.newFolder("bankstand"), "events.json"), new Gson());
    return new SlayerTaskCompletionCapture(outbox, enabled::get, () -> 1L, current::get, null);
  }

  private static VarbitChanged countChanged(int value) {
    VarbitChanged event = new VarbitChanged();
    event.setVarpId(VarPlayerID.SLAYER_COUNT);
    event.setValue(value);
    return event;
  }

  private Map<String, Object> onlyPayload() {
    assertEquals(1, outbox.pending().size());
    TransientEvent event = outbox.pending().get(0).getEvent();
    assertEquals(TransientEvent.TYPE_SLAYER_TASK_COMPLETED, event.getType());
    return event.getPayload();
  }

  private static Integer intOf(Object value) {
    return value == null ? null : ((Number) value).intValue();
  }

  @Test
  public void recognisesEveryCompletionFixture() {
    for (String line :
        new String[] {
          TASK_ONE, TASK_COMPLETE_NO_POINTS, TASK_POINTS, TASK_LARGE_STREAK, TASK_COMPETE_TURAEL,
          TASK_MAX_STREAK, TASK_MAX_POINTS, TASK_WILDERNESS
        }) {
      assertTrue(line, SlayerTaskCompletionCapture.isCompletionLine(Text.removeTags(line)));
    }
    assertFalse(SlayerTaskCompletionCapture.isCompletionLine(TASK_COMPLETE));
  }

  @Test
  public void capturesPointsAndStreakFromTheLineAndTheTaskFromBeforeIt() throws Exception {
    SlayerTaskCompletionCapture capture = capture();
    capture.onVarbitChanged(countChanged(3));
    current.set(SlayerTask.NONE);

    capture.onMessage(Text.removeTags(TASK_POINTS));

    Map<String, Object> payload = onlyPayload();
    assertEquals("Cave horrors", payload.get("creature"));
    assertEquals(120, (int) intOf(payload.get("killed")));
    assertEquals(10, (int) intOf(payload.get("pointsAwarded")));
    assertEquals(18_000, (int) intOf(payload.get("pointsTotal")));
    assertEquals(9, (int) intOf(payload.get("streak")));
    assertTrue(payload.containsKey("master"));
    assertNull(payload.get("master"));
  }

  @Test
  public void readsGroupedDigitsAndTheMaximumPointsForm() throws Exception {
    SlayerTaskCompletionCapture capture = capture();
    capture.onMessage(Text.removeTags(TASK_LARGE_STREAK));
    capture.onMessage(Text.removeTags(TASK_MAX_POINTS));

    TransientEvent first = outbox.pending().get(0).getEvent();
    assertEquals(2465, (int) intOf(first.getPayload().get("streak")));
    assertEquals(131_071, (int) intOf(first.getPayload().get("pointsTotal")));
    TransientEvent second = outbox.pending().get(1).getEvent();
    assertNull("no award is named in this form", second.getPayload().get("pointsAwarded"));
    assertEquals(131_071, (int) intOf(second.getPayload().get("pointsTotal")));
  }

  @Test
  public void aCompletionWithNoPointsCarriesNullNotZero() throws Exception {
    capture().onMessage(Text.removeTags(TASK_ONE));
    Map<String, Object> payload = onlyPayload();
    assertEquals(1, (int) intOf(payload.get("streak")));
    assertNull(payload.get("pointsAwarded"));
    assertNull(payload.get("pointsTotal"));
  }

  @Test
  public void anUnseenTaskIsNullNotGuessed() throws Exception {
    capture().onMessage(Text.removeTags(TASK_POINTS));
    Map<String, Object> payload = onlyPayload();
    assertNull(payload.get("creature"));
    assertNull(payload.get("killed"));
  }

  @Test
  public void sendsNothingWhileOff() throws Exception {
    enabled.set(false);
    capture().onMessage(Text.removeTags(TASK_POINTS));
    assertTrue(outbox.pending().isEmpty());
  }

  /**
   * The bookkeeping runs whatever the toggle says. A line arriving while off still closes
   * the task it describes, so a toggle switched on later cannot attach an old task to the
   * next completion.
   */
  @Test
  public void aCompletionWhileOffStillConsumesTheRememberedTask() throws Exception {
    SlayerTaskCompletionCapture capture = capture();
    capture.onVarbitChanged(countChanged(3));
    enabled.set(false);
    capture.onMessage(Text.removeTags(TASK_POINTS));

    enabled.set(true);
    capture.onMessage(Text.removeTags(TASK_POINTS));
    assertNull(onlyPayload().get("creature"));
  }

  @Test
  public void aCountDroppingToZeroDoesNotForgetTheTask() throws Exception {
    SlayerTaskCompletionCapture capture = capture();
    capture.onVarbitChanged(countChanged(1));
    capture.onVarbitChanged(countChanged(0));
    capture.onMessage(Text.removeTags(TASK_POINTS));
    assertEquals("Cave horrors", onlyPayload().get("creature"));
  }
}
