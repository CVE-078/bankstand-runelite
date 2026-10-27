package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bankstand.http.HttpResponse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A pairing, a disconnect and a slow reply from the pairing before them, in every order. */
public class PairingReplacementTest {

  private static final String ALL_BLOCKS =
      "{\"accepted\":true,\"stored\":true,\"storedBlocks\":"
          + "[\"skills\",\"quests\",\"diaries\",\"collectionLog\",\"combatAchievements\"]}";

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private PluginHarness h;

  private final Map<String, Integer> skills = map("attack", 100);
  private final Map<String, String> quests = map("Cook's Assistant", "FINISHED");
  private final Map<String, String> diaries = map("Varrock Easy", "COMPLETE");
  private final Map<String, Integer> caCounts = map("easy", 3);
  private final Set<Integer> clog = new LinkedHashSet<>(Collections.singletonList(13262));

  private static <V> Map<String, V> map(String key, V value) {
    Map<String, V> m = new LinkedHashMap<>();
    m.put(key, value);
    return m;
  }

  @Before
  public void setUp() throws Exception {
    h = new PluginHarness(folder.getRoot());
    // Each code pairs to its own token, so the test can see which reply was kept.
    h.transport.byPath.put(
        "/pair",
        body -> {
          String code = body.contains("AAAAAAAA") ? "a" : "b";
          return new HttpResponse(
              200, "{\"deviceToken\":\"tok-" + code + "\",\"deviceId\":\"d\",\"expiresAt\":null}");
        });
    h.transport.byPath.put("/submit", body -> new HttpResponse(200, ALL_BLOCKS));
    h.logIn();
    h.executor.runAll();
  }

  private void submitSnapshot() throws Exception {
    h.call(
        "submitSnapshot",
        caCounts,
        null,
        PluginHarness.MAIN,
        h.generation(),
        "Zezima",
        skills,
        quests,
        diaries,
        null,
        clog,
        null,
        false,
        null,
        null,
        null);
  }

  private String token() {
    return h.deviceStore.load().getToken();
  }

  private boolean questsAcked() throws Exception {
    return !((QuestBaseline) h.get("questBaseline")).changedSince(quests);
  }

  @Test
  public void aPairingReplyAfterADisconnectIsDropped() throws Exception {
    h.call("pair", "AAAA-AAAA");
    h.call("disconnect");
    h.executor.runAll();
    assertFalse(h.deviceStore.load().isPaired());
  }

  @Test
  public void anOlderPairingReplyNeverOverwritesANewerOne() throws Exception {
    h.call("pair", "AAAA-AAAA");
    h.call("pair", "BBBB-BBBB");
    Runnable older = h.executor.take();
    h.executor.runAll(); // the newer pairing answers first
    assertEquals("tok-b", token());
    older.run();
    assertEquals("tok-b", token());
  }

  @Test
  public void aSnapshotAckedBeforeAReplaceAdvancesItsBaselines() throws Exception {
    submitSnapshot();
    h.executor.runAll();
    assertTrue("control: the same flow advances when nothing replaced it", questsAcked());
  }

  @Test
  public void aSnapshotAckedAfterAReplaceDoesNotAdvanceBaselines() throws Exception {
    submitSnapshot();
    Runnable inFlight = h.executor.take();
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();
    inFlight.run();
    h.executor.runAll();
    assertFalse(questsAcked());
    assertNull(h.ackedStore.load(PluginHarness.MAIN).getQuests());
  }

  @Test
  public void aLoadStartedBeforeARePairNeverRestoresTheOldAcks() throws Exception {
    submitSnapshot();
    h.executor.runAll();
    assertTrue(questsAcked());

    // A restart: the load is queued at login, and a re-pair lands while it is in flight.
    h = new PluginHarness(folder.getRoot());
    h.transport.byPath.put(
        "/pair", body -> new HttpResponse(200, "{\"deviceToken\":\"tok-b\",\"deviceId\":\"d\"}"));
    h.onClientThread = false;
    h.logIn();
    Runnable load = h.executor.take();
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();
    load.run();
    h.runClientThread();
    h.executor.runAll();
    assertFalse(questsAcked());
  }

  @Test
  public void aStaleRejectionLeavesNoFailureOnTheNewPairing() throws Exception {
    h.transport.byPath.put(
        "/submit",
        body -> {
          DeviceCredentials current = h.deviceStore.load();
          DeviceCredentials next = DeviceCredentials.none();
          next.setToken("tok-new");
          next.setServerUrl(current.getServerUrl());
          next.setPairingId("second");
          h.deviceStore.save(next);
          return new HttpResponse(401, "");
        });
    submitSnapshot();
    h.executor.runAll();
    assertNull(h.get("lastFailureReason"));
    assertFalse(((SubmitGate) h.get("submitGate")).isHalted());
  }

  @Test
  public void pairingAndDisconnectClearTheLastFailure() throws Exception {
    h.set("lastFailureReason", "Bankstand is busy.");
    h.call("pair", "AAAA-AAAA");
    h.executor.runAll();
    assertNull(h.get("lastFailureReason"));
    h.set("lastFailureReason", "Bankstand is busy.");
    h.call("disconnect");
    assertNull(h.get("lastFailureReason"));
  }

  @Test
  public void aPairingThatChangesNothingNeverBlocksTheLoad() throws Exception {
    submitSnapshot();
    h.executor.runAll();
    // A restart; a pairing starts during the load and fails to save, so nothing was forgotten.
    h = new PluginHarness(folder.getRoot());
    h.transport.byPath.put(
        "/pair", body -> new HttpResponse(200, "{\"deviceToken\":\"tok-b\",\"deviceId\":\"d\"}"));
    h.onClientThread = false;
    h.logIn();
    Runnable load = h.executor.take();
    java.io.File device = new java.io.File(folder.getRoot(), "device.json");
    assertTrue(device.delete());
    assertTrue(new java.io.File(device, "x").mkdirs());
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();
    load.run();
    h.runClientThread();
    h.executor.runAll();
    assertTrue("the acks on disk were restored", questsAcked());
  }

  @Test
  public void disconnectWithoutAClientStillForgetsThePairing() throws Exception {
    h.set("pairingClient", null);
    h.call("disconnect");
    assertFalse(h.deviceStore.load().isPaired());
  }

  @Test
  public void pairingWithAnotherServerResendsEveryBlock() throws Exception {
    ((CollectionLogAccumulator) h.get("collectionLog")).restore(new LinkedHashSet<>(clog));
    submitSnapshot();
    h.executor.runAll();
    assertTrue(questsAcked());

    h.serverUrl = "https://other.example";
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();

    assertFalse("quests", questsAcked());
    assertTrue("diaries", ((DiaryBaseline) h.get("diaryBaseline")).changedSince(diaries));
    assertTrue(
        "combat achievements",
        ((CombatAchievementBaseline) h.get("combatAchievementBaseline")).changedSince(caCounts));
    assertTrue(
        "collection log",
        ((CollectionLogBaseline) h.get("collectionLogBaseline")).changedSince(clog.size()));
    AckedState disk = h.ackedStore.load(PluginHarness.MAIN);
    assertNull(disk.getQuests());
    assertNull(disk.getDiaries());
    assertNull(disk.getCombatAchievements());
    assertEquals(-1, disk.getCollectionLogAcked());
    assertEquals("what the player browsed is kept", clog, disk.getCollectionLogItems());
  }
}
