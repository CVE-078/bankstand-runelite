package com.bankstand;

import com.bankstand.dto.PairResponse;
import com.bankstand.http.HttpTransport;
import com.bankstand.http.OkHttpTransport;
import com.bankstand.session.AccountSession;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;

@PluginDescriptor(
    name = "Bankstand",
    description = "Pair this client with your Bankstand account for client-verified identity.",
    tags = {"bankstand", "account", "progress", "external"})
public class BankstandPlugin extends Plugin {

  @Inject private Client client;
  @Inject private ClientToolbar clientToolbar;
  @Inject private ConfigManager configManager;
  @Inject private BankstandConfig config;
  @Inject private OkHttpClient okHttpClient;
  @Inject private Gson gson;
  @Inject private ScheduledExecutorService executor;

  private final AccountSession session = new AccountSession();
  private BankstandClient pairingClient;
  private BankstandPanel panel;
  private NavigationButton navButton;

  @Provides
  BankstandConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(BankstandConfig.class);
  }

  @Override
  protected void startUp() {
    HttpTransport transport = new OkHttpTransport(okHttpClient);
    pairingClient = new BankstandClient(transport, gson);
    panel = new BankstandPanel(this::pair, this::disconnect);

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

  private void pair(String rawCode) {
    if (panel != null) {
      panel.showBusy();
    }
    executor.submit(
        () -> {
          try {
            PairResponse res =
                pairingClient.exchangePairingCode(config.serverBaseUrl(), rawCode);
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
    BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(0xC8, 0xA2, 0x3C));
    g.fillRect(4, 4, 16, 16);
    g.dispose();
    return image;
  }
}
