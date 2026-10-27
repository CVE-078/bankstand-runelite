package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bankstand.http.HttpResponse;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
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

/** The raw per-task diary values: read with the diary counts, sent only as a rider. */
public class DiaryTaskVarsTest {

  private static final String OTHER_BLOCKS =
      "[\"skills\",\"diaries\",\"combatAchievements\",\"accountType\",\"slayerTask\"";
  private static final String STORED_WITH_VARS =
      "{\"accepted\":true,\"stored\":true,\"storedBlocks\":" + OTHER_BLOCKS + ",\"diaryTaskVars\"]}";
  // A server from before this field: it strips the block and never acks it.
  private static final String STORED_WITHOUT_VARS =
      "{\"accepted\":true,\"stored\":true,\"storedBlocks\":" + OTHER_BLOCKS + "]}";

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private File dir;
  private PluginHarness h;
  private String reply = STORED_WITH_VARS;

  @Before
  public void setUp() throws Exception {
    dir = folder.getRoot();
    h = start();
  }

  private PluginHarness start() throws Exception {
    PluginHarness harness = new PluginHarness(dir);
    // Quest reads run a client script the fake client cannot answer; sessions add requests.
    harness.toggles.put("collectQuests", false);
    harness.toggles.put("collectSessions", false);
    harness.transport.byPath.put("/submit", body -> new HttpResponse(200, reply));
    harness.varps.put(VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY, 0b1011);
    harness.varbits.put(VarbitID.ATJUN_EASY_BANANA, 5);
    harness.varbits.put(VarbitID.WESTERN_EASY_REWARD, 1);
    harness.xp = 1_000;
    harness.logIn();
    harness.executor.runAll();
    return harness;
  }

  /** One periodic capture, run to completion. */
  private void capture() throws Exception {
    h.plugin.captureSkills();
    h.runClientThread();
    h.executor.runAll();
  }

  /** The snapshot submissions so far (identity submits carry no skills). */
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
  public void theFirstReadSendsTheWholeAllowlistAndNoEvents() throws Exception {
    capture();
    JsonObject vars = last().getAsJsonObject("diaryTaskVars");
    assertNotNull(vars);
    assertEquals(23, vars.getAsJsonObject("varps").size());
    assertEquals(39 + 48, vars.getAsJsonObject("varbits").size());
    assertEquals(0b1011, vars.getAsJsonObject("varps").get("1184").getAsInt());
    assertEquals(5, vars.getAsJsonObject("varbits").get("3566").getAsInt());
    assertEquals(1, vars.getAsJsonObject("varbits").get("4511").getAsInt());
    assertTrue("per-tier counts keep their own field", last().has("diaryTasks"));
    assertTrue("no events from this path", h.eventOutbox.pending().isEmpty());
  }

  @Test
  public void aChangeRidesTheNextSubmit() throws Exception {
    capture();
    h.varps.put(VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY, 0b1111);
    h.xp = 2_000;
    capture();
    assertEquals(2, snapshots().size());
    assertEquals(0b1111, last().getAsJsonObject("diaryTaskVars").getAsJsonObject("varps").get("1184").getAsInt());
  }

  @Test
  public void anUnchangedBlockStaysOff() throws Exception {
    capture();
    h.xp = 2_000;
    capture();
    assertEquals(2, snapshots().size());
    assertFalse(last().has("diaryTaskVars"));
  }

  @Test
  public void aChangeAloneNeverForcesASubmit() throws Exception {
    capture();
    h.varps.put(VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY, 0b1111);
    capture();
    assertEquals(1, snapshots().size());
  }

  @Test
  public void aServerThatStripsTheBlockCausesNoExtraSubmits() throws Exception {
    reply = STORED_WITHOUT_VARS;
    capture();
    capture();
    capture();
    assertEquals(1, snapshots().size());
    // Unacked, it keeps riding on submits that happen anyway.
    h.xp = 2_000;
    capture();
    assertEquals(2, snapshots().size());
    assertTrue(last().has("diaryTaskVars"));
  }

  @Test
  public void anAccountSwitchResendsTheBlock() throws Exception {
    capture();
    h.state(GameState.LOGIN_SCREEN);
    h.hash = 77L;
    h.logIn();
    h.executor.runAll();
    capture();
    assertEquals(2, snapshots().size());
    assertTrue(last().has("diaryTaskVars"));
  }

  @Test
  public void aRePairResendsTheBlock() throws Exception {
    h.transport.byPath.put(
        "/pair", body -> new HttpResponse(200, "{\"deviceToken\":\"tok-b\",\"deviceId\":\"d\"}"));
    capture();
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();
    h.xp = 2_000;
    capture();
    assertTrue(last().has("diaryTaskVars"));
  }

  @Test
  public void aRestartRemembersWhatWasAcked() throws Exception {
    capture();
    h = start();
    h.xp = 2_000;
    capture();
    assertEquals(1, snapshots().size());
    assertFalse(last().has("diaryTaskVars"));
    assertNotNull(h.ackedStore.load(PluginHarness.MAIN).getDiaryTaskVars());
  }

  @Test
  public void theLogoutReadSendsSkillsButNoVarBasedBlock() throws Exception {
    h.varbits.put(VarbitID.ARDOUGNE_EASY_COUNT, 3);
    capture();
    h.ticks(1);
    // A cleared client: every varp and varbit reads zero, but XP moved on.
    h.varps.clear();
    h.varbits.clear();
    h.xp = 2_000;
    h.state(GameState.LOGIN_SCREEN);
    h.executor.runAll();
    assertEquals(2, snapshots().size());
    JsonObject logout = last();
    assertTrue(logout.has("skills"));
    for (String block :
        new String[] {
          "diaries", "diaryTasks", "diaryTaskVars", "combatAchievements",
          "combatAchievementBossCounts", "accountType", "quests", "slayerTask"
        }) {
      assertFalse(block, logout.has(block));
    }
  }

  @Test
  public void anExcludedWorldReadsAndSendsNothing() throws Exception {
    h.hopTo(EnumSet.of(WorldType.SEASONAL, WorldType.MEMBERS));
    h.executor.runAll();
    capture();
    assertTrue(snapshots().isEmpty());
  }

  @Test
  public void aServerManifestWithoutTheBlockKeepsItOff() throws Exception {
    CapabilityManifest.RawManifest raw = new CapabilityManifest.RawManifest();
    raw.schemaVersion = CapabilityManifest.SUPPORTED_SCHEMA_VERSION;
    raw.capabilities = new ArrayList<>(CapabilityManifest.SUPPORTED_CAPABILITIES);
    raw.capabilities.remove("diaryTaskVars");
    raw.uploadIntervalSeconds = 300;
    h.set("manifest", CapabilityManifest.validate(raw));
    capture();
    assertNull(last().get("diaryTaskVars"));
  }
}
