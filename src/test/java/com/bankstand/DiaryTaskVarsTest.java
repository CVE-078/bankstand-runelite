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
import java.util.Set;
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
    assertEquals(39 + 48 + 48, vars.getAsJsonObject("varbits").size());
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
  public void aReadBeforeTheVarsArriveNeverOverwritesKnownState() throws Exception {
    h.varbits.put(VarbitID.FREMENNIK_MED_COUNT, 8);
    capture();
    assertTrue(last().has("diaryTasks"));
    // A relog whose diary values have not arrived yet reads all zeroes.
    h.state(GameState.LOGIN_SCREEN);
    h.varps.clear();
    h.varbits.clear();
    h.logIn();
    h.executor.runAll();
    h.xp = 2_000;
    capture();
    assertEquals(2, snapshots().size());
    for (String block : new String[] {"diaries", "diaryTasks", "diaryTaskVars"}) {
      assertFalse(block, last().has(block));
    }
    // Once they arrive, the real values go out.
    h.varps.put(VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY, 0b11111111);
    h.varbits.put(VarbitID.FREMENNIK_MED_COUNT, 9);
    h.xp = 3_000;
    capture();
    assertTrue(last().has("diaryTaskVars"));
  }

  @Test
  public void anAllZeroBlockIsNeverSentEvenForANewCharacter() throws Exception {
    h.varps.clear();
    h.varbits.clear();
    capture();
    assertFalse("unknown beats a zero that may be too early", last().has("diaryTaskVars"));
    assertTrue("a loaded, empty baseline: the zero counts are real", last().has("diaries"));
  }

  @Test
  public void aZeroReadBeforeThisLoginsLoadFinishesSendsNoDiaryBlock() throws Exception {
    h.state(GameState.LOGIN_SCREEN);
    h.executor.runAll();
    h.hash = 77L;
    h.varps.clear();
    h.varbits.clear();
    h.logIn();
    // Queued at LOGGED_IN, before the identity submit the settle ticks queue.
    Runnable load = h.executor.take();
    h.executor.runAll();
    h.xp = 2_000;
    capture();
    for (String block : new String[] {"diaries", "diaryTasks", "diaryTaskVars"}) {
      assertFalse(block, last().has(block));
    }
    load.run();
    h.executor.runAll();
    h.varbits.put(VarbitID.FREMENNIK_MED_COUNT, 2);
    h.xp = 3_000;
    capture();
    assertTrue("a real read after the load goes out", last().has("diaryTasks"));
  }

  @Test
  public void aZeroReadAfterAFailedLoadSendsNoDiaryBlock() throws Exception {
    java.nio.file.Files.write(
        new File(dir, "acked.json").toPath(), "{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    h = start();
    h.varps.clear();
    h.varbits.clear();
    h.xp = 2_000;
    capture();
    for (String block : new String[] {"diaries", "diaryTasks", "diaryTaskVars"}) {
      assertFalse(block, last().has(block));
    }
    h.varbits.put(VarbitID.FREMENNIK_MED_COUNT, 2);
    h.xp = 3_000;
    capture();
    assertTrue(last().has("diaryTasks"));
  }

  /** Logs a new character in while the acked-state file cannot be read. */
  private File unreadableLogin() throws Exception {
    File acked = new File(dir, "acked.json");
    h.state(GameState.LOGIN_SCREEN);
    h.executor.runAll();
    org.junit.Assume.assumeTrue(acked.setReadable(false) && !acked.canRead());
    h.hash = 77L;
    h.varps.clear();
    h.varbits.clear();
    h.logIn();
    h.executor.runAll();
    h.xp = 2_000;
    capture();
    assertFalse("unknown baseline: no zero diaries", last().has("diaries"));
    return acked;
  }

  @Test
  public void aLaterSuccessfulReadLiftsTheFailedLoad() throws Exception {
    capture();
    File acked = unreadableLogin();
    acked.setReadable(true);
    h.xp = 3_000;
    capture();
    h.xp = 4_000;
    capture();
    assertTrue("a new character's real zeroes, once the baseline is known", last().has("diaries"));
    assertFalse(last().has("diaryTaskVars"));
  }

  @Test
  public void aRetriedLoadNeverWipesLiveState() throws Exception {
    capture();
    File acked = unreadableLogin();
    CollectionLogAccumulator log = (CollectionLogAccumulator) h.get("collectionLog");
    QuestBaseline quests = (QuestBaseline) h.get("questBaseline");
    try {
      log.observe(13262);
      quests.restore("just-acked");
      h.xp = 3_000;
      capture(); // its retry fails again
      assertTrue(log.observed().contains(13262));
      assertEquals("just-acked", quests.ackedDigest());
    } finally {
      acked.setReadable(true);
    }
    h.xp = 4_000;
    capture(); // this retry succeeds, after live state exists: merged, never cleared
    assertTrue(log.observed().contains(13262));
    assertEquals("just-acked", quests.ackedDigest());
  }

  private Set<Integer> storedLog() {
    return new AckedStateStore(new File(dir, "acked.json"), new Gson())
        .load(PluginHarness.MAIN)
        .getCollectionLogItems();
  }

  private void observe(int item) throws Exception {
    ((CollectionLogAccumulator) h.get("collectionLog")).observe(item);
  }

  private Set<Integer> observed() throws Exception {
    return ((CollectionLogAccumulator) h.get("collectionLog")).observed();
  }

  @Test
  public void aSaveDuringAFailedLoginLoadNeverErasesTheStoredLog() throws Exception {
    observe(13262);
    capture();
    assertTrue(storedLog().contains(13262));
    File acked = new File(dir, "acked.json");
    org.junit.Assume.assumeTrue(acked.setReadable(false) && !acked.canRead());
    try {
      h = start();
      h.xp = 2_000;
      capture(); // acked, while this login's load has failed
    } finally {
      acked.setReadable(true);
    }
    h.xp = 3_000;
    capture(); // the retry loads
    h.xp = 4_000;
    capture(); // and the next save writes the merged log
    assertTrue(observed().contains(13262));
    assertTrue(storedLog().contains(13262));
  }

  @Test
  public void aRePairDuringTheLoginLoadStillRestoresTheStoredLog() throws Exception {
    observe(13262);
    capture();
    PluginHarness next = new PluginHarness(dir);
    next.toggles.put("collectQuests", false);
    next.toggles.put("collectSessions", false);
    next.transport.byPath.put(
        "/pair", body -> new HttpResponse(200, "{\"deviceToken\":\"tok-b\",\"deviceId\":\"d\"}"));
    next.logIn();
    Runnable load = next.executor.take();
    next.call("pair", "BBBB-BBBB");
    next.executor.runAll();
    load.run();
    next.executor.runAll();
    h = next;
    assertTrue(observed().contains(13262));
  }

  @Test
  public void aRePairKeepsTheNeedToLoad() throws Exception {
    h.transport.byPath.put(
        "/pair", body -> new HttpResponse(200, "{\"deviceToken\":\"tok-b\",\"deviceId\":\"d\"}"));
    capture();
    File acked = unreadableLogin();
    try {
      h.call("pair", "BBBB-BBBB");
      h.executor.runAll();
      h.xp = 3_000;
      capture();
      assertFalse("the stored log is still unread", last().has("diaries"));
    } finally {
      acked.setReadable(true);
    }
    h.xp = 4_000;
    capture(); // the retry loads
    h.xp = 5_000;
    capture();
    assertTrue("nothing is acked on the new pairing", last().has("diaries"));
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
