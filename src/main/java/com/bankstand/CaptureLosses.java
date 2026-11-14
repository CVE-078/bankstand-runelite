package com.bankstand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Since start-up: snapshots read again after a refusal or a wait (nothing lost), and captures
 * discarded for good, by reason. Outlives a login, so the panel can keep saying it.
 */
final class CaptureLosses {
  enum Reason {
    REMOVED("not saved, data was removed"),
    NO_ANSWER("not saved, no answer from Bankstand in time"),
    FULL("not saved, too many were waiting for Bankstand"),
    REPAIRED("not saved, made under the previous pairing");

    final String why;

    Reason(String why) {
      this.why = why;
    }
  }

  private final Map<Reason, Map<CaptureBoundary.Kind, Integer>> lost = new EnumMap<>(Reason.class);
  private int recovered;

  /** Counts {@code counts} for {@code reason}; returns its panel line, or null for none. */
  synchronized String add(Reason reason, Map<CaptureBoundary.Kind, Integer> counts) {
    String line = line(counts, reason.why);
    if (line != null) {
      Map<CaptureBoundary.Kind, Integer> total =
          lost.computeIfAbsent(reason, r -> new EnumMap<>(CaptureBoundary.Kind.class));
      counts.forEach((kind, n) -> total.merge(kind, n, Integer::sum));
    }
    return line;
  }

  synchronized void recovered(int snapshots) {
    recovered += snapshots;
  }

  synchronized int recovered() {
    return recovered;
  }

  synchronized int count(Reason reason, CaptureBoundary.Kind kind) {
    return lost.getOrDefault(reason, Collections.emptyMap()).getOrDefault(kind, 0);
  }

  int discarded(CaptureBoundary.Kind kind) {
    return count(Reason.REMOVED, kind);
  }

  int timedOut(CaptureBoundary.Kind kind) {
    return count(Reason.NO_ANSWER, kind);
  }

  /** The panel's standing lines: what was recovered, then what was lost and why. */
  synchronized List<String> summary() {
    List<String> lines = new ArrayList<>();
    if (recovered > 0) {
      lines.add(
          "Since start-up: "
              + line(single(CaptureBoundary.Kind.SNAPSHOT, recovered), "held back, read again from the game once Bankstand answers"));
    }
    lost.forEach((reason, counts) -> lines.add("Since start-up: " + line(counts, reason.why)));
    return lines;
  }

  static Map<CaptureBoundary.Kind, Integer> single(CaptureBoundary.Kind kind, int n) {
    Map<CaptureBoundary.Kind, Integer> one = new EnumMap<>(CaptureBoundary.Kind.class);
    one.put(kind, n);
    return one;
  }

  /** "2 events, 1 loot record not saved, ..." or null for none. */
  static String line(Map<CaptureBoundary.Kind, Integer> counts, String why) {
    List<String> parts = new ArrayList<>();
    for (Map.Entry<CaptureBoundary.Kind, Integer> e : counts.entrySet()) {
      int n = e.getValue();
      if (n > 0) {
        parts.add(n + " " + (n == 1 ? e.getKey().one : e.getKey().many));
      }
    }
    return parts.isEmpty() ? null : String.join(", ", parts) + " " + why;
  }
}
