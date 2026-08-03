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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import okhttp3.OkHttpClient;

@PluginDescriptor(
    name = "Bankstand",
    description = "Pair this client with your Bankstand account for client-verified identity.",
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
  // and 7797 fires when the interface finishes building. Searching the log is what
  // makes it enumerate everything rather than one page, which is why the menu entry
  // below triggers the log's own Search rather than trying to walk pages.
  private static final int COLLECTION_LOG_ITEM_SCRIPT = 4100;
  private static final int COLLECTION_LOG_SETUP_SCRIPT = 7797;
  private static final int COLLECTION_LOG_SEARCH_SCRIPT = 2240;
  private static final String SYNC_MENU_OPTION = "Sync to Bankstand";

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

  private final AccountSession session = new AccountSession();
  private final SkillBaseline skillBaseline = new SkillBaseline();
  private final QuestBaseline questBaseline = new QuestBaseline();
  private final DiaryBaseline diaryBaseline = new DiaryBaseline();
  private final CollectionLogAccumulator collectionLog = new CollectionLogAccumulator();
  // Whether the collection log interface is currently built, so the menu entry is
  // only offered where it means something.
  private boolean collectionLogOpen;
  // Suppresses a recurring failure from printing every capture cycle.
  private final NoticeGate noticeGate = new NoticeGate();
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
  }

  @Override
  protected void shutDown() {
    pairingClient = null;
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
    if (event.getScriptId() != COLLECTION_LOG_ITEM_SCRIPT || !isCollectionLogSharingEnabled()) {
      return;
    }
    Object[] args = event.getScriptEvent() == null ? null : event.getScriptEvent().getArguments();
    // args[1] is the item id. Guarded rather than assumed: this is an internal game
    // script and its shape is not a contract we control.
    if (args == null || args.length < 2 || !(args[1] instanceof Integer)) {
      return;
    }
    collectionLog.observe((Integer) args[1]);
  }

  @Subscribe
  public void onScriptPostFired(ScriptPostFired event) {
    if (event.getScriptId() == COLLECTION_LOG_SETUP_SCRIPT) {
      collectionLogOpen = true;
    }
  }

  /**
   * Offers the sync action as a menu entry rather than a drawn button.
   *
   * <p>A button would have to be positioned by hand against the log's own controls,
   * which collides with any other plugin doing the same (WikiSync draws one there
   * already) and breaks whenever the interface is reshuffled. A menu entry cannot
   * collide, and costs a fraction of the code.
   */
  @Subscribe
  public void onMenuOpened(MenuOpened event) {
    if (!collectionLogOpen || !isCollectionLogSharingEnabled() || !isPaired()) {
      return;
    }
    client
        .createMenuEntry(-1)
        .setOption(SYNC_MENU_OPTION)
        .setTarget("")
        .setType(MenuAction.RUNELITE)
        .onClick(e -> syncCollectionLog());
  }

  /**
   * Makes the log enumerate everything by triggering its own Search, which is the only
   * way to see the whole log without the player walking every page.
   */
  private void syncCollectionLog() {
    clientThread.invoke(
        () -> {
          client.menuAction(
              -1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
          client.runScript(COLLECTION_LOG_SEARCH_SCRIPT);
        });
    notice("Reading your collection log...");
  }

  private boolean isCollectionLogSharingEnabled() {
    return config.shareCollectionLog();
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged event) {
    GameState state = event.getGameState();
    if (state == GameState.LOGGED_IN) {
      // Adopts the account only if it changed; the -1 logged-out sentinel is ignored.
      session.onLogin(client.getAccountHash());
    } else if (state == GameState.LOGIN_SCREEN) {
      session.onLogout();
      collectionLogOpen = false;
    }
  }

  @Subscribe
  public void onGameTick(GameTick event) {
    // Submit the logged-in character's identity once per session, when paired. The
    // local player (and its name) is reliably available a tick after LOGGED_IN, which
    // is why this runs on the tick rather than directly on the state change.
    if (pairingClient == null || !isPaired() || !session.isActive() || session.isSubmitted()) {
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

  @Schedule(period = CAPTURE_INTERVAL_SECONDS, unit = ChronoUnit.SECONDS)
  public void captureSkills() {
    if (pairingClient == null || !isPaired() || !session.isActive()) {
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
    clientThread.invoke(
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
          // Quest and diary state are opt-in; read them in this same block so they are
          // one consistent snapshot with the skills, and leave each null (never sent)
          // when off.
          Map<String, String> quests = isQuestSharingEnabled() ? readQuestStates() : null;
          Map<String, String> diaries = isDiarySharingEnabled() ? readDiaryStates() : null;
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

  private void onSkillsCaptured(
      long accountHash,
      int generation,
      String name,
      Map<String, Integer> skills,
      Map<String, String> quests,
      Map<String, String> diaries) {
    // A change of account forgets every baseline so the new account submits afresh.
    if (generation != baselineGeneration) {
      skillBaseline.reset();
      questBaseline.reset();
      diaryBaseline.reset();
      // A collection log belongs to one character; carrying it across an account
      // switch would attribute one account's items to another.
      collectionLog.reset();
      baselineGeneration = generation;
    }
    if (!shouldSubmit(skillBaseline, skills, questBaseline, quests, diaryBaseline, diaries)) {
      return;
    }
    submitSnapshot(accountHash, generation, name, skills, quests, diaries);
  }

  // A quest or diary change alone is enough to submit; a null map (the opt-in is off)
  // never contributes. Package-private and static so it is unit-testable with real
  // SkillBaseline/QuestBaseline/DiaryBaseline instances, without a Client or
  // ConfigManager fake.
  static boolean shouldSubmit(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries) {
    return skillBaseline.changedSince(skills)
        || (quests != null && questBaseline.changedSince(quests))
        || (diaries != null && diaryBaseline.changedSince(diaries));
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
  // Package-private and static for the same reason as shouldSubmit above.
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

  private void submitSnapshot(
      long accountHash,
      int generation,
      String name,
      Map<String, Integer> skills,
      Map<String, String> quests,
      Map<String, String> diaries) {
    String url = savedServerUrl();
    String token =
        configManager.getConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_TOKEN);
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
            diaries);
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
                  }
                });
            // A reached server is a success for notice purposes even when it stored
            // nothing: "stale" or "not_applied" is the server working as intended, and
            // is not something the player can act on. Only announce the recovery, so a
            // healthy client stays silent rather than narrating every 60 seconds.
            if (session.isCurrent(accountHash, generation) && noticeGate.onSuccess()) {
              notice("Reconnected. Your progress is syncing again.");
            }
          } catch (SubmitException e) {
            // Do not advance the baseline: the change is unsent, retry next cycle. The
            // token and account hash are never logged.
            if (session.isCurrent(accountHash, generation) && noticeGate.onFailure(e.getMessage())) {
              notice("Could not sync your progress. " + e.getMessage());
            }
          }
        });
  }

  private boolean isPaired() {
    String token =
        configManager.getConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_TOKEN);
    return token != null && !token.trim().isEmpty();
  }

  // Both default to false on the config item, so an unset key (never opted in) reads
  // as off. Quest and diary state are more sensitive than hiscore stats, so the
  // capture path must check these before reading or sending either.
  private boolean isQuestSharingEnabled() {
    return config.shareQuests();
  }

  private boolean isDiarySharingEnabled() {
    return config.shareDiaries();
  }

  private void submitIdentity(long accountHash, int generation, String displayName) {
    String url = savedServerUrl();
    String token =
        configManager.getConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_TOKEN);
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
            // credentials must not suppress the first notice against the new ones.
            noticeGate.onSuccess();
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
    configManager.unsetConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_TOKEN);
    configManager.unsetConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_ID);
    configManager.unsetConfiguration(BankstandKeys.GROUP, BankstandKeys.KEY_TOKEN_EXPIRES_AT);
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

  private void storeToken(PairResponse res) {
    configManager.setConfiguration(
        BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_TOKEN, res.getDeviceToken());
    if (res.getDeviceId() != null) {
      configManager.setConfiguration(
          BankstandKeys.GROUP, BankstandKeys.KEY_DEVICE_ID, res.getDeviceId());
    }
    if (res.getExpiresAt() != null) {
      configManager.setConfiguration(
          BankstandKeys.GROUP, BankstandKeys.KEY_TOKEN_EXPIRES_AT, res.getExpiresAt());
    }
  }

  // Read through the config item so an unset key falls back to the same default the
  // settings screen shows, then normalised so a blank or padded value cannot reach
  // the socket. A player who clears the field gets prod back, not a failed request.
  private String savedServerUrl() {
    return BankstandKeys.normaliseServerUrl(config.serverBaseUrl());
  }
}
