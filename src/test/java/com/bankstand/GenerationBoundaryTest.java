package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The capture boundary, driven end to end: login, captures, the identity check and uploads run
 * against a fake server that applies the real capture rule, with a hand-run executor and clock.
 */
public class GenerationBoundaryTest {
  private static final long A = 1L;
  private static final long B = 2L;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private File dir;
  private BoundaryRig rig;

  @Before
  public void setUp() throws Exception {
    dir = folder.newFolder("bankstand");
    rig = new BoundaryRig(dir);
  }

  /** A previous run learned {@code generation} for A under this pairing; this is the restart. */
  private void restartedKnowing(int generation) {
    rig.server.generation.put(A, generation);
    rig.generations.observe(A, generation);
    rig = BoundaryRig.restart(dir, rig);
  }

  private BoundaryRig restartedRig() {
    return BoundaryRig.restart(dir, rig);
  }

  private void captureOneOfEach(String tag) {
    rig.event(A, "e-" + tag);
    rig.kill(A, 7);
    rig.loot.addAll(rig.ledger.closeAll());
    rig.presence(A, PresenceSignal.LOGIN);
  }

  // Startup

  @Test
  public void aStartupHoldsEveryCaptureUntilTheServerAnswersThenSendsThemStamped() throws Exception {
    restartedKnowing(3);
    rig.login(A);
    rig.askIdentity(A);
    captureOneOfEach("pre");
    rig.sendAll();
    assertTrue("nothing leaves before the answer", rig.server.accepted.isEmpty());
    assertTrue(rig.events.pending().isEmpty());
    assertFalse(rig.generations.isConfirmed(A));

    rig.runExecutor();
    assertEquals(Collections.singletonList(3), rig.eventStamps());
    rig.sendAll();
    assertEquals(3, rig.server.accepted.size());
    assertTrue(rig.server.refused.isEmpty());
    assertTrue(rig.lost.isEmpty());
    assertEquals(0, rig.boundary.heldCount());
  }

  @Test
  public void aStartupOfflineKeepsHoldingAndSendsOnceTheServerAnswers() throws Exception {
    restartedKnowing(3);
    rig.server.offline = true;
    rig.login(A);
    rig.askIdentity(A);
    captureOneOfEach("pre");
    rig.runExecutor();
    assertFalse(rig.generations.isConfirmed(A));
    assertEquals(3, rig.boundary.heldCount());

    rig.server.offline = false;
    rig.askIdentity(A);
    rig.runExecutor();
    rig.sendAll();
    assertEquals(3, rig.server.accepted.size());
    assertTrue(rig.lost.isEmpty());
  }

  @Test
  public void aServerThatNeverAnswersDropsTheHoldAfterItsTimeLimitCountedAndSaid() {
    rig.server.offline = true;
    rig.login(A);
    rig.askIdentity(A);
    captureOneOfEach("pre");
    rig.runExecutor();
    rig.clock.addAndGet(CaptureBoundary.MAX_HELD_MS);
    rig.boundary.expire();
    assertEquals("not yet past the limit", 3, rig.boundary.heldCount());
    rig.clock.addAndGet(1);
    rig.boundary.expire();
    assertEquals(0, rig.boundary.heldCount());
    assertEquals(1, rig.boundary.losses().timedOut(CaptureBoundary.Kind.EVENT));
    assertEquals(1, rig.boundary.losses().timedOut(CaptureBoundary.Kind.LOOT));
    assertEquals(1, rig.boundary.losses().timedOut(CaptureBoundary.Kind.PRESENCE));
    assertEquals(
        Collections.singletonList(
            "1 event, 1 loot record, 1 session mark not saved, no answer from Bankstand in time"),
        rig.lost);
  }

  @Test
  public void aFullHoldDropsTheOldestCountedAndSaid() {
    rig.login(A);
    for (int i = 0; i < CaptureBoundary.MAX_HELD + 2; i++) {
      rig.event(A, "e" + i);
    }
    assertEquals(CaptureBoundary.MAX_HELD, rig.boundary.heldCount());
    assertEquals(2, rig.boundary.losses().count(CaptureLosses.Reason.FULL, CaptureBoundary.Kind.EVENT));
    assertEquals("a full hold is not a late answer", 0, rig.boundary.losses().timedOut(CaptureBoundary.Kind.EVENT));
    assertEquals(
        Arrays.asList(
            "1 event not saved, too many were waiting for Bankstand",
            "1 event not saved, too many were waiting for Bankstand"),
        rig.lost);
  }

  // Identity answers

  @Test
  public void aCurrentAnswerConfirmsAndLaterCapturesGoStraightOutStamped() throws Exception {
    rig.login(A);
    rig.askIdentity(A);
    rig.runExecutor();
    assertTrue(rig.generations.isConfirmed(A));
    rig.event(A, "e");
    assertEquals(Collections.singletonList(1), rig.eventStamps());
    rig.sendEvents();
    assertEquals(Collections.singletonList("e"), rig.server.accepted);
  }

  @Test
  public void anAnswerForAReplacedPairingChangesNothing() throws Exception {
    rig.server.generation.put(A, 2);
    rig.login(A);
    rig.askIdentity(A);
    rig.repair("pairing-2", Collections.emptyMap());
    rig.event(A, "new");
    rig.runExecutor();
    assertFalse(rig.generations.isConfirmed(A));
    assertNull(rig.generations.get(A));
    assertEquals("still held for the new pairing's own answer", 1, rig.boundary.heldCount());
  }

  @Test
  public void anAnswerForAnEarlierLoginNeverConfirmsOrReleasesTheNextOne() throws Exception {
    rig.server.generation.put(A, 2);
    rig.generations.observe(A, 2);
    rig.login(A);
    rig.askIdentity(A);
    rig.event(A, "first-login");
    rig.logout();
    rig.login(A);
    rig.event(A, "second-login");
    rig.runExecutor();
    assertFalse("the second login waits for its own answer", rig.generations.isConfirmed(A));
    assertEquals(Collections.singletonList(2), rig.eventStamps());
    assertEquals("the second login's capture is still held", 1, rig.boundary.heldCount());

    rig.askIdentity(A);
    rig.runExecutor();
    assertTrue(rig.generations.isConfirmed(A));
    assertEquals(Arrays.asList(2, 2), rig.eventStamps());
  }

  @Test
  public void anAnswerArrivingAfterLogoutStillSettlesThatLoginsCaptures() throws Exception {
    rig.login(A);
    rig.askIdentity(A);
    rig.presence(A, PresenceSignal.LOGIN);
    rig.presence(A, PresenceSignal.LOGOUT);
    rig.logout();
    rig.runExecutor();
    rig.sendPresence();
    assertEquals(Arrays.asList(PresenceSignal.LOGIN, PresenceSignal.LOGOUT), rig.server.accepted);
    assertFalse("a logged-out answer confirms nothing", rig.generations.isConfirmed(A));
  }

  @Test
  public void anAnswerWithoutAGenerationSendsUnstampedAndTheServerDecides() throws Exception {
    rig.server.older = true;
    rig.generations.observe(A, 3);
    rig.login(A);
    rig.askIdentity(A);
    rig.event(A, "e");
    rig.runExecutor();
    assertTrue(rig.generations.isConfirmed(A));
    assertNull("a remembered value is never a stamp", rig.generations.stampFor(A));
    assertEquals(Collections.singletonList((Integer) null), rig.eventStamps());
  }

  @Test
  public void eachCharacterWaitsForItsOwnAnswer() throws Exception {
    rig.login(A);
    rig.askIdentity(A);
    rig.runExecutor();
    rig.logout();
    rig.login(B);
    rig.event(B, "b");
    assertTrue(rig.events.pending().isEmpty());
    assertEquals(1, rig.boundary.heldCount());
  }

  // Pairing replacement mid-flight

  @Test
  public void aRePairWhileAnIdentityCheckIsInFlightIgnoresItsAnswer() throws Exception {
    rig.server.generation.put(A, 5);
    rig.login(A);
    rig.askIdentity(A);
    rig.event(A, "old-pairing");
    rig.server.beforeRead = () -> rig.repair("pairing-2", Collections.emptyMap());
    rig.runExecutor();
    assertFalse(rig.generations.isConfirmed(A));
    assertNull(rig.generations.get(A));
    assertTrue(rig.events.pending().isEmpty());
    assertTrue(rig.server.accepted.isEmpty());
    assertNull("nothing saved for the new pairing", restartedRig().generations.get(A));
  }

  @Test
  public void aRePairWhileAnUploadIsInFlightIgnoresItsAck() throws Exception {
    rig.login(A);
    rig.askIdentity(A);
    rig.runExecutor();
    rig.event(A, "e");
    rig.server.remove(A);
    rig.server.beforeRead = () -> rig.repair("pairing-2", Collections.emptyMap());
    rig.sendEvents();
    assertFalse(rig.generations.isConfirmed(A));
    assertNull(rig.generations.get(A));
    assertTrue("the old pairing's refusal is not this pairing's loss", rig.lost.isEmpty());
    assertTrue(rig.notices.isEmpty());
  }

  @Test
  public void aCaptureTakenUnderAReplacedPairingIsNeverSent() throws Exception {
    rig.login(A);
    rig.askIdentity(A);
    rig.runExecutor();
    CaptureBoundary.Ticket before = rig.boundary.ticket(A);
    rig.repair("pairing-2", Collections.emptyMap());
    boolean[] sent = {false};
    rig.boundary.queue(before, CaptureBoundary.Kind.LOOT, stamp -> sent[0] = true);
    assertFalse(sent[0]);
    assertEquals(1, rig.boundary.losses().count(CaptureLosses.Reason.REPAIRED, CaptureBoundary.Kind.LOOT));
  }

  @Test
  public void aRePairWhileTheTokenIsReadWaitsSoTheOldAnswerNeverCounts() throws Exception {
    rig.server.generation.put(A, 2);
    rig.login(A);
    Thread[] repairer = new Thread[1];
    CaptureSend<Long> send =
        rig.boundary.send(
            () -> {
              String old = rig.token;
              repairer[0] = new Thread(() -> rig.repair("pairing-2", Collections.emptyMap()));
              repairer[0].start();
              waitUntilBlocked(repairer[0]);
              return old;
            },
            () -> Collections.singletonList(A),
            a -> a);
    repairer[0].join();
    assertEquals("tok-1", send.token);
    rig.boundary.identityAnswered(send.asked(A), 2);
    assertFalse("the old pairing's answer never confirms the new one", rig.generations.isConfirmed(A));
  }

  private static void waitUntilBlocked(Thread t) {
    long until = System.nanoTime() + 2_000_000_000L;
    while (t.isAlive() && t.getState() != Thread.State.BLOCKED && System.nanoTime() < until) {
      Thread.yield();
    }
  }

  // What the panel says was lost or recovered

  @Test
  public void aRefusedSnapshotIsCountedAsRecoverableApartFromLostCaptures() {
    rig.login(A);
    CaptureBoundary.Ticket asked = rig.boundary.ticket(A);
    rig.boundary.acked(asked, CaptureBoundary.Kind.SNAPSHOT, 2, 1, 1);
    rig.boundary.acked(asked, CaptureBoundary.Kind.EVENT, 2, 1, 2);
    assertEquals(1, rig.boundary.losses().recovered());
    assertEquals(0, rig.boundary.losses().discarded(CaptureBoundary.Kind.SNAPSHOT));
    assertEquals(2, rig.boundary.losses().discarded(CaptureBoundary.Kind.EVENT));
    java.util.List<String> summary =
        Arrays.asList(
            "Since start-up: 1 snapshot held back, read again from the game once Bankstand answers",
            "Since start-up: 2 events not saved, data was removed");
    assertEquals(summary, rig.boundary.losses().summary());
    rig.logout();
    rig.login(B);
    assertEquals("the counts outlive a login", summary, rig.boundary.losses().summary());
  }

  @Test
  public void aSnapshotWaitingForTheAnswerCountsOncePerLogin() {
    rig.login(A);
    rig.boundary.snapshotWaiting(A);
    rig.boundary.snapshotWaiting(A);
    assertEquals(1, rig.boundary.losses().recovered());
    rig.logout();
    rig.login(A);
    rig.boundary.snapshotWaiting(A);
    assertEquals(2, rig.boundary.losses().recovered());
  }

  // Every capture path discovers the generation on its own

  @Test
  public void eventsLootAndPresenceNeedOnlyTheIdentityCheckNotASnapshot() throws Exception {
    rig.server.generation.put(A, 4);
    rig.generations.observe(A, 4);
    rig.login(A);
    captureOneOfEach("x");
    rig.askIdentity(A);
    rig.runExecutor();
    rig.sendAll();
    assertEquals(1, rig.server.identityCalls);
    assertEquals(3, rig.server.accepted.size());
  }

  // Supplementary: the tick needs a live client.
  @Test
  public void theTickAsksForEveryEnabledCaptureNotOnlySkillXp() throws Exception {
    String tick = body(plugin(), "public void onGameTick(");
    assertTrue(tick.contains("if (enabledCapabilitySet().isEmpty())"));
    assertFalse(tick.contains("isSkillCaptureEnabled()"));
  }

  // Removal mid-flight

  @Test
  public void aRemovalAfterCaptureRefusesItCountsItAndSaysSoOnce() throws Exception {
    rig.login(A);
    rig.askIdentity(A);
    rig.runExecutor();
    captureOneOfEach("before");
    rig.server.remove(A);
    rig.sendAll();
    assertTrue(rig.server.accepted.isEmpty());
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.EVENT));
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.LOOT));
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.PRESENCE));
    assertEquals(Collections.singletonList(CaptureGenerations.STALE_NOTICE), rig.notices);
    assertEquals("1 event not saved, data was removed", rig.lost.get(0));

    rig.event(A, "after");
    rig.sendEvents();
    assertEquals(Collections.singletonList("after"), rig.server.accepted);
  }

  @Test
  public void aRemovalAfterTheServerReadItsAnswerStillNeverLetsAnOlderCaptureIn() throws Exception {
    rig.login(A);
    rig.askIdentity(A);
    rig.event(A, "pre");
    rig.server.afterRead = () -> rig.server.remove(A);
    rig.runExecutor();
    rig.server.afterRead = () -> {};
    assertEquals("released under the value read before", Collections.singletonList(1), rig.eventStamps());
    rig.sendEvents();
    assertTrue(rig.server.accepted.isEmpty());
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.EVENT));
  }

  // The removal boundary: same login, same pairing, a capture made before the removal

  @Test
  public void aCaptureMadeBeforeARemovalIsNeverStampedWithTheNewGeneration() throws Exception {
    restartedKnowing(3);
    rig.login(A);
    rig.askIdentity(A);
    captureOneOfEach("pre-removal");
    rig.server.remove(A);
    rig.runExecutor();

    assertTrue("never queued under 4", rig.events.pending().isEmpty());
    assertTrue(rig.loot.pending().isEmpty());
    assertNull(rig.presence.peek());
    rig.sendAll();
    assertTrue(rig.server.accepted.isEmpty());
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.EVENT));
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.LOOT));
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.PRESENCE));
    assertEquals(
        Collections.singletonList("1 event, 1 loot record, 1 session mark not saved, data was removed"),
        rig.lost);
    assertEquals(Collections.singletonList(CaptureGenerations.STALE_NOTICE), rig.notices);

    rig.event(A, "post-removal");
    rig.sendEvents();
    assertEquals(Collections.singletonList("post-removal"), rig.server.accepted);
  }

  @Test
  public void withNothingKnownACapturedClearedCharacterIsDiscardedNotGuessed() throws Exception {
    rig.server.generation.put(A, 2);
    rig.login(A);
    rig.askIdentity(A);
    rig.event(A, "unknown-interval");
    rig.runExecutor();
    assertTrue(rig.events.pending().isEmpty());
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.EVENT));
  }

  @Test
  public void withNothingKnownANeverClearedCharacterLosesNothing() throws Exception {
    rig.login(A);
    rig.askIdentity(A);
    rig.event(A, "e");
    rig.runExecutor();
    assertEquals(Collections.singletonList(1), rig.eventStamps());
  }

  @Test
  public void provenOnlyWhenTheAnswerCannotBeNewerThanTheCapture() {
    assertTrue(CaptureBoundary.proven(3, 3));
    assertFalse(CaptureBoundary.proven(3, 4));
    assertFalse(CaptureBoundary.proven(3, 1));
    assertTrue(CaptureBoundary.proven(null, 1));
    assertFalse(CaptureBoundary.proven(null, 2));
    assertTrue("the server decides", CaptureBoundary.proven(3, null));
  }

  @Test
  public void aLootRecordStartedBeforeTheAnswerIsSettledWhenItCloses() throws Exception {
    restartedKnowing(3);
    rig.login(A);
    rig.askIdentity(A);
    rig.kill(A, 7);
    rig.runExecutor();
    rig.kill(A, 7);
    rig.sendLoot();
    assertEquals(2, rig.server.accepted.size());
    assertTrue(rig.lost.isEmpty());
  }

  @Test(timeout = 20_000)
  public void captureAndRePairOnTwoThreadsNeverDeadlock() throws Exception {
    rig.login(A);
    Thread pairing =
        new Thread(
            () -> {
              for (int i = 0; i < 200; i++) {
                rig.repair("p" + i, Collections.emptyMap());
              }
            });
    pairing.start();
    for (int i = 0; i < 200; i++) {
      rig.kill(A, i);
      rig.event(A, "e" + i);
      rig.presence(A, PresenceSignal.LOGIN);
    }
    pairing.join();
  }

  @Test
  public void aHeartbeatIsNeverHeld() {
    rig.login(A);
    rig.presence.offerHeartbeat(new PresenceSignal(PresenceSignal.ACTIVE, A, 0L));
    assertNull(rig.presence.peek());
    assertEquals(0, rig.boundary.heldCount());
  }

  // Re-pair

  @Test
  public void aRePairDropsWhatWasHeldCountsItAndLearnsTheListedGenerations() throws Exception {
    rig.login(A);
    rig.event(A, "held");
    rig.kill(A, 7);
    rig.loot.addAll(rig.ledger.closeAll());
    PairingQueues.Dropped dropped = rig.repair("pairing-2", Collections.singletonMap(A, 6));
    assertEquals(1, dropped.events);
    assertEquals(1, dropped.loot);
    assertEquals(0, rig.boundary.heldCount());
    assertTrue(rig.generations.isConfirmed(A));
    rig.server.generation.put(A, 6);
    rig.event(A, "new");
    rig.sendEvents();
    assertEquals(Collections.singletonList("new"), rig.server.accepted);
    assertEquals("saved under the new pairing", Integer.valueOf(6), restartedRig().generations.get(A));
  }

  @Test
  public void aGenerationLearnedUnderAnotherPairingIsNeverRestored() {
    rig.generations.observe(A, 3);
    // A crash after the swap, before the old values were cleared from disk.
    rig.pairing = "pairing-2";
    assertNull(restartedRig().generations.get(A));
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
    rig.store.save(1L, state);
    rig.store.save(7L, state);
    rig.store.saveCaptureGeneration(1L, 2, "pairing-1");

    rig.store.clearBaselines();

    for (long account : new long[] {1L, 7L}) {
      AckedState cleared = rig.store.load(account);
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
    assertEquals(Integer.valueOf(2), rig.store.load(1L).getCaptureGeneration());
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

  // Supplementary: the plugin needs a live client to run, so its wiring is pinned on the source.
  @Test
  public void theWiringHoldsTheBoundary() throws Exception {
    String source = plugin();
    String login = body(source, "public void onGameStateChanged(");
    assertTrue("a login waits for a fresh answer", login.contains("captureBoundary.startLogin("));
    assertTrue(login.contains("captureBoundary.endLogin();"));
    assertTrue(
        "no snapshot before the server answered",
        body(source, "private void onSkillsCaptured(")
            .contains("if (!captureGenerations.isConfirmed(accountHash))"));
    String identity = body(source, "private void submitIdentity(");
    assertTrue(
        "the ask is pinned to this login and pairing before it leaves",
        identity.indexOf("captureBoundary.send(") < identity.indexOf("executor.submit("));
    assertFalse("a ticket is only taken with its token", source.contains("captureBoundary.ticket("));
    for (String sender :
        Arrays.asList(
            "public void drainEventOutbox(",
            "private void sendLoot(",
            "private void submitSnapshot(",
            "private void submitIdentity(")) {
      assertTrue(sender, body(source, sender).contains("captureBoundary.send("));
    }
    assertTrue(body(source, "private void runPresenceSends(").contains("captureBoundary.send("));
    assertTrue(body(source, "private PanelModel buildPanelModel(").contains("captureBoundary.losses().summary()"));
    assertTrue(body(source, "private void onSkillsCaptured(").contains("captureBoundary.snapshotWaiting(accountHash);"));
    assertTrue(identity.contains(".discover("));
    for (String kind : Arrays.asList("EVENT", "LOOT", "PRESENCE", "SNAPSHOT")) {
      assertTrue(kind + " acks go through the boundary", source.contains("CaptureBoundary.Kind." + kind + ","));
    }
    assertFalse("no answer bypasses the boundary", source.contains("captureGenerations.observe("));
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
    assertTrue(source.contains("captureBoundary.onLost("));
    assertTrue(body(source, "public void drainEventOutbox(").contains("captureBoundary.expire();"));
  }
}
