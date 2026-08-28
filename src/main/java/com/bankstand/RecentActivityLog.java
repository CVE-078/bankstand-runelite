package com.bankstand;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the panel's "recent activity" block shows: a short, session-scoped list of named
 * things this device has sent, newest first. Not persisted, unlike {@link AckedState}'s
 * new {@code lastSyncedAt}: a restart has nothing to show and that is fine, the same way
 * every other purely in-memory piece of state in this plugin starts fresh on launch.
 *
 * <p>Deliberately not derived later from stored ids. A collection log unlock or a
 * combat achievement completion is captured with its human name right there in the chat
 * line; re-deriving a description afterwards from a persisted item id or task id would
 * need a lookup table this plugin does not keep, so {@link #describe} runs at the same
 * moment the event itself is built, off the exact payload that event carries.
 *
 * <p><b>Thread-safe</b> the same way {@link EventOutbox} is, and for the same reason:
 * {@link #record} runs from a capture's {@code @Subscribe} handler or a submit
 * acknowledgement, both on the client thread, while {@link #recent} runs from the Swing
 * event dispatch thread when the panel repaints. Each method is one read-modify-write
 * over the same list, so without synchronization a repaint racing a record could see a
 * torn list.
 */
final class RecentActivityLog {

  static final int MAX_ENTRIES = 10;

  private final LinkedList<PanelModel.ActivityRow> entries = new LinkedList<>();

  /** Adds one description as the newest entry, stamped with the moment it was recorded,
   *  evicting the oldest once full. */
  synchronized void record(String description) {
    entries.addFirst(new PanelModel.ActivityRow(description, System.currentTimeMillis()));
    while (entries.size() > MAX_ENTRIES) {
      entries.removeLast();
    }
  }

  /** Every recorded row, newest first. A snapshot: mutating the result does not affect
   *  the log. */
  synchronized List<PanelModel.ActivityRow> recent() {
    return new ArrayList<>(entries);
  }

  /** Forgets everything. Called on an account switch: an activity line left over from a
   *  different character reads as a bug on this one. */
  synchronized void clear() {
    entries.clear();
  }

  /**
   * The short human line a capture's own emit contributes to the panel, built from the
   * exact type and payload {@link TransientEvent} was constructed with.
   *
   * @return null for a type this method does not know how to describe, which the caller
   *     treats as "nothing to show" rather than a blank line. Every current {@link
   *     TransientEvent#TYPE_NOTABLE_DROP} type constant is covered; a future one added
   *     to the envelope without a case here simply stays silent on the panel, the same
   *     "under-reports rather than crashes" failure mode {@code capabilityNames} already
   *     accepts for the chat status line.
   */
  static String describe(String type, Map<String, Object> payload) {
    switch (type) {
      case TransientEvent.TYPE_COLLECTION_LOG_UNLOCK:
        return "Collection log: " + payload.get("itemName");
      case TransientEvent.TYPE_COMBAT_ACHIEVEMENT_COMPLETED:
        return "Combat achievements: " + payload.get("taskName") + " completed";
      case TransientEvent.TYPE_DIARY_TASK_COMPLETED:
        return "Diaries: "
            + capitalize(String.valueOf(payload.get("tier")))
            + " task completed in "
            + payload.get("area");
      case TransientEvent.TYPE_NOTABLE_DROP:
        return "Notable drop: " + payload.get("itemName");
      case TransientEvent.TYPE_PET_DROP:
        return "Pet drop: " + payload.get("petName");
      default:
        return null;
    }
  }

  private static String capitalize(String word) {
    if (word.isEmpty()) {
      return word;
    }
    return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
  }
}
