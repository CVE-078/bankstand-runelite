package com.bankstand;

import com.bankstand.dto.PairResponse;
import com.bankstand.dto.SubmitResponse;
import com.bankstand.dto.SubmitSnapshotResponse;
import com.bankstand.http.HttpTransport;
import com.bankstand.http.OkHttpTransport;
import com.bankstand.session.AccountSession;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
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
  @Inject private ClientToolbar clientToolbar;
  @Inject private ConfigManager configManager;
  @Inject private OkHttpClient okHttpClient;
  @Inject private Gson gson;
  @Inject private ScheduledExecutorService executor;
  @Inject private ClientThread clientThread;

  private final AccountSession session = new AccountSession();
  private final SkillBaseline skillBaseline = new SkillBaseline();
  private final QuestBaseline questBaseline = new QuestBaseline();
  private final DiaryBaseline diaryBaseline = new DiaryBaseline();
  // The generation the baseline currently tracks; a change means the account switched
  // and the baseline must be forgotten so the new account submits afresh.
  private int baselineGeneration = -1;
  private BankstandClient pairingClient;
  private BankstandPanel panel;
  private NavigationButton navButton;

  @Override
  protected void startUp() {
    HttpTransport transport = new OkHttpTransport(okHttpClient);
    pairingClient = new BankstandClient(transport, gson);
    panel =
        new BankstandPanel(
            savedServerUrl(),
            new BankstandPanel.Listener() {
              @Override
              public void onPair(String serverUrl, String code) {
                pair(serverUrl, code);
              }

              @Override
              public void onDisconnect() {
                disconnect();
              }

              @Override
              public void onShareQuestsChanged(boolean enabled) {
                setQuestSharingEnabled(enabled);
              }

              @Override
              public void onShareDiariesChanged(boolean enabled) {
                setDiarySharingEnabled(enabled);
              }
            });

    navButton =
        NavigationButton.builder()
            .tooltip("Bankstand")
            .icon(createIcon())
            .priority(7)
            .panel(panel)
            .build();
    clientToolbar.addNavigation(navButton);
    refreshPanelState();
  }

  @Override
  protected void shutDown() {
    if (navButton != null) {
      clientToolbar.removeNavigation(navButton);
    }
    panel = null;
    navButton = null;
    pairingClient = null;
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged event) {
    GameState state = event.getGameState();
    if (state == GameState.LOGGED_IN) {
      // Adopts the account only if it changed; the -1 logged-out sentinel is ignored.
      session.onLogin(client.getAccountHash());
    } else if (state == GameState.LOGIN_SCREEN) {
      session.onLogout();
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

  // A cooldown means the server rejected this cycle's change for pacing, not because
  // it was applied; the caller should retry the same change next cycle rather than
  // treat it as acknowledged. Package-private and static for the same reason as
  // shouldSubmit above.
  static boolean isStoredAccept(SubmitSnapshotResponse res) {
    return res.isAccepted() && !"cooldown".equals(res.getReason());
  }

  // Skills gate on accept because the server always stores an accepted skills update.
  // Quests are different: until bankstand PR #407 and PLUGIN_QUESTS_INGEST_ENABLED are
  // both live, the server accepts the submission but silently strips the unknown
  // quests key, so isStoredAccept alone would mark quest state as acknowledged when it
  // was never persisted, and that first quest snapshot would not be resent until a
  // relog. Gating on res.isStored() instead keeps questBaseline from advancing until
  // the server confirms this submission was actually stored, so an un-stored quests
  // submission keeps re-sending every capture and self-heals the moment storage lands.
  static boolean shouldAdvanceQuests(SubmitSnapshotResponse res, boolean questsIncluded) {
    return questsIncluded && res.isStored();
  }

  // Diaries advance on the same res.isStored() gate as quests, and for the same
  // reason: until the server's diaries capability flag is live, an accepted
  // submission can silently strip the diaries key, and advancing on accept alone
  // would mark that first diary snapshot as acknowledged when it was never
  // persisted, losing it until a relog.
  static boolean shouldAdvanceDiaries(SubmitSnapshotResponse res, boolean diariesIncluded) {
    return diariesIncluded && res.isStored();
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
        configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_TOKEN);
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
            // Advance the baseline(s) when the server accepted and was not rate-limiting
            // us; a cooldown means try the same change again next cycle. This makes a
            // dropped or throttled submit self-heal without a client-side queue.
            if (isStoredAccept(res)) {
              // Advance on the client thread, and only if this submit's login instance is
              // still current, so a stale ack from a superseded account cannot clobber the
              // current account's baseline (the same guard the panel update below uses).
              clientThread.invoke(
                  () -> {
                    if (session.isCurrent(accountHash, generation)) {
                      skillBaseline.advance(skills);
                      if (shouldAdvanceQuests(res, quests != null)) {
                        questBaseline.advance(quests);
                      }
                      if (shouldAdvanceDiaries(res, diaries != null)) {
                        diaryBaseline.advance(diaries);
                      }
                    }
                  });
            }
            if (panel != null && session.isCurrent(accountHash, generation)) {
              panel.showSnapshotOutcome(res.isStored(), res.getReason());
            }
          } catch (SubmitException e) {
            // Do not advance the baseline: the change is unsent, retry next cycle. The
            // token and account hash are never logged.
            if (panel != null && session.isCurrent(accountHash, generation)) {
              panel.showSubmitFailed(e.getMessage());
            }
          }
        });
  }

  private boolean isPaired() {
    String token =
        configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_TOKEN);
    return token != null && !token.trim().isEmpty();
  }

  // Null-safe: an unset key (never opted in) reads as false. Quest state is more
  // sensitive than hiscore stats, so the capture path must check this before reading
  // or sending it.
  private boolean isQuestSharingEnabled() {
    return Boolean.parseBoolean(
        configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_SHARE_QUESTS));
  }

  private void setQuestSharingEnabled(boolean enabled) {
    configManager.setConfiguration(
        BankstandConfig.GROUP, BankstandConfig.KEY_SHARE_QUESTS, String.valueOf(enabled));
  }

  // Null-safe: an unset key (never opted in) reads as false. Diary state is more
  // sensitive than hiscore stats, so the capture path must check this before reading
  // or sending it.
  private boolean isDiarySharingEnabled() {
    return Boolean.parseBoolean(
        configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_SHARE_DIARIES));
  }

  private void setDiarySharingEnabled(boolean enabled) {
    configManager.setConfiguration(
        BankstandConfig.GROUP, BankstandConfig.KEY_SHARE_DIARIES, String.valueOf(enabled));
  }

  private void submitIdentity(long accountHash, int generation, String displayName) {
    String url = savedServerUrl();
    String token =
        configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_TOKEN);
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
            // so the panel never shows a status from a superseded submit.
            if (panel != null && session.isCurrent(accountHash, generation)) {
              panel.showVerification(res.isVerified(), res.getLinkedRsn());
            }
          } catch (SubmitException e) {
            // A transient failure has already been retried to the cap; surface the
            // reason rather than failing silently. A relog retries afresh. The token
            // and account hash are never logged.
            if (panel != null && session.isCurrent(accountHash, generation)) {
              panel.showSubmitFailed(e.getMessage());
            }
          }
        });
  }

  private void pair(String serverUrl, String rawCode) {
    String url =
        serverUrl == null || serverUrl.trim().isEmpty()
            ? BankstandConfig.DEFAULT_SERVER_URL
            : serverUrl.trim();
    // Persist the URL so it survives a restart and the panel reopens with it.
    configManager.setConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_SERVER_URL, url);
    if (panel != null) {
      panel.showBusy();
    }
    executor.submit(
        () -> {
          try {
            PairResponse res = pairingClient.exchangePairingCode(url, rawCode);
            storeToken(res);
            if (panel != null) {
              panel.showConnected(res.getDeviceId(), res.getExpiresAt());
            }
          } catch (PairingException e) {
            // The message is generic and safe; the raw code and token are never logged.
            if (panel != null) {
              panel.showError(e.getMessage());
            }
          }
        });
  }

  private void disconnect() {
    configManager.unsetConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_TOKEN);
    configManager.unsetConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_ID);
    configManager.unsetConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_TOKEN_EXPIRES_AT);
    if (panel != null) {
      panel.showDisconnected();
    }
  }

  private void storeToken(PairResponse res) {
    configManager.setConfiguration(
        BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_TOKEN, res.getDeviceToken());
    if (res.getDeviceId() != null) {
      configManager.setConfiguration(
          BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_ID, res.getDeviceId());
    }
    if (res.getExpiresAt() != null) {
      configManager.setConfiguration(
          BankstandConfig.GROUP, BankstandConfig.KEY_TOKEN_EXPIRES_AT, res.getExpiresAt());
    }
  }

  private String savedServerUrl() {
    String url = configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_SERVER_URL);
    return url != null && !url.trim().isEmpty() ? url : BankstandConfig.DEFAULT_SERVER_URL;
  }

  private void refreshPanelState() {
    String token =
        configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_TOKEN);
    if (token != null && !token.trim().isEmpty()) {
      panel.showConnected(
          configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_ID),
          configManager.getConfiguration(
              BankstandConfig.GROUP, BankstandConfig.KEY_TOKEN_EXPIRES_AT));
    } else {
      panel.showDisconnected();
    }
    panel.setShareQuestsEnabled(isQuestSharingEnabled());
    panel.setShareDiariesEnabled(isDiarySharingEnabled());
  }

  private static BufferedImage createIcon() {
    // The Bankstand mark, bundled as src/main/resources/com/bankstand/icon.png.
    return ImageUtil.loadImageResource(BankstandPlugin.class, "icon.png");
  }
}
