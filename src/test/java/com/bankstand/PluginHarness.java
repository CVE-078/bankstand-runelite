package com.bankstand;

import com.bankstand.http.HttpResponse;
import com.bankstand.http.HttpTransport;
import com.bankstand.session.AccountSession;
import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;

/**
 * A paired plugin wired to fakes: a client, config, an executor the test drives by hand, and a
 * transport that records every request. Nothing reaches a network or RuneLite.
 */
final class PluginHarness {

  static final long MAIN = 42L;
  static final int[] LUMBRIDGE = {12850};
  static final int[] LMS_ARENA = {13658};

  // What the fake client and config report.
  Set<WorldType> world = EnumSet.of(WorldType.MEMBERS);
  int[] regions = LUMBRIDGE;
  long hash = MAIN;
  GameState gameState = GameState.LOGGED_IN;
  int xp;
  final Map<Integer, Integer> varps = new HashMap<>();
  final Map<Integer, Integer> varbits = new HashMap<>();
  String serverUrl = BankstandKeys.DEFAULT_SERVER_URL;
  final Map<String, Boolean> toggles = new HashMap<>();

  final ManualExecutor executor = new ManualExecutor();
  final RecordingTransport transport = new RecordingTransport();
  final BankstandPlugin plugin = new BankstandPlugin();
  final DeviceCredentialStore deviceStore;
  final AckedStateStore ackedStore;
  final EventOutbox eventOutbox;
  final LootOutbox lootOutbox;

  PluginHarness(File dir) throws Exception {
    Gson gson = new Gson();
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
                    case "isClientThread":
                      return true;
                    case "getGameState":
                      return gameState;
                    case "getLocalPlayer":
                      return localPlayer();
                    case "getSkillExperience":
                      return xp;
                    case "getVarpValue":
                      return varps.getOrDefault((Integer) args[0], 0);
                    case "getVarbitValue":
                      return args.length == 1 ? varbits.getOrDefault((Integer) args[0], 0) : 0;
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
                    return toggles.getOrDefault(method.getName(), Boolean.TRUE);
                  }
                  if ("serverBaseUrl".equals(method.getName())) {
                    return serverUrl;
                  }
                  return zero(method.getReturnType());
                });
    deviceStore = new DeviceCredentialStore(new File(dir, "device.json"), gson);
    DeviceCredentials paired = DeviceCredentials.none();
    paired.setToken("device-token");
    paired.setServerUrl(serverUrl);
    paired.setPairingId("first");
    deviceStore.save(paired);
    ackedStore = new AckedStateStore(new File(dir, "acked.json"), gson);
    eventOutbox = new EventOutbox(new File(dir, "events.json"), gson);
    lootOutbox = new LootOutbox(new File(dir, "loot.json"), gson);

    ClientThread clientThread = new ClientThread();
    Field threadClient = ClientThread.class.getDeclaredField("client");
    threadClient.setAccessible(true);
    threadClient.set(clientThread, client);

    set("client", client);
    set("config", config);
    set("clientThread", clientThread);
    set("executor", executor);
    set("configManager", configManager(dir));
    set("chatMessageManager", chatMessageManager());
    set("deviceStore", deviceStore);
    set("ackedStore", ackedStore);
    set("eventOutbox", eventOutbox);
    set("lootOutbox", lootOutbox);
    set("pairingClient", new BankstandClient(transport, gson, executor));
    set(
        "pairingQueues",
        new PairingQueues(
            (PresenceQueue) get("presenceQueue"),
            eventOutbox,
            (LootLedger) get("lootLedger"),
            lootOutbox,
            () -> deviceStore.load().getPairingId()));
  }

  /** A real ConfigManager over a scratch file, built without RuneLite's injector. */
  private static ConfigManager configManager(File dir) throws Exception {
    ConfigManager manager = (ConfigManager) unsafe().allocateInstance(ConfigManager.class);
    Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
    Constructor<?> data = dataType.getDeclaredConstructor(File.class);
    data.setAccessible(true);
    setField(manager, "configProfile", data.newInstance(new File(dir, "runelite.properties")));
    setField(manager, "eventBus", new EventBus());
    Constructor<?> handler =
        Class.forName("net.runelite.client.config.ConfigInvocationHandler")
            .getDeclaredConstructor(ConfigManager.class);
    handler.setAccessible(true);
    setField(manager, "handler", handler.newInstance(manager));
    return manager;
  }

  private static net.runelite.api.Player localPlayer() {
    return (net.runelite.api.Player)
        Proxy.newProxyInstance(
            net.runelite.api.Player.class.getClassLoader(),
            new Class<?>[] {net.runelite.api.Player.class},
            (proxy, method, args) ->
                "getName".equals(method.getName()) ? "Zezima" : zero(method.getReturnType()));
  }

  /** Collects chat notices instead of drawing them. */
  private static net.runelite.client.chat.ChatMessageManager chatMessageManager() throws Exception {
    Object manager = unsafe().allocateInstance(net.runelite.client.chat.ChatMessageManager.class);
    setField(manager, "queuedMessages", new java.util.concurrent.ConcurrentLinkedQueue<>());
    return (net.runelite.client.chat.ChatMessageManager) manager;
  }

  /** Runs what the plugin handed the client thread with invokeLater. */
  void runClientThread() throws Exception {
    ClientThread thread = (ClientThread) get("clientThread");
    Method drain = ClientThread.class.getDeclaredMethod("invoke");
    drain.setAccessible(true);
    drain.invoke(thread);
  }

  private static sun.misc.Unsafe unsafe() throws Exception {
    Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    return (sun.misc.Unsafe) unsafeField.get(null);
  }

  static Object zero(Class<?> type) {
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

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  void set(String name, Object value) throws Exception {
    Field field = BankstandPlugin.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(plugin, value);
  }

  Object get(String name) throws Exception {
    Field field = BankstandPlugin.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(plugin);
  }

  /** Calls a private plugin method by name; arguments must match its parameter count. */
  Object call(String name, Object... args) throws Exception {
    for (Method method : BankstandPlugin.class.getDeclaredMethods()) {
      if (method.getName().equals(name) && method.getParameterCount() == args.length) {
        method.setAccessible(true);
        return method.invoke(plugin, args);
      }
    }
    throw new NoSuchMethodException(name);
  }

  void state(GameState state) {
    GameStateChanged event = new GameStateChanged();
    event.setGameState(state);
    plugin.onGameStateChanged(event);
  }

  void ticks(int count) {
    for (int i = 0; i < count; i++) {
      plugin.onGameTick(new GameTick());
    }
  }

  void logIn() {
    state(GameState.LOGGED_IN);
    ticks(CaptureGate.SETTLE_TICKS);
  }

  void hopTo(Set<WorldType> type) {
    state(GameState.HOPPING);
    world = type;
    logIn();
  }

  int generation() throws Exception {
    return ((AccountSession) get("session")).getGeneration();
  }

  /** Runs tasks queued on the executor until it is idle. Scheduled (delayed) ones wait. */
  static final class ManualExecutor extends AbstractExecutorService
      implements ScheduledExecutorService {
    final Deque<Runnable> queue = new ArrayDeque<>();
    final List<Runnable> scheduled = new ArrayList<>();
    final List<Throwable> errors = new ArrayList<>();

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
      return newTaskFor(java.util.concurrent.Executors.callable(runnable, value));
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
      return new FutureTask<T>(callable) {
        @Override
        protected void setException(Throwable t) {
          errors.add(t);
          super.setException(t);
        }
      };
    }

    @Override
    public void execute(Runnable command) {
      queue.add(command);
    }

    /** Pulls the next queued task without running it, so a test can reorder completions. */
    Runnable take() {
      return queue.poll();
    }

    void runAll() {
      Runnable next;
      while ((next = queue.poll()) != null) {
        next.run();
      }
      if (!errors.isEmpty()) {
        throw new AssertionError("a task threw", errors.get(0));
      }
    }

    private ScheduledFuture<?> later(Runnable task) {
      scheduled.add(task);
      return new Stub();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      return later(command);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      return later(command);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      return later(command);
    }

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    private static final class Stub extends FutureTask<Object> implements ScheduledFuture<Object> {
      Stub() {
        super(() -> null);
      }

      @Override
      public long getDelay(TimeUnit unit) {
        return 0;
      }

      @Override
      public int compareTo(Delayed other) {
        return 0;
      }
    }
  }

  /** Answers each request by its path and keeps every one it saw. */
  static final class RecordingTransport implements HttpTransport {
    final List<String> urls = new ArrayList<>();
    final List<String> bodies = new ArrayList<>();
    Function<String, HttpResponse> answer = body -> new HttpResponse(200, "{}");
    final Map<String, Function<String, HttpResponse>> byPath = new HashMap<>();

    @Override
    public synchronized HttpResponse post(String url, String body, Map<String, String> headers) {
      urls.add(url);
      bodies.add(body);
      for (Map.Entry<String, Function<String, HttpResponse>> route : byPath.entrySet()) {
        if (url.endsWith(route.getKey())) {
          return route.getValue().apply(body);
        }
      }
      return answer.apply(body);
    }

    @Override
    public synchronized HttpResponse get(String url, Map<String, String> headers) {
      urls.add(url);
      return new HttpResponse(404, "");
    }

    synchronized long count(String path) {
      return urls.stream().filter(u -> u.endsWith(path)).count();
    }
  }
}
