package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** How refusals and lost replies settle what was queued under a generation. */
public class CaptureAckTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final long A = 1L;

  private File dir;
  private BoundaryRig rig;

  @Before
  public void setUp() throws Exception {
    dir = folder.newFolder("rig");
    rig = new BoundaryRig(dir);
    rig.login(A);
    rig.askIdentity(A);
    rig.runExecutor();
  }

  private void queueEventAndLoot(String id) {
    rig.event(A, id);
    rig.kill(A, 7);
    rig.loot.addAll(rig.ledger.closeAll());
  }

  @Test
  public void aStaleGenerationAckDropsEventsAndLootForGoodAndCountsThemLost() throws Exception {
    queueEventAndLoot("pre");
    rig.server.remove(A);
    rig.sendEvents();
    rig.sendLoot();
    assertTrue(rig.events.pending().isEmpty());
    assertTrue(rig.loot.pending().isEmpty());
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.EVENT));
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.LOOT));
    assertEquals(Collections.singletonList(CaptureGenerations.STALE_NOTICE), rig.notices);
  }

  @Test
  public void aStaleAckDropsEventsAndLootForGoodButIsNotCountedAsARemoval() throws Exception {
    queueEventAndLoot("old");
    rig.server.remove(A);
    rig.server.refusal = "stale";
    rig.sendEvents();
    rig.sendLoot();
    assertTrue("never retried", rig.events.pending().isEmpty());
    assertTrue("never retried", rig.loot.pending().isEmpty());
    assertEquals(0, rig.boundary.losses().discarded(CaptureBoundary.Kind.EVENT));
    assertEquals(0, rig.boundary.losses().discarded(CaptureBoundary.Kind.LOOT));
    assertTrue(rig.notices.isEmpty());
  }

  @Test
  public void aLostAckKeepsTheItemQueuedUnderItsStampAndTheResendIsTaken() throws Exception {
    rig.event(A, "e1");
    rig.server.loseReply = true;
    try {
      rig.sendEvents();
      fail("the reply was lost");
    } catch (SubmitException expected) {
      // Retryable: the entry stays queued.
    }
    rig.server.loseReply = false;
    assertEquals(Collections.singletonList(1), rig.eventStamps());
    rig.sendEvents();
    assertTrue(rig.events.pending().isEmpty());
    assertEquals(Arrays.asList("e1", "e1"), rig.server.accepted);
  }

  @Test
  public void aLostAckThenARemovalAndARestartRefusesTheResendAndNeverRestampsIt()
      throws Exception {
    rig.event(A, "e1");
    rig.server.loseReply = true;
    try {
      rig.sendEvents();
      fail("the reply was lost");
    } catch (SubmitException expected) {
      // Retryable: the entry stays queued.
    }
    rig.server.loseReply = false;
    rig.server.remove(A);

    rig = BoundaryRig.restart(dir, rig);
    assertEquals("the stamp survives the restart", Collections.singletonList(1), rig.eventStamps());
    rig.login(A);
    rig.askIdentity(A);
    rig.runExecutor();
    assertEquals("learned the new generation", Integer.valueOf(2), rig.generations.stampFor(A));
    assertEquals("the queued entry keeps its old stamp", Collections.singletonList(1), rig.eventStamps());

    rig.sendEvents();
    assertTrue(rig.events.pending().isEmpty());
    assertEquals(1, rig.boundary.losses().discarded(CaptureBoundary.Kind.EVENT));
    assertEquals(Collections.singletonList("e1"), rig.server.refused);
  }

  @Test
  public void aFailedIdentityCheckReachesTheCallerAsItsOwnSubmitException() throws Exception {
    SubmitException refused = new SubmitException("Bankstand is busy.", true);
    java.util.concurrent.CompletableFuture<com.bankstand.dto.SubmitResponse> sent =
        new java.util.concurrent.CompletableFuture<>();
    Throwable[] seen = {null};
    rig.boundary
        .discover(rig.boundary.ticket(A), () -> sent)
        .whenComplete((res, failure) -> seen[0] = failure);
    sent.completeExceptionally(refused);
    assertTrue("not wrapped, so its Retry-After is honoured", seen[0] == refused);
  }

  @Test
  public void aRestartHoldsNewCapturesInMemoryUntilTheServerAnswersAgain() throws Exception {
    rig = BoundaryRig.restart(dir, rig);
    rig.login(A);
    rig.event(A, "held");
    assertEquals("held, not queued on disk", 0, rig.events.pending().size());
    assertEquals(1, rig.boundary.heldCount());

    // Closing RuneLite before the answer loses what was held: it is never written to disk.
    BoundaryRig again = BoundaryRig.restart(dir, rig);
    assertEquals(0, again.events.pending().size());
    assertEquals(0, again.boundary.heldCount());

    rig.askIdentity(A);
    rig.runExecutor();
    assertEquals(Collections.singletonList(1), rig.eventStamps());
  }
}
