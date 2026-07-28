package com.bankstand;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: the whole pairing flow in one place. Paste the code and Pair;
 * the server URL lives under a collapsed "Advanced" toggle (defaults to prod, so
 * only a local tester ever opens it) rather than in a separate config screen. All
 * Swing mutations run on the EDT; the plugin drives the connected/error/disconnected
 * transitions from a background thread via the show* methods, each of which marshals
 * back onto the EDT.
 */
class BankstandPanel extends PluginPanel {

  /** Callbacks into the plugin. */
  interface Listener {
    void onPair(String serverUrl, String code);

    void onDisconnect();

    void onShareQuestsChanged(boolean enabled);

    void onShareDiariesChanged(boolean enabled);
  }

  private final JTextField codeField = new JTextField();
  private final JTextField urlField = new JTextField();
  private final JButton pairButton = new JButton("Pair");
  private final JButton disconnectButton = new JButton("Disconnect");
  private final JButton advancedToggle = new JButton("Advanced");
  private final JPanel advancedPanel = new JPanel();
  private final JLabel statusLabel = new JLabel();
  private final JPanel formPanel = new JPanel();
  private final JCheckBox shareQuestsCheckbox = new JCheckBox("Share quest progress");
  private final JPanel questSharingPanel = new JPanel();
  private final JCheckBox shareDiariesCheckbox = new JCheckBox("Share achievement diary progress");
  private final JPanel diarySharingPanel = new JPanel();

  BankstandPanel(String initialServerUrl, Listener listener) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));

    JLabel title = new JLabel("Bankstand");
    title.setForeground(ColorScheme.BRAND_ORANGE);
    title.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel hint =
        new JLabel(
            "<html>Generate a pairing code at Bankstand &gt; Account &gt; Connect RuneLite,"
                + " then paste it here.</html>");
    hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    hint.setAlignmentX(Component.LEFT_ALIGNMENT);
    hint.setBorder(BorderFactory.createEmptyBorder(6, 0, 10, 0));

    JLabel codeLabel = fieldLabel("Pairing code");
    capHeight(codeField);
    codeField.setAlignmentX(Component.LEFT_ALIGNMENT);
    codeField.setToolTipText("XXXX-XXXX");

    pairButton.setAlignmentX(Component.LEFT_ALIGNMENT);
    pairButton.addActionListener(
        e -> {
          pairButton.setEnabled(false);
          listener.onPair(urlField.getText(), codeField.getText());
        });

    // Advanced: the server URL, collapsed by default. Normal users never open it.
    urlField.setText(initialServerUrl);
    capHeight(urlField);
    urlField.setAlignmentX(Component.LEFT_ALIGNMENT);
    urlField.setToolTipText("Bankstand server URL");
    advancedPanel.setLayout(new BoxLayout(advancedPanel, BoxLayout.Y_AXIS));
    advancedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    advancedPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    JLabel urlLabel = fieldLabel("Server URL");
    advancedPanel.add(urlLabel);
    advancedPanel.add(urlField);
    advancedPanel.setVisible(false);

    advancedToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
    advancedToggle.setHorizontalAlignment(SwingConstants.LEFT);
    advancedToggle.setBorderPainted(false);
    advancedToggle.setContentAreaFilled(false);
    advancedToggle.setFocusPainted(false);
    advancedToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    advancedToggle.addActionListener(
        e -> {
          advancedPanel.setVisible(!advancedPanel.isVisible());
          revalidate();
          repaint();
        });

    formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
    formPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    formPanel.add(codeLabel);
    formPanel.add(codeField);
    formPanel.add(Box.createVerticalStrut(8));
    formPanel.add(pairButton);
    formPanel.add(Box.createVerticalStrut(6));
    formPanel.add(advancedToggle);
    formPanel.add(advancedPanel);

    disconnectButton.setAlignmentX(Component.LEFT_ALIGNMENT);
    disconnectButton.addActionListener(e -> listener.onDisconnect());

    statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 8, 0));

    // Quest progress is more sensitive than hiscore stats (which sync unconditionally),
    // so it is opt-in, default off, and only ever offered once paired.
    shareQuestsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
    shareQuestsCheckbox.addActionListener(
        e -> listener.onShareQuestsChanged(shareQuestsCheckbox.isSelected()));

    JLabel shareQuestsDisclosure =
        new JLabel(
            "<html>Sends which quests you've started and finished so you can see them on your"
                + " guides. Only you can see it, it's never public. This is more personal than"
                + " your hiscore stats.</html>");
    shareQuestsDisclosure.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    shareQuestsDisclosure.setAlignmentX(Component.LEFT_ALIGNMENT);
    shareQuestsDisclosure.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

    questSharingPanel.setLayout(new BoxLayout(questSharingPanel, BoxLayout.Y_AXIS));
    questSharingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    questSharingPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    questSharingPanel.add(shareQuestsCheckbox);
    questSharingPanel.add(shareQuestsDisclosure);
    // Hidden until connected; showConnected/showDisconnected toggle it like disconnectButton.
    questSharingPanel.setVisible(false);

    // Achievement diary progress is opt-in for the same reason as quest progress above.
    shareDiariesCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
    shareDiariesCheckbox.addActionListener(
        e -> listener.onShareDiariesChanged(shareDiariesCheckbox.isSelected()));

    JLabel shareDiariesDisclosure =
        new JLabel(
            "<html>Sends which achievement diary tiers you've completed so you can see them on"
                + " your guides. Only you can see it, it's never public. This is more personal"
                + " than your hiscore stats.</html>");
    shareDiariesDisclosure.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    shareDiariesDisclosure.setAlignmentX(Component.LEFT_ALIGNMENT);
    shareDiariesDisclosure.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

    diarySharingPanel.setLayout(new BoxLayout(diarySharingPanel, BoxLayout.Y_AXIS));
    diarySharingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    diarySharingPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    diarySharingPanel.add(shareDiariesCheckbox);
    diarySharingPanel.add(shareDiariesDisclosure);
    // Hidden until connected; showConnected/showDisconnected toggle it like disconnectButton.
    diarySharingPanel.setVisible(false);

    add(title);
    add(hint);
    add(formPanel);
    add(statusLabel);
    add(questSharingPanel);
    add(diarySharingPanel);
    add(disconnectButton);

    showDisconnected();
  }

  private static JLabel fieldLabel(String text) {
    JLabel label = new JLabel(text);
    label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    return label;
  }

  // Stop a single-line field from stretching to fill the vertical BoxLayout.
  private static void capHeight(JTextField field) {
    field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
  }

  void showBusy() {
    SwingUtilities.invokeLater(
        () -> {
          pairButton.setEnabled(false);
          statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
          statusLabel.setText("Pairing...");
        });
  }

  void showConnected(String deviceId, String expiresAt) {
    SwingUtilities.invokeLater(
        () -> {
          formPanel.setVisible(false);
          disconnectButton.setVisible(true);
          questSharingPanel.setVisible(true);
          diarySharingPanel.setVisible(true);
          codeField.setText("");
          pairButton.setEnabled(true);
          statusLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
          StringBuilder text = new StringBuilder("<html>Connected.");
          if (deviceId != null && !deviceId.isEmpty()) {
            text.append("<br>Device ").append(escape(deviceId));
          }
          if (expiresAt != null && !expiresAt.isEmpty()) {
            text.append("<br>Expires ").append(escape(expiresAt));
          }
          statusLabel.setText(text.append("</html>").toString());
          revalidate();
          repaint();
        });
  }

  void showDisconnected() {
    SwingUtilities.invokeLater(
        () -> {
          formPanel.setVisible(true);
          disconnectButton.setVisible(false);
          questSharingPanel.setVisible(false);
          diarySharingPanel.setVisible(false);
          pairButton.setEnabled(true);
          statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
          statusLabel.setText("Not connected.");
          revalidate();
          repaint();
        });
  }

  void showError(String message) {
    SwingUtilities.invokeLater(
        () -> {
          pairButton.setEnabled(true);
          statusLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
          statusLabel.setText("<html>" + escape(message) + "</html>");
        });
  }

  /** Refines the connected status once the logged-in character has been submitted. */
  void showVerification(boolean verified, String linkedRsn) {
    SwingUtilities.invokeLater(
        () -> {
          if (verified) {
            statusLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
            String who =
                linkedRsn != null && !linkedRsn.isEmpty() ? " as " + escape(linkedRsn) : "";
            statusLabel.setText("<html>RuneLite verified" + who + ".</html>");
          } else {
            statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            statusLabel.setText(
                "<html>Connected. This character is not tracked in Bankstand yet.</html>");
          }
        });
  }

  /** Reports a skills-sync outcome while staying in the connected state. */
  void showSnapshotOutcome(boolean stored, String reason) {
    SwingUtilities.invokeLater(
        () -> {
          if (stored) {
            statusLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
            statusLabel.setText("<html>Stats synced.</html>");
          } else {
            // Accepted but not stored (e.g. ingest not enabled yet, or a stale/duplicate
            // submit). Not an error; keep it muted.
            statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            statusLabel.setText("<html>Stats received.</html>");
          }
        });
  }

  /** Flags a failed identity update while staying in the connected state. */
  void showSubmitFailed(String message) {
    SwingUtilities.invokeLater(
        () -> {
          statusLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
          statusLabel.setText(
              "<html>Connected, but the update did not reach Bankstand.<br>"
                  + escape(message)
                  + "</html>");
        });
  }

  /** Initialises the quest-sharing checkbox from the stored config value, e.g. on build. */
  void setShareQuestsEnabled(boolean enabled) {
    SwingUtilities.invokeLater(() -> shareQuestsCheckbox.setSelected(enabled));
  }

  /** Initialises the diary-sharing checkbox from the stored config value, e.g. on build. */
  void setShareDiariesEnabled(boolean enabled) {
    SwingUtilities.invokeLater(() -> shareDiariesCheckbox.setSelected(enabled));
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
