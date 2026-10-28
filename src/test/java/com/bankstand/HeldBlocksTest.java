package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.SubmitSnapshotResponse;
import com.bankstand.http.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A block the server held, rather than wrote, is never acked, and is not re-sent unchanged. */
public class HeldBlocksTest {

  private static final String KEPT = "Kept saved progress";

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private PluginHarness h;
  private final long[] now = {1_000_000L};
  // What the next /submit answers; null loses the response.
  private String reply = stored("skills", "quests", "diaries", "combatAchievements");
  private int xp = 1_000;

  @Before
  public void setUp() throws Exception {
    h = start(folder.getRoot());
  }

  private PluginHarness start(File dir) throws Exception {
    PluginHarness harness = new PluginHarness(dir);
    harness.toggles.put("collectSessions", false);
    harness.toggles.put("collectSlayer", false);
    harness.toggles.put("collectAccountType", false);
    harness.transport.byPath.put(
        "/submit", body -> reply == null ? null : new HttpResponse(200, reply));
    harness.set("heldBlocks", new HeldBlocks(() -> now[0]));
    harness.logIn();
    harness.executor.runAll();
    // Past the diary early-read hold, so only the server decides.
    harness.ticks(DiaryBaseline.HOLD_TICKS);
    return harness;
  }

  private static String stored(String... blocks) {
    return response(blocks, null);
  }

  private static String response(String[] stored, String[] held) {
    JsonObject json = new JsonObject();
    json.addProperty("accepted", true);
    json.addProperty("stored", stored.length > 0);
    json.add("storedBlocks", new Gson().toJsonTree(stored));
    if (held != null) {
      json.add("heldBlocks", new Gson().toJsonTree(held));
    }
    return json.toString();
  }

  private static Map<String, String> quests(String state) {
    Map<String, String> quests = new LinkedHashMap<>();
    quests.put("COOKS_ASSISTANT", state);
    return quests;
  }

  private static Map<String, String> diaries(String medium) {
    Map<String, String> diaries = new LinkedHashMap<>();
    diaries.put("VARROCK_EASY", "COMPLETE");
    diaries.put("VARROCK_MEDIUM", medium);
    return diaries;
  }

  private static Map<String, Integer> counts(int medium) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    counts.put("VARROCK_EASY", 14);
    counts.put("VARROCK_MEDIUM", medium);
    return counts;
  }

  /** One capture through the real plugin path. A moved skill forces a submit. */
  private void capture(
      boolean moveSkills, Map<String, String> quests, Map<String, String> d, Map<String, Integer> c)
      throws Exception {
    if (moveSkills) {
      xp++;
    }
    Map<String, Integer> skills = new LinkedHashMap<>();
    skills.put("attack", xp);
    h.call(
        "onSkillsCaptured",
        h.hash, h.generation(), "Zezima", skills, quests, d, c, null, true);
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

  private String acked(String baseline) throws Exception {
    Object b = h.get(baseline);
    return (String) b.getClass().getDeclaredMethod("ackedDigest").invoke(b);
  }

  private AckedState persisted() {
    return h.ackedStore.load(h.hash);
  }

  @SuppressWarnings("unchecked")
  private Long syncedAt(Capability capability) throws Exception {
    return ((Map<String, Long>) h.get("lastSyncedAt")).get(capability.key);
  }

  private String row(Capability capability) throws Exception {
    PanelModel model = (PanelModel) h.call("buildPanelModel");
    for (PanelModel.CapabilityRow row : model.capabilities) {
      if (row.name.equals(capability.label)) {
        return PanelPresentation.capabilityStatus(row, now[0]);
      }
    }
    throw new AssertionError("no row for " + capability);
  }

  /** Quests acked at FINISHED, then a lower read held by the server. */
  private void questsHeld() throws Exception {
    capture(true, quests("FINISHED"), null, null);
    reply = response(new String[] {"skills"}, new String[] {"quests"});
    capture(true, quests("IN_PROGRESS"), null, null);
  }

  @Test
  public void heldBlocksAreParsedAndAbsentReadsAsEmpty() {
    Gson gson = new Gson();
    SubmitSnapshotResponse withHeld =
        gson.fromJson(response(new String[] {"skills"}, new String[] {"quests"}), SubmitSnapshotResponse.class);
    assertEquals(List.of("quests"), withHeld.getHeldBlocks());
    SubmitSnapshotResponse older = gson.fromJson(stored("skills"), SubmitSnapshotResponse.class);
    assertTrue(older.getHeldBlocks().isEmpty());
  }

  @Test
  public void heldQuestsAreNotAckedWhileStoredSkillsAre() throws Exception {
    capture(true, quests("FINISHED"), null, null);
    String questsAcked = acked("questBaseline");
    String skillsAcked = acked("skillBaseline");
    Long questsSynced = syncedAt(Capability.QUESTS);
    assertNotNull(questsAcked);

    reply = response(new String[] {"skills"}, new String[] {"quests"});
    capture(true, quests("IN_PROGRESS"), null, null);

    assertEquals("IN_PROGRESS", last().getAsJsonObject("quests").get("COOKS_ASSISTANT").getAsString());
    assertEquals(questsAcked, acked("questBaseline"));
    assertFalse(skillsAcked.equals(acked("skillBaseline")));
    assertEquals(questsAcked, persisted().getQuests());
    assertEquals(acked("skillBaseline"), persisted().getSkills());
    assertEquals(questsSynced, syncedAt(Capability.QUESTS));
    assertEquals(questsSynced, persisted().getLastSyncedAt().get(Capability.QUESTS.key));
    assertEquals(KEPT, row(Capability.QUESTS));
    assertEquals("just now", row(Capability.SKILLS));
  }

  @Test
  public void aHeldRowExplainsItselfAndIsNotAFailure() throws Exception {
    questsHeld();
    assertEquals(
        "This read was lower than what Bankstand already has, so it was not saved.",
        PanelPresentation.capabilityTooltip(
            new PanelModel.CapabilityRow("Quests", null, true)));
    assertNull(PanelPresentation.capabilityTooltip(new PanelModel.CapabilityRow("Quests", 1L, false)));
    PanelModel model = (PanelModel) h.call("buildPanelModel");
    assertEquals(PanelPresentation.SyncDot.GREEN, model.dot);
    assertNull(model.lastFailureReason);
  }

  @Test
  public void heldDiariesKeepTheFloorAndAreNotPersisted() throws Exception {
    capture(true, null, diaries("COMPLETE"), counts(13));
    AckedState before = persisted();
    assertNotNull(before.getDiaries());

    reply = response(new String[] {"skills"}, new String[] {"diaries", "diaryTasks"});
    capture(true, null, diaries("COMPLETE"), counts(12));
    assertEquals(12, last().getAsJsonObject("diaryTasks").get("VARROCK_MEDIUM").getAsInt());

    AckedState after = persisted();
    assertEquals(before.getDiaries(), after.getDiaries());
    assertEquals(before.getDiaryCounts(), after.getDiaryCounts());
    assertEquals(KEPT, row(Capability.DIARIES));

    // Soon after a hop, an early read below the kept floor is still held by the plugin itself.
    reply = stored("skills", "diaries");
    h.hopTo(EnumSet.of(WorldType.MEMBERS));
    h.executor.runAll();
    Map<String, String> partial = diaries("COMPLETE");
    partial.put("VARROCK_HARD", "COMPLETE");
    capture(true, null, partial, counts(12));
    assertFalse(last().has("diaries"));
    assertFalse(last().has("diaryTasks"));
  }

  @Test
  public void anUnchangedHeldReadIsNotUploadedAgainNorForcesASubmit() throws Exception {
    questsHeld();
    int sent = snapshots().size();
    for (int i = 0; i < 3; i++) {
      capture(false, quests("IN_PROGRESS"), null, null);
    }
    assertEquals("no forced submit", sent, snapshots().size());
    capture(true, quests("IN_PROGRESS"), null, null);
    assertFalse("the held block stays out", last().has("quests"));
    assertEquals(KEPT, row(Capability.QUESTS));
  }

  @Test
  public void laterValidDataIsStoredAndTheNormalAckResumes() throws Exception {
    questsHeld();
    String held = acked("questBaseline");
    reply = stored("skills", "quests");
    now[0] += 120_000L;
    capture(false, quests("FINISHED_AND_MORE"), null, null);
    assertTrue(last().has("quests"));
    assertFalse(held.equals(acked("questBaseline")));
    assertEquals(acked("questBaseline"), persisted().getQuests());
    assertEquals("just now", row(Capability.QUESTS));
    // Acked, so the same read is unchanged and not sent.
    int sent = snapshots().size();
    capture(false, quests("FINISHED_AND_MORE"), null, null);
    assertEquals(sent, snapshots().size());
  }

  @Test
  public void aDifferentLowerReadIsSentAndHeldAgain() throws Exception {
    questsHeld();
    capture(false, quests("NOT_STARTED"), null, null);
    assertEquals("NOT_STARTED", last().getAsJsonObject("quests").get("COOKS_ASSISTANT").getAsString());
    int sent = snapshots().size();
    // Now the new value is the held one; the old one would be sent again.
    capture(false, quests("NOT_STARTED"), null, null);
    assertEquals(sent, snapshots().size());
    capture(false, quests("IN_PROGRESS"), null, null);
    assertEquals(sent + 1, snapshots().size());
    assertEquals(KEPT, row(Capability.QUESTS));
  }

  @Test
  public void aRestartStartsWithNoHold() throws Exception {
    questsHeld();
    PluginHarness first = h;
    h = start(folder.getRoot());
    assertEquals(first.ackedStore.load(PluginHarness.MAIN).getQuests(), acked("questBaseline"));
    capture(false, quests("IN_PROGRESS"), null, null);
    assertTrue(last().has("quests"));
  }

  @Test
  public void aRelogOrAnAccountSwitchClearsTheHold() throws Exception {
    questsHeld();
    h.state(GameState.LOGIN_SCREEN);
    h.executor.runAll();
    h.hash = 77L;
    h.logIn();
    h.executor.runAll();
    h.ticks(DiaryBaseline.HOLD_TICKS);
    assertFalse("another account never shows it", KEPT.equals(row(Capability.QUESTS)));
    h.state(GameState.LOGIN_SCREEN);
    h.executor.runAll();
    h.hash = PluginHarness.MAIN;
    h.logIn();
    h.executor.runAll();
    h.ticks(DiaryBaseline.HOLD_TICKS);
    assertFalse(KEPT.equals(row(Capability.QUESTS)));
    capture(false, quests("IN_PROGRESS"), null, null);
    assertTrue(last().has("quests"));
  }

  @Test
  public void aRePairClearsTheHold() throws Exception {
    questsHeld();
    h.call("forgetAcks");
    assertFalse(KEPT.equals(row(Capability.QUESTS)));
    capture(false, quests("IN_PROGRESS"), null, null);
    assertTrue(last().has("quests"));
  }

  @Test
  public void aHoldOlderThanThirtyMinutesIsOfferedOnceMore() throws Exception {
    questsHeld();
    int sent = snapshots().size();
    now[0] += HeldBlocks.EXPIRY_MS - 1;
    capture(false, quests("IN_PROGRESS"), null, null);
    assertEquals(sent, snapshots().size());
    now[0] += 1;
    capture(false, quests("IN_PROGRESS"), null, null);
    assertEquals(sent + 1, snapshots().size());
    assertTrue(last().has("quests"));
    // Held again, so the clock restarts and it stays out.
    capture(false, quests("IN_PROGRESS"), null, null);
    assertEquals(sent + 1, snapshots().size());
  }

  @Test
  public void aLostResponseIsNeitherAckNorHold() throws Exception {
    capture(true, quests("FINISHED"), null, null);
    String questsAcked = acked("questBaseline");
    reply = null;
    capture(true, quests("IN_PROGRESS"), null, null);
    // Every retry is lost too, until the submit gives up.
    while (!h.executor.scheduled.isEmpty()) {
      h.executor.scheduled.remove(0).run();
      h.executor.runAll();
    }
    assertEquals(questsAcked, acked("questBaseline"));
    assertFalse(KEPT.equals(row(Capability.QUESTS)));
    reply = stored("skills");
    int sent = snapshots().size();
    capture(false, quests("IN_PROGRESS"), null, null);
    assertEquals("resent", sent + 1, snapshots().size());
    assertTrue(last().has("quests"));
  }

  @Test
  public void aDuplicateReplyIsNeitherAckNorHold() throws Exception {
    capture(true, quests("FINISHED"), null, null);
    String questsAcked = acked("questBaseline");
    reply = response(new String[0], new String[0]);
    capture(true, quests("IN_PROGRESS"), null, null);
    assertEquals(questsAcked, acked("questBaseline"));
    assertFalse(KEPT.equals(row(Capability.QUESTS)));
    int sent = snapshots().size();
    capture(false, quests("IN_PROGRESS"), null, null);
    assertEquals("resent", sent + 1, snapshots().size());
  }

  @Test
  public void diariesStoredWithTasksHeldCountsAsHeld() throws Exception {
    capture(true, null, diaries("COMPLETE"), counts(13));
    String diariesAcked = acked("diaryBaseline");
    Long synced = syncedAt(Capability.DIARIES);
    reply = response(new String[] {"skills", "diaries"}, new String[] {"diaryTasks"});
    now[0] += 120_000L;
    capture(true, null, diaries("COMPLETE"), counts(12));
    assertEquals(diariesAcked, acked("diaryBaseline"));
    assertEquals(synced, syncedAt(Capability.DIARIES));
    assertEquals(KEPT, row(Capability.DIARIES));
    int sent = snapshots().size();
    capture(false, null, diaries("COMPLETE"), counts(12));
    assertEquals(sent, snapshots().size());
  }

  @Test
  public void aHeldLampClaimHoldsCombatAchievements() throws Exception {
    h.varbits.put(CombatAchievementVarbits.ALL.values().iterator().next(), 5);
    capture(true, null, null, null);
    String caAcked = acked("combatAchievementBaseline");
    h.varbits.put(CombatAchievementVarbits.ALL.values().iterator().next(), 4);
    reply = response(new String[] {"skills", "combatAchievements"}, new String[] {"combatAchievementLampClaimed"});
    capture(true, null, null, null);
    assertTrue(last().has("combatAchievements"));
    assertEquals(caAcked, acked("combatAchievementBaseline"));
    assertEquals(KEPT, row(Capability.COMBAT_ACHIEVEMENTS));
    capture(true, null, null, null);
    assertFalse(last().has("combatAchievements"));
  }

  @Test
  public void heldDiaryTaskVarsAreNotAckedNorResent() throws Exception {
    h.varps.put(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, 0b111);
    h.varbits.put(VarbitID.VARROCK_EASY_COUNT, 14);
    reply = stored("skills", "diaryTaskVars");
    capture(true, null, null, null);
    String varsAcked = (String) h.get("diaryTaskVarsAcked");
    assertNotNull(varsAcked);
    h.varps.put(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, 0b011);
    reply = response(new String[] {"skills"}, new String[] {"diaryTaskVars"});
    capture(true, null, null, null);
    assertTrue(last().has("diaryTaskVars"));
    assertEquals(varsAcked, h.get("diaryTaskVarsAcked"));
    assertEquals(varsAcked, persisted().getDiaryTaskVars());
    capture(true, null, null, null);
    assertFalse(last().has("diaryTaskVars"));
  }

  @Test
  public void anOlderServerWithoutHeldBlocksAcksAsBefore() throws Exception {
    capture(true, quests("FINISHED"), null, null);
    reply = stored("skills", "quests");
    capture(true, quests("IN_PROGRESS"), null, null);
    assertEquals(acked("questBaseline"), persisted().getQuests());
    assertEquals("just now", row(Capability.QUESTS));
    int sent = snapshots().size();
    capture(false, quests("IN_PROGRESS"), null, null);
    assertEquals(sent, snapshots().size());
  }
}
