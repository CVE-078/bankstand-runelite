package com.bankstand;

import java.util.Collections;
import java.util.List;

/**
 * An immutable snapshot of what {@link BankstandPanel} shows, built on the client thread and
 * rendered on the Swing EDT. The panel never reads the client or config itself.
 */
final class PanelModel {

  final boolean paired;
  final String linkedName;
  final PanelPresentation.SyncDot dot;
  final List<CapabilityRow> capabilities;
  final List<ActivityRow> recentActivity;
  final String lastFailureReason;
  final String serverUrl;
  // Full loot on, play sessions off: offer to turn sessions on.
  final boolean offerSessions;

  PanelModel(
      boolean paired,
      String linkedName,
      PanelPresentation.SyncDot dot,
      List<CapabilityRow> capabilities,
      List<ActivityRow> recentActivity,
      String lastFailureReason,
      String serverUrl,
      boolean offerSessions) {
    this.paired = paired;
    this.linkedName = linkedName;
    this.dot = dot;
    this.capabilities = capabilities;
    this.recentActivity = recentActivity;
    this.lastFailureReason = lastFailureReason;
    this.serverUrl = serverUrl;
    this.offerSessions = offerSessions;
  }

  static PanelModel empty(String serverUrl) {
    return new PanelModel(
        false,
        null,
        PanelPresentation.SyncDot.GREY,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        serverUrl,
        false);
  }

  /** Whether to show the play sessions prompt. It only offers; it never turns sessions on. */
  static boolean shouldOfferSessions(boolean lootOn, boolean sessionsOn) {
    return lootOn && !sessionsOn;
  }

  /** {@code lastSyncedAtMs} is null when it never synced. */
  static final class CapabilityRow {
    final String name;
    final Long lastSyncedAtMs;

    CapabilityRow(String name, Long lastSyncedAtMs) {
      this.name = name;
      this.lastSyncedAtMs = lastSyncedAtMs;
    }
  }

  static final class ActivityRow {
    final String description;
    final long atMs;

    ActivityRow(String description, long atMs) {
      this.description = description;
      this.atMs = atMs;
    }
  }
}
