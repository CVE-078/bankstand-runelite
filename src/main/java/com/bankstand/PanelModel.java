package com.bankstand;

import java.util.Collections;
import java.util.List;

/**
 * A frozen snapshot of everything {@link BankstandPanel} shows, assembled by the plugin
 * on the client thread and handed to the panel to render on the Swing event dispatch
 * thread. Plain data, deliberately: the panel never reaches back into the plugin, the
 * client, or the config to ask for anything itself, so what it draws can be reasoned
 * about, and tested, from a value alone.
 */
final class PanelModel {

  final boolean paired;
  final String linkedName;
  final PanelPresentation.SyncDot dot;
  final List<CapabilityRow> capabilities;
  final List<String> recentActivity;
  final String lastFailureReason;
  final String serverUrl;

  PanelModel(
      boolean paired,
      String linkedName,
      PanelPresentation.SyncDot dot,
      List<CapabilityRow> capabilities,
      List<String> recentActivity,
      String lastFailureReason,
      String serverUrl) {
    this.paired = paired;
    this.linkedName = linkedName;
    this.dot = dot;
    this.capabilities = capabilities;
    this.recentActivity = recentActivity;
    this.lastFailureReason = lastFailureReason;
    this.serverUrl = serverUrl;
  }

  /** What the panel shows before the plugin has ever built a real snapshot: the initial
   *  paint, before any capture or button click has run. */
  static PanelModel empty(String serverUrl) {
    return new PanelModel(
        false,
        null,
        PanelPresentation.SyncDot.GREY,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        serverUrl);
  }

  /** One row of the per-capability list: a display name and when it last genuinely
   *  synced, or null when it never has. */
  static final class CapabilityRow {
    final String name;
    final Long lastSyncedAtMs;

    CapabilityRow(String name, Long lastSyncedAtMs) {
      this.name = name;
      this.lastSyncedAtMs = lastSyncedAtMs;
    }
  }
}
