package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.client.game.ItemStack;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A whole play session through every send path, counted at the transport. */
public class ExcludedWorldSendTest {

  private static final String SUBMIT = "/submit";
  private static final String EVENTS = "/events";
  private static final String LOOT = "/loot";
  private static final String PRESENCE = "/presence";

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private PluginHarness h;
  private CollectionLogUnlockCapture unlocks;
  private LootCapture loot;

  private void start(Set<WorldType> world, int[] regions) throws Exception {
    h = new PluginHarness(folder.newFolder());
    h.world = world;
    h.regions = regions;
    // Quest reads run a client script the fake client cannot answer.
    h.toggles.put("collectQuests", false);
    unlocks =
        new CollectionLogUnlockCapture(
            h.eventOutbox,
            h.plugin.gated(() -> true),
            () -> ((com.bankstand.session.AccountSession) get("session")).getAccountHash());
    loot =
        new LootCapture(
            () -> invokeGate("isLootCaptureEnabled"),
            () -> ((com.bankstand.session.AccountSession) get("session")).getAccountHash(),
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
            (LootLedger) get("lootLedger"),
            h.lootOutbox,
            () -> {},
            Instant::now);
  }

  private Object get(String field) {
    try {
      return h.get(field);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private boolean invokeGate(String name) {
    try {
      return (Boolean) h.call(name);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  /** Login, a drop, a kill, the identity tick, a capture, a drain, then logout. */
  private void playASession() throws Exception {
    h.logIn();
    h.executor.runAll();
    unlocks.handleMessage("New item added to your collection log: Pet snakeling");
    loot.handleKill(2, "Goblin", Collections.singletonList(new ItemStack(526, 1)));
    h.ticks(1);
    h.plugin.captureSkills();
    h.runClientThread();
    h.plugin.drainEventOutbox();
    h.executor.runAll();
    h.state(GameState.LOGIN_SCREEN);
    h.executor.runAll();
  }

  @Test
  public void aRegularWorldUsesEverySendPath() throws Exception {
    start(EnumSet.of(WorldType.MEMBERS), PluginHarness.LUMBRIDGE);
    playASession();
    for (String path : new String[] {SUBMIT, EVENTS, LOOT, PRESENCE}) {
      assertTrue("control: " + path + " is reached", h.transport.count(path) > 0);
    }
  }

  @Test
  public void anExcludedWorldMakesNoRequestAtAll() throws Exception {
    Object[][] excluded = {
      {EnumSet.of(WorldType.SEASONAL, WorldType.MEMBERS), PluginHarness.LUMBRIDGE},
      {EnumSet.of(WorldType.DEADMAN), PluginHarness.LUMBRIDGE},
      {EnumSet.of(WorldType.BETA_WORLD, WorldType.MEMBERS), PluginHarness.LUMBRIDGE},
      {EnumSet.of(WorldType.QUEST_SPEEDRUNNING, WorldType.MEMBERS), PluginHarness.LUMBRIDGE},
      {EnumSet.of(WorldType.TOURNAMENT_WORLD), PluginHarness.LUMBRIDGE},
      {EnumSet.of(WorldType.MEMBERS), PluginHarness.LMS_ARENA},
    };
    for (Object[] world : excluded) {
      @SuppressWarnings("unchecked")
      Set<WorldType> type = (Set<WorldType>) world[0];
      start(type, (int[]) world[1]);
      playASession();
      assertEquals(type + " sent " + h.transport.urls, 0, h.transport.urls.size());
    }
  }

  @Test
  public void presenceWaitsForTheLoginToSettle() throws Exception {
    start(EnumSet.of(WorldType.MEMBERS), PluginHarness.LUMBRIDGE);
    h.state(GameState.LOGGED_IN);
    h.ticks(CaptureGate.SETTLE_TICKS - 1);
    h.executor.runAll();
    assertEquals(0, h.transport.count(PRESENCE));
    h.ticks(1);
    h.executor.runAll();
    assertEquals(1, h.transport.count(PRESENCE));
  }

  @Test
  public void presenceIsNotSentInsideALastManStandingGame() throws Exception {
    start(EnumSet.of(WorldType.MEMBERS), PluginHarness.LMS_ARENA);
    h.logIn();
    h.plugin.drainEventOutbox();
    h.executor.runAll();
    assertEquals(0, h.transport.count(PRESENCE));

    // Leaving the arena loads a region, which the client reports as LOGGED_IN.
    h.regions = PluginHarness.LUMBRIDGE;
    h.state(GameState.LOGGED_IN);
    h.ticks(1);
    h.executor.runAll();
    assertEquals(1, h.transport.count(PRESENCE));
  }

  @Test
  public void aLootWindowClosedAfterAHopCarriesNothingFromTheExcludedWorld() throws Exception {
    start(EnumSet.of(WorldType.MEMBERS), PluginHarness.LUMBRIDGE);
    h.toggles.put("collectSessions", false);
    h.logIn();
    loot.handleKill(2, "Goblin", Collections.singletonList(new ItemStack(526, 1)));
    h.hopTo(EnumSet.of(WorldType.SEASONAL, WorldType.MEMBERS));
    loot.handleKill(3, "Cow", Collections.singletonList(new ItemStack(2132, 1)));
    // Logging out on the excluded world closes the window the regular world opened.
    h.state(GameState.LOGIN_SCREEN);
    h.executor.runAll();

    assertEquals(1, h.transport.count(LOOT));
    String body = String.join("\n", h.transport.bodies);
    assertTrue(body.contains("Goblin"));
    assertFalse(body.contains("Cow"));
    assertFalse(body.contains("2132"));
  }
}
