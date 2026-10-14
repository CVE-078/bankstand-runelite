package com.bankstand;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** The side panel and the status lines: what they show and the state behind it. */
final class PanelController {

  /** Live plugin state the panel and status lines read. Client thread only. */
  interface Inputs {
    boolean isPaired();

    String serverUrl();

    /** The logged-in character's name, or null. */
    String playerName();

    EnumSet<Capability> enabledCapabilities();

    boolean isSubmitHalted();

    /** Collection log entries seen, or -1 when none. */
    int collectionLogSize();

    /** What the game reports this account to be. Read only with a character loaded. */
    String accountType();

    String manifestDescription();

    boolean offerSessions();
  }

  private final Consumer<Runnable> onClientThread;
  private final Inputs inputs;

  // Null while the plugin is stopped. volatile: detach() clears it while refresh() reads it
  // from the executor; refresh snapshots it into a local.
  private volatile BankstandPanel panel;
  // Session-scoped: a restart or account switch starts empty.
  private final RecentActivityLog recentActivityLog = new RecentActivityLog();
  // Written on the client thread, read on the EDT. Keyed by capability name.
  private final Map<String, Long> lastSyncedAt = new ConcurrentHashMap<>();
  // Set from the HTTP completion thread, read on the client thread.
  private volatile String lastFailureReason;
  // Zero until something reached the server. Written on the executor, read on the client thread.
  private volatile long lastSubmitAtMs;

  PanelController(Consumer<Runnable> onClientThread, Inputs inputs) {
    this.onClientThread = onClientThread;
    this.inputs = inputs;
  }

  /** Any thread. */
  void attach(BankstandPanel panel) {
    this.panel = panel;
  }

  /** Any thread. */
  void detach() {
    panel = null;
  }

  /**
   * Any thread. Rebuilds the panel: builds the model on the client thread, renders on the EDT.
   * Reads {@code panel} once into a local, so a racing {@link #detach()} cannot cause an NPE on
   * the EDT; a late render into a detached panel is harmless.
   */
  void refresh() {
    BankstandPanel current = panel;
    if (current == null) {
      return;
    }
    onClientThread.accept(
        () -> {
          PanelModel model = buildModel();
          SwingUtilities.invokeLater(() -> current.render(model));
        });
  }

  /** Client thread only. Returns a frozen snapshot for the panel. */
  PanelModel buildModel() {
    String name = inputs.playerName();
    List<PanelModel.CapabilityRow> rows =
        capabilityRows(inputs.enabledCapabilities(), lastSyncedAt);
    PanelPresentation.SyncDot dot =
        PanelPresentation.resolveDot(
            inputs.isPaired(), lastSubmitAtMs > 0L, lastFailureReason != null);
    return new PanelModel(
        inputs.isPaired(),
        name,
        dot,
        rows,
        recentActivityLog.recent(),
        lastFailureReason,
        inputs.serverUrl(),
        inputs.offerSessions());
  }

  /** Client thread only. The lines {@code ::bstand status} prints. */
  List<String> statusLines() {
    String name = inputs.playerName();
    return StatusReport.lines(
        inputs.isPaired(),
        inputs.serverUrl(),
        name,
        lastSubmitAtMs == 0L ? null : describeAge(System.currentTimeMillis() - lastSubmitAtMs),
        inputs.isSubmitHalted()
            ? "authentication failed, so submission is paused. Re-pair to resume."
            : null,
        capabilityNames(inputs.enabledCapabilities()),
        inputs.collectionLogSize(),
        // Only with an account loaded: the varbit reads 0 logged out, same as a regular account.
        name == null ? null : inputs.accountType(),
        "Server accepts: " + inputs.manifestDescription() + ".");
  }

  /**
   * Client thread only. Adds a capture's emit to the panel: a recent-activity line and a fresh
   * last-synced stamp.
   */
  void recordEmittedActivity(TransientEvent event) {
    String description = RecentActivityLog.describe(event.getType(), event.getPayload());
    if (description != null) {
      recentActivityLog.record(description);
    }
    Capability capability = Capability.forEventType(event.getType());
    if (capability != null) {
      lastSyncedAt.put(capability.key, System.currentTimeMillis());
    }
    refresh();
  }

  /** Any thread. Marks a capability as synced now. */
  void stamp(Capability capability) {
    lastSyncedAt.put(capability.key, System.currentTimeMillis());
  }

  /** Client thread only. Stamps what this ack synced and records its activity lines. */
  void recordSynced(EnumSet<Capability> advanced, boolean skillsChanged) {
    // A null activity line stamps the time but records nothing.
    long now = System.currentTimeMillis();
    for (Map.Entry<String, String> synced :
        syncedCapabilitiesThisCycle(advanced, skillsChanged).entrySet()) {
      lastSyncedAt.put(synced.getKey(), now);
      if (synced.getValue() != null) {
        recentActivityLog.record(synced.getValue());
      }
    }
  }

  /** Client thread only. Forgets the last character's stamps and activity. */
  void clearForNewAccount() {
    lastSyncedAt.clear();
    recentActivityLog.clear();
  }

  /** Client thread only. Replaces the stamps with the ones saved for this character. */
  void restoreSyncedAt(Map<String, Long> saved) {
    lastSyncedAt.clear();
    lastSyncedAt.putAll(saved);
  }

  /** Any thread. The live stamps map, for saving with the acked state. */
  Map<String, Long> syncedAt() {
    return lastSyncedAt;
  }

  /** Any thread. Shown on the panel's header dot and failure footer. */
  void submitFailed(String reason) {
    lastFailureReason = reason;
  }

  /** Any thread. */
  void submitSucceeded() {
    lastSubmitAtMs = System.currentTimeMillis();
    lastFailureReason = null;
  }

  /** Coarse on purpose: more precision would imply more than we know. */
  static String describeAge(long millis) {
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

  /** The panel's per-capability rows, in {@link Capability} order, skipping disabled ones. */
  static List<PanelModel.CapabilityRow> capabilityRows(
      EnumSet<Capability> enabled, Map<String, Long> lastSyncedAt) {
    List<PanelModel.CapabilityRow> rows = new ArrayList<>();
    for (Capability capability : enabled) {
      rows.add(new PanelModel.CapabilityRow(capability.label, lastSyncedAt.get(capability.key)));
    }
    return rows;
  }

  /**
   * Capability names for the status and sync lines, in a stable order. A capability sent on the
   * wire but missing here makes status silently under-report, so a test pins it.
   */
  static List<String> capabilityNames(EnumSet<Capability> enabled) {
    List<String> on = new ArrayList<>();
    for (Capability capability : enabled) {
      on.add(capability.statusName);
    }
    return on;
  }

  /**
   * Which capabilities this ack synced, with a recent-activity line each (null for no line).
   * Skills ride along on every submission, so they also need {@code skillsChanged}. The
   * collection log and combat achievements get no line: their chat captures already add a more
   * specific one.
   */
  static Map<String, String> syncedCapabilitiesThisCycle(
      EnumSet<Capability> advanced, boolean skillsChanged) {
    Map<String, String> synced = new LinkedHashMap<>();
    for (Capability capability : advanced) {
      if (capability != Capability.SKILLS || skillsChanged) {
        synced.put(capability.key, capability.syncedLine);
      }
    }
    return synced;
  }
}
