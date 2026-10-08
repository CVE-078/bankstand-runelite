package com.bankstand;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel showing sync status. Renders only from a {@link PanelModel} snapshot and
 * holds no reference to the plugin or client.
 */
class BankstandPanel extends PluginPanel {

  private final Runnable onSyncNow;
  private final Runnable onOpenBankstand;
  private final Runnable onRequestRefresh;

  BankstandPanel(Runnable onSyncNow, Runnable onOpenBankstand, Runnable onRequestRefresh) {
    super();
    this.onSyncNow = onSyncNow;
    this.onOpenBankstand = onOpenBankstand;
    this.onRequestRefresh = onRequestRefresh;
    render(PanelModel.empty(""));
  }

  @Override
  public void onActivate() {
    onRequestRefresh.run();
  }

  /** Must be called on the Swing event dispatch thread. */
  void render(PanelModel model) {
    removeAll();

    add(buildHeader(model));
    add(buildCapabilityList(model));
    add(buildRecentActivity(model));
    add(buildActions(model));

    revalidate();
    repaint();
  }

  private JPanel buildHeader(PanelModel model) {
    JPanel header = new JPanel();
    header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
    header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    header.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    JComponent dot = statusDot(dotColor(model.dot), dotTooltip(model));

    JLabel title = new JLabel(headerText(model));
    title.setFont(FontManager.getRunescapeBoldFont());
    title.setForeground(Color.WHITE);

    JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    titleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    titleRow.add(dot);
    titleRow.add(title);
    header.add(leftAligned(titleRow));

    // A stale server address otherwise fails every sync silently.
    if (model.paired) {
      JLabel serverLine = new JLabel("Paired with " + model.serverUrl);
      serverLine.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
      serverLine.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
      header.add(leftAligned(serverLine));
    }

    return header;
  }

  private static String headerText(PanelModel model) {
    if (!model.paired) {
      return "Bankstand: not paired";
    }
    return model.linkedName == null ? "Bankstand" : "Bankstand: " + model.linkedName;
  }

  private static String dotTooltip(PanelModel model) {
    switch (model.dot) {
      case GREEN:
        return "Paired, last sync succeeded";
      case AMBER:
        return "Paired, the last sync attempt failed";
      case GREY:
      default:
        return model.paired ? "Paired, nothing has synced yet" : "Not paired";
    }
  }

  private static Color dotColor(PanelPresentation.SyncDot dot) {
    switch (dot) {
      case GREEN:
        return ColorScheme.PROGRESS_COMPLETE_COLOR;
      case AMBER:
        return Color.ORANGE;
      case GREY:
      default:
        // MEDIUM_GRAY_COLOR is under WCAG's 3:1 contrast on this background.
        return ColorScheme.LIGHT_GRAY_COLOR;
    }
  }

  // Painted rather than a Unicode glyph, which some client fonts render as nothing.
  private static JComponent statusDot(Color color, String tooltip) {
    JComponent dot =
        new JComponent() {
          @Override
          protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 3, 9, 9);
            g2.dispose();
          }
        };
    dot.setOpaque(false);
    dot.setPreferredSize(new Dimension(11, 16));
    dot.setToolTipText(tooltip);
    return dot;
  }

  private JPanel buildCapabilityList(PanelModel model) {
    JPanel section = new JPanel();
    section.setLayout(new javax.swing.BoxLayout(section, javax.swing.BoxLayout.Y_AXIS));
    section.setBackground(ColorScheme.DARK_GRAY_COLOR);
    section.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    section.add(leftAligned(sectionTitle("Capabilities")));

    if (model.capabilities.isEmpty()) {
      section.add(leftAligned(mutedLabel("Nothing switched on")));
    } else {
      long now = System.currentTimeMillis();
      for (PanelModel.CapabilityRow row : model.capabilities) {
        section.add(buildCapabilityRow(row, now));
      }
    }
    return section;
  }

  private JPanel buildCapabilityRow(PanelModel.CapabilityRow row, long now) {
    JPanel line = new JPanel(new BorderLayout());
    line.setBackground(ColorScheme.DARK_GRAY_COLOR);
    line.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

    JLabel name = new JLabel(row.name);
    name.setForeground(Color.WHITE);

    // Never synced shows a placeholder, never "0m ago".
    JLabel synced =
        new JLabel(
            row.lastSyncedAtMs == null
                ? "—"
                : PanelPresentation.formatAge(now - row.lastSyncedAtMs));
    synced.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    synced.setHorizontalAlignment(SwingConstants.RIGHT);

    line.add(name, BorderLayout.WEST);
    line.add(synced, BorderLayout.EAST);
    return line;
  }

  private JPanel buildRecentActivity(PanelModel model) {
    JPanel section = new JPanel();
    section.setLayout(new javax.swing.BoxLayout(section, javax.swing.BoxLayout.Y_AXIS));
    section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    section.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    section.add(leftAligned(sectionTitle("Recent activity")));

    List<PanelModel.ActivityRow> recent = model.recentActivity;
    if (recent.isEmpty()) {
      section.add(leftAligned(mutedLabel("Nothing sent yet this session")));
    } else {
      long now = System.currentTimeMillis();
      for (PanelModel.ActivityRow row : recent) {
        JLabel label =
            new JLabel(row.description + " (" + PanelPresentation.formatAge(now - row.atMs) + ")");
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        section.add(leftAligned(label));
      }
    }
    return section;
  }

  private JPanel buildActions(PanelModel model) {
    JPanel section = new JPanel();
    section.setLayout(new javax.swing.BoxLayout(section, javax.swing.BoxLayout.Y_AXIS));
    section.setBackground(ColorScheme.DARK_GRAY_COLOR);
    section.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    JButton syncButton = new JButton("Sync now");
    syncButton.setFocusable(false);
    syncButton.addActionListener(e -> onSyncNow.run());

    JButton openButton = new JButton("Open Bankstand");
    openButton.setFocusable(false);
    openButton.addActionListener(e -> onOpenBankstand.run());

    JPanel buttons = new JPanel();
    buttons.setLayout(new javax.swing.BoxLayout(buttons, javax.swing.BoxLayout.Y_AXIS));
    buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
    buttons.add(syncButton);
    buttons.add(javax.swing.Box.createVerticalStrut(4));
    buttons.add(openButton);
    section.add(leftAligned(buttons));

    if (model.lastFailureReason != null) {
      JLabel failure = new JLabel("<html>" + escapeHtml(model.lastFailureReason) + "</html>");
      failure.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
      failure.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
      section.add(leftAligned(failure));
    }

    return section;
  }

  private static JLabel sectionTitle(String text) {
    JLabel label = new JLabel(text);
    label.setFont(FontManager.getRunescapeBoldFont());
    label.setForeground(Color.WHITE);
    label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
    return label;
  }

  private static JLabel mutedLabel(String text) {
    JLabel label = new JLabel(text);
    label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    return label;
  }

  // BorderLayout.WEST: BoxLayout alignmentX did not reliably left-align on a real client.
  private static JPanel leftAligned(JComponent inner) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.add(inner, BorderLayout.WEST);
    return wrapper;
  }

  // The failure text reaches an html-rendering JLabel, so escape markup characters.
  private static String escapeHtml(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
