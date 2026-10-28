package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.http.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.runelite.api.WorldType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Account type and slayer task: a default read soon after login may be a value not yet sent. */
public class MutableReadHoldTest {

  private static final String STORED =
      "{\"accepted\":true,\"stored\":true,\"storedBlocks\":[\"skills\",\"accountType\","
          + "\"slayerTask\"]}";
  private static final SlayerTask TASK =
      new SlayerTask(true, "Cave horrors", 3, 120, "nieve", 1200, 55, null);

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private PluginHarness h;
  private int xp = 1_000;

  @Before
  public void setUp() throws Exception {
    h = new PluginHarness(folder.getRoot());
    h.toggles.put("collectQuests", false);
    h.toggles.put("collectSessions", false);
    // Unacked here, so its counts would force every submit.
    h.toggles.put("collectCombatAchievements", false);
    // Slayer reads go through onSkillsCaptured directly; the real reader needs a real client.
    h.toggles.put("collectSlayer", false);
    h.transport.byPath.put("/submit", body -> new HttpResponse(200, STORED));
  }

  private void logIn(int accountTypeVarbit) {
    h.varbits.put(AccountTypes.ACCOUNT_TYPE_VARBIT, accountTypeVarbit);
    h.logIn();
    h.executor.runAll();
  }

  /** A hop: same account and baselines, login clock restarted. */
  private void soonAfterLogin() {
    h.hopTo(EnumSet.of(WorldType.MEMBERS));
    h.executor.runAll();
  }

  private void capture() throws Exception {
    h.xp = ++xp;
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
  }

  private void slayer(SlayerTask task, boolean moveXp) throws Exception {
    if (moveXp) {
      xp++;
    }
    h.call(
        "onSkillsCaptured",
        PluginHarness.MAIN,
        h.generation(),
        "Zezima",
        Map.of("attack", xp),
        null,
        null,
        null,
        task,
        true);
    h.executor.runAll();
  }

  private List<JsonObject> snapshots() {
    List<JsonObject> out = new ArrayList<>();
    for (String body : h.transport.bodies) {
      JsonObject json = body == null ? null : new Gson().fromJson(body, JsonObject.class);
      if (json != null && json.has("skills")) {
        out.add(json);
      }
    }
    return out;
  }

  private JsonObject last() {
    List<JsonObject> all = snapshots();
    return all.get(all.size() - 1);
  }

  @Test
  public void anEarlyRegularAfterAnAckedIronIsHeldThenBelievedOnce() throws Exception {
    logIn(1);
    capture();
    assertEquals("ironman", last().get("accountType").getAsString());
    soonAfterLogin();
    h.varbits.put(AccountTypes.ACCOUNT_TYPE_VARBIT, 0);
    capture();
    assertFalse("held", last().has("accountType"));
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
    assertEquals("believed after the window", "regular", last().get("accountType").getAsString());
    capture();
    assertFalse("sent once", last().has("accountType"));
  }

  @Test
  public void aFreshPairingHoldsRegularUntilTheWindowEnds() throws Exception {
    logIn(0);
    capture();
    assertFalse(last().has("accountType"));
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
    assertEquals("regular", last().get("accountType").getAsString());
  }

  @Test
  public void aFreshPairingSendsAnIronTypeAtOnce() throws Exception {
    logIn(3);
    capture();
    assertEquals("hardcore", last().get("accountType").getAsString());
  }

  @Test
  public void anUnknownTypeIsNeverSentAsADefault() throws Exception {
    logIn(99);
    capture();
    assertFalse(last().has("accountType"));
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
    assertFalse(last().has("accountType"));
  }

  @Test
  public void anEarlyNoneAfterAnAckedTaskIsHeldThenBelieved() throws Exception {
    logIn(1);
    slayer(TASK, true);
    assertTrue("a real task goes at once", last().getAsJsonObject("slayerTask").get("active").getAsBoolean());
    soonAfterLogin();
    slayer(SlayerTask.NONE, true);
    assertFalse("held", last().has("slayerTask"));
    h.ticks(DiaryBaseline.HOLD_TICKS);
    slayer(SlayerTask.NONE, true);
    assertFalse(last().getAsJsonObject("slayerTask").get("active").getAsBoolean());
    slayer(SlayerTask.NONE, true);
    assertFalse("sent once", last().has("slayerTask"));
  }

  @Test
  public void aFreshLoginHoldsNoneUntilTheWindowEnds() throws Exception {
    logIn(1);
    slayer(SlayerTask.NONE, true);
    assertFalse(last().has("slayerTask"));
    h.ticks(DiaryBaseline.HOLD_TICKS);
    slayer(SlayerTask.NONE, true);
    assertFalse(last().getAsJsonObject("slayerTask").get("active").getAsBoolean());
  }

  @Test
  public void aCompletionInTheWindowIsStillReportedAndTheTaskClearsAfterIt() throws Exception {
    logIn(1);
    slayer(TASK, true);
    soonAfterLogin();
    SlayerTaskCompletionCapture completions =
        new SlayerTaskCompletionCapture(
            h.eventOutbox, () -> true, () -> PluginHarness.MAIN, () -> TASK, null);
    net.runelite.api.events.VarbitChanged moved = new net.runelite.api.events.VarbitChanged();
    moved.setVarpId(net.runelite.api.gameval.VarPlayerID.SLAYER_COUNT);
    moved.setValue(1);
    completions.onVarbitChanged(moved);
    completions.onMessage(
        "You've completed 56 tasks and received 15 points, giving you a total of 1215; return to a"
            + " Slayer master.");
    assertEquals(
        TransientEvent.TYPE_SLAYER_TASK_COMPLETED,
        h.eventOutbox.pending().get(0).getEvent().getType());
    slayer(SlayerTask.NONE, true);
    assertFalse("the snapshot's NONE waits for the window", last().has("slayerTask"));
    h.ticks(DiaryBaseline.HOLD_TICKS);
    slayer(SlayerTask.NONE, true);
    assertFalse(last().getAsJsonObject("slayerTask").get("active").getAsBoolean());
  }

  @Test
  public void aHeldReadNeverForcesASubmit() throws Exception {
    logIn(1);
    capture();
    slayer(TASK, true);
    soonAfterLogin();
    long before = h.transport.count("/submit");
    h.varbits.put(AccountTypes.ACCOUNT_TYPE_VARBIT, 0);
    slayer(SlayerTask.NONE, false);
    assertEquals(before, h.transport.count("/submit"));
  }
}
