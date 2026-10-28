package com.bankstand;

/** Pure formatting for {@link BankstandPanel}, testable without a client or display. */
final class PanelPresentation {

  private PanelPresentation() {}

  enum SyncDot {
    GREEN,
    AMBER,
    GREY
  }

  /**
   * @param paired whether a device token is stored
   * @param everSucceeded whether a submit has landed at least once this session
   * @param lastAttemptFailed whether the most recent submit attempt failed
   */
  static SyncDot resolveDot(boolean paired, boolean everSucceeded, boolean lastAttemptFailed) {
    if (!paired) {
      return SyncDot.GREY;
    }
    if (lastAttemptFailed) {
      return SyncDot.AMBER;
    }
    // Nothing has landed yet: unknown, not a false green.
    return everSucceeded ? SyncDot.GREEN : SyncDot.GREY;
  }

  /** A capability row's right-hand text. Never synced shows a placeholder, never "0m ago". */
  static String capabilityStatus(PanelModel.CapabilityRow row, long nowMs) {
    if (row.held) {
      return "Kept saved progress";
    }
    return row.lastSyncedAtMs == null ? "—" : formatAge(nowMs - row.lastSyncedAtMs);
  }

  /** Null when the row needs no explanation. */
  static String capabilityTooltip(PanelModel.CapabilityRow row) {
    return row.held
        ? "This read was lower than what Bankstand already has, so it was not saved."
        : null;
  }

  /** A short age like "just now", "2m ago", "3h ago", "5d ago", sized for a narrow panel row. */
  static String formatAge(long ageMillis) {
    if (ageMillis < 60_000L) {
      return "just now";
    }
    long minutes = ageMillis / 60_000L;
    if (minutes < 60) {
      return minutes + "m ago";
    }
    long hours = minutes / 60;
    if (hours < 24) {
      return hours + "h ago";
    }
    long days = hours / 24;
    return days + "d ago";
  }
}
