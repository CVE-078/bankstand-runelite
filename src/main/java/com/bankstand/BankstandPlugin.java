package com.bankstand;

import com.bankstand.dto.PairResponse;
import com.bankstand.dto.SubmitResponse;
import com.bankstand.http.HttpTransport;
import com.bankstand.http.OkHttpTransport;
import com.bankstand.session.AccountSession;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@PluginDescriptor(
    name = "Bankstand",
    description = "Pair this client with your Bankstand account for client-verified identity.",
    tags = {"bankstand", "account", "progress", "external"})
public class BankstandPlugin extends Plugin {

  @Inject private Client client;
  @Inject private ClientToolbar clientToolbar;
  @Inject private ConfigManager configManager;
  @Inject private OkHttpClient okHttpClient;
  @Inject private Gson gson;
  @Inject private ScheduledExecutorService executor;

  private final AccountSession session = new AccountSession();
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
    // One attempt per account per session; a relog retries.
    session.markSubmitted();
    submitIdentity(session.getAccountHash(), name);
  }

  private boolean isPaired() {
    String token =
        configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_TOKEN);
    return token != null && !token.trim().isEmpty();
  }

  private void submitIdentity(long accountHash, String displayName) {
    String url = savedServerUrl();
    String token =
        configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_TOKEN);
    executor.submit(
        () -> {
          try {
            SubmitResponse res =
                pairingClient.submitIdentity(url, token, accountHash, displayName);
            if (panel != null) {
              panel.showVerification(res.isVerified(), res.getLinkedRsn());
            }
          } catch (SubmitException e) {
            // Transient or a revoked token: leave the connected status as-is; a relog
            // retries. The token and account hash are never logged.
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
  }

  private static BufferedImage createIcon() {
    // The Bankstand mark, bundled as src/main/resources/com/bankstand/icon.png.
    return ImageUtil.loadImageResource(BankstandPlugin.class, "icon.png");
  }
}
