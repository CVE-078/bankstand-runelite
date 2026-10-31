package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.PairResponse;
import com.bankstand.http.HttpResponse;
import com.bankstand.http.HttpTransport;
import com.bankstand.session.AccountSession;
import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameTick;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Drives the real plugin's login tick: the identity request goes out only with a capture on. */
public class IdentityGateTest {
  private static final String URL = "https://x.test";
  private static final long ACCOUNT = 123456789012345L;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final List<String> posts = new ArrayList<>();

  @Test
  public void noIdentityRequestIsSentAtLoginWithEveryCaptureOff() throws Exception {
    BankstandPlugin plugin = plugin(new HashSet<>());
    tickPastSettle(plugin);
    assertTrue("sent: " + posts, posts.isEmpty());
  }

  @Test
  public void anyOneCaptureOnSendsTheIdentityRequestOnce() throws Exception {
    BankstandPlugin plugin = plugin(new HashSet<>(Arrays.asList("collectPetDrops")));
    tickPastSettle(plugin);
    assertEquals(1, posts.size());
    assertTrue(posts.get(0).contains(Long.toString(ACCOUNT)));
  }

  private void tickPastSettle(BankstandPlugin plugin) {
    for (int i = 0; i < CaptureGate.SETTLE_TICKS + 2; i++) {
      plugin.onGameTick(new GameTick());
    }
  }

  private BankstandPlugin plugin(Set<String> on) throws Exception {
    BankstandPlugin plugin = new BankstandPlugin();
    Gson gson = new Gson();
    ScheduledExecutorService inline =
        proxy(
            ScheduledExecutorService.class,
            (p, m, a) -> {
              if (m.getName().equals("submit") || m.getName().equals("execute")) {
                ((Runnable) a[0]).run();
              }
              return null;
            });
    HttpTransport transport =
        new HttpTransport() {
          @Override
          public HttpResponse post(String url, String body, Map<String, String> headers) {
            posts.add(body);
            return new HttpResponse(200, "{\"verified\":true}");
          }

          @Override
          public HttpResponse get(String url, Map<String, String> headers) {
            return new HttpResponse(404, "");
          }
        };
    DeviceCredentialStore devices =
        new DeviceCredentialStore(new File(folder.getRoot(), "device.json"), gson);
    devices.save(
        DeviceCredentials.of(
            gson.fromJson("{\"deviceToken\":\"tok\",\"deviceId\":\"d\"}", PairResponse.class),
            URL));
    Player player =
        proxy(Player.class, (p, m, a) -> m.getName().equals("getName") ? "Zezima" : null);
    Client client =
        proxy(
            Client.class,
            (p, m, a) -> {
              switch (m.getName()) {
                case "getLocalPlayer":
                  return player;
                case "getWorldType":
                  return EnumSet.noneOf(WorldType.class);
                case "getGameState":
                  return GameState.LOGGED_IN;
                default:
                  return defaultFor(m.getReturnType());
              }
            });
    BankstandConfig config =
        proxy(
            BankstandConfig.class,
            (p, m, a) ->
                m.getName().equals("serverBaseUrl")
                    ? URL
                    : m.getReturnType() == boolean.class
                        ? on.contains(m.getName())
                        : defaultFor(m.getReturnType()));
    set(plugin, "client", client);
    set(plugin, "config", config);
    set(plugin, "executor", inline);
    set(plugin, "deviceStore", devices);
    set(plugin, "pairingClient", new BankstandClient(transport, gson, inline));
    EventOutbox events = new EventOutbox(new File(folder.getRoot(), "events.json"), gson);
    LootOutbox loot = new LootOutbox(new File(folder.getRoot(), "loot.json"), gson);
    AccountSession session = (AccountSession) get(plugin, "session");
    PairingQueues queues =
        new PairingQueues(
            (PresenceQueue) get(plugin, "presenceQueue"),
            events,
            (LootLedger) get(plugin, "lootLedger"),
            loot,
            (CaptureGenerations) get(plugin, "captureGenerations"),
            () -> devices.load().getPairingId(),
            session,
            System::currentTimeMillis);
    set(plugin, "captureBoundary", queues.boundary());
    session.onLogin(ACCOUNT);
    return plugin;
  }

  private static Object defaultFor(Class<?> type) {
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

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = BankstandPlugin.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object get(Object target, String name) throws Exception {
    Field field = BankstandPlugin.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }
}
