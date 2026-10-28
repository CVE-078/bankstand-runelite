package com.bankstand;

import com.bankstand.dto.EventAck;
import com.bankstand.dto.PairResponse;
import com.bankstand.dto.PresenceResponse;
import com.bankstand.dto.SubmitLootResponse;
import com.bankstand.dto.SubmitSnapshotResponse;
import com.bankstand.http.HttpTransport;
import com.bankstand.http.OkHttpTransport;
import com.bankstand.session.AccountSession;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
    name = "Bankstand",
    description =
        "Sync your skills, quests, achievement diaries, combat achievements, collection log, play"
            + " sessions, loot and slayer task to your Bankstand account. Sends data to an"
            + " external server.",
    tags = {"bankstand", "account", "progress", "external"})
public class BankstandPlugin extends Plugin {

  // Bounded retry for a transient submit failure (network, 429, 5xx). Terminal failures fail
  // fast. Runs on the background executor, so the backoff never blocks the game thread.
  private static final int MAX_SUBMIT_ATTEMPTS = 3;
  private static final long SUBMIT_RETRY_BASE_DELAY_MS = 1000L;

  // Matches the server's per-device cooldown, so a change is reported at most once per window.
  private static final int CAPTURE_INTERVAL_SECONDS = 60;

  // A submit refused by that cooldown is retried once, within these bounds.
  private static final long COOLDOWN_RETRY_FALLBACK_MS = 60_000L;
  private static final long COOLDOWN_RETRY_MAX_MS = 120_000L;

  // A batch that still fails after its bounded retry waits for the next scheduled drain.
  private static final int EVENT_DRAIN_INTERVAL_SECONDS = 60;

  // Must match the server's per-request event cap: over it, the server rejects the whole
  // request. Chunking limits one bad group's blast radius to its own chunk.
  private static final int MAX_EVENTS_PER_SUBMIT = 50;

  // The collection log lives server-side and is only revealed while the log interface
  // enumerates it: this script fires once per item (item id is its second argument). A
  // player-run Search enumerates the whole log; the plugin only watches, never drives it.
  private static final int COLLECTION_LOG_ITEM_SCRIPT = 4100;

  // Its own directory, so a player can find and delete what the plugin keeps.
  private static final File ACKED_STATE_DIR = new File(RuneLite.RUNELITE_DIR, "bankstand");
  private static final String ACKED_STATE_FILE = "acked-state.json";
  private static final String DEVICE_FILE = "device.json";
  private static final String MANIFEST_FILE = "manifest.json";
  private static final String EVENTS_FILE = "events.json";
  private static final String LOOT_FILE = "loot-outbox.json";


  // Prefix for every chat line the plugin writes. The darker light-theme gold stays readable
  // both on the parchment chat box and on a transparent one.
  private static final Color BRAND = new Color(0xB3730A);
  private static final String NOTICE_PREFIX = "Bankstand: ";
  private static final String NO_LONGER_PAIRED =
      "This client is no longer paired. Pair again with a new code from bankstand.gg/account/connection.";
  private static final String UNPAIR_UNREACHABLE = "Could not reach Bankstand. Remove this device on the website.";

  // The server contract is an explicit skill allowlist, not Skill.values(): an unknown key
  // (OVERALL, or a newly added skill) rejects the whole skills block. Widen the server first.
  // Package-private so a test pins its content.
  static final EnumSet<Skill> CAPTURED_SKILLS =
      EnumSet.of(
          Skill.ATTACK,
          Skill.DEFENCE,
          Skill.STRENGTH,
          Skill.HITPOINTS,
          Skill.RANGED,
          Skill.PRAYER,
          Skill.MAGIC,
          Skill.COOKING,
          Skill.WOODCUTTING,
          Skill.FLETCHING,
          Skill.FISHING,
          Skill.FIREMAKING,
          Skill.CRAFTING,
          Skill.SMITHING,
          Skill.MINING,
          Skill.HERBLORE,
          Skill.AGILITY,
          Skill.THIEVING,
          Skill.SLAYER,
          Skill.FARMING,
          Skill.RUNECRAFT,
          Skill.HUNTER,
          Skill.CONSTRUCTION,
          Skill.SAILING);

  @Inject private Client client;
  @Inject private ConfigManager configManager;
  @Inject private BankstandConfig config;
  @Inject private OkHttpClient okHttpClient;
  @Inject private Gson gson;
  @Inject private ScheduledExecutorService executor;
  @Inject private ClientThread clientThread;
  @Inject private ChatMessageManager chatMessageManager;
  @Inject private InfoBoxManager infoBoxManager;
  @Inject private EventBus eventBus;
  @Inject private ItemManager itemManager;
  @Inject private ClientToolbar clientToolbar;

  private final AccountSession session = new AccountSession();
  private final SkillBaseline skillBaseline = new SkillBaseline();
  private final QuestBaseline questBaseline = new QuestBaseline();
  private final DiaryBaseline diaryBaseline = new DiaryBaseline();
  // Resolves diary task identity. Inert until the manifest lists verified regions.
  private final AccountTypeBaseline accountTypeBaseline = new AccountTypeBaseline();
  private final CombatAchievementBaseline combatAchievementBaseline =
      new CombatAchievementBaseline();
  private final CollectionLogAccumulator collectionLog = new CollectionLogAccumulator();
  private final CollectionLogBaseline collectionLogBaseline = new CollectionLogBaseline();
  private final CollectionLogSync collectionLogSync = new CollectionLogSync();
  private CollectionLogSyncInfoBox syncInfoBox;
  private AckedStateStore ackedStore;
  private ManifestStore manifestStore;
  private EventOutbox eventOutbox;
  private NotableDropCapture notableDropCapture;
  private PetDropCapture petDropCapture;
  private CollectionLogUnlockCapture collectionLogUnlockCapture;
  private CombatAchievementCompletionCapture combatAchievementCompletionCapture;
  private CombatAchievementTierCompletionCapture combatAchievementTierCompletionCapture;
  private DiaryTaskCompletionCapture diaryTaskCompletionCapture;
  private QuestCompletionCapture questCompletionCapture;
  private SlayerTaskCompletionCapture slayerTaskCompletionCapture;
  private SlayerTaskStart slayerTaskStart;
  private final SlayerTaskBaseline slayerTaskBaseline = new SlayerTaskBaseline();
  private int ticksSinceLoggedIn; // Since the last LOGGED_IN. Client thread.

  private final LootLedger lootLedger = new LootLedger();
  private final LootSendPacer lootPacer = new LootSendPacer();
  // Only its 429 pause is used: presence has no early sends.
  private final LootSendPacer presencePacer = new LootSendPacer();
  private final EventSendPacer eventPacer = new EventSendPacer();
  private final AtomicBoolean lootSending = new AtomicBoolean();
  // Held until every chunk of a drain, retries included, has answered: one flight per queue.
  private final AtomicBoolean eventSending = new AtomicBoolean();
  private LootOutbox lootOutbox;
  private PairingQueues pairingQueues;
  private LootCapture lootCapture;

  private final PresenceTracker presenceTracker = new PresenceTracker();
  private final PresenceQueue presenceQueue = new PresenceQueue();
  private final AtomicBoolean presenceSending = new AtomicBoolean();
  // A LOGGED_IN whose presence waits for the settle, then goes out a tick later. Client thread.
  private boolean presenceLoginPending;
  private volatile ScheduledFuture<?> hopTimer;
  // The generation whose next capture skips the change gate; -1 when none.
  private volatile int forcedSubmitGeneration = -1;
  // Null while the plugin is stopped. volatile: shutDown() clears it while refreshPanel()
  // reads it from the executor; refreshPanel snapshots it into a local.
  private volatile BankstandPanel panel;
  private NavigationButton navButton;
  // Session-scoped: a restart or account switch starts empty.
  private final RecentActivityLog recentActivityLog = new RecentActivityLog();
  // Written on the client thread, read on the EDT. Keyed by capability name.
  private final Map<String, Long> lastSyncedAt = new ConcurrentHashMap<>();
  // The panel's failure line, kept per path.
  private final SyncFailures failures = new SyncFailures(this::refreshPanel);

  /**
   * What the server currently ingests. Never null: falls back to the cached manifest, then the
   * bundled one, so a manifest problem never stops a paired client working.
   */
  private volatile CapabilityManifest manifest = CapabilityManifest.bundled();
  private DeviceCredentialStore deviceStore;
  // Suppresses a recurring failure from printing every capture cycle.
  private final NoticeGate noticeGate = new NoticeGate();
  // Stops a revoked token retrying forever, and paces a capture against a down server.
  private final SubmitGate submitGate = new SubmitGate();
  // Bumped per fresh snapshot submit, so a cooldown retry can tell it was superseded.
  private final AtomicLong submitSeq = new AtomicLong();
  // The last trusted skill read, so the logout read is checked against it, not trusted blind.
  private Map<String, Integer> lastSkillRead;
  // World and login-settle check shared by every capture.
  private final CaptureGate captureGate = new CaptureGate();
  // Zero until something reached the server. Written on the executor, read on the client thread.
  private volatile long lastSubmitAtMs;
  // A change means the account switched and the baseline must be forgotten.
  private int baselineGeneration = -1;
  // Digest of the raw diary task values last stored. Client thread.
  private String diaryTaskVarsAcked;
  // The generation whose acked state has loaded, and whether that read failed. Client thread.
  private int ackedLoadedGeneration = -1;
  private boolean ackedLoadFailed;
  private volatile BankstandClient pairingClient;
  // Set when a guided read finishes COMPLETE; cleared only once the server acknowledged a
  // submission carrying the flag, so a failed send keeps it pending.
  private boolean pendingFullEnumeration = false;
  // Whether the gate passed on the last in-game tick. Client thread.
  private boolean readyLastTick;

  @Provides
  BankstandConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(BankstandConfig.class);
  }

  @Override
  protected void startUp() {
    HttpTransport transport = new OkHttpTransport(okHttpClient);
    pairingClient = newClient(transport);
    // A file, not ConfigManager. See AckedStateStore.
    ackedStore = new AckedStateStore(new File(ACKED_STATE_DIR, ACKED_STATE_FILE), gson);
    manifestStore = new ManifestStore(new File(ACKED_STATE_DIR, MANIFEST_FILE), gson);
    // Cached copy first, so a capture never waits on the network.
    manifest = manifestStore.current(null);
    refreshManifest();
    deviceStore = new DeviceCredentialStore(new File(ACKED_STATE_DIR, DEVICE_FILE), gson);
    LegacyCredentials.migrate(configManager, deviceStore);

    eventOutbox = new EventOutbox(new File(ACKED_STATE_DIR, EVENTS_FILE), gson);
    slayerTaskStart =
        new SlayerTaskStart(
            new File(ACKED_STATE_DIR, "slayer-task-start.json"), gson, executor, client, session);
    // Feeds the panel from the same emit the captures make to the outbox.
    java.util.function.Consumer<TransientEvent> onEmit = this::recordEmittedActivity;
    notableDropCapture =
        new NotableDropCapture(
            eventOutbox,
            gated(this::isNotableDropCaptureEnabled),
            this::currentAccountHash,
            itemManager,
            () -> (long) config.notableDropThreshold(),
            NotableDropCapture.UNTRADEABLE_ALLOWLIST,
            onEmit);
    petDropCapture =
        new PetDropCapture(
            eventOutbox, gated(this::isPetDropCaptureEnabled), this::currentAccountHash, onEmit);
    collectionLogUnlockCapture =
        new CollectionLogUnlockCapture(
            eventOutbox,
            gated(this::isCollectionLogEventCaptureEnabled),
            this::currentAccountHash,
            onEmit);
    combatAchievementCompletionCapture =
        new CombatAchievementCompletionCapture(
            eventOutbox,
            gated(this::isCombatAchievementCompletionCaptureEnabled),
            this::currentAccountHash,
            onEmit);
    combatAchievementTierCompletionCapture =
        new CombatAchievementTierCompletionCapture(
            eventOutbox,
            gated(this::isCombatAchievementTierCompletionCaptureEnabled),
            this::currentAccountHash,
            onEmit);
    diaryTaskCompletionCapture =
        new DiaryTaskCompletionCapture(
            eventOutbox,
            gated(this::isDiaryTaskCompletionCaptureEnabled),
            this::currentAccountHash,
            onEmit);
    questCompletionCapture =
        new QuestCompletionCapture(
            eventOutbox,
            gated(this::isQuestCompletionCaptureEnabled),
            this::currentAccountHash,
            client,
            BankstandPlugin::isKnownQuest,
            onEmit);
    eventBus.register(notableDropCapture);
    eventBus.register(petDropCapture);
    eventBus.register(collectionLogUnlockCapture);
    eventBus.register(combatAchievementCompletionCapture);
    eventBus.register(combatAchievementTierCompletionCapture);
    eventBus.register(diaryTaskCompletionCapture);
    eventBus.register(questCompletionCapture);
    slayerTaskCompletionCapture =
        new SlayerTaskCompletionCapture(
            eventOutbox,
            this::isSlayerEventCaptureEnabled,
            this::currentAccountHash,
            () ->
                slayerTaskStart.read(
                    isSlayerCaptureEnabled() && SlayerTaskReader.isReadable(ticksSinceLoggedIn)),
            onEmit);
    eventBus.register(slayerTaskCompletionCapture);
    lootOutbox = new LootOutbox(new File(ACKED_STATE_DIR, LOOT_FILE), gson);
    pairingQueues =
        new PairingQueues(
            presenceQueue,
            eventOutbox,
            lootLedger,
            lootOutbox,
            PairingQueues.pairingOf(deviceStore));
    lootCapture =
        new LootCapture(
            this::isLootCaptureEnabled,
            this::currentAccountHash,
            new LootCapture.Items() {
              @Override
              public int canonical(int itemId) {
                return itemManager.canonicalize(itemId);
              }

              @Override
              public boolean isNotable(int itemId, int quantity) {
                return isNotableStack(itemId, quantity);
              }
            },
            lootLedger,
            lootOutbox,
            this::requestFastLootSend,
            Instant::now);
    eventBus.register(lootCapture);

    // A toolbar icon, not a permanent sidebar tab: the panel is checked on demand.
    panel =
        new BankstandPanel(
            () -> clientThread.invoke(this::runManualSync),
            () -> LinkBrowser.browse(savedServerUrl()),
            this::refreshPanel,
            this::turnOnSessions);
    navButton =
        NavigationButton.builder()
            .tooltip("Bankstand")
            .icon(icon())
            .panel(panel)
            .priority(5)
            .build();
    clientToolbar.addNavigation(navButton);
    refreshPanel();
  }

  @Override
  protected void shutDown() {
    BankstandClient api = pairingClient;
    pairingClient = null;
    // Stops scheduled retries from dispatching; their callbacks see a failure.
    if (api != null) {
      api.close();
    }
    // A pairing reply still in flight is now stale: never stored, and its token revoked.
    if (pairingQueues != null) {
      pairingQueues.beginPairing();
    }
    if (notableDropCapture != null) {
      eventBus.unregister(notableDropCapture);
    }
    if (petDropCapture != null) {
      eventBus.unregister(petDropCapture);
    }
    if (collectionLogUnlockCapture != null) {
      eventBus.unregister(collectionLogUnlockCapture);
    }
    if (combatAchievementCompletionCapture != null) {
      eventBus.unregister(combatAchievementCompletionCapture);
    }
    if (combatAchievementTierCompletionCapture != null) {
      eventBus.unregister(combatAchievementTierCompletionCapture);
    }
    if (diaryTaskCompletionCapture != null) {
      eventBus.unregister(diaryTaskCompletionCapture);
    }
    if (questCompletionCapture != null) {
      eventBus.unregister(questCompletionCapture);
    }
    if (slayerTaskCompletionCapture != null) {
      eventBus.unregister(slayerTaskCompletionCapture);
    }
    if (lootCapture != null) {
      eventBus.unregister(lootCapture);
    }
    // Presence is dropped; the server closes the session on silence. Loot is kept on disk.
    if (lootOutbox != null) {
      lootOutbox.addAll(lootLedger.closeAll());
    }
    cancelHopTimer();
    presenceTracker.reset();
    presenceQueue.clear();
    presenceLoginPending = false;
    if (navButton != null) {
      clientToolbar.removeNavigation(navButton);
    }
    panel = null;
    navButton = null;
    // An infobox outlives the plugin that added it.
    collectionLogSync.reset();
    hideSyncInfoBox();
  }

  private long currentAccountHash() {
    return session.getAccountHash();
  }

  /** True once logged in long enough to read, on a world whose progress counts. */
  boolean isCaptureReady() {
    return captureGate.allows(client.getWorldType(), client.getMapRegions());
  }

  // World and regions may already be cleared at the login screen; the last tick saw them live.
  boolean isFinalReadReady() {
    return readyLastTick && isCaptureReady();
  }

  /** Wraps an event capture's gate in the shared world and settle check. */
  BooleanSupplier gated(BooleanSupplier capability) {
    return () -> isCaptureReady() && capability.getAsBoolean();
  }

  private boolean isNotableDropCaptureEnabled() {
    return pairingClient != null
        && isPaired()
        && session.isActive()
        && config.collectNotableDrops()
        && manifest.allows("notableDrops");
  }

  private boolean isPetDropCaptureEnabled() {
    return pairingClient != null
        && isPaired()
        && session.isActive()
        && config.collectPetDrops()
        && manifest.allows("petDrops");
  }

  // Separate from isCollectionLogCaptureEnabled, which also gates the guided sync that must
  // still work unpaired. Without these gates, events pile up unpaired, or carry accountHash -1,
  // which the server rejects and which poisons the whole batch.
  private boolean isCollectionLogEventCaptureEnabled() {
    return pairingClient != null
        && isPaired()
        && session.isActive()
        && isCollectionLogCaptureEnabled();
  }

  /** Standard worlds only. */
  private boolean isLootCaptureEnabled() {
    return pairingClient != null
        && isPaired()
        && session.isActive()
        && isCaptureReady()
        && config.collectLoot()
        && manifest.allows("loot");
  }

  private boolean isSlayerCaptureEnabled() {
    return config.collectSlayer() && manifest.allows("slayer");
  }

  private boolean isSlayerEventCaptureEnabled() {
    return pairingClient != null
        && isPaired()
        && session.isActive()
        && isCaptureReady()
        && isSlayerCaptureEnabled();
  }

  /** Whether a presence signal may leave the client. The tracker runs regardless. */
  private boolean isSessionCaptureEnabled() {
    return pairingClient != null
        && isPaired()
        && config.collectSessions()
        && manifest.allows("sessions");
  }

  /** The notable-drop rule, used to send a kill's loot early. Client thread. */
  private boolean isNotableStack(int itemId, int quantity) {
    ItemComposition comp = itemManager.getItemComposition(itemId);
    if (comp == null) {
      return false;
    }
    Long unitValue = comp.isGeTradeable() ? (long) itemManager.getItemPrice(itemId) : null;
    return NotableDropCapture.qualifies(
        comp.getName(),
        comp.isGeTradeable(),
        unitValue,
        quantity,
        config.notableDropThreshold(),
        NotableDropCapture.UNTRADEABLE_ALLOWLIST);
  }

  /**
   * Drains the outbox, grouped by capturing account and split into chunks of at most {@link
   * #MAX_EVENTS_PER_SUBMIT}. Each chunk acks independently, so an invalid event only blocks its
   * own chunk. A failed chunk waits for the next drain; {@link EventSendPacer} paces the rest.
   */
  @Schedule(period = EVENT_DRAIN_INTERVAL_SECONDS, unit = ChronoUnit.SECONDS)
  public void drainEventOutbox() {
    pulsePresence();
    retryQueuedPresence();
    if (lootOutbox != null) {
      lootOutbox.addAll(lootLedger.closeAll());
    }
    drainLoot();
    if (pairingClient == null || eventOutbox == null) {
      return;
    }
    String url = savedServerUrl();
    String token;
    List<OutboxEntry> pending;
    // One step, so the token and the queue always belong to the same pairing.
    synchronized (pairingQueues) {
      token = deviceStore.load().tokenFor(url);
      pending = eventOutbox.pending();
    }
    if (token == null || pending.isEmpty() || submitGate.isHalted()) {
      return;
    }
    if (!eventSending.compareAndSet(false, true)) {
      return;
    }
    Map<Long, List<TransientEvent>> byAccount = new LinkedHashMap<>();
    for (OutboxEntry entry : pending) {
      byAccount.computeIfAbsent(entry.getAccountHash(), key -> new ArrayList<>()).add(entry.getEvent());
    }
    BankstandClient api = pairingClient;
    executor.submit(
        () -> {
          List<CompletableFuture<?>> flights = new ArrayList<>();
          SyncFailures.Drain drain = failures.startDrain();
          try {
            for (Map.Entry<Long, List<TransientEvent>> group : byAccount.entrySet()) {
              long hash = group.getKey();
              for (List<TransientEvent> chunk : chunkEvents(group.getValue(), MAX_EVENTS_PER_SUBMIT)) {
                // The first attempt runs here, so a 429, unrouted reply or re-pair stops the next chunk.
                if (!eventPacer.maySend(hash, System.currentTimeMillis()) || isReplaced(url, token)) {
                  break;
                }
                flights.add(api
                    .submitEventsWithRetry(
                        url,
                        token,
                        hash,
                        chunk,
                        MAX_SUBMIT_ATTEMPTS,
                        SUBMIT_RETRY_BASE_DELAY_MS)
                    .whenComplete(
                        (response, failure) -> {
                          // The old pairing's reply says nothing about the new one.
                          if (isReplaced(url, token)) {
                            return;
                          }
                          if (failure != null) {
                            drain.failed(failure);
                            log.debug("event drain failed for one chunk: {}", failure.getMessage());
                            if (failure instanceof SubmitException
                                && ((SubmitException) failure).isAuthFailure()
                                && haltIfCurrent(url, token)) {
                              notice(NO_LONGER_PAIRED);
                            }
                          }
                          if (eventPacer.record(hash, System.currentTimeMillis(), response, failure)) {
                            eventOutbox.ack(EventOutbox.idsToAck(response.getAcks()));
                          }
                        }));
              }
            }
          } finally {
            CompletableFuture.allOf(flights.toArray(new CompletableFuture<?>[0]))
                .whenComplete((done, failure) -> drain.finish(flights.size(), eventSending));
          }
        });
  }

  /** Offers this minute's "online" mark, while in game on a standard world. */
  private void pulsePresence() {
    long hash = presenceTracker.accountHash();
    if (!presenceTracker.isOnStandardWorld()
        || hash == AccountSession.LOGGED_OUT
        || !isCaptureReady()) {
      return;
    }
    if (!isSessionCaptureEnabled()) {
      return;
    }
    presenceQueue.offerHeartbeat(
        new PresenceSignal(PresenceSignal.ACTIVE, hash, System.nanoTime())
            .withLoot(isLootConsented()));
    sendPresence();
  }

  /** A failed logout or off stays queued; retry it here, logged in or not. */
  private void retryQueuedPresence() {
    if (shouldRetryPresence(presenceQueue.size(), manifest.allows("sessions"))) {
      sendPresence();
    }
  }

  static boolean shouldRetryPresence(int queued, boolean serverAllowsSessions) {
    return queued > 0 && serverAllowsSessions;
  }

  /** Queues transitions if sessions are on, then sends them. */
  private void enqueuePresence(List<PresenceSignal> signals) {
    if (signals.isEmpty() || !isSessionCaptureEnabled()) {
      return;
    }
    queuePresence(signals);
  }

  /** Queues without the toggle check, for the {@code off} sent as sessions turn off. */
  private void queuePresence(List<PresenceSignal> signals) {
    boolean lootOn = isLootConsented();
    for (PresenceSignal signal : signals) {
      if (signal.getAccountHash() != AccountSession.LOGGED_OUT) {
        presenceQueue.addTransition(signal.withLoot(lootOn));
      }
    }
    sendPresence();
  }

  /** Sends queued presence in order on the executor. At most one send chain runs. */
  private void sendPresence() {
    if (pairingClient == null
        || !isPaired()
        || submitGate.isHalted()
        || !presencePacer.mayDrain(System.currentTimeMillis())) {
      return;
    }
    if (!presenceSending.compareAndSet(false, true)) {
      return;
    }
    executor.submit(this::runPresenceSends);
  }

  private void runPresenceSends() {
    while (true) {
      PresenceSignal next;
      String url = savedServerUrl();
      String token;
      synchronized (pairingQueues) {
        next = presenceQueue.peek();
        token = deviceStore.load().tokenFor(url);
      }
      if (token == null) {
        presenceSending.set(false);
        return;
      }
      if (next == null) {
        presenceSending.set(false);
        // A value queued between the peek and the release found the chain still busy.
        if (presenceQueue.peek() == null || !presenceSending.compareAndSet(false, true)) {
          return;
        }
        continue;
      }
      BankstandClient client = pairingClient;
      if (client == null) {
        presenceSending.set(false);
        return;
      }
      try {
        PresenceResponse res =
            client.submitPresence(
                url,
                token,
                next.getAccountHash(),
                next.getState(),
                next.elapsedMillis(System.nanoTime()),
                next.getLoot());
        // Answered for a pairing since replaced: its queue is gone, so this says nothing.
        if (isReplaced(url, token)) {
          continue;
        }
        presenceQueue.onAnswered(next);
        if (res.isApplied()) {
          lastSyncedAt.put(Capability.SESSIONS.key, System.currentTimeMillis());
          if (PresenceSignal.LOGIN.equals(next.getState())) {
            onSessionStarted(next.getAccountHash());
          }
        }
      } catch (SubmitException e) {
        log.debug("presence send failed: {}", e.getMessage());
        if (isReplaced(url, token)) {
          continue;
        }
        presencePacer.pauseFor(System.currentTimeMillis(), e.getRetryAfterMillis());
        if (e.isAuthFailure() && haltIfCurrent(url, token)) {
          notice(NO_LONGER_PAIRED);
        }
        if (e.isRetryable()) {
          presenceQueue.onRetryableFailure(next);
        } else {
          presenceQueue.onTerminalFailure(next);
        }
        presenceSending.set(false);
        return;
      }
    }
  }

  /** A session started: the next capture skips the change gate, to record starting XP. */
  private void onSessionStarted(long accountHash) {
    clientThread.invoke(
        () -> {
          if (session.getAccountHash() != accountHash) {
            return;
          }
          forcedSubmitGeneration = session.getGeneration();
          captureSkills();
        });
  }

  /** A notable kill closed its loot window: send it now if pacing allows. */
  private void requestFastLootSend() {
    if (lootPacer.tryFastSend(System.currentTimeMillis())) {
      drainLoot();
    }
  }

  /** Sends the loot outbox per account in chunks; a failure waits for the next drain. */
  private void drainLoot() {
    if (pairingClient == null
        || lootOutbox == null
        || !isPaired()
        || !manifest.allows("loot")
        || submitGate.isHalted()) {
      return;
    }
    if (!lootPacer.mayDrain(System.currentTimeMillis())) {
      return;
    }
    if (!lootSending.compareAndSet(false, true)) {
      return;
    }
    executor.submit(
        () -> {
          try {
            sendLoot();
          } finally {
            lootSending.set(false);
          }
        });
  }

  private void sendLoot() {
    String url = savedServerUrl();
    String token;
    List<LootEntry> pending;
    synchronized (pairingQueues) {
      token = deviceStore.load().tokenFor(url);
      pending = lootOutbox.pending();
    }
    if (token == null) {
      return;
    }
    Map<Long, List<LootEvent>> byAccount = new LinkedHashMap<>();
    for (LootEntry entry : pending) {
      byAccount.computeIfAbsent(entry.getAccountHash(), key -> new ArrayList<>()).add(entry.getEvent());
    }
    for (Map.Entry<Long, List<LootEvent>> group : byAccount.entrySet()) {
      for (List<LootEvent> chunk : chunk(group.getValue(), MAX_EVENTS_PER_SUBMIT)) {
        BankstandClient client = pairingClient;
        // A disconnect or re-pair since this drain started: nothing more goes out under it.
        if (client == null || isReplaced(url, token)) {
          return;
        }
        try {
          SubmitLootResponse res = client.submitLoot(url, token, group.getKey(), chunk);
          lootOutbox.ack(LootOutbox.idsToAck(res.getAcks()));
          if (res.getAcks().stream().anyMatch(EventAck::isStored)) {
            lastSyncedAt.put(Capability.LOOT.key, System.currentTimeMillis());
            refreshPanel();
          }
        } catch (SubmitException e) {
          log.debug("loot send failed: {}", e.getMessage());
          if (isReplaced(url, token)) {
            return;
          }
          if (e.isAuthFailure() && haltIfCurrent(url, token)) {
            notice(NO_LONGER_PAIRED);
          }
          if (e.getRetryAfterMillis() > 0) {
            lootPacer.pauseFor(System.currentTimeMillis(), e.getRetryAfterMillis());
          }
          return;
        }
      }
    }
  }

  /** {@link #chunkEvents} for any element type. */
  static <T> List<List<T>> chunk(List<T> items, int maxSize) {
    List<List<T>> chunks = new ArrayList<>();
    for (int start = 0; start < items.size(); start += maxSize) {
      chunks.add(new ArrayList<>(items.subList(start, Math.min(start + maxSize, items.size()))));
    }
    return chunks;
  }

  /** Splits into batches of at most {@code maxSize}, oldest first. */
  static List<List<TransientEvent>> chunkEvents(List<TransientEvent> events, int maxSize) {
    return chunk(events, maxSize);
  }

  private java.util.List<String> statusLines() {
    Player local = client.getLocalPlayer();
    String name = session.isActive() && local != null ? local.getName() : null;
    List<String> lines = statusReportLines(name);
    if (isPaired() && eventOutbox != null && lootOutbox != null) {
      lines.addAll(
          StatusReport.queueLines(
              "events", eventOutbox.pending().size(), eventOutbox.dropped(), eventOutbox.problem()));
      lines.addAll(
          StatusReport.queueLines(
              "loot drops", lootOutbox.pending().size(), lootOutbox.dropped(), lootOutbox.problem()));
    }
    return lines;
  }

  private List<String> statusReportLines(String name) {
    return StatusReport.lines(
        isPaired(),
        savedServerUrl(),
        name == null || name.isEmpty() ? null : name,
        lastSubmitAtMs == 0L ? null : StatusReport.describeAge(System.currentTimeMillis() - lastSubmitAtMs),
        submitGate.isHalted() ? "authentication failed, so submission is paused. Re-pair to resume." : null,
        enabledCapabilities(),
        collectionLog.isEmpty() ? -1 : collectionLog.size(),
        // Only with an account loaded: the varbit reads 0 logged out, same as a regular account.
        name == null || name.isEmpty()
            ? null
            : AccountTypes.describe(client.getVarbitValue(AccountTypes.ACCOUNT_TYPE_VARBIT)),
        "Server accepts: " + manifest.describe() + ".");
  }

  /**
   * Runs the scheduled capture rather than a second submit path, so it inherits the change
   * gate, per-block ack, world check, backoff and persistence.
   */
  private void requestManualCapture() {
    if (submitGate.isHalted()) {
      notice("Submission is paused after an authentication failure. Re-pair to resume.");
      return;
    }
    captureSkills();
  }

  /** Shared by {@code ::bstand sync} and the panel's "Sync now" button. */
  private void runManualSync() {
    for (String line : StatusReport.syncLines(isPaired(), enabledCapabilities())) {
      notice(line);
    }
    if (isPaired() && !enabledCapabilities().isEmpty()) {
      requestManualCapture();
    }
  }

  /** Re-runs the identity submit, ignoring the once-per-session flag. */
  private void relinkCharacter() {
    if (!isPaired()) {
      notice("Not paired. Paste a pairing code in the Bankstand settings first.");
      return;
    }
    if (!session.isActive()) {
      notice("Log in first, then run ::bstand link.");
      return;
    }
    if (!isSkillCaptureEnabled()) {
      notice("Turn on skill capture first: it carries the name and hash that link a character.");
      return;
    }
    if (!isCaptureReady()) {
      notice("Bankstand does not capture on this world. Try again on a regular world.");
      return;
    }
    clientThread.invoke(
        () -> {
          Player local = client.getLocalPlayer();
          String name = local == null ? null : local.getName();
          if (name == null || name.isEmpty()) {
            notice("Could not read your character name. Try again in a moment.");
            return;
          }
          notice("Linking " + name + "...");
          submitIdentity(session.getAccountHash(), session.getGeneration(), name);
        });
  }

  /** Which action a {@code ::bstand} invocation resolves to. Pure routing, testable. */
  enum CommandAction {
    STATUS,
    SYNC,
    LINK,
    LOG,
    REPAIR,
    EXPORT,
    HELP,
    UNKNOWN
  }

  static boolean isBankstandCommand(String command) {
    return BankstandKeys.COMMAND.equalsIgnoreCase(command)
        || BankstandKeys.COMMAND_ALIAS.equalsIgnoreCase(command);
  }

  static CommandAction actionFor(String[] args) {
    String action = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "status";
    switch (action) {
      case "status":
        return CommandAction.STATUS;
      case "sync":
        return CommandAction.SYNC;
      case "link":
        return CommandAction.LINK;
      case "log":
        return CommandAction.LOG;
      case "repair":
        return CommandAction.REPAIR;
      case "export":
        return CommandAction.EXPORT;
      case "help":
      case "commands":
        return CommandAction.HELP;
      default:
        return CommandAction.UNKNOWN;
    }
  }

  // A :: command, not !, so these private account actions are never broadcast to public chat.
  // A chat command because RuneLite config has no button item.
  @Subscribe
  public void onCommandExecuted(CommandExecuted event) {
    if (!isBankstandCommand(event.getCommand())) {
      return;
    }
    switch (actionFor(event.getArguments())) {
      case STATUS:
        for (String line : statusLines()) {
          notice(line);
        }
        break;
      case SYNC:
        runManualSync();
        break;
      case LINK:
        relinkCharacter();
        break;
      case LOG:
        armCollectionLogRead();
        break;
      case REPAIR:
        disconnect();
        notice("Paste a fresh pairing code in the Bankstand settings to reconnect.");
        break;
      case EXPORT:
        exportConfig();
        break;
      case HELP:
        for (String line : StatusReport.helpLines()) {
          notice(line);
        }
        break;
      case UNKNOWN:
      default:
        notice("Unknown command. Try ::bstand help to see what's available.");
        break;
    }
  }

  /**
   * Arms a guided collection log read. Arming only shows the infobox proactively; capture is
   * never gated on it, so this is safe mid-browse. Says why when it cannot run.
   */
  private void armCollectionLogRead() {
    if (!isCollectionLogCaptureEnabled()) {
      notice("Collection log capture is off. Turn it on in the Bankstand settings first.");
      return;
    }
    if (!isCollectionLogOpen()) {
      notice("Open your collection log first, then run ::bstand log.");
      return;
    }
    if (!isCaptureReady()) {
      notice("Bankstand does not capture on this world. Try again on a regular world.");
      return;
    }
    collectionLogSync.arm();
    showSyncInfoBox();
    notice("Armed. Click Search in the collection log to read it.");
  }

  /**
   * Prints the Collect/Events toggle state and copies it to the clipboard where one is
   * reachable. Chat gets the lines either way.
   */
  private void exportConfig() {
    List<String> lines =
        StatusReport.exportLines(
            config.collectSkills(),
            config.collectQuests(),
            config.collectDiaries(),
            config.collectCollectionLog(),
            config.collectCombatAchievements(),
            config.collectAccountType(),
            config.collectNotableDrops(),
            config.notableDropThreshold(),
            config.collectPetDrops(),
            config.collectSessions(),
            config.collectLoot(),
            config.collectSlayer());
    for (String line : lines) {
      notice(line);
    }
    if (copyToClipboard(String.join("\n", lines))) {
      notice("Copied to clipboard.");
    }
  }

  /** Best effort: a headless environment throws rather than failing quietly. */
  private static boolean copyToClipboard(String text) {
    try {
      java.awt.Toolkit.getDefaultToolkit()
          .getSystemClipboard()
          .setContents(new java.awt.datatransfer.StringSelection(text), null);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  /** The panel's per-capability rows, in {@link Capability} order, skipping disabled ones. */
  static List<PanelModel.CapabilityRow> capabilityRows(
      EnumSet<Capability> enabled, Map<String, Long> lastSyncedAt) {
    List<PanelModel.CapabilityRow> rows = new ArrayList<>();
    for (Capability capability : enabled) {
      rows.add(new PanelModel.CapabilityRow(capability.label, lastSyncedAt.get(capability.key)));
    }
    return rows;
  }

  private EnumSet<Capability> enabledCapabilitySet() {
    EnumSet<Capability> on = EnumSet.noneOf(Capability.class);
    addIf(on, Capability.SKILLS, isSkillCaptureEnabled());
    addIf(on, Capability.QUESTS, isQuestCaptureEnabled());
    addIf(on, Capability.DIARIES, isDiaryCaptureEnabled());
    addIf(on, Capability.COLLECTION_LOG, isCollectionLogCaptureEnabled());
    addIf(on, Capability.COMBAT_ACHIEVEMENTS, isCombatAchievementCaptureEnabled());
    addIf(on, Capability.ACCOUNT_TYPE, isAccountTypeCaptureEnabled());
    addIf(on, Capability.NOTABLE_DROPS, isNotableDropCaptureEnabled());
    addIf(on, Capability.PET_DROPS, isPetDropCaptureEnabled());
    addIf(on, Capability.SESSIONS, isSessionCaptureEnabled());
    addIf(on, Capability.LOOT, isLootConsented());
    addIf(on, Capability.SLAYER, isSlayerCaptureEnabled());
    return on;
  }

  private static void addIf(EnumSet<Capability> set, Capability capability, boolean on) {
    if (on) {
      set.add(capability);
    }
  }

  private java.util.List<String> enabledCapabilities() {
    return capabilityNames(enabledCapabilitySet());
  }

  /** Loot's toggle and manifest gate, safe off the client thread. */
  private boolean isLootConsented() {
    return config.collectLoot() && manifest.allows("loot");
  }

  /**
   * Capability names for the status and sync lines, in a stable order. A capability sent on the
   * wire but missing here makes status silently under-report, so a test pins it.
   */
  static java.util.List<String> capabilityNames(EnumSet<Capability> enabled) {
    java.util.List<String> on = new java.util.ArrayList<>();
    for (Capability capability : enabled) {
      on.add(capability.statusName);
    }
    return on;
  }

  /**
   * A pasted pairing code pairs, and the disconnect toggle forgets credentials (config has no
   * buttons). Both clear themselves, which re-fires this with a value that falls through.
   */
  @Subscribe
  public void onConfigChanged(ConfigChanged event) {
    if (!BankstandKeys.GROUP.equals(event.getGroup())) {
      return;
    }
    if (BankstandKeys.KEY_PAIRING_CODE.equals(event.getKey())) {
      String code = event.getNewValue();
      if (code != null && !code.trim().isEmpty()) {
        pair(code);
      }
    } else if (BankstandKeys.KEY_DISCONNECT.equals(event.getKey())
        && Boolean.parseBoolean(event.getNewValue())) {
      disconnect();
    } else if (BankstandKeys.KEY_SERVER_URL.equals(event.getKey())) {
      if (deviceStore.load().isPaired() && !isPaired()) {
        notice("Paired with another server. Paste a pairing code to pair with " + savedServerUrl() + ".");
      }
    } else if (BankstandKeys.KEY_COLLECT_SESSIONS.equals(event.getKey())) {
      onSessionsToggled(Boolean.parseBoolean(event.getNewValue()));
    } else if (BankstandKeys.KEY_COLLECT_LOOT.equals(event.getKey())) {
      if (!Boolean.parseBoolean(event.getNewValue())) {
        lootLedger.discard();
      }
      // Tell the server now, not at the next minute's mark.
      pulsePresence();
    }
    refreshPanel();
  }

  /** Off ends an open session with {@code off}; on, in game, starts one now. */
  private void onSessionsToggled(boolean on) {
    long now = System.nanoTime();
    if (on) {
      enqueuePresence(presenceTracker.onSessionsTurnedOn(now));
      return;
    }
    if (pairingClient != null && isPaired() && manifest.allows("sessions")) {
      queuePresence(presenceTracker.onSessionsTurnedOff(now));
    }
  }

  /**
   * Harvests the collection log while the client enumerates it, whoever started the
   * enumeration. Consent is the opt-in; the data is the player's own.
   */
  @Subscribe
  public void onScriptPreFired(ScriptPreFired event) {
    // No session means no known account to hold the items for.
    if (event.getScriptId() != COLLECTION_LOG_ITEM_SCRIPT
        || !isCollectionLogCaptureEnabled()
        || !session.isActive()
        || !isCaptureReady()) {
      return;
    }
    Object[] args = event.getScriptEvent() == null ? null : event.getScriptEvent().getArguments();
    // args[1] is the item id. Guarded: this internal game script's shape is not a contract.
    if (args == null || args.length < 2 || !(args[1] instanceof Integer)) {
      return;
    }
    collectionLog.observe((Integer) args[1]);
    // Passive browsing fires this too; the sync ignores it unless a guided read is armed.
    collectionLogSync.onItemObserved((Integer) args[1], isSearchOpen());
  }

  private void showSyncInfoBox() {
    if (syncInfoBox != null) {
      return;
    }
    syncInfoBox = new CollectionLogSyncInfoBox(icon(), this, collectionLogSync);
    infoBoxManager.addInfoBox(syncInfoBox);
  }

  private void hideSyncInfoBox() {
    if (syncInfoBox == null) {
      return;
    }
    infoBoxManager.removeInfoBox(syncInfoBox);
    syncInfoBox = null;
  }

  /**
   * Advances an in-flight read by one tick. The infobox follows {@code isActive()}, not the
   * outcome: a running read returns no outcome every tick, and a timed-out read ends with none.
   */
  private void tickCollectionLogSync() {
    // Turning the capability off stops entries arriving, which would look like a finished read.
    if (!isCollectionLogCaptureEnabled()) {
      collectionLogSync.reset();
      hideSyncInfoBox();
      return;
    }
    // A search can start a read nobody armed. Idempotent.
    showSyncInfoBox();
    CollectionLogSync.Outcome outcome =
        collectionLogSync.onTick(isSearchOpen(), isCollectionLogOpen());
    if (collectionLogSync.isActive()) {
      return;
    }
    hideSyncInfoBox();
    if (outcome != null) {
      // Slots filled, not raw ids held.
      notice(syncOutcomeMessage(outcome, VariantIds.countEntries(collectionLog.observed())));
      if (outcome == CollectionLogSync.Outcome.COMPLETE) {
        pendingFullEnumeration = true;
      }
    }
  }

  /**
   * Reports what the plugin captured, not the game's "X of Y" total, which could not be read
   * reliably off the interface. The server derives the log's own figures.
   */
  static String syncOutcomeMessage(CollectionLogSync.Outcome outcome, int entriesFilled) {
    String entries = entriesFilled + (entriesFilled == 1 ? " entry" : " entries");
    if (outcome == CollectionLogSync.Outcome.COMPLETE) {
      return "Collection log synced. " + entries + " logged.";
    }
    return "Partial read of your collection log. " + entries + " logged so far.";
  }

  /**
   * The chat line for an identity submit's result. Not-claimed and held-by-another-account need
   * different fixes, so they read differently. {@code outcome} is optional on the wire; a
   * missing or unknown value falls back to the not-claimed line.
   */
  static String identityNoticeFor(boolean verified, String linkedRsn, String outcome) {
    if (verified) {
      return "Verified as " + linkedRsn + ".";
    }
    if ("held_by_other".equals(outcome) || "hash_bound_elsewhere".equals(outcome)) {
      return "This character is already linked, so there is nothing to claim here."
          + " If that looks wrong, contact support.";
    }
    return "This character is not claimed on Bankstand yet, so nothing will sync."
        + " Claim it on your account page.";
  }

  /**
   * Whether a chat line is a quest or diary-tier completion broadcast. It only signals that
   * something may have changed; the capture re-reads every state anyway. Requires a specific
   * word, since a bare "Congratulations" also opens level-ups and clue rewards. Unverified
   * against a live client, both the wording and the GAMEMESSAGE type the caller checks.
   */
  static boolean isQuestOrDiaryCompletionMessage(String message) {
    if (!message.startsWith("Congratulations")) {
      return false;
    }
    String lower = message.toLowerCase(java.util.Locale.ROOT);
    return lower.contains("quest") || lower.contains("diary") || lower.contains("tasks in the");
  }

  private boolean isSearchOpen() {
    Widget results = client.getWidget(InterfaceID.Collection.SEARCH_RESULTS);
    return results != null && !results.isHidden();
  }

  /**
   * True while the player is in the collection log, search view included. Opening search can
   * hide the log's root widget, which must not count as closing the log.
   */
  private boolean isCollectionLogOpen() {
    Widget log = client.getWidget(InterfaceID.Collection.UNIVERSE);
    return (log != null && !log.isHidden()) || isSearchOpen();
  }

  private BufferedImage icon() {
    return ImageUtil.loadImageResource(BankstandPlugin.class, "icon.png");
  }

  /**
   * Adds a capture's emit to the panel: a recent-activity line and a fresh last-synced stamp.
   * Runs on the client thread, so refreshing the panel inline is safe.
   */
  private void recordEmittedActivity(TransientEvent event) {
    String description = RecentActivityLog.describe(event.getType(), event.getPayload());
    if (description != null) {
      recentActivityLog.record(description);
    }
    Capability capability = Capability.forEventType(event.getType());
    if (capability != null) {
      lastSyncedAt.put(capability.key, System.currentTimeMillis());
    }
    refreshPanel();
  }

  /**
   * Which capabilities this ack synced, with a recent-activity line each (null for no line).
   * Skills ride along on every submission, so they also need {@code skillsChanged}. The
   * collection log and combat achievements get no line: their chat captures already add a more
   * specific one.
   */
  static Map<String, String> syncedCapabilitiesThisCycle(
      EnumSet<Capability> advanced, boolean skillsChanged) {
    Map<String, String> synced = new LinkedHashMap<>();
    for (Capability capability : advanced) {
      if (capability != Capability.SKILLS || skillsChanged) {
        synced.put(capability.key, capability.syncedLine);
      }
    }
    return synced;
  }

  /**
   * Rebuilds the panel. Safe from any thread: builds the model on the client thread, renders on
   * the EDT. Reads {@code panel} once into a local, so a racing {@code shutDown()} cannot cause an
   * NPE on the EDT; a late render into a detached panel is harmless.
   */
  private void refreshPanel() {
    BankstandPanel current = panel;
    if (current == null) {
      return;
    }
    clientThread.invoke(
        () -> {
          PanelModel model = buildPanelModel();
          SwingUtilities.invokeLater(() -> current.render(model));
        });
  }

  /** Must run on the client thread. Returns a frozen snapshot for the panel. */
  private PanelModel buildPanelModel() {
    Player local = client.getLocalPlayer();
    String name = session.isActive() && local != null ? local.getName() : null;
    List<PanelModel.CapabilityRow> rows =
        capabilityRows(enabledCapabilitySet(), lastSyncedAt);
    PanelPresentation.SyncDot dot =
        PanelPresentation.resolveDot(isPaired(), lastSubmitAtMs > 0L, failures.any());
    return new PanelModel(
        isPaired(),
        name == null || name.isEmpty() ? null : name,
        dot,
        rows,
        recentActivityLog.recent(),
        failures.shown(),
        savedServerUrl(),
        PanelModel.shouldOfferSessions(config.collectLoot(), config.collectSessions()));
  }

  /** The panel prompt's button: the same as ticking the setting. */
  private void turnOnSessions() {
    configManager.setConfiguration(
        BankstandKeys.GROUP, BankstandKeys.KEY_COLLECT_SESSIONS, Boolean.TRUE.toString());
  }

  private boolean isCollectionLogCaptureEnabled() {
    return config.collectCollectionLog() && manifest.allows("collectionLog");
  }

  private boolean isCombatAchievementCaptureEnabled() {
    return config.collectCombatAchievements() && manifest.allows("combatAchievements");
  }

  // Chat listeners need their own pairing and session gates (the scheduled read has an outer
  // check): otherwise events pile up unpaired, or carry accountHash -1 and poison the batch.
  private boolean isCombatAchievementCompletionCaptureEnabled() {
    return pairingClient != null
        && isPaired()
        && session.isActive()
        && isCombatAchievementCaptureEnabled();
  }

  // Same gates as above. A tier completing is the same disclosure as a task completing.
  private boolean isCombatAchievementTierCompletionCaptureEnabled() {
    return pairingClient != null
        && isPaired()
        && session.isActive()
        && isCombatAchievementCaptureEnabled();
  }

  // Same gates as above. Reuses the diaries capability: it is the same disclosure.
  private boolean isDiaryTaskCompletionCaptureEnabled() {
    return pairingClient != null && isPaired() && session.isActive() && isDiaryCaptureEnabled();
  }

  // Same gates as above. Reuses the quests capability: it is the same disclosure.
  private boolean isQuestCompletionCaptureEnabled() {
    return pairingClient != null && isPaired() && session.isActive() && isQuestCaptureEnabled();
  }

  static boolean isKnownQuest(String name) {
    for (Quest quest : Quest.values()) {
      // The scroll parse drops a trailing "Quest" from most names (Crab Quest reads as Crab).
      if (quest.getName().equalsIgnoreCase(name)
          || quest.getName().equalsIgnoreCase(name + " Quest")) {
        return true;
      }
    }
    return false;
  }

  private boolean isAccountTypeCaptureEnabled() {
    return config.collectAccountType() && manifest.allows("accountType");
  }

  // Captures on the game's completion broadcast instead of waiting for the next scheduled run.
  @Subscribe
  public void onChatMessage(ChatMessage event) {
    if (event.getType() != ChatMessageType.GAMEMESSAGE) {
      return;
    }
    if (!isQuestCaptureEnabled() && !isDiaryCaptureEnabled()) {
      return;
    }
    String message = Text.removeTags(event.getMessage());
    if (isQuestOrDiaryCompletionMessage(message)) {
      // Log only the match, never the message text.
      log.debug("early capture: completion broadcast matched");
      captureSkills();
    }
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged event) {
    GameState state = event.getGameState();
    if (state == GameState.HOPPING) {
      presenceTracker.onHopping();
    } else if (state == GameState.LOGGED_IN) {
      // Adopts the account only if it changed; the -1 logged-out sentinel is ignored.
      if (session.onLogin(client.getAccountHash())) {
        lastSkillRead = null;
        adoptCharacter(session.getAccountHash(), session.getGeneration());
      }
      ticksSinceLoggedIn = 0;
      cancelHopTimer();
      presenceLoginPending = true;
    } else if (state == GameState.LOGIN_SCREEN) {
      // Before onLogout, which clears the session this needs to attribute the read to.
      captureFinalSnapshot();
      captureGate.reset();
      presenceLoginPending = false;
      lastSkillRead = null;
      // Loot first, so the last kills go before the logout.
      if (lootOutbox != null) {
        lootOutbox.addAll(lootLedger.closeAll());
        drainLoot();
      }
      enqueuePresence(presenceTracker.onLoginScreen(session.getAccountHash(), System.nanoTime()));
      if (presenceTracker.isHopTimerArmed()) {
        armHopTimer();
      }
      if (slayerTaskCompletionCapture != null) {
        slayerTaskCompletionCapture.reset();
      }
      session.onLogout();
      // A read belongs to the character that started it; abandon it silently.
      collectionLogSync.reset();
      hideSyncInfoBox();
    } else if (state == GameState.HOPPING || state == GameState.CONNECTION_LOST) {
      captureGate.reset();
    }
  }

  /** Sends a failed hop's logout once the timeout passes. Runs on the executor. */
  private void armHopTimer() {
    cancelHopTimer();
    hopTimer =
        executor.schedule(
            () -> enqueuePresence(presenceTracker.onHopTimer(System.nanoTime())),
            PresenceTracker.FAILED_HOP_TIMEOUT_NANOS,
            TimeUnit.NANOSECONDS);
  }

  private void cancelHopTimer() {
    ScheduledFuture<?> timer = hopTimer;
    if (timer != null) {
      timer.cancel(false);
      hopTimer = null;
    }
  }

  /** Decides a LOGGED_IN's presence once settled, by the gate every other capture uses. */
  private void flushDuePresence() {
    if (!presenceLoginPending || !captureGate.isSettled()) {
      return;
    }
    presenceLoginPending = false;
    enqueuePresence(
        presenceTracker.onLoggedIn(isCaptureReady(), client.getAccountHash(), System.nanoTime()));
  }

  @Subscribe
  public void onGameTick(GameTick event) {
    Widget welcome = client.getWidget(InterfaceID.WelcomeScreen.MOTW);
    captureGate.onTick(welcome != null && !welcome.isHidden());
    readyLastTick = isCaptureReady();
    if (ticksSinceLoggedIn < Integer.MAX_VALUE) {
      ticksSinceLoggedIn++;
    }
    flushDuePresence();
    // Drive the guided read first, unconditionally, so an armed sync can always finish.
    if (collectionLogSync.isActive()) {
      tickCollectionLogSync();
    }
    // Identity once per session. The local player's name is only reliable a tick after LOGGED_IN.
    if (pairingClient == null
        || !isPaired()
        || !session.isActive()
        || session.isSubmitted()
        || !isCaptureReady()
        || submitGate.isHalted()
        || submitGate.isPaused(System.currentTimeMillis())) {
      return;
    }
    // Identity sends the account hash and name, so it shares the skill capture opt-in.
    if (!isSkillCaptureEnabled()) {
      return;
    }
    Player local = client.getLocalPlayer();
    if (local == null) {
      return;
    }
    String name = local.getName();
    if (name == null || name.isEmpty()) {
      return;
    }
    // Marked in flight before dispatch and released on failure. The generation pins the result
    // to this login, so a stale result or late failure cannot affect a later one.
    session.markSubmitInFlight();
    submitIdentity(session.getAccountHash(), session.getGeneration(), name);
  }

  /**
   * Captures the last minute of a session, which the 60s schedule would drop. Uses the normal
   * capture path and does not block logout. A lost submit is harmless: the next login re-reads.
   */
  private void captureFinalSnapshot() {
    if (pairingClient == null || !isPaired() || !session.isActive() || !isSkillCaptureEnabled()) {
      return;
    }
    if (!isFinalReadReady()) {
      return;
    }
    if (!submitGate.allow()) {
      return;
    }
    Map<String, Integer> skills = readSkillXp();
    if (!isPlausibleFinalRead(lastSkillRead, skills)) {
      return;
    }
    onSkillsCaptured(
        session.getAccountHash(),
        session.getGeneration(),
        // The local player is gone; the server keeps the name it has.
        null,
        skills,
        // Skills alone: the varps may already be cleared, and only skills have a plausibility check.
        null,
        null,
        null,
        null,
        false);
  }

  @Schedule(period = CAPTURE_INTERVAL_SECONDS, unit = ChronoUnit.SECONDS)
  public void captureSkills() {
    if (pairingClient == null || !isPaired() || !session.isActive()) {
      return;
    }
    // Before reading anything: consuming a skip here is what advances the backoff.
    if (!submitGate.allow()) {
      return;
    }
    // Skills gate the whole capture: the envelope requires `skills`, the rest ride on it.
    if (!isSkillCaptureEnabled()) {
      return;
    }
    if (client.getGameState() != GameState.LOGGED_IN) {
      return;
    }
    if (!isCaptureReady()) {
      return;
    }
    // Read on the client thread into one snapshot, then submit off-thread.
    // invokeLater, never invoke: the :: command handler is already inside a running script, and
    // Quest.getState runs a script, so an inline call hits "scripts are not reentrant".
    clientThread.invokeLater(
        () -> {
          // A hop or disconnect can land between the check above and this read.
          if (!isCaptureReady()) {
            return;
          }
          Player local = client.getLocalPlayer();
          if (local == null) {
            return;
          }
          String name = local.getName();
          if (name == null || name.isEmpty()) {
            return;
          }
          long accountHash = session.getAccountHash();
          int generation = session.getGeneration();
          Map<String, Integer> skills = readSkillXp();
          lastSkillRead = skills;
          // Opt-in blocks are read in the same snapshot and left null (not sent) when off.
          Map<String, String> quests = isQuestCaptureEnabled() ? readQuestStates() : null;
          Map<String, String> diaries = isDiaryCaptureEnabled() ? readDiaryStates() : null;
          Map<String, Integer> diaryTasks =
              isDiaryCaptureEnabled() ? readDiaryTaskCounts() : null;
          // Read too early, a real task looks like none.
          SlayerTask slayerTask =
              isSlayerCaptureEnabled() && SlayerTaskReader.isReadable(ticksSinceLoggedIn)
                  ? slayerTaskStart.read(true)
                  : null;
          onSkillsCaptured(
              accountHash, generation, name, skills, quests, diaries, diaryTasks, slayerTask, true);
        });
  }

  // Runs on the client thread. Iterates the allowlist, never Skill.values().
  private Map<String, Integer> readSkillXp() {
    Map<String, Integer> skills = new LinkedHashMap<>();
    for (Skill skill : CAPTURED_SKILLS) {
      String key = skill.getName().toLowerCase();
      skills.put(key, client.getSkillExperience(skill));
    }
    return skills;
  }

  // Keyed by Quest enum name. Client thread. No allowlist: the server accepts any key up to its
  // cap.
  private Map<String, String> readQuestStates() {
    Map<String, String> quests = new LinkedHashMap<>();
    for (Quest quest : Quest.values()) {
      quests.put(quest.name(), quest.getState(client).name());
    }
    return quests;
  }

  /** Per-tier task counts. Same consent gate as {@link #readDiaryStates()}: one disclosure. */
  private Map<String, Integer> readDiaryTaskCounts() {
    return readVarbits(DiaryTaskVarbits.ALL);
  }

  // Client thread. The exact value of a completed tier's varbit is unverified, so read "not zero".
  private Map<String, String> readDiaryStates() {
    Map<String, String> diaries = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : DiaryVarbits.ALL.entrySet()) {
      boolean complete = client.getVarbitValue(e.getValue()) != 0;
      diaries.put(e.getKey(), complete ? "COMPLETE" : "INCOMPLETE");
    }
    return diaries;
  }

  // Counts per tier only: the game exposes no per-task state. A zero is a fact (0/41), kept.
  private Map<String, Integer> readCombatAchievementCounts() {
    return readVarbits(CombatAchievementVarbits.ALL);
  }

  // Per boss/activity. Rides the combat achievement gate and baseline.
  private Map<String, Integer> readCombatAchievementBossCounts() {
    return readVarbits(CombatAchievementBossVarbits.ALL);
  }

  // Client thread. One value per varbit, in the table's order.
  private Map<String, Integer> readVarbits(Map<String, Integer> varbits) {
    Map<String, Integer> values = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : varbits.entrySet()) {
      values.put(e.getKey(), client.getVarbitValue(e.getValue()));
    }
    return values;
  }

  private void onSkillsCaptured(
      long accountHash,
      int generation,
      String name,
      Map<String, Integer> skills,
      Map<String, String> quests,
      Map<String, String> diaries,
      Map<String, Integer> diaryTaskCounts,
      SlayerTask slayerTask,
      boolean varsReadable) {
    adoptCharacter(accountHash, generation);
    // Diary values read all zero before the game sends them, and progress never goes back. So
    // nothing is sent before this login's acked state has loaded, and an all-zero read never
    // replaces known state. Until HOLD_TICKS after login, a read below the acked values (a data
    // check) or with nothing acked (a time bound only) is held whole; after that it is sent.
    boolean ackedKnown = ackedLoadedGeneration == generation;
    // Read again each capture until it succeeds; until then the baseline stays unknown.
    if (ackedKnown && ackedLoadFailed) {
      loadAckedState(accountHash, generation);
    }
    // A rider: never a reason to submit, so a server that strips it cannot cause a resend loop.
    Map<String, Object> diaryTaskVars =
        varsReadable && isDiaryCaptureEnabled() && manifest.allows("diaryTaskVars")
            ? DiaryTaskVars.read(client::getVarpValue, client::getVarbitValue)
            : null;
    boolean held =
        !ackedKnown || diaryBaseline.holds(ticksSinceLoggedIn, diaries, diaryTaskCounts, diaryTaskVars);
    if (held
        || (DiaryBaseline.isAllZero(diaries, diaryTaskCounts)
            && (ackedLoadFailed || diaryBaseline.ackedDigest() != null))) {
      diaries = null;
      diaryTaskCounts = null;
    }
    // An opt-in is not retroactive to items observed before it.
    Set<Integer> clog =
        isCollectionLogCaptureEnabled() ? collectionLog.observed() : Collections.emptySet();
    // Not read at all when off: an opt-in is about what leaves the client.
    Map<String, Integer> combatAchievements =
        varsReadable && isCombatAchievementCaptureEnabled()
            ? readCombatAchievementCounts()
            : Collections.emptyMap();
    Map<String, Integer> combatAchievementBossCounts =
        varsReadable && isCombatAchievementCaptureEnabled()
            ? readCombatAchievementBossCounts()
            : Collections.emptyMap();
    // Null when off, or when the varbit holds an unknown value: a wrong badge is worse than none.
    String accountType =
        varsReadable && isAccountTypeCaptureEnabled()
            ? AccountTypes.keyFor(client.getVarbitValue(AccountTypes.ACCOUNT_TYPE_VARBIT))
            : null;
    SubmitPlan plan =
        plan(
            skillBaseline,
            skills,
            questBaseline,
            quests,
            diaryBaseline,
            diaries, diaryTaskCounts,
            collectionLogBaseline,
            clog,
            combatAchievementBaseline,
            combatAchievements,
            accountTypeBaseline,
            accountType,
            pendingFullEnumeration,
            slayerTaskBaseline,
            slayerTask);
    if (!plan.shouldSubmit() && forcedSubmitGeneration != generation) {
      return;
    }
    forcedSubmitGeneration = -1;
    // Never all zero: a too-early read and a new character look the same, and unknown is safe.
    if (diaryTaskVars != null
        && (held
            || DiaryTaskVars.isAllZero(diaryTaskVars)
            || CapabilityDigest.of(diaryTaskVars).equals(diaryTaskVarsAcked))) {
      diaryTaskVars = null;
    }
    // An omitted block is null or empty, so the per-block ack keys off what actually went out.
    submitSnapshot(
        plan.includesCombatAchievements() ? combatAchievements : null,
        // No baseline of its own: rides with the tier counts, like diary task counts.
        plan.includesCombatAchievements() ? combatAchievementBossCounts : null,
        accountHash,
        generation,
        name,
        skills,
        plan.includesQuests() ? quests : null,
        plan.includesDiaries() ? diaries : null,
        // No baseline of its own: the counts ride with the diaries block, so the two never drift.
        plan.includesDiaries() ? diaryTaskCounts : null,
        plan.includesCollectionLog() ? clog : Collections.emptySet(),
        plan.includesAccountType() ? accountType : null,
        plan.includesFullEnumeration(),
        plan.includesSlayerTask() ? slayerTask : null,
        diaryTaskVars,
        null);
  }

  /**
   * Forgets every baseline, then loads what disk says this character had acked. Runs at login, so
   * nothing captured for the new character meets the previous one's state. A capture arriving
   * before the load just re-sends.
   */
  private void adoptCharacter(long accountHash, int generation) {
    if (generation != baselineGeneration) {
      skillBaseline.reset();
      questBaseline.reset();
      diaryBaseline.reset();
      combatAchievementBaseline.reset();
      accountTypeBaseline.reset();
      slayerTaskBaseline.reset();
      diaryTaskVarsAcked = null;
      // Per-character state; carrying it across a switch would mix accounts.
      collectionLog.reset();
      collectionLogBaseline.reset();
      // A pending COMPLETE claim is about the log just reset, so it must not survive a relog.
      pendingFullEnumeration = false;
      // loadAckedState repopulates lastSyncedAt; the activity log is session-only.
      lastSyncedAt.clear();
      recentActivityLog.clear();
      eventPacer.forgetUnrouted();
      baselineGeneration = generation;
      loadAckedState(accountHash, generation);
    }
  }

  /**
   * Reads on the executor, applies on the client thread where the baselines live. The
   * {@code isCurrent} guard drops a slow read for an account the player has since left.
   */
  private void loadAckedState(long accountHash, int generation) {
    if (ackedStore == null) {
      ackedLoadedGeneration = generation;
      return;
    }
    // A re-pair during the read forgets acks; applying this read after it would undo that.
    long pairing = pairingQueues.epoch();
    executor.submit(
        () -> {
          AckedStateStore.Loaded loaded = ackedStore.loadChecked(accountHash);
          AckedState state = loaded.state;
          boolean failed = loaded.failed;
          clientThread.invoke(
              () -> {
                if (!session.isCurrent(accountHash, generation)) {
                  return;
                }
                boolean retry = ackedLoadedGeneration == generation;
                ackedLoadedGeneration = generation;
                // A failed read applies nothing, and is retried until one succeeds.
                ackedLoadFailed = failed;
                if (failed) {
                  return;
                }
                if (pairingQueues.epoch() != pairing) {
                  // A re-pair forgot the acks, but the observed log is kept: merge only that.
                  mergeCollectionLog(state);
                } else {
                  // A retry only fills in what is still unknown.
                  applyAckedState(state, retry);
                }
                refreshPanel();
              });
        });
  }

  /** Adds the stored log items to the ones seen this session; never drops one. */
  private void mergeCollectionLog(AckedState state) {
    Set<Integer> items = new java.util.HashSet<>(state.getCollectionLogItems());
    items.addAll(collectionLog.observed());
    collectionLog.restore(items);
  }

  /** Restores a loaded state; merged, only filling what is unknown, when live state exists. */
  private void applyAckedState(AckedState state, boolean merge) {
    if (!merge || skillBaseline.ackedDigest() == null) {
      skillBaseline.restore(state.getSkills());
    }
    if (!merge || questBaseline.ackedDigest() == null) {
      questBaseline.restore(state.getQuests());
    }
    if (!merge || diaryBaseline.ackedDigest() == null) {
      diaryBaseline.restore(state);
    }
    if (!merge || combatAchievementBaseline.ackedDigest() == null) {
      combatAchievementBaseline.restore(state.getCombatAchievements());
    }
    if (!merge || accountTypeBaseline.ackedValue() == null) {
      accountTypeBaseline.restore(state.getAccountType());
    }
    if (!merge || diaryTaskVarsAcked == null) {
      diaryTaskVarsAcked = state.getDiaryTaskVars();
    }
    // Stored items are always merged in, never dropped. The acked count only fills a gap.
    mergeCollectionLog(state);
    if (!merge || collectionLogBaseline.ackedCount() < 0) {
      collectionLogBaseline.restore(state.getCollectionLogAcked());
    }
    if (!merge) {
      lastSyncedAt.clear();
    }
    state.getLastSyncedAt().forEach(lastSyncedAt::putIfAbsent);
  }

  /**
   * Reads the baselines on the client thread and hands a finished document to the
   * executor to write. Reading them on the executor would race the next capture.
   */
  private void saveAckedState(long accountHash) {
    // Until this login's load has succeeded, memory may lack what disk holds: never write over it.
    if (ackedStore == null
        || ackedLoadedGeneration != session.getGeneration()
        || ackedLoadFailed) {
      return;
    }
    AckedState state = AckedState.empty();
    state.setSkills(skillBaseline.ackedDigest());
    state.setQuests(questBaseline.ackedDigest());
    diaryBaseline.saveTo(state);
    state.setCombatAchievements(combatAchievementBaseline.ackedDigest());
    state.setAccountType(accountTypeBaseline.ackedValue());
    state.setDiaryTaskVars(diaryTaskVarsAcked);
    state.setCollectionLogItems(collectionLog.observed());
    state.setCollectionLogAcked(collectionLogBaseline.ackedCount());
    state.setLastSyncedAt(lastSyncedAt);
    executor.submit(() -> ackedStore.save(accountHash, state));
  }

  /** Which blocks a capture puts on the wire. Skills is required, so it is not listed. */
  static final class SubmitPlan {
    private final boolean submit;
    private final boolean quests;
    private final boolean diaries;
    private final boolean collectionLog;
    private final boolean fullEnumeration;
    private final boolean combatAchievements;
    private final boolean accountType;
    private final boolean slayerTask;

    private SubmitPlan(
        boolean submit,
        boolean quests,
        boolean diaries,
        boolean collectionLog,
        boolean fullEnumeration,
        boolean combatAchievements,
        boolean accountType,
        boolean slayerTask) {
      this.submit = submit;
      this.quests = quests;
      this.diaries = diaries;
      this.collectionLog = collectionLog;
      this.fullEnumeration = fullEnumeration;
      this.combatAchievements = combatAchievements;
      this.accountType = accountType;
      this.slayerTask = slayerTask;
    }

    boolean shouldSubmit() {
      return submit;
    }

    boolean includesQuests() {
      return quests;
    }

    boolean includesDiaries() {
      return diaries;
    }

    boolean includesCollectionLog() {
      return collectionLog;
    }

    // True only when the flag rides on this submission's collection log block.
    boolean includesFullEnumeration() {
      return fullEnumeration;
    }

    boolean includesCombatAchievements() {
      return combatAchievements;
    }

    boolean includesAccountType() {
      return accountType;
    }

    boolean includesSlayerTask() {
      return slayerTask;
    }
  }

  /**
   * Decides what this capture sends, per capability: a block rides along only when it changed
   * since the server last acknowledged it.
   *
   * <p>Whole blocks only, never a delta: the server merges blocks with jsonb {@code ||}, a
   * top-level replace, so a partial block would erase the fields it left out. An unacked block
   * has no baseline, so it keeps re-sending until stored. An empty block is never sent: absent
   * means "not observed", empty would assert the player has none. Null means the opt-in is off.
   */
  static SubmitPlan plan(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries, Map<String, Integer> diaryTaskCounts,
      CollectionLogBaseline collectionLogBaseline,
      Set<Integer> collectionLogItems,
      CombatAchievementBaseline combatAchievementBaseline,
      Map<String, Integer> combatAchievements,
      AccountTypeBaseline accountTypeBaseline,
      String accountType,
      boolean fullEnumerationPending,
      SlayerTaskBaseline slayerTaskBaseline,
      SlayerTask slayerTask) {
    boolean sendQuests =
        quests != null && !quests.isEmpty() && questBaseline.changedSince(quests);
    boolean sendDiaries =
        diaries != null && !diaries.isEmpty() && diaryBaseline.changedSince(diaries, diaryTaskCounts);
    // The collection log counts toward the decision on its own. A pending full enumeration also
    // forces it, since a COMPLETE read can reveal no new ids. An empty log has no block to ride on.
    boolean sendCollectionLog =
        !collectionLogItems.isEmpty()
            && (collectionLogBaseline.changedSince(collectionLogItems.size())
                || fullEnumerationPending);
    boolean sendCombatAchievements =
        combatAchievements != null
            && !combatAchievements.isEmpty()
            && combatAchievementBaseline.changedSince(combatAchievements);
    // Counts on its own: the type rarely changes, so it must not wait on xp moving.
    boolean sendAccountType =
        accountType != null
            && !accountType.isEmpty()
            && accountTypeBaseline.changedSince(accountType);
    // On its own: a finished task must replace the stored one even with no xp change.
    boolean sendSlayerTask = slayerTaskBaseline.changedSince(slayerTask);
    boolean submit =
        skillBaseline.changedSince(skills)
            || sendQuests
            || sendDiaries
            || sendCollectionLog
            || sendCombatAchievements
            || sendAccountType
            || sendSlayerTask;
    return new SubmitPlan(
        submit,
        sendQuests,
        sendDiaries,
        sendCollectionLog,
        fullEnumerationPending && sendCollectionLog,
        sendCombatAchievements,
        sendAccountType,
        sendSlayerTask);
  }

  /**
   * Whether a read taken during logout is worth submitting. A cleared client reads as zeroes or
   * nothing, which would be an XP regression, so the read is checked against the last trusted
   * one. Discarding is free: the next login re-reads.
   */
  static boolean isPlausibleFinalRead(
      Map<String, Integer> previous, Map<String, Integer> fresh) {
    if (fresh == null || fresh.isEmpty()) {
      return false;
    }
    // No trusted read this session: a cleared client cannot be told apart, so skip it.
    if (previous == null) {
      return false;
    }
    for (Map.Entry<String, Integer> before : previous.entrySet()) {
      Integer now = fresh.get(before.getKey());
      if (now == null || now < before.getValue()) {
        return false;
      }
    }
    return true;
  }

  // Baselines advance only on the per-block ack. The server answers 200 accepted=true even for
  // outcomes that store nothing (stale, regression, unclaimed, not_applied).
  static boolean shouldAdvanceSkills(SubmitSnapshotResponse res) {
    return res.isBlockStored("skills");
  }

  // Optional blocks must also have been sent. The whole-submission verdict is not enough: a
  // one-shot fact like a diary tier, falsely acked, would never be resent.
  static boolean shouldAdvanceQuests(SubmitSnapshotResponse res, boolean questsIncluded) {
    return questsIncluded && res.isBlockStored("quests");
  }

  static boolean shouldAdvanceDiaries(SubmitSnapshotResponse res, boolean diariesIncluded) {
    return diariesIncluded && res.isBlockStored("diaries");
  }

  static boolean shouldAdvanceCollectionLog(SubmitSnapshotResponse res, boolean included) {
    return included && res.isBlockStored("collectionLog");
  }

  static boolean shouldAdvanceCombatAchievements(
      SubmitSnapshotResponse res, boolean included) {
    return included && res.isBlockStored("combatAchievements");
  }

  // A false ack here never self-heals: an account type changes once if ever.
  static boolean shouldAdvanceAccountType(SubmitSnapshotResponse res, boolean included) {
    return included && res.isBlockStored("accountType");
  }

  static boolean shouldAdvanceSlayerTask(SubmitSnapshotResponse res, boolean included) {
    return included && res.isBlockStored("slayerTask");
  }

  /** Milliseconds until a cooldown-refused submit may retry, or -1 when it was not refused. */
  static long cooldownRetryDelayMs(String reason, String nextSubmitAfter, long nowMs) {
    if (!"cooldown".equals(reason)) {
      return -1;
    }
    long delay;
    try {
      // A second of margin, so the retry does not land on the window's edge.
      delay = Instant.parse(nextSubmitAfter).toEpochMilli() - nowMs + 1000L;
    } catch (RuntimeException e) {
      delay = COOLDOWN_RETRY_FALLBACK_MS;
    }
    return Math.max(1000L, Math.min(COOLDOWN_RETRY_MAX_MS, delay));
  }

  static boolean isSuperseded(long seq, long latestSeq) {
    return seq != latestSeq;
  }

  /** Null when already announced: a rejected token halts and is told once until re-pairing. */
  static String identityFailureNotice(
      Throwable failure, SubmitGate submitGate, NoticeGate noticeGate) {
    boolean auth = failure instanceof SubmitException && ((SubmitException) failure).isAuthFailure();
    boolean announce =
        auth ? submitGate.onAuthFailure() : noticeGate.onFailure(failure.getMessage());
    return announce ? "Could not verify this character. " + failure.getMessage() : null;
  }

  private void submitSnapshot(
      Map<String, Integer> combatAchievementCounts,
      Map<String, Integer> combatAchievementBossCounts,
      long accountHash,
      int generation,
      String name,
      Map<String, Integer> skills,
      Map<String, String> quests,
      Map<String, String> diaries,
      Map<String, Integer> diaryTaskCounts,
      Set<Integer> collectionLogItems,
      String accountType,
      boolean fullEnumeration,
      SlayerTask slayerTask,
      Map<String, Object> diaryTaskVars,
      Map<String, Object> retryBody) {
    String url = savedServerUrl();
    String token = deviceStore.load().tokenFor(url);
    // A retry re-sends the original envelope: a cooldown is not recorded, so its id is unused.
    long seq = retryBody == null ? submitSeq.incrementAndGet() : submitSeq.get();
    Map<String, Object> body =
        retryBody != null
            ? retryBody
            : SubmitEnvelope.body(
                UuidV7.generate(),
                SubmitEnvelope.SCHEMA_VERSION,
                PluginVersion.VERSION,
                Instant.now().toString(),
                accountHash,
                name,
                skills,
                quests,
                diaries,
                collectionLogItems,
                combatAchievementCounts,
                diaryTaskCounts,
                accountType,
                fullEnumeration,
                combatAchievementBossCounts,
                slayerTask == null ? null : slayerTask.toWire());
    if (retryBody == null && diaryTaskVars != null) {
      body.put("diaryTaskVars", diaryTaskVars);
    }
    // Taken now, so an ack that lands after a re-pair reset is dropped, not applied over it.
    long pairing = pairingQueues.epoch();
    BankstandClient api = pairingClient;
    executor.submit(
        () -> {
          api
              .submitSnapshotWithRetry(url, token, body, MAX_SUBMIT_ATTEMPTS, SUBMIT_RETRY_BASE_DELAY_MS)
              .whenComplete(
                  (res, failure) -> {
                    if (failure != null) {
                      // Baseline not advanced, so the next cycle retries. Never log the token
                      // or account hash.
                      SubmitException e = (SubmitException) failure;
                      // A replaced pairing's failure says nothing about the current one.
                      if (isReplaced(url, token)) {
                        return;
                      }
                      // Shown on the panel's header dot and failure footer.
                      failures.snapshotFailed(e);
                      if (e.isAuthFailure()) {
                        // Retrying cannot fix a revoked token. This is the one failure the
                        // player must act on, so the gate announces it.
                        log.debug("submit refused: token rejected, halting until re-paired");
                        if (haltIfCurrent(url, token)) {
                          notice(NO_LONGER_PAIRED);
                        }
                        return;
                      }
                      // The message, never the body: it carries the account hash and name.
                      log.debug("submit failed: {}", e.getMessage());
                      submitGate.onFailure();
                      submitGate.pauseFor(System.currentTimeMillis(), e.getRetryAfterMillis());
                      if (session.isCurrent(accountHash, generation)
                          && noticeGate.onFailure(e.getMessage())) {
                        notice("Could not sync your progress. " + e.getMessage());
                      }
                      return;
                    }
                    if (isReplaced(url, token)) {
                      return;
                    }
                    // Advance each baseline only for a block the server wrote, on the client
                    // thread, and only if this login is still current.
                    clientThread.invoke(
                        () -> {
                          if (session.isCurrent(accountHash, generation)
                              && pairingQueues.epoch() == pairing) {
                            // Skills ride on every submission, so check for a real change
                            // before advance() overwrites the baseline.
                            boolean skillsChanged = skillBaseline.changedSince(skills);
                            boolean skillsAdvanced = shouldAdvanceSkills(res);
                            if (skillsAdvanced) {
                              skillBaseline.advance(skills);
                            }
                            boolean questsAdvanced = shouldAdvanceQuests(res, quests != null);
                            if (questsAdvanced) {
                              questBaseline.advance(quests);
                            }
                            boolean diariesAdvanced = shouldAdvanceDiaries(res, diaries != null);
                            if (diariesAdvanced) {
                              diaryBaseline.advance(diaries, diaryTaskCounts);
                            }
                            boolean collectionLogAdvanced =
                                shouldAdvanceCollectionLog(res, !collectionLogItems.isEmpty());
                            if (collectionLogAdvanced) {
                              collectionLogBaseline.advance(collectionLogItems.size());
                            }
                            // Cleared only once the block this flag rode on was stored.
                            if (fullEnumeration && collectionLogAdvanced) {
                              pendingFullEnumeration = false;
                            }
                            boolean combatAchievementsAdvanced =
                                shouldAdvanceCombatAchievements(
                                    res, combatAchievementCounts != null);
                            if (combatAchievementsAdvanced) {
                              combatAchievementBaseline.advance(combatAchievementCounts);
                            }
                            boolean accountTypeAdvanced =
                                shouldAdvanceAccountType(res, accountType != null);
                            if (accountTypeAdvanced) {
                              accountTypeBaseline.advance(accountType);
                            }
                            boolean slayerTaskAdvanced =
                                shouldAdvanceSlayerTask(res, slayerTask != null);
                            if (diaryTaskVars != null && res.isBlockStored("diaryTaskVars")) {
                              diaryTaskVarsAcked = CapabilityDigest.of(diaryTaskVars);
                              diaryBaseline.advanceVars(diaryTaskVars);
                            }
                            if (slayerTaskAdvanced) {
                              slayerTaskBaseline.advance(slayerTask);
                              lastSyncedAt.put(Capability.SLAYER.key, System.currentTimeMillis());
                            }
                            EnumSet<Capability> advanced = EnumSet.noneOf(Capability.class);
                            addIf(advanced, Capability.SKILLS, skillsAdvanced);
                            addIf(advanced, Capability.QUESTS, questsAdvanced);
                            addIf(advanced, Capability.DIARIES, diariesAdvanced);
                            addIf(advanced, Capability.COLLECTION_LOG, collectionLogAdvanced);
                            addIf(
                                advanced, Capability.COMBAT_ACHIEVEMENTS, combatAchievementsAdvanced);
                            addIf(advanced, Capability.ACCOUNT_TYPE, accountTypeAdvanced);
                            // A null activity line stamps the time but records nothing.
                            long now = System.currentTimeMillis();
                            for (Map.Entry<String, String> synced :
                                syncedCapabilitiesThisCycle(advanced, skillsChanged).entrySet()) {
                              lastSyncedAt.put(synced.getKey(), now);
                              if (synced.getValue() != null) {
                                recentActivityLog.record(synced.getValue());
                              }
                            }
                            // Unconditional: passive browsing can grow the accumulator even
                            // when nothing was stored.
                            saveAckedState(accountHash);
                            refreshPanel();
                          }
                        });
                    // A reached server counts as success for notices, even if it stored
                    // nothing. Only the recovery is announced, so a healthy client is quiet.
                    submitGate.onSuccess();
                    lastSubmitAtMs = System.currentTimeMillis();
                    failures.snapshotSucceeded();
                    // Stored blocks only, never the body.
                    log.debug(
                        "submit {}: reason={} storedBlocks=[{}]",
                        res.isStored() ? "stored" : "accepted",
                        res.getReason(),
                        String.join(",", res.getStoredBlocks()));
                    if (session.isCurrent(accountHash, generation) && noticeGate.onSuccess()) {
                      notice("Reconnected. Your progress is syncing again.");
                    }
                    long retryDelay =
                        cooldownRetryDelayMs(
                            res.getReason(), res.getNextSubmitAfter(), System.currentTimeMillis());
                    // Once only. Runs even after logout: the ack above still checks the login.
                    if (retryBody == null && retryDelay >= 0) {
                      executor.schedule(
                          () -> {
                            if (isSuperseded(seq, submitSeq.get())
                                || submitGate.isHalted()
                                || pairingClient == null
                                || !isPaired()
                                // A new pairing may be another account: never resend under it.
                                || isReplaced(url, token)) {
                              return;
                            }
                            submitSnapshot(
                                combatAchievementCounts,
                                combatAchievementBossCounts,
                                accountHash,
                                generation,
                                name,
                                skills,
                                quests,
                                diaries,
                                diaryTaskCounts,
                                collectionLogItems,
                                accountType,
                                fullEnumeration,
                                slayerTask,
                                diaryTaskVars,
                                body);
                          },
                          retryDelay,
                          TimeUnit.MILLISECONDS);
                    }
                  });
        });
  }

  private BankstandClient newClient(HttpTransport transport) {
    BankstandClient api = new BankstandClient(transport, gson, executor);
    api.setCurrentPairing((url, token) -> !isReplaced(url, token));
    return api;
  }

  /** Halts on a rejected token unless it was replaced; atomic with a pairing swap. */
  private boolean haltIfCurrent(String url, String token) {
    synchronized (pairingQueues) {
      return !isReplaced(url, token) && submitGate.onAuthFailure();
    }
  }

  // A token a new pairing or a disconnect replaced: its failures say nothing about the new one.
  private boolean isReplaced(String url, String token) {
    return !java.util.Objects.equals(token, deviceStore.load().tokenFor(url));
  }

  // Paired for the server now configured; a pairing for another origin counts as unpaired here.
  private boolean isPaired() {
    return deviceStore.load().tokenFor(savedServerUrl()) != null;
  }

  // Each gate is consent AND manifest. Config is the player's consent and the only thing that
  // can say yes; the manifest can only narrow it, so the server can stop ingesting a capability
  // without a plugin release. Quest and diary opt-ins default to off.
  private boolean isQuestCaptureEnabled() {
    return config.collectQuests() && manifest.allows("quests");
  }

  private boolean isDiaryCaptureEnabled() {
    return config.collectDiaries() && manifest.allows("diaries");
  }

  // Defaults to on: skill XP is already public, and it carries the account hash and name that
  // bind a paired client. Kept as an option so a player can go quiet on identity without
  // unpairing.
  private boolean isSkillCaptureEnabled() {
    return config.collectSkills() && manifest.allows("skills");
  }

  /**
   * Pulls the manifest in the background and caches it if it validates. Failures are silent and
   * keep the current manifest; the player cannot act on them.
   */
  private void refreshManifest() {
    if (pairingClient == null || manifestStore == null) {
      return;
    }
    String url = savedServerUrl();
    BankstandClient api = pairingClient;
    executor.submit(
        () -> {
          CapabilityManifest.RawManifest raw = api.fetchManifest(url);
          CapabilityManifest validated = CapabilityManifest.validate(raw);
          if (validated != null) {
            manifestStore.save(raw);
          }
          manifest = manifestStore.current(validated);
          log.debug("manifest in use: {}", manifest.describe());
        });
  }

  private void submitIdentity(long accountHash, int generation, String displayName) {
    BankstandClient api = pairingClient;
    if (api == null) {
      return;
    }
    String url = savedServerUrl();
    String token = deviceStore.load().tokenFor(url);
    executor.submit(
        () -> {
          api
              .submitIdentityWithRetry(
                  url, token, accountHash, displayName, MAX_SUBMIT_ATTEMPTS, SUBMIT_RETRY_BASE_DELAY_MS)
              .whenComplete(
                  (res, failure) -> {
                    if (failure != null) {
                      // Already retried to the cap. Release the in-flight mark so a later
                      // tick retries; otherwise the session stays unbound until a relog.
                      // Guarded on the login instance.
                      session.markSubmitFailed(accountHash, generation);
                      String message;
                      // Under the pairing lock, so a swap cannot land between check and halt.
                      synchronized (pairingQueues) {
                        if (isReplaced(url, token)) {
                          return;
                        }
                        // Never log the token or account hash.
                        message = identityFailureNotice(failure, submitGate, noticeGate);
                        if (failure instanceof SubmitException) {
                          submitGate.pauseFor(
                              System.currentTimeMillis(),
                              ((SubmitException) failure).getRetryAfterMillis());
                        }
                      }
                      if (session.isCurrent(accountHash, generation) && message != null) {
                        notice(message);
                      }
                      return;
                    }
                    // Drop the result unless this login instance and pairing are still current:
                    // the retry backoff can span a relog or a re-pair.
                    if (session.isCurrent(accountHash, generation) && !isReplaced(url, token)) {
                      notice(identityNoticeFor(res.isVerified(), res.getLinkedRsn(), res.getOutcome()));
                    }
                  });
        });
  }

  private void pair(String rawCode) {
    BankstandClient api = pairingClient;
    if (api == null) {
      return;
    }
    String url = savedServerUrl();
    long ticket = pairingQueues.beginPairing();
    notice("Pairing with " + url + "...");
    executor.submit(
        () -> {
          try {
            PairResponse res = api.exchangePairingCode(url, rawCode);
            Stored stored = storeToken(res, url, ticket, api);
            if (stored != Stored.OK) {
              // A disconnect or a newer pairing came first, or the save failed: never used.
              api.unpairInBackground(url, res.getDeviceToken(), () -> {});
              if (stored == Stored.UNSAVED) {
                notice("Pairing failed. The pairing could not be saved on this computer.");
              }
              return;
            }
            // A fresh pairing resets failure notices, and is the only thing that lifts an
            // auth halt.
            noticeGate.onSuccess();
            failures.clear();
            // Saved for the server it was made with; a URL changed meanwhile means not paired here.
            notice(
                isPaired()
                    ? "Connected. Your progress will sync from now on."
                    : "Paired with " + url + ", but the Server URL changed since. Pair again with a"
                        + " code from the server it points to now.");
          } catch (PairingException e) {
            // The message is generic; the raw code and token are never logged.
            notice("Pairing failed. " + e.getMessage());
          } finally {
            // Always clear the code: it is single-use either way.
            clearPairingCode();
          }
        });
  }

  private void disconnect() {
    DeviceCredentials paired = deviceStore.load();
    boolean[] removed = {false};
    // Always swapped: clear() unpairs this process even when the disk cannot follow.
    pairingQueues.replace(
        () -> {
          removed[0] = deviceStore.clear();
          return true;
        });
    failures.clear();
    // Unsetting the legacy keys is what removes the token from a synced profile upstream.
    LegacyCredentials.forget(configManager);
    // Untick the toggle so ticking it again fires this afresh.
    configManager.setConfiguration(
        BankstandKeys.GROUP, BankstandKeys.KEY_DISCONNECT, Boolean.FALSE.toString());
    notice(
        removed[0]
            ? "Disconnected. Nothing will be sent until you pair again."
            : "Disconnected for now, but the pairing could not be removed from this computer, so it"
                + " returns after a restart. Remove the device on bankstand.gg/account/connection.");
    // Revoked where it was paired, the one server that knows the token, whatever the URL is now.
    BankstandClient api = pairingClient;
    if (api != null) {
      api.unpairInBackground(
          paired.pairedServerUrl(), paired.getToken(), () -> notice(UNPAIR_UNREACHABLE));
    }
  }

  private void clearPairingCode() {
    configManager.setConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_PAIRING_CODE, "");
  }

  /**
   * Writes a line to the chat box. Logged out, the message is dropped, not queued: an outcome is
   * only worth reporting while the player is there to read it.
   */
  private void notice(String message) {
    String formatted = brandedNotice(message);
    clientThread.invoke(
        () -> {
          if (client.getGameState() == GameState.LOGGED_IN) {
            chatMessageManager.queue(
                QueuedMessage.builder()
                    .type(ChatMessageType.CONSOLE)
                    .runeLiteFormattedMessage(formatted)
                    .build());
          }
        });
  }

  /**
   * One chat line: the brand-coloured prefix, then the message in the chat's own colour. The
   * prefix is part of the text because a CONSOLE message does not render the sender name.
   */
  static String brandedNotice(String message) {
    return new ChatMessageBuilder().append(BRAND, NOTICE_PREFIX).append(message).build();
  }

  private enum Stored {
    OK,
    STALE,
    UNSAVED
  }

  private Stored storeToken(PairResponse res, String url, long ticket, BankstandClient api) {
    DeviceCredentials previous = deviceStore.load();
    PairingQueues.Dropped dropped =
        pairingQueues.replaceIfCurrent(
            ticket,
            () -> {
              // Resumed inside the swap, so a stale rejection cannot halt the new pairing.
              boolean saved = deviceStore.save(DeviceCredentials.of(res, url));
              if (saved) {
                submitGate.resume();
              }
              return saved;
            });
    if (dropped == null) {
      return Stored.STALE;
    }
    // The previous pairing and its queues stay in use.
    if (!dropped.swapped) {
      return Stored.UNSAVED;
    }
    eventPacer.forgetUnrouted();
    clientThread.invoke(this::forgetAcks);
    String discarded = dropped.repairNotice();
    if (discarded != null) {
      notice(discarded);
    }
    // The old token may belong to another account or server: revoke it where it was paired.
    api.unpairInBackground(
        previous.pairedServerUrl(), previous.getToken(), () -> notice(UNPAIR_UNREACHABLE));
    return Stored.OK;
  }

  /** The new pairing may be another account or server, so every block is sent to it afresh. */
  private void forgetAcks() {
    skillBaseline.reset();
    questBaseline.reset();
    diaryBaseline.reset();
    combatAchievementBaseline.reset();
    accountTypeBaseline.reset();
    slayerTaskBaseline.reset();
    collectionLogBaseline.reset();
    diaryTaskVarsAcked = null;
    // With the reset, so a load that read the old acks before it is never applied after it.
    pairingQueues.acksForgotten();
    lastSyncedAt.clear();
    // Queued after any earlier save, so a stale ack cannot be written back over this.
    executor.submit(ackedStore::forgetAcks);
  }

  // Normalised, so a blank or padded value never reaches the socket; a cleared field gets prod.
  private String savedServerUrl() {
    return BankstandKeys.normaliseServerUrl(config.serverBaseUrl());
  }
}
