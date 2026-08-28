package com.bankstand;

/**
 * Pure, client-free formatting for {@link BankstandPanel}: what color a status dot
 * resolves to, and how a millisecond age reads as a short relative time. Split out so
 * both are testable without a live client or a display, the same reasoning {@link
 * StatusReport} already uses to keep its own wording testable.
 */
final class PanelPresentation {

  private PanelPresentation() {}

  /** The header's one status dot. Named by color rather than by meaning, because color
   *  is the only thing {@link BankstandPanel} actually renders from it. */
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
    // Paired, no failure on record, but also nothing has actually landed yet (a fresh
    // pairing, or a session with capture still off): neither claim ("succeeded",
    // "failed") is true, so this reads as unknown rather than a false green.
    return everSucceeded ? SyncDot.GREEN : SyncDot.GREY;
  }

  /**
   * A short age like "just now", "2m ago", "3h ago", "5d ago". Coarser than {@code
   * BankstandPlugin#describeAge}'s chat wording on purpose: a capability row here is a
   * few dozen pixels wide, not a sentence in a chat window.
   */
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
