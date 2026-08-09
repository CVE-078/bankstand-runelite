package com.bankstand;

import com.bankstand.dto.PairResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
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
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
    name = "Bankstand",
    description =
        "Sync your skills, quests, diaries, combat achievements and collection log to your"
            + " Bankstand account",
    tags = {"bankstand", "account", "progress", "external"})
public class BankstandPlugin extends Plugin {

  // A bounded retry for a transient submit failure (network, 429, 5xx): three
  // attempts with a 1s, then 2s, backoff. Terminal failures (a revoked token, a
  // 4xx) fail fast. Runs on the background executor, so the backoff blocks nothing
  // on the game thread.
  private static final int MAX_SUBMIT_ATTEMPTS = 3;
  private static final long SUBMIT_RETRY_BASE_DELAY_MS = 1000L;

  // Capture the 23 skills on a fixed cadence and submit only when they changed since
  // the last acknowledged submit. The interval matches the server's per-device
  // cooldown so a change is reported at most once per window.
  private static final int CAPTURE_INTERVAL_SECONDS = 60;

  // The collection log is not held in varbits: Jagex keeps it server-side and only
  // reveals it while the log interface enumerates its items. Script 4100 fires once
  // per item during that enumeration, carrying the item id as its second argument,
  // which is what the harvest listens to. Searching the log is what makes it enumerate
  // everything rather than one page, and the script fires for every entry whoever ran
  // that Search, so watching a player-initiated one reads the whole log without the
  // plugin triggering anything. The menu entry below arms that watch; it does not, and
  // must not, drive the interface itself.
  private static final int COLLECTION_LOG_ITEM_SCRIPT = 4100;

  // Its own directory, so a player can find and delete what the plugin keeps.
  private static final File ACKED_STATE_DIR = new File(RuneLite.RUNELITE_DIR, "bankstand");
  private static final String ACKED_STATE_FILE = "acked-state.json";
  private static final String DEVICE_FILE = "device.json";
  private static final String MANIFEST_FILE = "manifest.json";


  // Every chat line the plugin writes carries a coloured prefix, so a message is
  // attributable at a glance in a busy chat box: the panel used to give that context
  // by simply being the thing you were looking at.
  //
  // This is the site's accent in its LIGHT-SURFACE variant (--app-accent under the
  // light theme), not the dark-theme gold the site itself shows. The chat box is a
  // pale parchment by default, where the brighter #f0a830 washes out. The darker gold
  // still reads on a transparent chat box over the game world, so it is the one that
  // works in both, rather than the one that looks best in either.
  private static final Color BRAND = new Color(0xB3730A);
  private static final String NOTICE_PREFIX = "Bankstand: ";

  // The server's v1 contract is frozen to these 23 XP skills. Skill.values() can
  // include entries the contract never anticipated (a client-only OVERALL total, or
  // a skill added to the game after the contract was frozen); an unknown key fails
  // the whole submission, so this allowlist is read instead of Skill.values().
  private static final EnumSet<Skill> CAPTURED_SKILLS =
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
          Skill.CONSTRUCTION);

  @Inject private Client client;
  @Inject private ConfigManager configManager;
  @Inject private BankstandConfig config;
  @Inject private OkHttpClient okHttpClient;
  @Inject private Gson gson;
  @Inject private ScheduledExecutorService executor;
  @Inject private ClientThread clientThread;
  @Inject private ChatMessageManager chatMessageManager;
  @Inject private InfoBoxManager infoBoxManager;

  private final AccountSession session = new AccountSession();
  private final SkillBaseline skillBaseline = new SkillBaseline();
  private final QuestBaseline questBaseline = new QuestBaseline();
  private final DiaryBaseline diaryBaseline = new DiaryBaseline();
  private final CombatAchievementBaseline combatAchievementBaseline =
      new CombatAchievementBaseline();
  private final CollectionLogAccumulator collectionLog = new CollectionLogAccumulator();
  private final CollectionLogBaseline collectionLogBaseline = new CollectionLogBaseline();
  private final CollectionLogSync collectionLogSync = new CollectionLogSync();
  private CollectionLogSyncInfoBox syncInfoBox;
  private AckedStateStore ackedStore;
  private ManifestStore manifestStore;

  /**
   * What the server currently ingests.
   *
   * <p>Never null once {@code startUp} has run: the store falls back to the last cached
   * manifest and then to the compiled-in one, so there is no state in which a manifest
   * problem stops a paired client working. Refreshed in the background, so a fetch never
   * sits in front of a capture.
   */
  private volatile CapabilityManifest manifest = CapabilityManifest.bundled();
  private DeviceCredentialStore deviceStore;
  // Suppresses a recurring failure from printing every capture cycle.
  private final NoticeGate noticeGate = new NoticeGate();
  // Stops a revoked token retrying forever, and paces a capture against a down server.
  private final SubmitGate submitGate = new SubmitGate();
  // The most recent trusted skill read, so the logout read can be checked against it
  // rather than trusted blind.
  private Map<String, Integer> lastSkillRead;
  // Zero until something has actually reached the server, so status can say so
  // rather than always claiming nothing has been sent.
  private long lastSubmitAtMs;
  // The generation the baseline currently tracks; a change means the account switched
  // and the baseline must be forgotten so the new account submits afresh.
  private int baselineGeneration = -1;
  private BankstandClient pairingClient;

  @Provides
  BankstandConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(BankstandConfig.class);
  }

  @Override
  protected void startUp() {
    HttpTransport transport = new OkHttpTransport(okHttpClient);
    pairingClient = new BankstandClient(transport, gson);
    // A file, not ConfigManager. AckedStateStore says why that is not a preference.
    ackedStore = new AckedStateStore(new File(ACKED_STATE_DIR, ACKED_STATE_FILE), gson);
    manifestStore = new ManifestStore(new File(ACKED_STATE_DIR, MANIFEST_FILE), gson);
    // The cached copy immediately, so the first capture of a session is never gated on a
    // network round trip, then a refresh in the background.
    manifest = manifestStore.current(null);
    refreshManifest();
    deviceStore = new DeviceCredentialStore(new File(ACKED_STATE_DIR, DEVICE_FILE), gson);
    migrateCredentialsOutOfConfig();
  }

  @Override
  protected void shutDown() {
    pairingClient = null;
    // An infobox outlives the plugin that added it, so a sync armed when the player
    // disables Bankstand would otherwise sit on screen with nothing behind it.
    collectionLogSync.reset();
    hideSyncInfoBox();
  }

  /** The status lines, gathered from live state. */
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
        // Only with an account actually loaded. The varbit reads 0 when logged
        // out, which is the same value as a regular account.
        name == null || name.isEmpty()
            ? null
            : AccountTypes.describe(client.getVarbitValue(AccountTypes.ACCOUNT_TYPE_VARBIT)),
        "Server accepts: " + manifest.describe() + ".");
  }

  /** `4 minutes ago`, coarsely. Precision here would imply more than we know. */
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
   * A manual sync runs the scheduled capture, rather than reading and submitting on its
   * own. A second submit path is how the two drift: this one inherits the change gate,
   * the per-block acknowledgement, the world check, the backoff and the persistence.
   */
  private void requestManualCapture() {
    if (submitGate.isHalted()) {
      notice("Submission is paused after an authentication failure. Re-pair to resume.");
      return;
    }
    captureSkills();
  }

  /**
   * Re-runs the identity submit, ignoring the once-per-session flag.
   *
   * <p>This is the action that was impossible during the incident: identity is
   * submitted once per login and marks itself done before dispatch, so a single
   * failure was unrecoverable short of a relog.
   */
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

  /**
   * {@code ::bankstand} and its subcommands.
   *
   * <p>A chat command rather than a settings button, because RuneLite has no button:
   * {@code @ConfigItem} carries only position, keyName, name, description, hidden,
   * warning, secret and section, verified against the client jar. A boolean that
   * resets itself is the alternative and it shows a checkbox pretending to be a
   * control.
   *
   * <p>{@code ::} rather than {@code !}, deliberately. A {@code !} command goes
   * through {@code ChatCommandManager} and is broadcast to everyone nearby; these are
   * private actions about one player's own account and have no business in public
   * chat.
   *
   * <p>Every action reports its outcome, because half the value of a manual trigger is
   * seeing what it did. During the incident that prompted this there was no way to
   * retry anything and no way to see why.
   */
  @Subscribe
  public void onCommandExecuted(CommandExecuted event) {
    String command = event.getCommand();
    if (!BankstandKeys.COMMAND.equalsIgnoreCase(command)
        && !BankstandKeys.COMMAND_ALIAS.equalsIgnoreCase(command)) {
      return;
    }
    String[] args = event.getArguments();
    String action = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "status";
    switch (action) {
      case "status":
        for (String line : statusLines()) {
          notice(line);
        }
        break;
      case "sync":
        for (String line : StatusReport.syncLines(isPaired(), enabledCapabilities())) {
          notice(line);
        }
        if (isPaired() && !enabledCapabilities().isEmpty()) {
          // Re-reads and submits through the one existing path, so a manual sync
          // inherits every rule an automatic one has rather than becoming a second
          // submit route that can drift from it.
          requestManualCapture();
        }
        break;
      case "link":
        relinkCharacter();
        break;
      default:
        notice("Unknown command. Try ::bstand, ::bstand sync or ::bstand link.");
        break;
    }
  }

  private java.util.List<String> enabledCapabilities() {
    return capabilityNames(
        isSkillCaptureEnabled(),
        isQuestCaptureEnabled(),
        isDiaryCaptureEnabled(),
        isCollectionLogCaptureEnabled(),
        isCombatAchievementCaptureEnabled());
  }

  /**
   * Capability names for the status and sync lines, in a stable display order.
   *
   * <p>Static and taking plain flags so it can be tested, because the failure it guards
   * is silent: a capability added to the wire but not to this list makes the plugin
   * under-report what it is sending, and the only symptom is a chat line that reads
   * fine. Combat achievements shipped that way, captured and stored correctly while
   * every status line denied it existed.
   */
  static java.util.List<String> capabilityNames(
      boolean skills, boolean quests, boolean diaries, boolean collectionLog, boolean combat) {
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
    return on;
  }

  /**
   * Config carries two actions as well as settings, because a settings screen has no
   * button: a pasted pairing code performs the pairing, and the disconnect toggle
   * forgets the credentials. Both clear themselves afterwards, which fires this
   * handler a second time with an empty or false value that falls through.
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
   * Harvests the collection log while the client enumerates it.
   *
   * <p>Deliberately NOT gated on the player having used our own trigger. Any
   * enumeration will do: another plugin's sync button, or the player simply searching
   * their own log. Consent is already handled by the opt-in, the data is the player's
   * own on their own client, and refusing to look at an enumeration we can see would
   * only make the feature worse for no gain in safety.
   */
  @Subscribe
  public void onScriptPreFired(ScriptPreFired event) {
    if (event.getScriptId() != COLLECTION_LOG_ITEM_SCRIPT || !isCollectionLogCaptureEnabled()) {
      return;
    }
    Object[] args = event.getScriptEvent() == null ? null : event.getScriptEvent().getArguments();
    // args[1] is the item id. Guarded rather than assumed: this is an internal game
    // script and its shape is not a contract we control.
    if (args == null || args.length < 2 || !(args[1] instanceof Integer)) {
      return;
    }
    collectionLog.observe((Integer) args[1]);
    // Passive browsing fires this script too. The sync ignores an entry unless a guided
    // read is armed, so ordinary page-turning still enriches the accumulator without
    // reporting itself as a whole-log sync.
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
   * Advances an in-flight read by one tick and clears up after it when it ends.
   *
   * <p>The infobox is keyed on the sync still being active, NOT on there being an
   * outcome. Those come apart in both directions and getting it wrong is invisible in
   * the state machine's own tests: a read in progress returns no outcome every tick, so
   * hiding on a null outcome would take the box away one tick after arming, and a read
   * abandoned on the arm timeout ends with no outcome at all, so hiding only on a
   * non-null one would leave the box up for good.
   *
   * <p>The count is reported either way, because the count is the fact. Whether the
   * read saw the whole log is a separate and weaker claim, and the wording keeps them
   * apart: a partial read says what to do about it rather than implying the sync
   * failed, since everything it did read has still been kept.
   */
  private void tickCollectionLogSync() {
    // Turning the capability off mid-read stops the entries arriving (the script
    // handler is gated on it), which would otherwise look like a read that finished.
    if (!isCollectionLogCaptureEnabled()) {
      collectionLogSync.reset();
      hideSyncInfoBox();
      return;
    }
    // A search starts a read with nobody having armed it, so the box is raised here
    // rather than only where the menu entry arms one. Idempotent.
    showSyncInfoBox();
    CollectionLogSync.Outcome outcome =
        collectionLogSync.onTick(isSearchOpen(), isCollectionLogOpen());
    if (collectionLogSync.isActive()) {
      return;
    }
    hideSyncInfoBox();
    if (outcome != null) {
      // Slots filled, not ids held. The accumulator keeps raw ids for submission,
      // so it is canonicalised here; CollectionLogSync already counts that way.
      notice(syncOutcomeMessage(outcome, VariantIds.countEntries(collectionLog.observed())));
    }
  }

  // Package-private and static so the wording is testable without a Client, the same
  // reason shouldSubmit and the shouldAdvance family are.
  /**
   * Reports what the plugin captured, not what the log totals.
   *
   * <p>It deliberately does not quote the game's own "189 of 1712": reading that
   * off the interface failed three times against a live client, and it stopped
   * being worth chasing once the server learned to derive both numbers from the
   * ids submitted here. This is the count that was sent; the log's own figure is
   * Bankstand's to show.
   */
  static String syncOutcomeMessage(CollectionLogSync.Outcome outcome, int entriesFilled) {
    String entries = entriesFilled + (entriesFilled == 1 ? " entry" : " entries");
    if (outcome == CollectionLogSync.Outcome.COMPLETE) {
      return "Collection log synced. " + entries + " logged.";
    }
    return "Partial read of your collection log. " + entries + " logged so far.";
  }

  /** True while the log's own search interface is on screen. */
  private boolean isSearchOpen() {
    Widget results = client.getWidget(InterfaceID.Collection.SEARCH_RESULTS);
    return results != null && !results.isHidden();
  }

  /**
   * True while the player is still in the collection log, search view included.
   *
   * <p>The search counts as still being in the log. Opening it can hide the log's own
   * root widget, and treating that as "the player closed the log" cancelled the read at
   * the exact moment the player did the one thing it was waiting for. Observed live: a
   * sync armed at 12:04:38 died silently two seconds later, then the next attempt
   * succeeded only because the search view was already open by then.
   */
  private boolean isCollectionLogOpen() {
    Widget log = client.getWidget(InterfaceID.Collection.UNIVERSE);
    return (log != null && !log.isHidden()) || isSearchOpen();
  }

  private BufferedImage icon() {
    return ImageUtil.loadImageResource(BankstandPlugin.class, "icon.png");
  }

  private boolean isCollectionLogCaptureEnabled() {
    return config.collectCollectionLog() && manifest.allows("collectionLog");
  }

  private boolean isCombatAchievementCaptureEnabled() {
    return config.collectCombatAchievements() && manifest.allows("combatAchievements");
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
      // A read belongs to the character that started it, and its interface is gone.
      // Abandoned rather than reported: an outcome nobody is there to read is noise.
      collectionLogSync.reset();
      hideSyncInfoBox();
    }
  }

  @Subscribe
  public void onGameTick(GameTick event) {
    // Drive the guided read first and unconditionally. It has to be able to finish even
    // when the identity submit below has already run or is being skipped, or an armed
    // sync would hang with its infobox up.
    if (collectionLogSync.isActive()) {
      tickCollectionLogSync();
    }
    // Submit the logged-in character's identity once per session, when paired. The
    // local player (and its name) is reliably available a tick after LOGGED_IN, which
    // is why this runs on the tick rather than directly on the state change.
    if (pairingClient == null || !isPaired() || !session.isActive() || session.isSubmitted()) {
      return;
    }
    // The identity submit sends the account hash and display name, which is the same
    // class of data the skill capture sends and is covered by the same opt-in. A
    // client with it off stays paired and stays silent.
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
    // Mark before dispatching so a slow submit does not fire again on the next tick.
    // One attempt per account per session; a relog retries. The generation pins this
    // submit to this login instance so a stale result cannot overwrite a later one.
    session.markSubmitted();
    submitIdentity(session.getAccountHash(), session.getGeneration(), name);
  }

  /**
   * The last minute of a session, which the 60s schedule otherwise drops.
   *
   * <p>Goes through {@code onSkillsCaptured} like any other capture, so it inherits the
   * change gate, the per-block acknowledgement, the retry and the persistence rather
   * than becoming a second submit path with its own rules. It does not block: the
   * submit is already handed to the executor, and this returns before the client has
   * finished logging out.
   *
   * <p>Nothing is lost when the read is rejected or the submit never lands. The next
   * login re-reads live state, which already contains these minutes.
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
        // The local player is already gone, so the name cannot be re-read here. The
        // envelope treats it as optional and the server keeps the one it has.
        null,
        skills,
        isQuestCaptureEnabled() ? readQuestStates() : null,
        isDiaryCaptureEnabled() ? readDiaryStates() : null);
  }

  @Schedule(period = CAPTURE_INTERVAL_SECONDS, unit = ChronoUnit.SECONDS)
  public void captureSkills() {
    if (pairingClient == null || !isPaired() || !session.isActive()) {
      return;
    }
    // Before reading anything: a halted or backing-off client should not be walking the
    // varbits either, and consuming a skip here is what makes the backoff advance.
    if (!submitGate.allow()) {
      return;
    }
    // Skills gate the whole capture, not just their own block. The v1 envelope makes
    // `skills` required and quests, diaries and the collection log optional riders on
    // it, so there is no submission to attach them to with this off. The config item
    // says so rather than leaving a player wondering why their quest opt-in went quiet.
    if (!isSkillCaptureEnabled()) {
      return;
    }
    if (client.getGameState() != GameState.LOGGED_IN) {
      return;
    }
    // Skip non-standard worlds (tournament, seasonal, leagues, deadman, PvP arena):
    // their XP is not the account's main-game progression.
    EnumSet<WorldType> worldType = client.getWorldType();
    if (!isStandardWorld(worldType)) {
      return;
    }
    // Read the skills and the identity on the client thread, into one consistent
    // snapshot, then dispatch the submit off-thread.
    //
    // **invokeLater, never invoke.** `invoke` runs the runnable INLINE when the caller
    // is already on the client thread, and one caller is: the `::` command handler
    // fires from inside `onScriptCallbackEvent`, so it is already inside a running
    // script. `readQuestStates` calls `Quest.getState`, which runs a script, and the
    // client asserts "scripts are not reentrant" and dies. That killed the manual sync
    // the first time anyone ran it. Deferring by a tick costs nothing for a snapshot
    // and makes the reentrant case unreachable rather than merely unlikely.
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
          // Quest and diary state are opt-in; read them in this same block so they are
          // one consistent snapshot with the skills, and leave each null (never sent)
          // when off.
          Map<String, String> quests = isQuestCaptureEnabled() ? readQuestStates() : null;
          Map<String, String> diaries = isDiaryCaptureEnabled() ? readDiaryStates() : null;
          onSkillsCaptured(accountHash, generation, name, skills, quests, diaries);
        });
  }

  // Only the main game (no non-standard world-type flags) carries real progression.
  private static boolean isStandardWorld(EnumSet<WorldType> worldType) {
    return !worldType.contains(WorldType.TOURNAMENT_WORLD)
        && !worldType.contains(WorldType.SEASONAL)
        && !worldType.contains(WorldType.DEADMAN)
        && !worldType.contains(WorldType.PVP_ARENA);
  }

  // Reads current XP for the 23 tracked skills, keyed by lowercase name. Runs on the
  // client thread (getSkillExperience must not be called off it). Iterates the
  // frozen allowlist rather than Skill.values() so a client-only OVERALL total, or a
  // skill the game added after the server contract was frozen, is never sent.
  private Map<String, Integer> readSkillXp() {
    Map<String, Integer> skills = new LinkedHashMap<>();
    for (Skill skill : CAPTURED_SKILLS) {
      String key = skill.getName().toLowerCase();
      skills.put(key, client.getSkillExperience(skill));
    }
    return skills;
  }

  // Reads every quest's completion state, keyed by the Quest enum constant name (the
  // server's contract key). Runs on the client thread alongside readSkillXp (Quest.
  // getState reads varps/varbits). Unlike the skills read, there is no allowlist: the
  // server accepts any key up to its cap, and 210 quests is well under it.
  private Map<String, String> readQuestStates() {
    Map<String, String> quests = new LinkedHashMap<>();
    for (Quest quest : Quest.values()) {
      quests.put(quest.name(), quest.getState(client).name());
    }
    return quests;
  }

  // Reads every tracked achievement diary tier's completion state, keyed by the
  // server's wire key (see DiaryVarbits). Runs on the client thread alongside
  // readSkillXp and readQuestStates (varbit reads must not happen off it). The exact
  // non-zero value a completed tier's varbit holds is unverified, so completion is
  // read as "not zero" rather than "equals one".
  private Map<String, String> readDiaryStates() {
    Map<String, String> diaries = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : DiaryVarbits.ALL.entrySet()) {
      boolean complete = client.getVarbitValue(e.getValue()) != 0;
      diaries.put(e.getKey(), complete ? "COMPLETE" : "INCOMPLETE");
    }
    return diaries;
  }

  // Reads how many combat achievement tasks are done per tier, keyed by the server's
  // wire key (see CombatAchievementVarbits). Client thread, like every other varbit
  // read here.
  //
  // COUNTS ONLY. The game exposes no per-task state, so this cannot say which tasks
  // are done and the website's task grid stays on placeholders. A zero is kept rather
  // than dropped: a tier really can be 0/41, and that is a fact, not an absence.
  private Map<String, Integer> readCombatAchievementCounts() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : CombatAchievementVarbits.ALL.entrySet()) {
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
      Map<String, String> diaries) {
    // Forget every baseline, then ask disk what this character already had accepted.
    // Forgetting first is what lets the load be slow: a capture arriving before it just
    // re-sends, which is what every client start did before any of this persisted.
    if (generation != baselineGeneration) {
      skillBaseline.reset();
      questBaseline.reset();
      diaryBaseline.reset();
      combatAchievementBaseline.reset();
      // A collection log belongs to one character; carrying it across an account
      // switch would attribute one account's items to another.
      collectionLog.reset();
      collectionLogBaseline.reset();
      baselineGeneration = generation;
      loadAckedState(accountHash, generation);
    }
    // Only send the log once the player has opted in; the accumulator may hold items
    // observed before they did, and an opt-in is not retroactive.
    Set<Integer> clog =
        isCollectionLogCaptureEnabled() ? collectionLog.observed() : Collections.emptySet();
    // Not read at all when the opt-in is off, rather than read and dropped later. An
    // opt-in is about what leaves the client, and the cheapest way to keep that true is
    // to never hold the numbers in the first place.
    Map<String, Integer> combatAchievements =
        isCombatAchievementCaptureEnabled()
            ? readCombatAchievementCounts()
            : Collections.emptyMap();
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
            combatAchievements);
    if (!plan.shouldSubmit()) {
      return;
    }
    // An omitted rider is dropped to the same null or empty the opt-in-off case already
    // uses, so submitSnapshot has exactly one notion of "was this block sent" and the
    // per-block acknowledgement keeps keying off what actually went on the wire.
    submitSnapshot(
        plan.includesCombatAchievements() ? combatAchievements : null,
        accountHash,
        generation,
        name,
        skills,
        plan.includesQuests() ? quests : null,
        plan.includesDiaries() ? diaries : null,
        plan.includesCollectionLog() ? clog : Collections.emptySet());
  }

  /**
   * Reads off the executor because the caller is the client thread, applies back on it
   * because the baselines live there. The {@code isCurrent} guard stops a slow read for
   * an account the player has already left landing on the one they are on now.
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
                combatAchievementBaseline.restore(state.getCombatAchievements());
                // Together, never one alone. CollectionLogBaseline.restore says why.
                collectionLog.restore(state.getCollectionLogItems());
                collectionLogBaseline.restore(state.getCollectionLogAcked());
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
    state.setCombatAchievements(combatAchievementBaseline.ackedDigest());
    state.setCollectionLogItems(collectionLog.observed());
    state.setCollectionLogAcked(collectionLogBaseline.ackedCount());
    executor.submit(() -> ackedStore.save(accountHash, state));
  }

  /**
   * Which blocks a capture puts on the wire.
   *
   * <p>Skills is not listed because it is not optional: the v1 envelope requires it, so
   * every submission carries it and the only question is whether to submit at all.
   */
  static final class SubmitPlan {
    private final boolean submit;
    private final boolean quests;
    private final boolean diaries;
    private final boolean collectionLog;
    private final boolean combatAchievements;

    private SubmitPlan(
        boolean submit,
        boolean quests,
        boolean diaries,
        boolean collectionLog,
        boolean combatAchievements) {
      this.submit = submit;
      this.quests = quests;
      this.diaries = diaries;
      this.collectionLog = collectionLog;
      this.combatAchievements = combatAchievements;
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

    boolean includesCombatAchievements() {
      return combatAchievements;
    }
  }

  /**
   * Decides what this capture sends, per capability rather than all or nothing.
   *
   * <p>The rule used to be "if anything changed, send everything", which meant a single
   * xp drop re-sent the entire collection log, around seventeen hundred ids, every
   * sixty seconds for as long as the player kept training. A capability now rides along
   * only when that capability changed since the server last acknowledged it.
   *
   * <p><b>Whole blocks only, never a delta within one.</b> The server merges capability
   * blocks with jsonb {@code ||}, a top-level replace, so a block carrying only its
   * changed fields would erase every field it left out. Capability-level granularity is
   * safe with that merge and field-level is silent data loss, which is why this decides
   * whether to include a block and never what to put in one. The envelope has no way to
   * express a partial block, so the unsafe granularity is unrepresentable rather than
   * merely discouraged.
   *
   * <p>Omitting an unchanged block does not weaken the per-block acknowledgement rule
   * that keeps an unstored block re-sending. A block the server never acknowledged has
   * no baseline to match, so it reads as changed and keeps going out until it is
   * stored.
   *
   * <p>An empty block is never sent whatever its baseline says: absent means "not
   * observed" on this wire, and an empty map would instead assert the player has none.
   * A null map is the opt-in being off, which never contributes and never sends.
   *
   * <p>Package-private and static so it is unit-testable with real baselines, without a
   * Client or a ConfigManager fake.
   */
  /** Without combat achievements, which every caller predating them omits. */
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
        null);
  }

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
    boolean sendQuests =
        quests != null && !quests.isEmpty() && questBaseline.changedSince(quests);
    boolean sendDiaries =
        diaries != null && !diaries.isEmpty() && diaryBaseline.changedSince(diaries);
    // Without the collection log counting toward the decision, a freshly synced log
    // would sit unsent until the player happened to gain xp, which is exactly when they
    // are least likely to be looking at it.
    boolean sendCollectionLog =
        !collectionLogItems.isEmpty()
            && collectionLogBaseline.changedSince(collectionLogItems.size());
    boolean sendCombatAchievements =
        combatAchievements != null
            && !combatAchievements.isEmpty()
            && combatAchievementBaseline.changedSince(combatAchievements);
    boolean submit =
        skillBaseline.changedSince(skills)
            || sendQuests
            || sendDiaries
            || sendCollectionLog
            || sendCombatAchievements;
    return new SubmitPlan(
        submit, sendQuests, sendDiaries, sendCollectionLog, sendCombatAchievements);
  }

  // Every baseline, skills included, advances only on the server's own per-block
  // acknowledgement.
  //
  // The obvious-looking alternative, "accepted and not on cooldown", does not work:
  // the server answers HTTP 200 with accepted=true for every outcome it recognises,
  // including stale, regression, unclaimed and not_applied, all of which store
  // nothing. Gating on that would advance a baseline for data the server discarded,
  // and the client would not resend until the value changed again on its own. That is
  // most visible before an account is bound (reason "unclaimed") or before a
  // capability's rollout flag is on (reason "not_applied"), which is exactly when a
  // first snapshot most needs to survive.
  //
  /**
   * Whether a read taken as the player logs out is worth submitting.
   *
   * <p>The read happens after the client has left the world, and whether skill XP is
   * still readable there cannot be verified outside a running client. So it is checked
   * rather than trusted: a cleared client reads as zeroes or as nothing, and submitting
   * that is an XP regression. Discarding costs nothing, because the next login re-reads
   * live state that already includes those minutes.
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

  // Package-private and static for the same reason as plan above.
  static boolean shouldAdvanceSkills(SubmitSnapshotResponse res) {
    return res.isBlockStored("skills");
  }

  // An optional capability block additionally checks that it was submitted at all, so a
  // cycle with the opt-in off never advances a baseline it did not send.
  //
  // The whole-submission stored verdict is no better a gate here than it is for skills:
  // it is decided by skills freshness, so it reads true even when the server dropped
  // this block because that capability's rollout flag is off. The consequence is worse
  // for a capability than for skills, though. Skill XP changes almost every cycle, so a
  // false acknowledgement resends itself; a diary tier completing is a one-shot fact, so
  // once falsely acknowledged the value never differs again and it is never resent, not
  // even after a relog. Gating on the per-block acknowledgement keeps an unstored block
  // re-sending every capture and self-heals the moment that capability's storage lands.
  static boolean shouldAdvanceQuests(SubmitSnapshotResponse res, boolean questsIncluded) {
    return questsIncluded && res.isBlockStored("quests");
  }

  static boolean shouldAdvanceDiaries(SubmitSnapshotResponse res, boolean diariesIncluded) {
    return diariesIncluded && res.isBlockStored("diaries");
  }

  static boolean shouldAdvanceCollectionLog(SubmitSnapshotResponse res, boolean included) {
    return included && res.isBlockStored("collectionLog");
  }

  // Per capability, like the rest. A whole-submission verdict cannot express that this
  // block was dropped by its own rollout flag while the submission still succeeded, so
  // gating on that would advance the baseline for data the server discarded and the
  // client would not resend until the counts changed on their own.
  static boolean shouldAdvanceCombatAchievements(
      SubmitSnapshotResponse res, boolean included) {
    return included && res.isBlockStored("combatAchievements");
  }

  private void submitSnapshot(
      Map<String, Integer> combatAchievementCounts,
      long accountHash,
      int generation,
      String name,
      Map<String, Integer> skills,
      Map<String, String> quests,
      Map<String, String> diaries,
      Set<Integer> collectionLogItems) {
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
            combatAchievementCounts);
    executor.submit(
        () -> {
          try {
            SubmitSnapshotResponse res =
                pairingClient.submitSnapshotWithRetry(
                    url, token, body, MAX_SUBMIT_ATTEMPTS, SUBMIT_RETRY_BASE_DELAY_MS);
            // Advance each baseline only for a block the server says it wrote, so an
            // unstored or throttled submit self-heals on the next capture without a
            // client-side queue.
            //
            // Advance on the client thread, and only if this submit's login instance is
            // still current, so a stale ack from a superseded account cannot clobber the
            // current account's baseline (the same guard the notice below uses).
            clientThread.invoke(
                () -> {
                  if (session.isCurrent(accountHash, generation)) {
                    if (shouldAdvanceSkills(res)) {
                      skillBaseline.advance(skills);
                    }
                    if (shouldAdvanceQuests(res, quests != null)) {
                      questBaseline.advance(quests);
                    }
                    if (shouldAdvanceDiaries(res, diaries != null)) {
                      diaryBaseline.advance(diaries);
                    }
                    if (shouldAdvanceCollectionLog(res, !collectionLogItems.isEmpty())) {
                      collectionLogBaseline.advance(collectionLogItems.size());
                    }
                    if (shouldAdvanceCombatAchievements(
                        res, combatAchievementCounts != null)) {
                      combatAchievementBaseline.advance(combatAchievementCounts);
                    }
                    // Unconditional, not only when a baseline advanced: passive browsing
                    // can have grown the accumulator even on a submit that stored nothing.
                    saveAckedState(accountHash);
                  }
                });
            // A reached server is a success for notice purposes even when it stored
            // nothing: "stale" or "not_applied" is the server working as intended, and
            // is not something the player can act on. Only announce the recovery, so a
            // healthy client stays silent rather than narrating every 60 seconds.
            submitGate.onSuccess();
            lastSubmitAtMs = System.currentTimeMillis();
            // Which blocks the server actually wrote, which is the one thing a
            // report cannot tell us and the baselines key off. Never the body: it
            // carries the account hash, the display name and the whole capture.
            log.debug(
                "submit {}: reason={} storedBlocks=[{}]",
                res.isStored() ? "stored" : "accepted",
                res.getReason(),
                String.join(",", res.getStoredBlocks()));
            if (session.isCurrent(accountHash, generation) && noticeGate.onSuccess()) {
              notice("Reconnected. Your progress is syncing again.");
            }
          } catch (SubmitException e) {
            // Do not advance the baseline: the change is unsent, retry next cycle. The
            // token and account hash are never logged.
            if (e.isAuthFailure()) {
              // Retrying cannot fix a revoked token, so stop rather than fail every
              // capture in silence. Announced by the gate, not NoticeGate, because this
              // is the one failure the player has to act on.
              log.debug("submit refused: token rejected, halting until re-paired");
              if (submitGate.onAuthFailure()) {
                notice("This client is no longer paired. Re-pair from your Bankstand account.");
              }
              return;
            }
            // The message, never the body: a body carries the account hash, the
            // display name and the whole capture.
            log.debug("submit failed: {}", e.getMessage());
            submitGate.onFailure();
            if (session.isCurrent(accountHash, generation) && noticeGate.onFailure(e.getMessage())) {
              notice("Could not sync your progress. " + e.getMessage());
            }
          }
        });
  }

  private boolean isPaired() {
    String token = deviceStore.load().getToken();
    return token != null && !token.trim().isEmpty();
  }

  // Both default to false on the config item, so an unset key (never opted in) reads
  // as off. Quest and diary state are more sensitive than hiscore stats, so the
  // capture path must check these before reading or sending either.
  //
  // **Every gate is an intersection of two things, and they are not the same thing.**
  // `config` is the player's consent and is the only one that can say yes. The manifest
  // is the server saying it will actually ingest that block, and can only ever narrow the
  // answer. So a capability the player switched off is never sent whatever the server
  // asks for, and a capability the server has stopped ingesting stops being uploaded
  // without waiting for a plugin release, which is the whole reason the manifest exists.
  private boolean isQuestCaptureEnabled() {
    return config.collectQuests() && manifest.allows("quests");
  }

  private boolean isDiaryCaptureEnabled() {
    return config.collectDiaries() && manifest.allows("diaries");
  }

  // Unlike the three opt-ins this defaults to ON, because skill XP is already public
  // on the hiscores and it carries the account hash and display name that identify the
  // character. With it off a paired client has nothing to bind with, so the pairing
  // does nothing. It exists as an option anyway for two reasons: the Plugin Hub wants
  // the warning about what is sent to sit on the option that enables the sending, and
  // a player who wants a paired client to go quiet should not have to unpair to do it.
  private boolean isSkillCaptureEnabled() {
    return config.collectSkills() && manifest.allows("skills");
  }

  /**
   * Pulls the manifest in the background and caches it if it validates.
   *
   * <p>Every failure is silent and leaves the current manifest in place. The player can
   * do nothing about a manifest fetch, so telling them about one would be noise in a chat
   * box; the active manifest is in {@code ::bstand} for when it matters.
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
          try {
            SubmitResponse res =
                pairingClient.submitIdentityWithRetry(
                    url,
                    token,
                    accountHash,
                    displayName,
                    MAX_SUBMIT_ATTEMPTS,
                    SUBMIT_RETRY_BASE_DELAY_MS);
            // Drop the result unless this login instance is still current. Guards two
            // cases opened by the multi-second retry backoff: a relog to a different
            // character, and a logout/relog to the SAME account (a fresh generation),
            // so the player never reads a status from a superseded submit.
            if (session.isCurrent(accountHash, generation)) {
              notice(
                  res.isVerified()
                      ? "Verified as " + res.getLinkedRsn() + "."
                      : "This character is not claimed on Bankstand yet, so nothing will sync."
                          + " Claim it on your account page.");
            }
          } catch (SubmitException e) {
            // A transient failure has already been retried to the cap; surface the
            // reason rather than failing silently. A relog retries afresh. The token
            // and account hash are never logged.
            if (session.isCurrent(accountHash, generation)) {
              notice("Could not verify this character. " + e.getMessage());
            }
          }
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
            // A fresh pairing is a clean slate: an outstanding failure against the old
            // credentials must not suppress the first notice against the new ones, and
            // re-pairing is the only thing that lifts a halt for a revoked token.
            noticeGate.onSuccess();
            submitGate.resume();
            notice("Connected. Your progress will sync from now on.");
          } catch (PairingException e) {
            // The message is generic and safe; the raw code and token are never logged.
            notice("Pairing failed. " + e.getMessage());
          } finally {
            // Always clear the code, successful or not: it is single-use either way,
            // and leaving a dead code in a settings field invites re-submitting it.
            clearPairingCode();
          }
        });
  }

  private void disconnect() {
    deviceStore.clear();
    // Unset the legacy config keys too. A profile that synced them still holds the
    // token upstream, and unsetting is what PATCHes the removal.
    forgetLegacyCredentialKeys();
    // Untick the toggle so it reads as an action taken rather than a state entered,
    // and so ticking it again later fires this handler afresh.
    configManager.setConfiguration(
        BankstandKeys.GROUP, BankstandKeys.KEY_DISCONNECT, Boolean.FALSE.toString());
    notice("Disconnected. Nothing will be sent until you pair again.");
  }

  private void clearPairingCode() {
    configManager.setConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_PAIRING_CODE, "");
  }

  /**
   * Says something to the player in the chat box.
   *
   * <p>This is the whole replacement for the side panel's status line, so it has to
   * hold every outcome the panel used to show. Logged out there is no chat box to
   * write to, and the message is dropped rather than queued: an outcome is only worth
   * reporting while the player is there to read it, and the capture cycle only runs
   * while logged in anyway.
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
   * Builds one chat line: the brand-coloured "Bankstand: " prefix, then the message in
   * the chat's own default colour.
   *
   * <p>Only the prefix is coloured. The body is what the player has to actually read,
   * and it is most legible in whatever colour their chat is already using, whichever
   * chat mode and transparency they run.
   *
   * <p>{@code addChatMessage}'s name argument is not rendered for a CONSOLE message,
   * which is why the prefix is part of the text rather than passed as the sender.
   */
  static String brandedNotice(String message) {
    return new ChatMessageBuilder().append(BRAND, NOTICE_PREFIX).append(message).build();
  }

  /**
   * Moves a pairing made before credentials left {@code ConfigManager}.
   *
   * <p>Runs once per start and is a no-op after the first. Without it, everyone paired
   * before this change silently stops submitting and has to work out why; with it, the
   * pairing survives and the copy RuneLite holds is removed.
   *
   * <p><b>The unset is the half that matters.</b> Writing the file only stops the token
   * being uploaded again. Unsetting the config keys is what PATCHes the deletion to
   * RuneLite's config service on a synced profile, so the credential stops being stored
   * by a third party rather than merely stopping being sent to one.
   *
   * <p>The file wins if both exist. A file means this already ran, and a stale config
   * value from another machine's sync must not overwrite a newer local pairing.
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

  // Read through the config item so an unset key falls back to the same default the
  // settings screen shows, then normalised so a blank or padded value cannot reach
  // the socket. A player who clears the field gets prod back, not a failed request.
  private String savedServerUrl() {
    return BankstandKeys.normaliseServerUrl(config.serverBaseUrl());
  }
}
