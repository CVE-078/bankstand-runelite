package com.bankstand;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: enter a pairing code and pair, or see the connection status and
 * disconnect. All Swing mutations run on the EDT; the connect/error/disconnected
 * transitions are driven by the plugin from a background thread via these methods,
 * each of which marshals back onto the EDT.
 */
class BankstandPanel extends PluginPanel {

  private final JTextField codeField = new JTextField();
  private final JButton pairButton = new JButton("Pair");
  private final JButton disconnectButton = new JButton("Disconnect");
  private final JLabel statusLabel = new JLabel();
  private final JPanel pairRow = new JPanel(new BorderLayout());

  BankstandPanel(Consumer<String> onPair, Runnable onDisconnect) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));

    JLabel title = new JLabel("Bankstand");
    title.setForeground(ColorScheme.BRAND_ORANGE);
    title.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel hint =
        new JLabel(
            "<html>Generate a pairing code in Bankstand &gt; Account &gt; Connect RuneLite,"
                + " then paste it below.</html>");
    hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    hint.setAlignmentX(Component.LEFT_ALIGNMENT);
    hint.setBorder(BorderFactory.createEmptyBorder(6, 0, 8, 0));

    codeField.setToolTipText("Pairing code (XXXX-XXXX)");

    pairButton.addActionListener(
        e -> {
          pairButton.setEnabled(false);
          onPair.accept(codeField.getText());
        });
    disconnectButton.addActionListener(e -> onDisconnect.run());

    JPanel form = new JPanel();
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.add(codeField);
    form.add(pairButton);
    pairRow.add(form, BorderLayout.CENTER);
    pairRow.setAlignmentX(Component.LEFT_ALIGNMENT);

    statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

    disconnectButton.setAlignmentX(Component.LEFT_ALIGNMENT);

    add(title);
    add(hint);
    add(pairRow);
    add(statusLabel);
    add(disconnectButton);

    showDisconnected();
  }

  /** Pairing is in flight. */
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
          pairRow.setVisible(false);
          disconnectButton.setVisible(true);
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
        });
  }

  void showDisconnected() {
    SwingUtilities.invokeLater(
        () -> {
          pairRow.setVisible(true);
          disconnectButton.setVisible(false);
          pairButton.setEnabled(true);
          statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
          statusLabel.setText("Not connected.");
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

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
