package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.http.HttpResponse;
import java.time.Instant;
import java.util.Collections;
import net.runelite.api.GameState;
import net.runelite.client.game.ItemStack;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** What the plugin does after the server says slow down, or cannot route a batch yet. */
public class ThrottlingTest {

  private static final String RATE_LIMITED = "{\"error\":\"rate limited\"}";
  private static final long TWO_MINUTES = 120_000L;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private PluginHarness h;
  private CollectionLogUnlockCapture unlocks;

  @Before
  public void setUp() throws Exception {
    h = new PluginHarness(folder.getRoot());
    // Presence has its own test below; here it would add requests to every count.
    h.toggles.put("collectSessions", false);
    unlocks =
        new CollectionLogUnlockCapture(h.eventOutbox, () -> true, () -> PluginHarness.MAIN);
    h.logIn();
    h.executor.runAll();
  }

  private void unlock(String item) {
    unlocks.handleMessage("New item added to your collection log: " + item);
  }

  private void drain() {
    h.plugin.drainEventOutbox();
    h.executor.runAll();
  }

  private EventSendPacer eventPacer() throws Exception {
    return (EventSendPacer) h.get("eventPacer");
  }

  @Test
  public void anEvents429PausesTheDrainAndKeepsTheEvents() throws Exception {
    h.transport.byPath.put("/events", body -> new HttpResponse(429, RATE_LIMITED, "120"));
    unlock("Pet snakeling");
    drain();
    assertEquals("a 429 is never retried in place", 1, h.transport.count("/events"));
    assertEquals(1, h.eventOutbox.pending().size());

    drain();
    assertEquals("paused for the Retry-After", 1, h.transport.count("/events"));
    long now = System.currentTimeMillis();
    assertFalse(eventPacer().maySend(PluginHarness.MAIN, now + TWO_MINUTES - 5_000L));
    assertTrue(eventPacer().maySend(PluginHarness.MAIN, now + TWO_MINUTES + 1_000L));
  }

  @Test
  public void anUnroutedBatchWaitsHalfAnHourThenSendsAgain() throws Exception {
    h.transport.byPath.put(
        "/events", body -> new HttpResponse(200, "{\"routed\":false,\"acks\":[]}"));
    unlock("Pet snakeling");
    drain();
    drain();
    assertEquals(1, h.transport.count("/events"));
    assertEquals("kept for when the character is linked", 1, h.eventOutbox.pending().size());
    long now = System.currentTimeMillis();
    assertFalse(eventPacer().maySend(PluginHarness.MAIN, now + 29 * 60_000L));
    assertTrue(eventPacer().maySend(PluginHarness.MAIN, now + 31 * 60_000L));
  }

  @Test
  public void aRelogRetriesAnUnroutedBatchAtOnce() throws Exception {
    h.transport.byPath.put(
        "/events", body -> new HttpResponse(200, "{\"routed\":false,\"acks\":[]}"));
    unlock("Pet snakeling");
    drain();
    h.state(GameState.LOGIN_SCREEN);
    h.hash = 77L;
    h.logIn();
    h.state(GameState.LOGIN_SCREEN);
    h.hash = PluginHarness.MAIN;
    h.logIn();
    drain();
    assertEquals(2, h.transport.count("/events"));
  }

  @Test
  public void aRePairRetriesAnUnroutedAccountAtOnce() throws Exception {
    h.transport.byPath.put(
        "/events", body -> new HttpResponse(200, "{\"routed\":false,\"acks\":[]}"));
    h.transport.byPath.put(
        "/pair", body -> new HttpResponse(200, "{\"deviceToken\":\"tok-b\",\"deviceId\":\"d\"}"));
    unlock("Pet snakeling");
    drain();
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();
    // The re-pair dropped the old queue; a fresh capture goes out under the new pairing.
    unlock("Abyssal orphan");
    drain();
    assertEquals(2, h.transport.count("/events"));
  }

  /** Starts a drain and holds back its event send, the last task it queues. */
  private Runnable startEventDrain() {
    h.plugin.drainEventOutbox();
    java.util.List<Runnable> queued = new java.util.ArrayList<>();
    for (Runnable task; (task = h.executor.take()) != null; ) {
      queued.add(task);
    }
    queued.subList(0, queued.size() - 1).forEach(Runnable::run);
    return queued.get(queued.size() - 1);
  }

  @Test
  public void aReplyToAReplacedPairingNeitherHoldsNorPauses() throws Exception {
    h.transport.byPath.put(
        "/pair",
        body ->
            new HttpResponse(
                200, "{\"deviceToken\":\"tok-" + body.hashCode() + "\",\"deviceId\":\"d\"}"));
    unlock("Pet snakeling");
    unlock("Abyssal orphan");
    Runnable oldDrain = startEventDrain();
    h.call("pair", "BBBB-BBBB");
    h.executor.runAll();

    h.transport.byPath.put(
        "/events", body -> new HttpResponse(200, "{\"routed\":false,\"acks\":[]}"));
    oldDrain.run();
    assertEquals("the old reply arrived", 1, h.transport.count("/events"));
    assertTrue("no unrouted hold", eventPacer().maySend(PluginHarness.MAIN, System.currentTimeMillis()));

    h.transport.byPath.put("/events", body -> new HttpResponse(429, RATE_LIMITED, "120"));
    unlock("Pet snakeling");
    oldDrain = startEventDrain();
    h.call("pair", "AAAA-AAAA");
    h.executor.runAll();
    oldDrain.run();
    assertTrue("no pause", eventPacer().maySend(PluginHarness.MAIN, System.currentTimeMillis()));
  }

  @Test
  public void aLoot429PausesLootSends() throws Exception {
    h.transport.byPath.put("/loot", body -> new HttpResponse(429, RATE_LIMITED, "120"));
    LootCapture loot =
        new LootCapture(
            () -> true,
            () -> PluginHarness.MAIN,
            new LootCapture.Items() {
              @Override
              public int canonical(int itemId) {
                return itemId;
              }

              @Override
              public boolean isNotable(int itemId, int quantity) {
                return false;
              }
            },
            (LootLedger) h.get("lootLedger"),
            h.lootOutbox,
            () -> {},
            Instant::now);
    loot.handleKill(2, "Goblin", Collections.singletonList(new ItemStack(526, 1)));
    h.state(GameState.LOGIN_SCREEN);
    h.executor.runAll();
    assertEquals(1, h.transport.count("/loot"));
    drain();
    assertEquals("paused for the Retry-After", 1, h.transport.count("/loot"));
    assertEquals(1, h.lootOutbox.pending().size());
    LootSendPacer pacer = (LootSendPacer) h.get("lootPacer");
    assertTrue(pacer.mayDrain(System.currentTimeMillis() + TWO_MINUTES + 1_000L));
  }

  @Test
  public void aPresence429PausesPresenceSends() throws Exception {
    h.toggles.put("collectSessions", true);
    h.transport.byPath.put("/presence", body -> new HttpResponse(429, RATE_LIMITED, "120"));
    h.call("pulsePresence");
    h.executor.runAll();
    assertEquals(1, h.transport.count("/presence"));

    // The next minute's mark and the queued retry both wait out the Retry-After.
    h.call("pulsePresence");
    drain();
    assertEquals(1, h.transport.count("/presence"));
  }
}
