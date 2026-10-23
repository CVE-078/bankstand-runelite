package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CaptureGateTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  // Main-game worlds. Every other flag, including one added later, is skipped.
  private static final EnumSet<WorldType> ALLOWED =
      EnumSet.of(
          WorldType.MEMBERS,
          WorldType.PVP,
          WorldType.BOUNTY,
          WorldType.SKILL_TOTAL,
          WorldType.HIGH_RISK,
          WorldType.LEGACY_ONLY,
          WorldType.EOC_ONLY);

  private static boolean skipped(WorldType type) {
    return !ALLOWED.contains(type);
  }

  private static CaptureGate settled() {
    CaptureGate gate = new CaptureGate();
    for (int i = 0; i < CaptureGate.SETTLE_TICKS; i++) {
      gate.onTick(false);
    }
    return gate;
  }

  /** A plugin whose client reports {@code world} and whose login has settled. */
  private static BankstandPlugin pluginOn(EnumSet<WorldType> world) throws Exception {
    return pluginOn(world, new int[] {12850});
  }

  private static BankstandPlugin pluginOn(EnumSet<WorldType> world, int[] regions)
      throws Exception {
    Client client =
        (Client)
            Proxy.newProxyInstance(
                Client.class.getClassLoader(),
                new Class<?>[] {Client.class},
                (proxy, method, args) -> {
                  switch (method.getName()) {
                    case "getWorldType":
                      return world;
                    case "getAccountHash":
                      return 42L;
                    case "getMapRegions":
                      return regions;
                    default:
                      return method.getReturnType() == boolean.class ? false : null;
                  }
                });
    BankstandPlugin plugin = new BankstandPlugin();
    Field clientField = BankstandPlugin.class.getDeclaredField("client");
    clientField.setAccessible(true);
    clientField.set(plugin, client);
    Field gateField = BankstandPlugin.class.getDeclaredField("captureGate");
    gateField.setAccessible(true);
    gateField.set(plugin, settled());
    return plugin;
  }

  @Test
  public void allowsExactlyTheMainWorlds() {
    assertEquals(ALLOWED, EnumSet.copyOf(CaptureGate.MAIN_WORLDS));
    for (WorldType type : WorldType.values()) {
      assertEquals(type.name(), !skipped(type), CaptureGate.allowsWorld(EnumSet.of(type)));
    }
  }

  @Test
  public void keepsFreeAndOrdinaryWorlds() {
    assertTrue(CaptureGate.allowsWorld(EnumSet.noneOf(WorldType.class)));
    assertTrue(CaptureGate.allowsWorld(EnumSet.of(WorldType.MEMBERS, WorldType.PVP)));
    assertTrue(CaptureGate.allowsWorld(EnumSet.of(WorldType.MEMBERS, WorldType.SKILL_TOTAL)));
  }

  /** Separate saves, or a mode this build does not know: fail closed. */
  @Test
  public void skipsSpecialAndUnknownModes() {
    assertFalse(CaptureGate.allowsWorld(EnumSet.of(WorldType.LAST_MAN_STANDING)));
    assertFalse(CaptureGate.allowsWorld(EnumSet.of(WorldType.FRESH_START_WORLD)));
    assertFalse(CaptureGate.allowsWorld(EnumSet.of(WorldType.MEMBERS, WorldType.HARDCORE_MAIN)));
  }

  /** A Last Man Standing game runs on a normal world with a temporary loadout. */
  @Test
  public void aLastManStandingGameBlocksEveryCapture() throws Exception {
    BankstandPlugin inGame = pluginOn(EnumSet.of(WorldType.MEMBERS), new int[] {12850, 13658});
    assertFalse(inGame.isCaptureReady());
    assertFalse(inGame.gated(() -> true).getAsBoolean());

    BankstandPlugin outside = pluginOn(EnumSet.of(WorldType.MEMBERS), new int[] {12850});
    assertTrue(outside.isCaptureReady());
    assertTrue(pluginOn(EnumSet.of(WorldType.MEMBERS), null).isCaptureReady());
  }

  @Test
  public void aSkippedFlagWinsAlongsideOthers() {
    assertFalse(CaptureGate.allowsWorld(EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL)));
  }

  @Test
  public void anUnknownWorldIsNotAllowed() {
    assertFalse(CaptureGate.allowsWorld(null));
  }

  @Test
  public void waitsForTheWelcomeScreenThenAFewTicks() {
    CaptureGate gate = new CaptureGate();
    gate.onTick(true);
    gate.onTick(true);
    assertFalse(gate.isSettled());
    for (int i = 0; i < CaptureGate.SETTLE_TICKS - 1; i++) {
      gate.onTick(false);
      assertFalse(gate.isSettled());
    }
    gate.onTick(false);
    assertTrue(gate.isSettled());
  }

  @Test
  public void theWelcomeScreenReopeningRestartsTheSettle() {
    CaptureGate gate = settled();
    gate.onTick(true);
    assertFalse(gate.isSettled());
  }

  @Test
  public void resetWaitsForAFreshSettle() {
    CaptureGate gate = settled();
    gate.reset();
    assertFalse(gate.allows(EnumSet.noneOf(WorldType.class), null));
  }

  @Test
  public void anUnsettledLoginBlocksEvenAMainWorld() throws Exception {
    BankstandPlugin plugin = pluginOn(EnumSet.of(WorldType.MEMBERS));
    Field gateField = BankstandPlugin.class.getDeclaredField("captureGate");
    gateField.setAccessible(true);
    gateField.set(plugin, new CaptureGate());

    assertFalse(plugin.isCaptureReady());
    assertFalse(plugin.gated(() -> true).getAsBoolean());
  }

  /** The scheduled, logout and identity reads use isCaptureReady; event captures use gated. */
  @Test
  public void everyGateFollowsTheWorldCheckForEveryType() throws Exception {
    for (WorldType type : WorldType.values()) {
      BankstandPlugin plugin = pluginOn(EnumSet.of(type));
      boolean allowed = !skipped(type);
      assertEquals(type.name(), allowed, plugin.isCaptureReady());
      assertEquals(type.name(), allowed, plugin.gated(() -> true).getAsBoolean());
      assertFalse(type.name(), plugin.gated(() -> false).getAsBoolean());
    }
  }

  private interface Trigger {
    void fire(EventOutbox outbox, BooleanSupplier gate);
  }

  /** Each event capture emits on a main world and stays silent on every skipped one. */
  @Test
  public void eachEventCaptureFollowsTheWorldCheckForEveryType() throws Exception {
    Trigger[] triggers = {
      (outbox, gate) -> {
        PetDropCapture pet = new PetDropCapture(outbox, gate, () -> 1L);
        pet.handleMessage("You have a funny feeling like you're being followed.");
        pet.handleMessage("New item added to your collection log: Baby mole");
      },
      (outbox, gate) ->
          new CollectionLogUnlockCapture(outbox, gate, () -> 1L)
              .handleMessage("New item added to your collection log: Abyssal orphan"),
      (outbox, gate) ->
          new CombatAchievementCompletionCapture(outbox, gate, () -> 1L)
              .handleMessage("Congratulations, you've completed a hard combat task: Whack-a-Mole."),
      (outbox, gate) ->
          new CombatAchievementTierCompletionCapture(outbox, gate, () -> 1L)
              .handleMessage(
                  "Congratulations, you have completed the easy tier of the Combat Achievements!"),
    };
    for (WorldType type : WorldType.values()) {
      BooleanSupplier gate = pluginOn(EnumSet.of(type)).gated(() -> true);
      for (int i = 0; i < triggers.length; i++) {
        EventOutbox outbox = new EventOutbox(new File(folder.newFolder(), "events.json"), new Gson());
        triggers[i].fire(outbox, gate);
        assertEquals(type.name() + " trigger " + i, skipped(type), outbox.pending().isEmpty());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Integer> lastSkillRead(BankstandPlugin plugin) throws Exception {
    Field f = BankstandPlugin.class.getDeclaredField("lastSkillRead");
    f.setAccessible(true);
    return (Map<String, Integer>) f.get(plugin);
  }

  private static void setLastSkillRead(BankstandPlugin plugin) throws Exception {
    Field f = BankstandPlugin.class.getDeclaredField("lastSkillRead");
    f.setAccessible(true);
    f.set(plugin, Collections.singletonMap("attack", 100));
  }

  /** The logout read is checked against the last read, which must be this account's. */
  @Test
  public void forgetsTheLastSkillReadOnANewLoginAndOnLogout() throws Exception {
    BankstandPlugin plugin = pluginOn(EnumSet.noneOf(WorldType.class));
    setLastSkillRead(plugin);
    GameStateChanged login = new GameStateChanged();
    login.setGameState(GameState.LOGGED_IN);
    plugin.onGameStateChanged(login);
    assertNull(lastSkillRead(plugin));

    setLastSkillRead(plugin);
    GameStateChanged logout = new GameStateChanged();
    logout.setGameState(GameState.LOGIN_SCREEN);
    plugin.onGameStateChanged(logout);
    assertNull(lastSkillRead(plugin));
    assertFalse(plugin.isCaptureReady());
  }
}
