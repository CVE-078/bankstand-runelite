package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.EventAck;
import com.bankstand.dto.SubmitEventsResponse;
import com.google.gson.Gson;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** No capture is stamped before the server has answered for its character this login. */
public class GenerationBoundaryTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private File dir;
  private AckedStateStore store;

  @Before
  public void setUp() throws Exception {
    dir = folder.newFolder("bankstand");
    store = new AckedStateStore(new File(dir, "acked.json"), new Gson());
  }

  private static TransientEvent event(String id) {
    return new TransientEvent(
        id, TransientEvent.TYPE_PET_DROP, "2026-10-20T10:00:00Z", new LinkedHashMap<>());
  }

  private static List<EventAck> acks(String json) {
    return new Gson().fromJson(json, SubmitEventsResponse.class).getAcks();
  }

  /** A run that restored generation 3 for account 1 from a previous session. */
  private CaptureGenerations restartedWith(long account, int generation) {
    store.saveCaptureGeneration(account, generation);
    CaptureGenerations generations = new CaptureGenerations();
    generations.persistTo(store, Runnable::run);
    return generations;
  }

  private EventOutbox outbox(CaptureGenerations generations) {
    EventOutbox outbox = new EventOutbox(new File(dir, "events.json"), new Gson());
    outbox.setCaptureGenerations(generations);
    return outbox;
  }

  private static Integer stampOf(EventOutbox outbox, String id) {
    for (OutboxEntry entry : outbox.pending()) {
      if (entry.getEvent().getId().equals(id)) {
        return entry.getCaptureGeneration();
      }
    }
    throw new AssertionError("not queued: " + id);
  }

  @Test
  public void aSlowIdentityCheckLeavesEveryCaptureUnstampedUntilItAnswers() {
    CaptureGenerations generations = restartedWith(1L, 3);
    generations.startSession(1L);
    EventOutbox outbox = outbox(generations);
    PresenceQueue presence = new PresenceQueue();
    presence.setCaptureGenerations(generations);
    LootLedger ledger = new LootLedger();
    ledger.setCaptureGenerations(generations);
    Map<Integer, Integer> bones = Collections.singletonMap(526, 1);

    // Restored from disk, but not confirmed this login: never used as a stamp.
    assertEquals(Integer.valueOf(3), generations.get(1L));
    assertFalse(generations.isConfirmed(1L));
    assertNull(generations.stampFor(1L));
    outbox.add(1L, event("early"));
    presence.addTransition(new PresenceSignal(PresenceSignal.LOGIN, 1L, 0L));
    ledger.record(1L, 3, "Rat", bones, Instant.parse("2026-10-20T10:00:00Z"));
    assertNull(stampOf(outbox, "early"));
    assertNull(presence.peek().getCaptureGeneration());
    Map<String, Object> envelope = new LinkedHashMap<>();
    generations.stamp(envelope, 1L);
    assertFalse(envelope.containsKey("captureGeneration"));

    // The identity check answers.
    generations.observe(1L, 3);
    assertTrue(generations.isConfirmed(1L));
    outbox.add(1L, event("late"));
    assertEquals(Integer.valueOf(3), stampOf(outbox, "late"));
    // What was queued before stays as it was: never relabelled.
    assertNull(stampOf(outbox, "early"));
    List<LootEntry> closed = ledger.closeAll();
    assertNull(closed.get(0).getCaptureGeneration());
  }

  @Test
  public void anIdentityCheckThatTimesOutConfirmsNothing() {
    CaptureGenerations generations = restartedWith(1L, 3);
    generations.startSession(1L);
    // A failed identity call never reaches observe or confirm: nothing to apply.
    assertFalse(generations.isConfirmed(1L));
    assertNull(generations.stampFor(1L));
  }

  @Test
  public void anOfflineStartQueuesUnstampedAndNeverRelabelsOnceOnline() {
    CaptureGenerations generations = restartedWith(1L, 2);
    generations.startSession(1L);
    EventOutbox outbox = outbox(generations);
    outbox.add(1L, event("offline"));

    // Back online: the server is at 3 after a removal made while offline.
    generations.observe(1L, 3);
    outbox.add(1L, event("online"));
    assertNull(stampOf(outbox, "offline"));
    assertEquals(Integer.valueOf(3), stampOf(outbox, "online"));

    // The server refuses the unstamped one for a cleared character; it is dropped, not resent.
    String refused =
        "{\"acks\":[{\"id\":\"offline\",\"outcome\":\"rejected\",\"reason\":\"stale_generation\"},"
            + "{\"id\":\"online\",\"outcome\":\"stored\"}]}";
    outbox.ack(EventOutbox.idsToAck(acks(refused)));
    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void aSwitchedCharacterNeverUsesTheOtherCharactersGeneration() {
    CaptureGenerations generations = new CaptureGenerations();
    generations.startSession(1L);
    generations.observe(1L, 4);
    assertEquals(Integer.valueOf(4), generations.stampFor(1L));

    generations.startSession(2L);
    assertFalse(generations.isConfirmed(2L));
    assertNull(generations.stampFor(2L));
    generations.observe(2L, 1);
    assertEquals(Integer.valueOf(1), generations.stampFor(2L));
    assertEquals(Integer.valueOf(4), generations.stampFor(1L));

    // Back to the first character: a new login waits for a fresh answer.
    generations.startSession(1L);
    assertNull(generations.stampFor(1L));
    generations.observe(1L, 4);
    assertEquals(Integer.valueOf(4), generations.stampFor(1L));
  }

  @Test
  public void aServerAnswerWithoutAGenerationOpensCaptureUnstamped() {
    // An older server, or a character this user does not route.
    CaptureGenerations generations = new CaptureGenerations();
    generations.startSession(1L);
    generations.confirm(1L);
    assertTrue(generations.isConfirmed(1L));
    assertNull(generations.stampFor(1L));
  }

  @Test
  public void aRemovalDuringCaptureRefusesWhatWasInFlightAndSaysHowMany() {
    CaptureGenerations generations = new CaptureGenerations();
    List<String> activity = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    generations.onStaleRefusal(notices::add, () -> true);
    generations.onRefused(activity::add);
    generations.startSession(1L);
    generations.observe(1L, 2);
    EventOutbox outbox = outbox(generations);
    outbox.add(1L, event("a"));
    outbox.add(1L, event("b"));

    // Removed on the site while both were in flight.
    String refused =
        "{\"captureGeneration\":3,\"acks\":["
            + "{\"id\":\"a\",\"outcome\":\"rejected\",\"reason\":\"stale_generation\"},"
            + "{\"id\":\"b\",\"outcome\":\"rejected\",\"reason\":\"stale_generation\"}]}";
    generations.observe(1L, 3, 2, acks(refused));
    outbox.ack(EventOutbox.idsToAck(acks(refused)));

    assertTrue("refused, not retried", outbox.pending().isEmpty());
    assertEquals(Integer.valueOf(3), generations.stampFor(1L));
    assertEquals(Collections.singletonList(CaptureGenerations.STALE_NOTICE), notices);
    assertEquals(Collections.singletonList("2 captures not saved: data was removed"), activity);

    // A refused snapshot is re-captured, so it is not counted as lost.
    generations.observe(1L, 4, 3, "stale_generation");
    assertEquals(1, activity.size());
    outbox.add(1L, event("c"));
    assertEquals(Integer.valueOf(4), stampOf(outbox, "c"));
  }

  @Test
  public void aRefusedCaptureIsCountedOnce() {
    assertNull(CaptureGenerations.refusedActivity(0));
    assertEquals("1 capture not saved: data was removed", CaptureGenerations.refusedActivity(1));
    assertTrue(CaptureGenerations.refusedActivity(200).length() <= 45);
  }

  @Test
  public void rePairingForgetsConfirmationsAndLearnsTheListedOnes() {
    CaptureGenerations generations = new CaptureGenerations();
    generations.startSession(1L);
    generations.observe(1L, 2);
    generations.forget();
    assertFalse(generations.isConfirmed(1L));
    generations.observeAll(Collections.singletonMap(1L, 5));
    assertEquals(Integer.valueOf(5), generations.stampFor(1L));
  }

  @Test
  public void rePairingVoidsEveryCharactersBaselinesButKeepsWhatIsNotResent() {
    AckedState state = AckedState.empty();
    state.setSkills("s");
    state.setQuests("q");
    state.setDiaries("d");
    state.setCombatAchievements("c");
    state.setAccountType("IRONMAN");
    state.setCollectionLogItems(Collections.singleton(4151));
    state.setCollectionLogAcked(1);
    state.setDiaryTaskBits(Collections.singletonMap(1, 3));
    state.setLastSyncedAt(Collections.singletonMap("skills", 5L));
    state.setBaselineGeneration(2);
    store.save(1L, state);
    store.save(7L, state);
    store.saveCaptureGeneration(1L, 2);

    store.clearBaselines();

    for (long account : new long[] {1L, 7L}) {
      AckedState cleared = store.load(account);
      assertNull(cleared.getSkills());
      assertNull(cleared.getQuests());
      assertNull(cleared.getDiaries());
      assertNull(cleared.getCombatAchievements());
      assertNull(cleared.getAccountType());
      assertEquals(-1, cleared.getCollectionLogAcked());
      assertNull(cleared.getBaselineGeneration());
      // Not resent: diary task bits only ever produce events.
      assertEquals(Collections.singletonMap(1, 3), cleared.getDiaryTaskBits());
      assertEquals(Collections.singleton(4151), cleared.getCollectionLogItems());
    }
    assertEquals(Integer.valueOf(2), store.load(1L).getCaptureGeneration());
  }

  private static String plugin() throws Exception {
    return new String(
        Files.readAllBytes(
            java.nio.file.Paths.get(
                "src", "main", "java", "com", "bankstand", "BankstandPlugin.java")),
        StandardCharsets.UTF_8);
  }

  private static String body(String source, String signature) {
    int start = source.indexOf(signature);
    return source.substring(start, source.indexOf("\n  }\n", start));
  }

  // The plugin needs a live client to run, so its wiring is pinned on the source.
  @Test
  public void theWiringHoldsTheBoundary() throws Exception {
    String source = plugin();
    assertTrue(
        "a login waits for a fresh answer",
        body(source, "public void onGameStateChanged(")
            .contains("captureGenerations.startSession(client.getAccountHash());"));
    String capture = body(source, "private void onSkillsCaptured(");
    assertTrue(
        "no snapshot before the server answered",
        capture.contains("if (!captureGenerations.isConfirmed(accountHash))"));
    assertTrue(
        "any identity answer confirms",
        body(source, "private void submitIdentity(")
            .contains("captureGenerations.confirm(accountHash);"));
    String store = body(source, "private void storeToken(");
    assertTrue("a re-pair resends every snapshot block", store.contains("ackedStore.clearBaselines();"));
    assertTrue(store.contains("resetSnapshotBaselines("));
    assertTrue("a re-pair re-runs the identity check", store.contains("session.markSubmitFailed("));
    assertTrue(
        "a resend never claims a full collection log",
        body(source, "private void resetSnapshotBaselines(").contains("pendingFullEnumeration = false;"));
    assertTrue(
        "an ack under a replaced pairing never advances a baseline",
        body(source, "private void submitSnapshot(").contains("&& !isReplaced(url, token)"));
    assertTrue(source.contains("captureGenerations.onRefused("));
  }
}
