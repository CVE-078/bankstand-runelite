package com.bankstand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The current slayer task, or the observed fact that there is none ({@code active: false}).
 * Fields are nullable, since "not observed" is never zero. {@code master} is always null:
 * there is no verified id-to-name mapping.
 */
final class SlayerTask {

  static final SlayerTask NONE = new SlayerTask(false, null, null, null, null, null, null);

  final boolean active;
  final String creature;
  final Integer remaining;
  final Integer assigned;
  final String master;
  final Integer points;
  final Integer streak;

  SlayerTask(
      boolean active,
      String creature,
      Integer remaining,
      Integer assigned,
      String master,
      Integer points,
      Integer streak) {
    this.active = active;
    this.creature = creature;
    this.remaining = remaining;
    this.assigned = assigned;
    this.master = master;
    this.points = points;
    this.streak = streak;
  }

  /** The {@code slayerTask} block, nulls kept. */
  Map<String, Object> toWire() {
    Map<String, Object> block = new LinkedHashMap<>();
    block.put("active", active);
    if (!active) {
      return block;
    }
    block.put("creature", creature);
    block.put("remaining", remaining);
    block.put("assigned", assigned);
    block.put("master", master);
    block.put("points", points);
    block.put("streak", streak);
    return block;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SlayerTask)) {
      return false;
    }
    SlayerTask other = (SlayerTask) o;
    return active == other.active
        && Objects.equals(creature, other.creature)
        && Objects.equals(remaining, other.remaining)
        && Objects.equals(assigned, other.assigned)
        && Objects.equals(master, other.master)
        && Objects.equals(points, other.points)
        && Objects.equals(streak, other.streak);
  }

  @Override
  public int hashCode() {
    return Objects.hash(active, creature, remaining, assigned, master, points, streak);
  }
}
