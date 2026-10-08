package com.bankstand;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The panel's session-only "recent activity" list, newest first. Descriptions are built when the
 * event is emitted, since there is no lookup table to rebuild them from ids later.
 *
 * <p>Synchronized: written on the client thread, read on the Swing EDT.
 */
final class RecentActivityLog {

  static final int MAX_ENTRIES = 10;

  private final LinkedList<PanelModel.ActivityRow> entries = new LinkedList<>();

  synchronized void record(String description) {
    entries.addFirst(new PanelModel.ActivityRow(description, System.currentTimeMillis()));
    while (entries.size() > MAX_ENTRIES) {
      entries.removeLast();
    }
  }

  /** A copy, newest first. */
  synchronized List<PanelModel.ActivityRow> recent() {
    return new ArrayList<>(entries);
  }

  /** Called on an account switch. */
  synchronized void clear() {
    entries.clear();
  }

  /** Null for an unknown type, which the panel skips. */
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
      case TransientEvent.TYPE_SLAYER_TASK_COMPLETED:
        Object creature = payload.get("creature");
        return creature == null ? "Slayer task completed" : "Slayer task completed: " + creature;
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
