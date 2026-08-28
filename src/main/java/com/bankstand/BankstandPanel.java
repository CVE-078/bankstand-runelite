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
 * The plugin's sidebar panel (#1174): a single scrollable view answering "did my sync
 * actually work", reachable from a toolbar icon rather than a permanent tab. The
 * plugin's whole design otherwise is chat lines and {@code ::bstand} commands for
 * something looked at once per device, which is deliberate; this panel does not change
 * that, it just gives the same information a place to be read back without scrolling
 * chat history.
 *
 * <p>Renders entirely from a {@link PanelModel} snapshot handed in by {@link
 * #render}; holds no reference to the plugin, the client, or the config itself, so
 * what it draws can be reasoned about from a value alone. Rebuilds its content from
 * scratch on every render rather than patching it in place: a repaint happens at most
 * once a minute (the capture cadence) or on a user action, never per game tick, so
 * there is no performance reason to diff and patch instead.
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

  /** RuneLite calls this when the sidebar opens on this tab (a fresh open, or
   *  switching back to it from another plugin's tab), which is the one moment a
   *  player is looking at the panel without having just clicked a button in it. The
   *  plugin hops to the client thread to rebuild real state and back to the event
   *  dispatch thread to repaint; this call itself stays cheap and synchronous. */
  @Override
  public void onActivate() {
    onRequestRefresh.run();
  }

  /** Rebuilds the four blocks from the given snapshot and repaints. Must be called on
   *  the Swing event dispatch thread. */
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

    // Named here for the same reason StatusReport's own "Paired with X" line exists:
    // a stale or misconfigured server address otherwise fails every sync in silence.
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
        // MEDIUM_GRAY_COLOR (77,77,77) on this panel's DARKER_GRAY_COLOR (30,30,30)
        // background is a ~2:1 contrast ratio, under WCAG's 3:1 floor for anything
        // meant to be seen. LIGHT_GRAY_COLOR is already this panel's own muted-text
        // colour (the paired-with line, timestamps, placeholders) and reads clearly.
        return ColorScheme.LIGHT_GRAY_COLOR;
    }
  }

  // A painted circle instead of a Unicode glyph ("●" in a bare JLabel, this
  // panel's original approach): glyph coverage for that character depends on
  // whatever font Swing falls back to for an unstyled label, and on a real client it
  // rendered nothing at all rather than merely the wrong colour. Painting it directly
  // has no font to get wrong.
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

    // Never a number, and never "0m ago" for something that has not happened: absence
    // is a different fact from a fresh sync, the same "'—' rather than a false zero"
    // rule this plugin's own StatusReport already renders by for the collection log.
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

  // BoxLayout's own per-child alignmentX is the textbook way to left-pin a component
  // narrower than its container, and it is what this file relied on before. In
  // practice, one label on a real client (this panel's own "Capabilities" heading)
  // still rendered centered after a clean rebuild, with no code-level difference from
  // a sibling heading built the exact same way that rendered correctly. The capability
  // rows never misrendered in testing, and they use BorderLayout, not alignmentX, so
  // this wraps every other label the same way rather than trying to explain the one
  // that did not listen.
  private static JPanel leftAligned(JComponent inner) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.add(inner, BorderLayout.WEST);
    return wrapper;
  }

  // A failure message is the server's or the HTTP client's own text, never
  // player-authored, but it still reaches an html-rendering JLabel: escape the handful
  // of characters that would otherwise be read as markup rather than pulling in a full
  // HTML escaper for a plugin with zero third-party runtime dependencies.
  private static String escapeHtml(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
