package com.bankstand;

import com.bankstand.dto.EventAck;
import com.bankstand.dto.PairResponse;
import com.bankstand.dto.SubmitEventsResponse;
import com.bankstand.dto.SubmitResponse;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
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
        "Sync your skills, quests, achievement diaries, combat achievements and collection log to"
            + " your Bankstand account. Sends data to an external server.",
    tags = {"bankstand", "account", "progress", "external"})
public class BankstandPlugin extends Plugin {

  // Bounded retry for a transient submit failure (network, 429, 5xx). Terminal failures fail
  // fast. Runs on the background executor, so the backoff never blocks the game thread.
  private static final int MAX_SUBMIT_ATTEMPTS = 3;
  private static final long SUBMIT_RETRY_BASE_DELAY_MS = 1000L;

  // Matches the server's per-device cooldown, so a change is reported at most once per window.
  private static final int CAPTURE_INTERVAL_SECONDS = 60;

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

  // Untradeable pets have no GE price, so they are matched by name. Each entry must be the
  // exact ItemComposition#getName() string (checked against the cache, not wiki display names),
  // since the check is a plain Set#contains. Package-private so a test pins its content.
  static final Set<String> NOTABLE_UNTRADEABLE_ALLOWLIST = Set.of(
      "Abyssal orphan",
      "Abyssal protector",
      "Aggy",
      "Baby Mole",
      "Baby chinchompa",
      "Baron",
      "Beaver",
      "Beef",
      "Bloodhound",
      "Bran",
      "Butch",
      "Callisto cub",
      "Chompy chick",
      "Dom",
      "Giant Squirrel",
      "Gull",
      "Hellpuppy",
      "Herbi",
      "Heron",
      "Huberte",
      "Ikkle Hydra",
      "Jal-Nib-Rek",
      "Kalphite Princess",
      "Lil' Creator",
      "Lil' Zik",
      "Lil'viathan",
      "Little Nightmare",
      "Maggot marquess",
      "Moxi",
      "Mr McGroot",
      "Muphin",
      "Nexling",
      "Nid",
      "Noon",
      "Olmlet",
      "Pet Chaos Elemental",
      "Pet Dagannoth Prime",
      "Pet Dagannoth Rex",
      "Pet Dagannoth Supreme",
      "Pet General Graardor",
      "Pet K'ril Tsutsaroth",
      "Pet Kraken",
      "Pet Kree'arra",
      "Pet Penance Queen",
      "Pet Smoke Devil",
      "Pet Snakeling",
      "Pet Zilyana",
      "Pet dark core",
      "Phoenix",
      "Prince Black Dragon",
      "Quetzin",
      "Rift guardian",
      "Rock golem",
      "Rocky",
      "Scorpia's offspring",
      "Scurry",
      "Skotos",
      "Smol Heredit",
      "Smolcano",
      "Soup",
      "Sraracha",
      "Tangleroot",
      "Tiny tempor",
      "Tumeken's guardian",
      "TzRek-Jad",
      "Venenatis spiderling",
      "Vet'ion jr.",
      "Vorki",
      "Wisp",
      "Yami",
      "Youngllef");


  // Prefix for every chat line the plugin writes. The darker light-theme gold stays readable
  // both on the parchment chat box and on a transparent one.
  private static final Color BRAND = new Color(0xB3730A);
  private static final String NOTICE_PREFIX = "Bankstand: ";

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
  private final DiaryTaskBits diaryTaskBits = new DiaryTaskBits();
  private final DiaryTaskManifest diaryTaskManifest = DiaryTaskManifest.shipped();
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
  // Null while the plugin is stopped. volatile: shutDown() clears it while refreshPanel()
  // reads it from the executor; refreshPanel snapshots it into a local.
  private volatile BankstandPanel panel;
  private NavigationButton navButton;
  // Session-scoped: a restart or account switch starts empty.
  private final RecentActivityLog recentActivityLog = new RecentActivityLog();
  // Written on the client thread, read on the EDT. Keyed by capability name.
  private final Map<String, Long> lastSyncedAt = new ConcurrentHashMap<>();
  // Set from the HTTP completion thread, read on the client thread.
  private volatile String lastFailureReason;

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
  // The last trusted skill read, so the logout read is checked against it, not trusted blind.
  private Map<String, Integer> lastSkillRead;
  // Zero until something reached the server. Written on the executor, read on the client thread.
  private volatile long lastSubmitAtMs;
  // A change means the account switched and the baseline must be forgotten.
  private int baselineGeneration = -1;
  private BankstandClient pairingClient;
  // Set when a guided read finishes COMPLETE; cleared only once the server acknowledged a
  // submission carrying the flag, so a failed send keeps it pending.
  private boolean pendingFullEnumeration = false;

  @Provides
  BankstandConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(BankstandConfig.class);
  }

  @Override
  protected void startUp() {
    HttpTransport transport = new OkHttpTransport(okHttpClient);
    pairingClient = new BankstandClient(transport, gson, executor);
    // A file, not ConfigManager. See AckedStateStore.
    ackedStore = new AckedStateStore(new File(ACKED_STATE_DIR, ACKED_STATE_FILE), gson);
    manifestStore = new ManifestStore(new File(ACKED_STATE_DIR, MANIFEST_FILE), gson);
    // Cached copy first, so a capture never waits on the network.
    manifest = manifestStore.current(null);
    refreshManifest();
    deviceStore = new DeviceCredentialStore(new File(ACKED_STATE_DIR, DEVICE_FILE), gson);
    migrateCredentialsOutOfConfig();

    eventOutbox = new EventOutbox(new File(ACKED_STATE_DIR, EVENTS_FILE), gson);
    // Feeds the panel from the same emit the captures make to the outbox.
    java.util.function.Consumer<TransientEvent> onEmit = this::recordEmittedActivity;
    notableDropCapture =
        new NotableDropCapture(
            eventOutbox,
            this::isNotableDropCaptureEnabled,
            this::currentAccountHash,
            itemManager,
            () -> (long) config.notableDropThreshold(),
            NOTABLE_UNTRADEABLE_ALLOWLIST,
            onEmit);
    petDropCapture =
        new PetDropCapture(
            eventOutbox, this::isPetDropCaptureEnabled, this::currentAccountHash, onEmit);
    collectionLogUnlockCapture =
        new CollectionLogUnlockCapture(
            eventOutbox, this::isCollectionLogEventCaptureEnabled, this::currentAccountHash, onEmit);
    combatAchievementCompletionCapture =
        new CombatAchievementCompletionCapture(
            eventOutbox,
            this::isCombatAchievementCompletionCaptureEnabled,
            this::currentAccountHash,
            onEmit);
    combatAchievementTierCompletionCapture =
        new CombatAchievementTierCompletionCapture(
            eventOutbox,
            this::isCombatAchievementTierCompletionCaptureEnabled,
            this::currentAccountHash,
            onEmit);
    diaryTaskCompletionCapture =
        new DiaryTaskCompletionCapture(
            eventOutbox,
            this::isDiaryTaskCompletionCaptureEnabled,
            this::currentAccountHash,
            client::getVarpValue,
            diaryTaskBits,
            diaryTaskManifest,
            () -> saveAckedState(currentAccountHash()),
            onEmit);
    eventBus.register(notableDropCapture);
    eventBus.register(petDropCapture);
    eventBus.register(collectionLogUnlockCapture);
    eventBus.register(combatAchievementCompletionCapture);
    eventBus.register(combatAchievementTierCompletionCapture);
    eventBus.register(diaryTaskCompletionCapture);

    // A toolbar icon, not a permanent sidebar tab: the panel is checked on demand.
    panel =
        new BankstandPanel(
            () -> clientThread.invoke(this::runManualSync),
            () -> LinkBrowser.browse(savedServerUrl()),
            this::refreshPanel);
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
    pairingClient = null;
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

  /**
   * Drains the outbox, grouped by capturing account and split into chunks of at most {@link
   * #MAX_EVENTS_PER_SUBMIT}. Each chunk acks independently, so an invalid event only blocks its
   * own chunk. A failed chunk waits for the next scheduled drain.
   */
  @Schedule(period = EVENT_DRAIN_INTERVAL_SECONDS, unit = ChronoUnit.SECONDS)
  public void drainEventOutbox() {
    if (pairingClient == null || eventOutbox == null || !isPaired()) {
      return;
    }
    List<OutboxEntry> pending = eventOutbox.pending();
    if (pending.isEmpty()) {
      return;
    }
    Map<Long, List<TransientEvent>> byAccount = new LinkedHashMap<>();
    for (OutboxEntry entry : pending) {
      byAccount.computeIfAbsent(entry.getAccountHash(), key -> new ArrayList<>()).add(entry.getEvent());
    }
    String url = savedServerUrl();
    String token = deviceStore.load().getToken();
    executor.submit(
        () -> {
          for (Map.Entry<Long, List<TransientEvent>> group : byAccount.entrySet()) {
            for (List<TransientEvent> chunk : chunkEvents(group.getValue(), MAX_EVENTS_PER_SUBMIT)) {
              // Chunks retry independently; the retry is a scheduled task, never a blocked thread.
              pairingClient
                  .submitEventsWithRetry(
                      url, token, group.getKey(), chunk, MAX_SUBMIT_ATTEMPTS, SUBMIT_RETRY_BASE_DELAY_MS)
                  .whenComplete(
                      (response, failure) -> {
                        if (failure != null) {
                          log.debug(
                              "event drain failed for one account group's chunk: {}",
                              failure.getMessage());
                          return;
                        }
                        if (!response.isRouted()) {
                          // Unclaimed: leave every entry pending, like a network failure.
                          return;
                        }
                        eventOutbox.ack(idsToAck(response.getAcks()));
                      });
            }
          }
        });
  }

  static List<List<TransientEvent>> chunkEvents(List<TransientEvent> events, int maxSize) {
    List<List<TransientEvent>> chunks = new ArrayList<>();
    for (int start = 0; start < events.size(); start += maxSize) {
      chunks.add(new ArrayList<>(events.subList(start, Math.min(start + maxSize, events.size()))));
    }
    return chunks;
  }

  /**
   * The ids the outbox should stop retrying: stored or duplicate, plus {@code "stale"}
   * rejections, which only get truer with time. {@code "not_applied"} stays pending, since the
   * capability flag can be turned on later.
   */
  static Set<String> idsToAck(List<EventAck> acks) {
    Set<String> ids = new HashSet<>();
    for (EventAck ack : acks) {
      if (ack.isStored() || isPermanentlyRejected(ack)) {
        ids.add(ack.getId());
      }
    }
    return ids;
  }

  private static boolean isPermanentlyRejected(EventAck ack) {
    return "rejected".equals(ack.getOutcome()) && "stale".equals(ack.getReason());
  }

  private java.util.List<String> statusLines() {
    Player local = client.getLocalPlayer();
    String name = session.isActive() && local != null ? local.getName() : null;
    return StatusReport.lines(
        isPaired(),
        savedServerUrl(),
        name == null || name.isEmpty() ? null : name,
        lastSubmitAtMs == 0L ? null : describeAge(System.currentTimeMillis() - lastSubmitAtMs),
        submitGate.isHalted() ? "authentication failed, so submission is paused. Re-pair to resume." : null,
        enabledCapabilities(),
        collectionLog.isEmpty() ? -1 : collectionLog.size(),
        // Only with an account loaded: the varbit reads 0 logged out, same as a regular account.
        name == null || name.isEmpty()
            ? null
            : AccountTypes.describe(client.getVarbitValue(AccountTypes.ACCOUNT_TYPE_VARBIT)),
        "Server accepts: " + manifest.describe() + ".");
  }

  /** Coarse on purpose: more precision would imply more than we know. */
  private static String describeAge(long millis) {
    long minutes = millis / 60_000L;
    if (minutes < 1) {
      return "less than a minute ago";
    }
    if (minutes == 1) {
      return "1 minute ago";
    }
    if (minutes < 60) {
      return minutes + " minutes ago";
    }
    long hours = minutes / 60;
    return hours == 1 ? "1 hour ago" : hours + " hours ago";
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
            config.collectPetDrops());
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

  // Maps a chat-triggered event to the capability key the panel's "last synced" uses, so a live
  // event and a snapshot ack update the same figure.
  private static final Map<String, String> CAPABILITY_KEY_BY_EVENT_TYPE = capabilityKeyByEventType();

  private static Map<String, String> capabilityKeyByEventType() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(TransientEvent.TYPE_NOTABLE_DROP, "notableDrops");
    map.put(TransientEvent.TYPE_PET_DROP, "petDrops");
    map.put(TransientEvent.TYPE_COLLECTION_LOG_UNLOCK, "collectionLog");
    map.put(TransientEvent.TYPE_COMBAT_ACHIEVEMENT_COMPLETED, "combatAchievements");
    map.put(TransientEvent.TYPE_DIARY_TASK_COMPLETED, "diaries");
    return map;
  }

  /** The panel's per-capability rows, in {@link #capabilityNames} order, skipping disabled ones. */
  static List<PanelModel.CapabilityRow> capabilityRows(
      boolean skills,
      boolean quests,
      boolean diaries,
      boolean collectionLog,
      boolean combat,
      boolean accountType,
      boolean notableDrops,
      boolean petDrops,
      Map<String, Long> lastSyncedAt) {
    List<PanelModel.CapabilityRow> rows = new ArrayList<>();
    if (skills) {
      rows.add(capabilityRow("Skills", "skills", lastSyncedAt));
    }
    if (quests) {
      rows.add(capabilityRow("Quests", "quests", lastSyncedAt));
    }
    if (diaries) {
      rows.add(capabilityRow("Diaries", "diaries", lastSyncedAt));
    }
    if (collectionLog) {
      rows.add(capabilityRow("Collection log", "collectionLog", lastSyncedAt));
    }
    if (combat) {
      rows.add(capabilityRow("Combat achievements", "combatAchievements", lastSyncedAt));
    }
    if (accountType) {
      rows.add(capabilityRow("Account type", "accountType", lastSyncedAt));
    }
    if (notableDrops) {
      rows.add(capabilityRow("Notable drops", "notableDrops", lastSyncedAt));
    }
    if (petDrops) {
      rows.add(capabilityRow("Pet drops", "petDrops", lastSyncedAt));
    }
    return rows;
  }

  private static PanelModel.CapabilityRow capabilityRow(
      String label, String key, Map<String, Long> lastSyncedAt) {
    return new PanelModel.CapabilityRow(label, lastSyncedAt.get(key));
  }

  private java.util.List<String> enabledCapabilities() {
    return capabilityNames(
        isSkillCaptureEnabled(),
        isQuestCaptureEnabled(),
        isDiaryCaptureEnabled(),
        isCollectionLogCaptureEnabled(),
        isCombatAchievementCaptureEnabled(),
        isAccountTypeCaptureEnabled(),
        isNotableDropCaptureEnabled(),
        isPetDropCaptureEnabled());
  }

  /**
   * Capability names for the status and sync lines, in a stable order. A capability sent on the
   * wire but missing here makes status silently under-report, so a test pins it.
   */
  static java.util.List<String> capabilityNames(
      boolean skills,
      boolean quests,
      boolean diaries,
      boolean collectionLog,
      boolean combat,
      boolean accountType,
      boolean notableDrops,
      boolean petDrops) {
    java.util.List<String> on = new java.util.ArrayList<>();
    if (skills) {
      on.add("skills");
    }
    if (quests) {
      on.add("quests");
    }
    if (diaries) {
      on.add("diaries");
    }
    if (collectionLog) {
      on.add("collection log");
    }
    if (combat) {
      on.add("combat achievements");
    }
    if (accountType) {
      on.add("account type");
    }
    if (notableDrops) {
      on.add("notable drops");
    }
    if (petDrops) {
      on.add("pet drops");
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
    }
  }

  /**
   * Harvests the collection log while the client enumerates it, whoever started the
   * enumeration. Consent is the opt-in; the data is the player's own.
   */
  @Subscribe
  public void onScriptPreFired(ScriptPreFired event) {
    if (event.getScriptId() != COLLECTION_LOG_ITEM_SCRIPT || !isCollectionLogCaptureEnabled()) {
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
    String capability = CAPABILITY_KEY_BY_EVENT_TYPE.get(event.getType());
    if (capability != null) {
      lastSyncedAt.put(capability, System.currentTimeMillis());
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
      boolean skillsAdvanced,
      boolean skillsChanged,
      boolean questsAdvanced,
      boolean diariesAdvanced,
      boolean collectionLogAdvanced,
      boolean combatAchievementsAdvanced,
      boolean accountTypeAdvanced) {
    Map<String, String> synced = new LinkedHashMap<>();
    if (skillsAdvanced && skillsChanged) {
      synced.put("skills", "Skills synced");
    }
    if (questsAdvanced) {
      synced.put("quests", "Quests synced");
    }
    if (diariesAdvanced) {
      synced.put("diaries", "Diaries synced");
    }
    if (collectionLogAdvanced) {
      synced.put("collectionLog", null);
    }
    if (combatAchievementsAdvanced) {
      synced.put("combatAchievements", null);
    }
    if (accountTypeAdvanced) {
      synced.put("accountType", "Account type synced");
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
        capabilityRows(
            isSkillCaptureEnabled(),
            isQuestCaptureEnabled(),
            isDiaryCaptureEnabled(),
            isCollectionLogCaptureEnabled(),
            isCombatAchievementCaptureEnabled(),
            isAccountTypeCaptureEnabled(),
            isNotableDropCaptureEnabled(),
            isPetDropCaptureEnabled(),
            lastSyncedAt);
    PanelPresentation.SyncDot dot =
        PanelPresentation.resolveDot(isPaired(), lastSubmitAtMs > 0L, lastFailureReason != null);
    return new PanelModel(
        isPaired(),
        name == null || name.isEmpty() ? null : name,
        dot,
        rows,
        recentActivityLog.recent(),
        lastFailureReason,
        savedServerUrl());
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
    if (state == GameState.LOGGED_IN) {
      // Adopts the account only if it changed; the -1 logged-out sentinel is ignored.
      session.onLogin(client.getAccountHash());
    } else if (state == GameState.LOGIN_SCREEN) {
      // Before onLogout, which clears the session this needs to attribute the read to.
      captureFinalSnapshot();
      session.onLogout();
      // A read belongs to the character that started it; abandon it silently.
      collectionLogSync.reset();
      hideSyncInfoBox();
    }
  }

  @Subscribe
  public void onGameTick(GameTick event) {
    // Drive the guided read first, unconditionally, so an armed sync can always finish.
    if (collectionLogSync.isActive()) {
      tickCollectionLogSync();
    }
    // Identity once per session. The local player's name is only reliable a tick after LOGGED_IN.
    if (pairingClient == null || !isPaired() || !session.isActive() || session.isSubmitted()) {
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
        isQuestCaptureEnabled() ? readQuestStates() : null,
        isDiaryCaptureEnabled() ? readDiaryStates() : null,
        isDiaryCaptureEnabled() ? readDiaryTaskCounts() : null);
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
    // Non-standard worlds (tournament, seasonal, deadman, PvP arena) are not main-game progress.
    EnumSet<WorldType> worldType = client.getWorldType();
    if (!isStandardWorld(worldType)) {
      return;
    }
    // Read on the client thread into one snapshot, then submit off-thread.
    // invokeLater, never invoke: the :: command handler is already inside a running script, and
    // Quest.getState runs a script, so an inline call hits "scripts are not reentrant".
    clientThread.invokeLater(
        () -> {
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
          onSkillsCaptured(accountHash, generation, name, skills, quests, diaries, diaryTasks);
        });
  }

  private static boolean isStandardWorld(EnumSet<WorldType> worldType) {
    return !worldType.contains(WorldType.TOURNAMENT_WORLD)
        && !worldType.contains(WorldType.SEASONAL)
        && !worldType.contains(WorldType.DEADMAN)
        && !worldType.contains(WorldType.PVP_ARENA);
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
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : DiaryTaskVarbits.ALL.entrySet()) {
      counts.put(e.getKey(), client.getVarbitValue(e.getValue()));
    }
    return counts;
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
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : CombatAchievementVarbits.ALL.entrySet()) {
      counts.put(e.getKey(), client.getVarbitValue(e.getValue()));
    }
    return counts;
  }

  // Per boss/activity. Rides the combat achievement gate and baseline.
  private Map<String, Integer> readCombatAchievementBossCounts() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : CombatAchievementBossVarbits.ALL.entrySet()) {
      counts.put(e.getKey(), client.getVarbitValue(e.getValue()));
    }
    return counts;
  }

  private void onSkillsCaptured(
      long accountHash,
      int generation,
      String name,
      Map<String, Integer> skills,
      Map<String, String> quests,
      Map<String, String> diaries,
      Map<String, Integer> diaryTaskCounts) {
    // Forget every baseline, then load what disk says this character had acked. A capture
    // arriving before the load just re-sends.
    if (generation != baselineGeneration) {
      skillBaseline.reset();
      questBaseline.reset();
      diaryBaseline.reset();
      diaryTaskBits.reset();
      combatAchievementBaseline.reset();
      accountTypeBaseline.reset();
      // Per-character state; carrying it across a switch would mix accounts.
      collectionLog.reset();
      collectionLogBaseline.reset();
      // A pending COMPLETE claim is about the log just reset, so it must not survive a relog.
      pendingFullEnumeration = false;
      // loadAckedState repopulates lastSyncedAt; the activity log is session-only.
      lastSyncedAt.clear();
      recentActivityLog.clear();
      baselineGeneration = generation;
      loadAckedState(accountHash, generation);
    }
    // An opt-in is not retroactive to items observed before it.
    Set<Integer> clog =
        isCollectionLogCaptureEnabled() ? collectionLog.observed() : Collections.emptySet();
    // Not read at all when off: an opt-in is about what leaves the client.
    Map<String, Integer> combatAchievements =
        isCombatAchievementCaptureEnabled()
            ? readCombatAchievementCounts()
            : Collections.emptyMap();
    Map<String, Integer> combatAchievementBossCounts =
        isCombatAchievementCaptureEnabled()
            ? readCombatAchievementBossCounts()
            : Collections.emptyMap();
    // Null when off, or when the varbit holds an unknown value: a wrong badge is worse than none.
    String accountType =
        isAccountTypeCaptureEnabled()
            ? AccountTypes.keyFor(client.getVarbitValue(AccountTypes.ACCOUNT_TYPE_VARBIT))
            : null;
    SubmitPlan plan =
        plan(
            skillBaseline,
            skills,
            questBaseline,
            quests,
            diaryBaseline,
            diaries,
            collectionLogBaseline,
            clog,
            combatAchievementBaseline,
            combatAchievements,
            accountTypeBaseline,
            accountType,
            pendingFullEnumeration);
    if (!plan.shouldSubmit()) {
      return;
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
        plan.includesFullEnumeration());
  }

  /**
   * Reads on the executor, applies on the client thread where the baselines live. The
   * {@code isCurrent} guard drops a slow read for an account the player has since left.
   */
  private void loadAckedState(long accountHash, int generation) {
    if (ackedStore == null) {
      return;
    }
    executor.submit(
        () -> {
          AckedState state = ackedStore.load(accountHash);
          clientThread.invoke(
              () -> {
                if (!session.isCurrent(accountHash, generation)) {
                  return;
                }
                skillBaseline.restore(state.getSkills());
                questBaseline.restore(state.getQuests());
                diaryBaseline.restore(state.getDiaries());
                diaryTaskBits.restore(state.getDiaryTaskBits());
                combatAchievementBaseline.restore(state.getCombatAchievements());
                accountTypeBaseline.restore(state.getAccountType());
                // Together, never one alone. See CollectionLogBaseline.restore.
                collectionLog.restore(state.getCollectionLogItems());
                collectionLogBaseline.restore(state.getCollectionLogAcked());
                lastSyncedAt.clear();
                lastSyncedAt.putAll(state.getLastSyncedAt());
                refreshPanel();
              });
        });
  }

  /**
   * Reads the baselines on the client thread and hands a finished document to the
   * executor to write. Reading them on the executor would race the next capture.
   */
  private void saveAckedState(long accountHash) {
    if (ackedStore == null) {
      return;
    }
    AckedState state = AckedState.empty();
    state.setSkills(skillBaseline.ackedDigest());
    state.setQuests(questBaseline.ackedDigest());
    state.setDiaries(diaryBaseline.ackedDigest());
    state.setDiaryTaskBits(diaryTaskBits.snapshot());
    state.setCombatAchievements(combatAchievementBaseline.ackedDigest());
    state.setAccountType(accountTypeBaseline.ackedValue());
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

    private SubmitPlan(
        boolean submit,
        boolean quests,
        boolean diaries,
        boolean collectionLog,
        boolean fullEnumeration,
        boolean combatAchievements,
        boolean accountType) {
      this.submit = submit;
      this.quests = quests;
      this.diaries = diaries;
      this.collectionLog = collectionLog;
      this.fullEnumeration = fullEnumeration;
      this.combatAchievements = combatAchievements;
      this.accountType = accountType;
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
  }

  /** Without combat achievements. */
  static SubmitPlan plan(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries,
      CollectionLogBaseline collectionLogBaseline,
      Set<Integer> collectionLogItems) {
    return plan(
        skillBaseline,
        skills,
        questBaseline,
        quests,
        diaryBaseline,
        diaries,
        collectionLogBaseline,
        collectionLogItems,
        new CombatAchievementBaseline(),
        null,
        new AccountTypeBaseline(),
        null,
        false);
  }

  /** Without the account type. */
  static SubmitPlan plan(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries,
      CollectionLogBaseline collectionLogBaseline,
      Set<Integer> collectionLogItems,
      CombatAchievementBaseline combatAchievementBaseline,
      Map<String, Integer> combatAchievements) {
    return plan(
        skillBaseline,
        skills,
        questBaseline,
        quests,
        diaryBaseline,
        diaries,
        collectionLogBaseline,
        collectionLogItems,
        combatAchievementBaseline,
        combatAchievements,
        new AccountTypeBaseline(),
        null,
        false);
  }

  /** Without the full-enumeration signal. */
  static SubmitPlan plan(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries,
      CollectionLogBaseline collectionLogBaseline,
      Set<Integer> collectionLogItems,
      CombatAchievementBaseline combatAchievementBaseline,
      Map<String, Integer> combatAchievements,
      AccountTypeBaseline accountTypeBaseline,
      String accountType) {
    return plan(
        skillBaseline,
        skills,
        questBaseline,
        quests,
        diaryBaseline,
        diaries,
        collectionLogBaseline,
        collectionLogItems,
        combatAchievementBaseline,
        combatAchievements,
        accountTypeBaseline,
        accountType,
        false);
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
      Map<String, String> diaries,
      CollectionLogBaseline collectionLogBaseline,
      Set<Integer> collectionLogItems,
      CombatAchievementBaseline combatAchievementBaseline,
      Map<String, Integer> combatAchievements,
      AccountTypeBaseline accountTypeBaseline,
      String accountType,
      boolean fullEnumerationPending) {
    boolean sendQuests =
        quests != null && !quests.isEmpty() && questBaseline.changedSince(quests);
    boolean sendDiaries =
        diaries != null && !diaries.isEmpty() && diaryBaseline.changedSince(diaries);
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
    boolean submit =
        skillBaseline.changedSince(skills)
            || sendQuests
            || sendDiaries
            || sendCollectionLog
            || sendCombatAchievements
            || sendAccountType;
    return new SubmitPlan(
        submit,
        sendQuests,
        sendDiaries,
        sendCollectionLog,
        fullEnumerationPending && sendCollectionLog,
        sendCombatAchievements,
        sendAccountType);
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
    if (previous == null) {
      return true;
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
      boolean fullEnumeration) {
    String url = savedServerUrl();
    String token = deviceStore.load().getToken();
    String version = getClass().getPackage().getImplementationVersion();
    String pluginVersion = version != null ? version : "dev";
    Map<String, Object> body =
        SubmitEnvelope.body(
            UuidV7.generate(),
            SubmitEnvelope.SCHEMA_VERSION,
            pluginVersion,
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
            combatAchievementBossCounts);
    executor.submit(
        () -> {
          pairingClient
              .submitSnapshotWithRetry(url, token, body, MAX_SUBMIT_ATTEMPTS, SUBMIT_RETRY_BASE_DELAY_MS)
              .whenComplete(
                  (res, failure) -> {
                    if (failure != null) {
                      // Baseline not advanced, so the next cycle retries. Never log the token
                      // or account hash.
                      SubmitException e = (SubmitException) failure;
                      // Shown on the panel's header dot and failure footer.
                      lastFailureReason = e.getMessage();
                      refreshPanel();
                      if (e.isAuthFailure()) {
                        // Retrying cannot fix a revoked token. This is the one failure the
                        // player must act on, so the gate announces it.
                        log.debug("submit refused: token rejected, halting until re-paired");
                        if (submitGate.onAuthFailure()) {
                          notice("This client is no longer paired. Re-pair from your Bankstand account.");
                        }
                        return;
                      }
                      // The message, never the body: it carries the account hash and name.
                      log.debug("submit failed: {}", e.getMessage());
                      submitGate.onFailure();
                      if (session.isCurrent(accountHash, generation)
                          && noticeGate.onFailure(e.getMessage())) {
                        notice("Could not sync your progress. " + e.getMessage());
                      }
                      return;
                    }
                    // Advance each baseline only for a block the server wrote, on the client
                    // thread, and only if this login is still current.
                    clientThread.invoke(
                        () -> {
                          if (session.isCurrent(accountHash, generation)) {
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
                              diaryBaseline.advance(diaries);
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
                            long now = System.currentTimeMillis();
                            for (Map.Entry<String, String> synced :
                                syncedCapabilitiesThisCycle(
                                        skillsAdvanced,
                                        skillsChanged,
                                        questsAdvanced,
                                        diariesAdvanced,
                                        collectionLogAdvanced,
                                        combatAchievementsAdvanced,
                                        accountTypeAdvanced)
                                    .entrySet()) {
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
                    lastFailureReason = null;
                    // Stored blocks only, never the body.
                    log.debug(
                        "submit {}: reason={} storedBlocks=[{}]",
                        res.isStored() ? "stored" : "accepted",
                        res.getReason(),
                        String.join(",", res.getStoredBlocks()));
                    if (session.isCurrent(accountHash, generation) && noticeGate.onSuccess()) {
                      notice("Reconnected. Your progress is syncing again.");
                    }
                  });
        });
  }

  private boolean isPaired() {
    String token = deviceStore.load().getToken();
    return token != null && !token.trim().isEmpty();
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
  // bind a paired client. Kept as an option so the Plugin Hub warning sits on it and a player can
  // go quiet without unpairing.
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
    executor.submit(
        () -> {
          CapabilityManifest.RawManifest raw = pairingClient.fetchManifest(url);
          CapabilityManifest validated = CapabilityManifest.validate(raw);
          if (validated != null) {
            manifestStore.save(raw);
          }
          manifest = manifestStore.current(validated);
          log.debug("manifest in use: {}", manifest.describe());
        });
  }

  private void submitIdentity(long accountHash, int generation, String displayName) {
    String url = savedServerUrl();
    String token = deviceStore.load().getToken();
    executor.submit(
        () -> {
          pairingClient
              .submitIdentityWithRetry(
                  url, token, accountHash, displayName, MAX_SUBMIT_ATTEMPTS, SUBMIT_RETRY_BASE_DELAY_MS)
              .whenComplete(
                  (res, failure) -> {
                    if (failure != null) {
                      // Already retried to the cap. Release the in-flight mark so a later
                      // tick retries; otherwise the session stays unbound until a relog.
                      // Guarded on the login instance.
                      session.markSubmitFailed(accountHash, generation);
                      // Never log the token or account hash.
                      if (session.isCurrent(accountHash, generation)) {
                        notice("Could not verify this character. " + failure.getMessage());
                      }
                      return;
                    }
                    // Drop the result unless this login instance is still current: the retry
                    // backoff can span a relog.
                    if (session.isCurrent(accountHash, generation)) {
                      notice(identityNoticeFor(res.isVerified(), res.getLinkedRsn(), res.getOutcome()));
                    }
                  });
        });
  }

  private void pair(String rawCode) {
    String url = savedServerUrl();
    notice("Pairing with " + url + "...");
    executor.submit(
        () -> {
          try {
            PairResponse res = pairingClient.exchangePairingCode(url, rawCode);
            storeToken(res);
            // A fresh pairing resets failure notices, and is the only thing that lifts an
            // auth halt.
            noticeGate.onSuccess();
            submitGate.resume();
            notice("Connected. Your progress will sync from now on.");
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
    deviceStore.clear();
    // Unsetting the legacy keys is what removes the token from a synced profile upstream.
    forgetLegacyCredentialKeys();
    // Untick the toggle so ticking it again fires this afresh.
    configManager.setConfiguration(
        BankstandKeys.GROUP, BankstandKeys.KEY_DISCONNECT, Boolean.FALSE.toString());
    notice("Disconnected. Nothing will be sent until you pair again.");
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

  /**
   * Moves credentials from ConfigManager to the device file. A no-op after the first run. The
   * unset matters most: it is what deletes the token from RuneLite's config sync on a synced
   * profile. The file wins if both exist, so a stale synced value never overwrites a newer
   * pairing.
   */
  private void migrateCredentialsOutOfConfig() {
    String legacyToken =
        configManager.getConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_TOKEN);
    if (legacyToken == null || legacyToken.trim().isEmpty()) {
      return;
    }
    if (!deviceStore.load().isPaired()) {
      DeviceCredentials migrated = new DeviceCredentials();
      migrated.setToken(legacyToken);
      migrated.setDeviceId(
          configManager.getConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_ID));
      migrated.setExpiresAt(
          configManager.getConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_TOKEN_EXPIRES_AT));
      deviceStore.save(migrated);
    }
    forgetLegacyCredentialKeys();
  }

  /** Clears the pre-file credential keys, and on a synced profile the upstream copy. */
  private void forgetLegacyCredentialKeys() {
    configManager.unsetConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_TOKEN);
    configManager.unsetConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_ID);
    configManager.unsetConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_TOKEN_EXPIRES_AT);
  }

  private void storeToken(PairResponse res) {
    DeviceCredentials credentials = new DeviceCredentials();
    credentials.setToken(res.getDeviceToken());
    credentials.setDeviceId(res.getDeviceId());
    credentials.setExpiresAt(res.getExpiresAt());
    deviceStore.save(credentials);
  }

  // Normalised, so a blank or padded value never reaches the socket; a cleared field gets prod.
  private String savedServerUrl() {
    return BankstandKeys.normaliseServerUrl(config.serverBaseUrl());
  }
}
