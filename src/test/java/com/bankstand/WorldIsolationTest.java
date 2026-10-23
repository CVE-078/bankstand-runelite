package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.session.AccountSession;
import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptEvent;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemStack;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A paired plugin driven through world hops, profiles and account switches with a fake client. */
public class WorldIsolationTest {

  private static final long MAIN = 42L;
  private static final long ALT = 77L;
  private static final int[] LUMBRIDGE = {12850};
  private static final int[] LMS_ARENA = {13658};

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  // What the fake client reports.
  private Set<WorldType> world = EnumSet.of(WorldType.MEMBERS);
  private int[] regions = LUMBRIDGE;
  private long hash = MAIN;

  private BankstandPlugin plugin;

  @Before
  public void setUp() throws Exception {
    Client client =
        (Client)
            Proxy.newProxyInstance(
                Client.class.getClassLoader(),
                new Class<?>[] {Client.class},
                (proxy, method, args) -> {
                  switch (method.getName()) {
                    case "getWorldType":
                      return world;
                    case "getMapRegions":
                      return regions;
                    case "getAccountHash":
                      return hash;
                    default:
                      return zero(method.getReturnType());
                  }
                });
    BankstandConfig config =
        (BankstandConfig)
            Proxy.newProxyInstance(
                BankstandConfig.class.getClassLoader(),
                new Class<?>[] {BankstandConfig.class},
                (proxy, method, args) -> {
                  if (method.getReturnType() == boolean.class) {
                    // Sessions off keeps presence sends (and their executor) out of these tests.
                    return !"collectSessions".equals(method.getName());
                  }
                  if (method.getReturnType() == String.class) {
                    return BankstandKeys.DEFAULT_SERVER_URL;
                  }
                  return zero(method.getReturnType());
                });
    DeviceCredentialStore store =
        new DeviceCredentialStore(new File(folder.getRoot(), "device.json"), new Gson());
    DeviceCredentials paired = DeviceCredentials.none();
    paired.setToken("device-token");
    paired.setServerUrl(BankstandKeys.DEFAULT_SERVER_URL);
    store.save(paired);

    plugin = new BankstandPlugin();
    set("client", client);
    set("config", config);
    set("deviceStore", store);
    set("pairingClient", new BankstandClient(null, new Gson(), null));
  }

  private static Object zero(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    return null;
  }

  private void set(String name, Object value) throws Exception {
    Field field = BankstandPlugin.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(plugin, value);
  }

  private Object get(String name) throws Exception {
    Field field = BankstandPlugin.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(plugin);
  }

  private BooleanSupplier predicate(String name) throws Exception {
    Method method = BankstandPlugin.class.getDeclaredMethod(name);
    method.setAccessible(true);
    return () -> {
      try {
        return (Boolean) method.invoke(plugin);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    };
  }

  private LongSupplier currentHash() throws Exception {
    Method method = BankstandPlugin.class.getDeclaredMethod("currentAccountHash");
    method.setAccessible(true);
    return () -> {
      try {
        return (Long) method.invoke(plugin);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    };
  }

  private void state(GameState state) {
    GameStateChanged event = new GameStateChanged();
    event.setGameState(state);
    plugin.onGameStateChanged(event);
  }

  private void ticks(int count) {
    for (int i = 0; i < count; i++) {
      plugin.onGameTick(new GameTick());
    }
  }

  private void logIn() {
    state(GameState.LOGGED_IN);
    ticks(CaptureGate.SETTLE_TICKS);
  }

  private void hopTo(Set<WorldType> type) {
    state(GameState.HOPPING);
    world = type;
    logIn();
  }

  /** Every gate a capture or send consults, by the plugin's own predicates. */
  private boolean anyCaptureOpen() throws Exception {
    String[] gates = {"isLootCaptureEnabled", "isSlayerEventCaptureEnabled"};
    boolean open = plugin.isCaptureReady() || plugin.isFinalReadReady();
    for (String gate : gates) {
      open |= predicate(gate).getAsBoolean();
    }
    String[] events = {
      "isNotableDropCaptureEnabled",
      "isPetDropCaptureEnabled",
      "isCollectionLogEventCaptureEnabled",
      "isCombatAchievementCompletionCaptureEnabled",
      "isCombatAchievementTierCompletionCaptureEnabled",
      "isDiaryTaskCompletionCaptureEnabled",
      "isQuestCompletionCaptureEnabled",
    };
    for (String gate : events) {
      open |= plugin.gated(predicate(gate)).getAsBoolean();
    }
    return open;
  }

  private boolean everyCaptureOpen() throws Exception {
    return plugin.isCaptureReady()
        && plugin.isFinalReadReady()
        && predicate("isLootCaptureEnabled").getAsBoolean()
        && predicate("isSlayerEventCaptureEnabled").getAsBoolean()
        && plugin.gated(predicate("isCollectionLogEventCaptureEnabled")).getAsBoolean()
        && plugin.gated(predicate("isDiaryTaskCompletionCaptureEnabled")).getAsBoolean();
  }

  private CollectionLogUnlockCapture unlocks(EventOutbox outbox) throws Exception {
    return new CollectionLogUnlockCapture(
        outbox, plugin.gated(predicate("isCollectionLogEventCaptureEnabled")), currentHash());
  }

  private static void unlock(CollectionLogUnlockCapture capture, String item) {
    capture.handleMessage("New item added to your collection log: " + item);
  }

  @Test
  public void aHopIntoALastManStandingGameAndOutAgain() throws Exception {
    logIn();
    assertTrue(everyCaptureOpen());

    // The game itself is a normal world; only the arena region marks it.
    regions = LMS_ARENA;
    ticks(1);
    assertFalse(anyCaptureOpen());

    // Logging out from inside the game: the login screen may report no regions any more.
    regions = null;
    assertFalse("the last in-game tick was inside the arena", plugin.isFinalReadReady());

    regions = LUMBRIDGE;
    ticks(1);
    assertTrue(everyCaptureOpen());

    // A Last Man Standing world flag fails closed too.
    hopTo(EnumSet.of(WorldType.LAST_MAN_STANDING));
    assertFalse(anyCaptureOpen());
    hopTo(EnumSet.of(WorldType.MEMBERS));
    assertTrue(everyCaptureOpen());
  }

  @Test
  public void aLeaguesProfileOnTheSameHashCapturesNothing() throws Exception {
    EventOutbox outbox = new EventOutbox(folder.newFile("events.json"), new Gson());
    CollectionLogUnlockCapture capture = unlocks(outbox);
    logIn();
    unlock(capture, "Abyssal orphan");

    hopTo(EnumSet.of(WorldType.SEASONAL, WorldType.MEMBERS));
    assertFalse(anyCaptureOpen());
    unlock(capture, "Leagues trophy");

    hopTo(EnumSet.of(WorldType.MEMBERS));
    unlock(capture, "Pet snakeling");

    List<OutboxEntry> pending = outbox.pending();
    assertEquals(2, pending.size());
    for (OutboxEntry entry : pending) {
      assertEquals(MAIN, entry.getAccountHash());
      assertFalse(entry.getEvent().getPayload().toString().contains("Leagues"));
    }
  }

  @Test
  public void unknownAndNewWorldBitsFailClosed() throws Exception {
    logIn();
    hopTo(null);
    assertFalse(anyCaptureOpen());
    hopTo(EnumSet.of(WorldType.HARDCORE_MAIN));
    assertFalse(anyCaptureOpen());
    hopTo(EnumSet.of(WorldType.MEMBERS, WorldType.FRESH_START_WORLD));
    assertFalse(anyCaptureOpen());
  }

  /** Feeds one collection log item to the harvest, as the client's enumeration script does. */
  private Set<Integer> harvest(int itemId) throws Exception {
    ScriptEvent script =
        (ScriptEvent)
            Proxy.newProxyInstance(
                ScriptEvent.class.getClassLoader(),
                new Class<?>[] {ScriptEvent.class},
                (proxy, method, args) ->
                    "getArguments".equals(method.getName())
                        ? new Object[] {0, itemId}
                        : zero(method.getReturnType()));
    ScriptPreFired event = new ScriptPreFired(4100);
    event.setScriptEvent(script);
    plugin.onScriptPreFired(event);
    return ((CollectionLogAccumulator) get("collectionLog")).observed();
  }

  @Test
  public void theCollectionLogHarvestReadsOnlyARegularWorldWithAKnownAccount() throws Exception {
    hash = AccountSession.LOGGED_OUT;
    logIn();
    assertTrue("no account hash yet", harvest(1).isEmpty());
    state(GameState.LOGIN_SCREEN);

    hash = MAIN;
    logIn();
    hopTo(EnumSet.of(WorldType.SEASONAL));
    assertTrue(harvest(2).isEmpty());
    hopTo(EnumSet.of(WorldType.MEMBERS));
    regions = LMS_ARENA;
    assertTrue(harvest(3).isEmpty());
    regions = LUMBRIDGE;
    assertEquals(Collections.singleton(4), harvest(4));
  }

  @Test
  public void nothingIsOpenWhileLoadingOrBeforeTheHashIsKnown() throws Exception {
    hash = AccountSession.LOGGED_OUT;
    logIn();
    for (String gate :
        new String[] {
          "isLootCaptureEnabled",
          "isSlayerEventCaptureEnabled",
          "isCollectionLogEventCaptureEnabled",
          "isDiaryTaskCompletionCaptureEnabled",
          "isNotableDropCaptureEnabled",
        }) {
      assertFalse("no account hash yet: " + gate, predicate(gate).getAsBoolean());
    }
    state(GameState.LOGIN_SCREEN);

    hash = MAIN;
    state(GameState.LOGGED_IN);
    ticks(CaptureGate.SETTLE_TICKS - 1);
    assertFalse("login not settled", anyCaptureOpen());
    ticks(1);
    assertTrue(everyCaptureOpen());

    state(GameState.CONNECTION_LOST);
    assertFalse(anyCaptureOpen());
  }

  @Test
  public void anAccountSwitchKeepsQueuedItemsOnTheirOwnAccount() throws Exception {
    EventOutbox outbox = new EventOutbox(folder.newFile("events.json"), new Gson());
    CollectionLogUnlockCapture capture = unlocks(outbox);
    logIn();
    unlock(capture, "Abyssal orphan");

    state(GameState.LOGIN_SCREEN);
    hash = ALT;
    state(GameState.LOGGED_IN);
    unlock(capture, "Before settle");
    ticks(CaptureGate.SETTLE_TICKS);
    unlock(capture, "Pet snakeling");

    List<OutboxEntry> pending = outbox.pending();
    assertEquals(2, pending.size());
    assertEquals(MAIN, pending.get(0).getAccountHash());
    assertTrue(pending.get(0).getEvent().getPayload().toString().contains("Abyssal orphan"));
    assertEquals(ALT, pending.get(1).getAccountHash());
    assertTrue(pending.get(1).getEvent().getPayload().toString().contains("Pet snakeling"));
  }

  /** The next character never meets the previous one's log or diary bits, even before a capture. */
  @Test
  public void anAccountSwitchForgetsTheLastCharactersStateAtLogin() throws Exception {
    logIn();
    CollectionLogAccumulator log = (CollectionLogAccumulator) get("collectionLog");
    log.restore(new HashSet<>(Collections.singletonList(13262)));
    DiaryTaskBits bits = (DiaryTaskBits) get("diaryTaskBits");
    bits.restore(Collections.singletonMap(4499, 1));

    state(GameState.LOGIN_SCREEN);
    hash = ALT;
    state(GameState.LOGGED_IN);

    assertTrue(log.observed().isEmpty());
    assertTrue(bits.snapshot().isEmpty());
  }

  @Test
  public void lootFromAKillOnASkippedWorldIsNeverRecorded() throws Exception {
    LootOutbox outbox = new LootOutbox(folder.newFile("loot.json"), new Gson());
    LootLedger ledger = new LootLedger();
    LootCapture capture =
        new LootCapture(
            predicate("isLootCaptureEnabled"),
            currentHash(),
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
            ledger,
            outbox,
            () -> {},
            Instant::now);
    logIn();
    capture.handleKill(2, "Goblin", Collections.singletonList(new ItemStack(526, 1)));
    hopTo(EnumSet.of(WorldType.SEASONAL));
    capture.handleKill(3, "Cow", Collections.singletonList(new ItemStack(526, 1)));
    hopTo(EnumSet.of(WorldType.MEMBERS));
    regions = LMS_ARENA;
    capture.handleKill(4, "Rat", Collections.singletonList(new ItemStack(526, 1)));

    List<LootEntry> closed = ledger.closeAll();
    assertEquals(1, closed.size());
    assertEquals(2, closed.get(0).getEvent().getNpcId());
    assertEquals(MAIN, closed.get(0).getAccountHash());
  }

  @Test
  public void slayerProgressOnASkippedWorldIsNotRemembered() throws Exception {
    AtomicInteger reads = new AtomicInteger();
    SlayerTaskCompletionCapture capture =
        new SlayerTaskCompletionCapture(
            new EventOutbox(folder.newFile("events.json"), new Gson()),
            predicate("isSlayerEventCaptureEnabled"),
            currentHash(),
            () -> {
              reads.incrementAndGet();
              return SlayerTask.NONE;
            },
            null);
    logIn();
    hopTo(EnumSet.of(WorldType.SEASONAL));
    VarbitChanged changed = new VarbitChanged();
    changed.setVarpId(VarPlayerID.SLAYER_COUNT);
    changed.setValue(5);
    capture.onVarbitChanged(changed);
    assertEquals(0, reads.get());

    hopTo(EnumSet.of(WorldType.MEMBERS));
    capture.onVarbitChanged(changed);
    assertEquals(1, reads.get());
  }

}
