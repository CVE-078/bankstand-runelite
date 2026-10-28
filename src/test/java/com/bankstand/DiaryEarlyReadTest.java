package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.http.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Diary reads taken before the game has sent every value must never lower what was stored. */
public class DiaryEarlyReadTest {

  private static final String STORED =
      "{\"accepted\":true,\"stored\":true,\"storedBlocks\":[\"skills\",\"diaries\","
          + "\"combatAchievements\",\"accountType\",\"slayerTask\",\"diaryTaskVars\"]}";
  private static final String[] DIARY_BLOCKS = {"diaries", "diaryTasks", "diaryTaskVars"};

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private PluginHarness h;
  private int xp = 1_000;

  @Before
  public void setUp() throws Exception {
    h = start(true);
    // Nothing is acked yet, so the first read waits out the hold.
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
  }

  /** A plugin over the same state folder, as after a client restart. */
  private PluginHarness start(boolean loaded) throws Exception {
    PluginHarness harness = new PluginHarness(folder.getRoot());
    harness.toggles.put("collectQuests", false);
    harness.toggles.put("collectSessions", false);
    // The slayer read needs a real client once the login is old enough.
    harness.toggles.put("collectSlayer", false);
    harness.transport.byPath.put("/submit", body -> new HttpResponse(200, STORED));
    if (loaded) {
      progress(harness);
    }
    harness.xp = xp;
    harness.logIn();
    harness.executor.runAll();
    return harness;
  }

  /** Account A: Varrock easy and medium done, three easy task bits set. */
  private static void progress(PluginHarness harness) {
    harness.varbits.put(DiaryVarbits.ALL.get("VARROCK_EASY"), 1);
    harness.varbits.put(DiaryVarbits.ALL.get("VARROCK_MEDIUM"), 1);
    harness.varbits.put(VarbitID.VARROCK_EASY_COUNT, 14);
    harness.varbits.put(VarbitID.VARROCK_MED_COUNT, 13);
    harness.varps.put(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, 0b111);
  }

  private static void zeroes(PluginHarness harness) {
    harness.varps.clear();
    harness.varbits.clear();
  }

  /** One periodic capture with moved XP, so a submit always goes out. */
  private void capture() throws Exception {
    h.xp = ++xp;
    h.plugin.captureSkills();
    h.runClientThread();
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

  private void assertNoDiaryBlock(JsonObject body) {
    for (String block : DIARY_BLOCKS) {
      assertFalse(block + " in " + body, body.has(block));
    }
  }

  private static String flag(JsonObject body, String tier) {
    return body.getAsJsonObject("diaries").get(tier).getAsString();
  }

  private static int count(JsonObject body, String tier) {
    return body.getAsJsonObject("diaryTasks").get(tier).getAsInt();
  }

  /** A hop: same account and baselines, with the login clock restarted. */
  private void soonAfterLogin() {
    h.hopTo(EnumSet.of(WorldType.MEMBERS));
    h.executor.runAll();
  }

  private void relog(long hash) throws Exception {
    h.state(GameState.LOGIN_SCREEN);
    h.executor.runAll();
    h.hash = hash;
    zeroes(h);
    h.logIn();
    h.executor.runAll();
  }

  @Test
  public void aStepAckedProgressIsWhatTheFirstReadSent() {
    JsonObject first = last();
    assertEquals("COMPLETE", flag(first, "VARROCK_MEDIUM"));
    assertEquals(13, count(first, "VARROCK_MEDIUM"));
    assertTrue(first.has("diaryTaskVars"));
  }

  @Test
  public void bAndCStepAllZeroAfterTheSettleSendsNoDiaryBlockAndTheLaterRealReadNothingNew()
      throws Exception {
    h = start(false);
    capture();
    assertEquals(1, snapshots().size());
    assertNoDiaryBlock(last());
    progress(h);
    capture();
    assertEquals(2, snapshots().size());
    assertNoDiaryBlock(last());
  }

  @Test
  public void dStepAPartialLoadAfterARestartSendsNoDiaryBlock() throws Exception {
    h = start(false);
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_EASY"), 1);
    h.varbits.put(VarbitID.VARROCK_EASY_COUNT, 14);
    h.varps.put(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, 0b001);
    capture();
    assertNoDiaryBlock(last());
    // Everything arrives: same as stored, so still nothing to send.
    progress(h);
    capture();
    assertNoDiaryBlock(last());
    // Real progress goes out as before.
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_HARD"), 1);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_HARD"));
    assertEquals("COMPLETE", flag(last(), "VARROCK_MEDIUM"));
  }

  @Test
  public void dStepAPartialLoadAfterARelogSendsNoDiaryBlock() throws Exception {
    relog(PluginHarness.MAIN);
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_EASY"), 1);
    h.varbits.put(VarbitID.VARROCK_EASY_COUNT, 14);
    capture();
    assertNoDiaryBlock(last());
  }

  @Test
  public void dStepOnlyTheTaskVarsLaggingStillHoldsTheWholeDiaryRead() throws Exception {
    soonAfterLogin();
    progress(h);
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_HARD"), 1);
    h.varps.put(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, 0b001);
    capture();
    assertNoDiaryBlock(last());
    h.varps.put(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, 0b111);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_HARD"));
    assertFalse("the vars equal what was acked", last().has("diaryTaskVars"));
  }

  @Test
  public void dStepALowerCountAloneHoldsTheBlock() throws Exception {
    soonAfterLogin();
    h.varbits.put(VarbitID.VARROCK_MED_COUNT, 2);
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_HARD"), 1);
    capture();
    assertNoDiaryBlock(last());
  }

  @Test
  public void aHeldReadNeverCausesASubmitOfItsOwn() throws Exception {
    soonAfterLogin();
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_MEDIUM"), 0);
    for (int i = 0; i < 3; i++) {
      h.plugin.captureSkills();
      h.runClientThread();
      h.executor.runAll();
    }
    assertEquals(1, snapshots().size());
  }

  @Test
  public void aCountRiseWithoutAFlagChangeResendsTheDiaryBlocksOnce() throws Exception {
    h.varbits.put(VarbitID.VARROCK_HARD_COUNT, 3);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_MEDIUM"));
    assertEquals(3, count(last(), "VARROCK_HARD"));
    capture();
    assertNoDiaryBlock(last());
  }

  @Test
  public void aHeldCountRiseNeverCausesASubmitOfItsOwn() throws Exception {
    soonAfterLogin();
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_MEDIUM"), 0);
    h.varbits.put(VarbitID.VARROCK_HARD_COUNT, 3);
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
    assertEquals(1, snapshots().size());
  }

  @Test
  public void aLowerReadLongAfterLoginIsBelievedSoTheHoldIsBounded() throws Exception {
    h.varbits.put(VarbitID.VARROCK_MED_COUNT, 12);
    h.ticks(DiaryBaseline.HOLD_TICKS);
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_HARD"), 1);
    capture();
    assertEquals(12, count(last(), "VARROCK_MEDIUM"));
    // Acked, so the lower value is the new floor and nothing repeats.
    capture();
    assertNoDiaryBlock(last());
  }

  @Test
  public void eStepASwitchNeverCarriesOneAccountsDiaryToTheOther() throws Exception {
    relog(77L);
    capture();
    JsonObject b = last();
    if (b.has("diaries")) {
      assertFalse("A's state under B", b.getAsJsonObject("diaries").toString().contains("\"COMPLETE\""));
    }
    assertFalse(b.has("diaryTaskVars"));
    relog(PluginHarness.MAIN);
    capture();
    assertNoDiaryBlock(last());
    progress(h);
    capture();
    assertNoDiaryBlock(last());
  }

  @Test
  public void eStepAHopWithZeroesSendsNoDiaryBlock() throws Exception {
    zeroes(h);
    h.hopTo(EnumSet.of(WorldType.MEMBERS));
    h.executor.runAll();
    capture();
    assertNoDiaryBlock(last());
  }

  @Test
  public void iStepANewAccountsZeroReadGoesOnceAfterTheHold() throws Exception {
    relog(77L);
    capture();
    assertNoDiaryBlock(last());
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
    JsonObject b = last();
    assertEquals("INCOMPLETE", flag(b, "VARROCK_EASY"));
    assertEquals(0, count(b, "VARROCK_EASY"));
    assertFalse("all-zero vars stay unknown", b.has("diaryTaskVars"));
    capture();
    assertNoDiaryBlock(last());
  }

  @Test
  public void gStepNoAckedStateHoldsEvenRealValuesUntilTheWindowEnds() throws Exception {
    relog(77L);
    capture();
    assertNoDiaryBlock(last());
    progress(h);
    capture();
    assertNoDiaryBlock(last());
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_MEDIUM"));
    assertEquals(13, count(last(), "VARROCK_MEDIUM"));
    assertTrue(last().has("diaryTaskVars"));
    capture();
    assertNoDiaryBlock(last());
  }

  @Test
  public void hStepNoAckedStateHoldsAPartialLoadThenSendsTheCurrentRead() throws Exception {
    relog(77L);
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_EASY"), 1);
    h.varbits.put(VarbitID.VARROCK_EASY_COUNT, 14);
    capture();
    assertNoDiaryBlock(last());
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_EASY"));
    assertEquals("INCOMPLETE", flag(last(), "VARROCK_MEDIUM"));
  }

  @Test
  public void aHeldUnknownBaselineReadNeverCausesASubmitOfItsOwn() throws Exception {
    relog(77L);
    capture();
    int sent = snapshots().size();
    progress(h);
    for (int i = 0; i < 3; i++) {
      h.plugin.captureSkills();
      h.runClientThread();
      h.executor.runAll();
    }
    assertEquals(sent, snapshots().size());
  }

  @Test
  public void aRePairThenAnEarlyZeroReadSendsNoDiaryBlock() throws Exception {
    h.transport.byPath.put(
        "/pair", body -> new HttpResponse(200, "{\"deviceToken\":\"tok-b\",\"deviceId\":\"d\"}"));
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();
    relog(PluginHarness.MAIN);
    capture();
    assertNoDiaryBlock(last());
  }

  // Fresh pairing: what the client sends for a character with nothing acked. Evidence only.

  private static int varp(JsonObject body) {
    return body.getAsJsonObject("diaryTaskVars").getAsJsonObject("varps")
        .get(Integer.toString(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY)).getAsInt();
  }

  private static int reward(JsonObject body) {
    return body.getAsJsonObject("diaryTaskVars").getAsJsonObject("varbits")
        .get(Integer.toString(VarbitID.VARROCK_EASY_REWARD)).getAsInt();
  }

  @Test
  public void freshPartialValuesStillThereAtTick200AreSentAsReadThenTheFullReadFollows()
      throws Exception {
    relog(77L);
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_EASY"), 1);
    h.varbits.put(VarbitID.VARROCK_EASY_COUNT, 14);
    h.varps.put(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, 0b001);
    h.ticks(DiaryBaseline.HOLD_TICKS - CaptureGate.SETTLE_TICKS - 1);
    capture();
    assertNoDiaryBlock(last());
    h.ticks(1);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_EASY"));
    assertEquals("INCOMPLETE", flag(last(), "VARROCK_MEDIUM"));
    assertEquals(0, count(last(), "VARROCK_MEDIUM"));
    assertEquals(0b001, varp(last()));
    progress(h);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_MEDIUM"));
    assertEquals(13, count(last(), "VARROCK_MEDIUM"));
    assertEquals(0b111, varp(last()));
  }

  @Test
  public void freshCountsBeforeTaskBitsSendZeroBitsThenTheBitsRideTheNextSubmit()
      throws Exception {
    relog(77L);
    h.ticks(DiaryBaseline.HOLD_TICKS);
    h.varbits.put(VarbitID.VARROCK_EASY_COUNT, 14);
    capture();
    assertEquals(14, count(last(), "VARROCK_EASY"));
    assertEquals(0, varp(last()));
    h.varps.put(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, 0b111);
    capture();
    assertFalse("flags and counts unchanged", last().has("diaries"));
    assertEquals(0b111, varp(last()));
  }

  @Test
  public void freshTaskBitsBeforeCountsSendZeroCountsThenTheCounts() throws Exception {
    relog(77L);
    h.ticks(DiaryBaseline.HOLD_TICKS);
    h.varps.put(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, 0b111);
    capture();
    assertEquals("INCOMPLETE", flag(last(), "VARROCK_EASY"));
    assertEquals(0, count(last(), "VARROCK_EASY"));
    assertEquals(0b111, varp(last()));
    h.varbits.put(VarbitID.VARROCK_EASY_COUNT, 14);
    capture();
    assertEquals(14, count(last(), "VARROCK_EASY"));
    assertEquals(14, last().getAsJsonObject("diaryTaskVars").getAsJsonObject("varbits")
        .get(Integer.toString(VarbitID.VARROCK_EASY_COUNT)).getAsInt());
  }

  @Test
  public void freshTierFlagAndRewardAfterTheCountsEachGoOutWhenTheyLand() throws Exception {
    relog(77L);
    h.ticks(DiaryBaseline.HOLD_TICKS);
    h.varbits.put(VarbitID.VARROCK_EASY_COUNT, 14);
    capture();
    assertEquals("INCOMPLETE", flag(last(), "VARROCK_EASY"));
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_EASY"), 1);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_EASY"));
    assertFalse("tier flags are not in the rider", last().has("diaryTaskVars"));
    h.varbits.put(VarbitID.VARROCK_EASY_REWARD, 1);
    capture();
    assertFalse("a reward alone only changes the rider", last().has("diaries"));
    assertEquals(1, reward(last()));
  }

  @Test
  public void freshATierFlagBeforeItsCountSendsCompleteWithACountOfZero() throws Exception {
    relog(77L);
    h.ticks(DiaryBaseline.HOLD_TICKS);
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_EASY"), 1);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_EASY"));
    assertEquals(0, count(last(), "VARROCK_EASY"));
  }

  @Test
  public void freshAHopDuringTheHoldRestartsTheWindow() throws Exception {
    relog(77L);
    progress(h);
    h.ticks(150);
    soonAfterLogin();
    h.ticks(100);
    capture();
    assertNoDiaryBlock(last());
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_MEDIUM"));
  }

  @Test
  public void freshALogoutDuringTheHoldRestartsTheWindow() throws Exception {
    relog(77L);
    h.ticks(150);
    relog(77L);
    progress(h);
    h.ticks(100);
    capture();
    assertNoDiaryBlock(last());
  }

  @Test
  public void freshASwitchDuringTheHoldNeverSendsTheFirstAccountsDiaryUnderTheSecond()
      throws Exception {
    relog(77L);
    progress(h);
    h.ticks(150);
    capture();
    relog(88L);
    h.varbits.put(DiaryVarbits.ALL.get("VARROCK_EASY"), 1);
    h.varbits.put(VarbitID.VARROCK_EASY_COUNT, 14);
    h.ticks(100);
    capture();
    assertNoDiaryBlock(last());
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
    assertEquals("88", last().get("accountHash").getAsString());
    assertEquals("COMPLETE", flag(last(), "VARROCK_EASY"));
    assertEquals("INCOMPLETE", flag(last(), "VARROCK_MEDIUM"));
    for (JsonObject body : snapshots()) {
      if ("77".equals(body.get("accountHash").getAsString())) {
        assertNoDiaryBlock(body);
      }
    }
  }

  @Test
  public void freshARestartBeforeTheFirstAckHoldsAgain() throws Exception {
    h.transport.byPath.put("/submit", body -> new HttpResponse(400, ""));
    relog(77L);
    progress(h);
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
    assertEquals("sent but refused", "COMPLETE", flag(last(), "VARROCK_MEDIUM"));
    h = start(false);
    relog(77L);
    progress(h);
    capture();
    assertNoDiaryBlock(last());
    h.ticks(DiaryBaseline.HOLD_TICKS);
    capture();
    assertEquals("COMPLETE", flag(last(), "VARROCK_MEDIUM"));
  }

  @Test
  public void freshEarlyReadsOfSkillsAndCombatAchievementsAreNotHeld()
      throws Exception {
    relog(77L);
    h.xp = 0;
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
    JsonObject body = last();
    assertNoDiaryBlock(body);
    assertEquals(0, body.getAsJsonObject("skills").getAsJsonObject("attack").get("xp").getAsInt());
    // An ironman whose varbit has not arrived reads as regular, so regular waits out the window.
    assertFalse(body.has("accountType"));
    assertEquals(0, body.getAsJsonObject("combatAchievements").get("easy").getAsInt());
  }
}
