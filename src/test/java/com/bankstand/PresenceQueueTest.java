package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class PresenceQueueTest {

  private static PresenceSignal signal(String state, long nanos) {
    return new PresenceSignal(state, 1L, nanos);
  }

  @Test
  public void onlyLoginAndActiveCarryTheLootFlag() {
    assertEquals(Boolean.TRUE, signal(PresenceSignal.LOGIN, 1).withLoot(true).getLoot());
    assertEquals(Boolean.FALSE, signal(PresenceSignal.ACTIVE, 1).withLoot(false).getLoot());
    assertNull(signal(PresenceSignal.LOGOUT, 1).withLoot(true).getLoot());
    assertNull(signal(PresenceSignal.OFF, 1).withLoot(true).getLoot());
    assertNull(signal(PresenceSignal.LOGIN, 1).getLoot());
  }

  @Test
  public void transitionsGoOutInOrder() {
    PresenceQueue queue = new PresenceQueue();
    PresenceSignal logout = signal(PresenceSignal.LOGOUT, 1);
    PresenceSignal login = signal(PresenceSignal.LOGIN, 2);
    queue.addTransition(logout);
    queue.addTransition(login);

    assertSame(logout, queue.peek());
    queue.onAnswered(logout);
    assertSame(login, queue.peek());
  }

  @Test
  public void aLoginNeverReplacesAnUnacknowledgedLogout() {
    PresenceQueue queue = new PresenceQueue();
    PresenceSignal logout = signal(PresenceSignal.LOGOUT, 1);
    queue.addTransition(logout);
    queue.onRetryableFailure(logout);
    queue.addTransition(signal(PresenceSignal.LOGIN, 2));

    assertEquals(2, queue.size());
    assertSame("the logout is still first, and still owed", logout, queue.peek());
  }

  @Test
  public void aTransitionThatFailsStaysAtTheHead() {
    PresenceQueue queue = new PresenceQueue();
    PresenceSignal logout = signal(PresenceSignal.LOGOUT, 1);
    queue.addTransition(logout);
    queue.onRetryableFailure(logout);
    assertSame(logout, queue.peek());
  }

  @Test
  public void aTerminalFailureDropsTheTransition() {
    PresenceQueue queue = new PresenceQueue();
    PresenceSignal logout = signal(PresenceSignal.LOGOUT, 1);
    queue.addTransition(logout);
    queue.onTerminalFailure(logout);
    assertNull(queue.peek());
  }

  @Test
  public void holdsAtMostEightTransitionsDroppingTheOldest() {
    PresenceQueue queue = new PresenceQueue();
    for (int i = 0; i < PresenceQueue.MAX_TRANSITIONS + 2; i++) {
      queue.addTransition(signal(PresenceSignal.LOGIN, i));
    }
    assertEquals(PresenceQueue.MAX_TRANSITIONS, queue.size());
    assertEquals(2L, queue.peek().getCreatedAtNanos());
  }

  @Test
  public void aHeartbeatIsOneLatestValue() {
    PresenceQueue queue = new PresenceQueue();
    queue.offerHeartbeat(signal(PresenceSignal.ACTIVE, 1));
    PresenceSignal newer = signal(PresenceSignal.ACTIVE, 2);
    queue.offerHeartbeat(newer);
    assertEquals(1, queue.size());
    assertSame(newer, queue.peek());
  }

  @Test
  public void aFailedHeartbeatIsDroppedNotRetried() {
    PresenceQueue queue = new PresenceQueue();
    PresenceSignal active = signal(PresenceSignal.ACTIVE, 1);
    queue.offerHeartbeat(active);
    queue.onRetryableFailure(active);
    assertNull(queue.peek());
  }

  @Test
  public void aHeartbeatWaitsBehindEveryTransition() {
    PresenceQueue queue = new PresenceQueue();
    PresenceSignal login = signal(PresenceSignal.LOGIN, 1);
    queue.addTransition(login);
    PresenceSignal active = signal(PresenceSignal.ACTIVE, 2);
    queue.offerHeartbeat(active);

    assertSame(login, queue.peek());
    queue.onAnswered(login);
    assertSame(active, queue.peek());
  }

  @Test
  public void aTransitionSupersedesAnOlderPendingHeartbeat() {
    PresenceQueue queue = new PresenceQueue();
    queue.offerHeartbeat(signal(PresenceSignal.ACTIVE, 1));
    PresenceSignal logout = signal(PresenceSignal.LOGOUT, 2);
    queue.addTransition(logout);
    queue.onAnswered(logout);
    assertNull(queue.peek());
  }

  @Test
  public void elapsedTimeComesFromTheMonotonicClockAndGrowsWhileRetried() {
    PresenceSignal logout = new PresenceSignal(PresenceSignal.LOGOUT, 1L, 1_000_000_000L);
    assertEquals(0L, logout.elapsedMillis(1_000_000_000L));
    assertEquals(180_000L, logout.elapsedMillis(181_000_000_000L));
    assertEquals("never negative", 0L, logout.elapsedMillis(0L));
  }

  /** Presence is held in memory only. */
  @Test
  public void presenceIsNeverPersisted() throws IOException {
    for (String name : new String[] {"PresenceQueue", "PresenceTracker", "PresenceSignal"}) {
      String source =
          new String(
              Files.readAllBytes(
                  Paths.get("src", "main", "java", "com", "bankstand", name + ".java")),
              StandardCharsets.UTF_8);
      for (String banned : new String[] {"java.io", "java.nio", "Gson", "ConfigManager"}) {
        assertEquals(name + " must not use " + banned, -1, source.indexOf(banned));
      }
    }
  }
}
