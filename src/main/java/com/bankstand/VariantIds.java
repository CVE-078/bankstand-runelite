package com.bankstand;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Item ids that fill a collection log slot another id already fills.
 *
 * <p>Some slots are awarded by two sources under two different ids, and the log
 * lists them once. Volcanic Mine's Prospector pieces are not Motherlode Mine's,
 * so a player with both sets holds eight items filling four slots, and counting
 * raw ids reported 193 where the log said 189.
 *
 * <p>Used for COUNTING only. Submissions still carry the ids the client actually
 * reported: which item a player holds is a fact, and canonicalising it on the
 * wire would tell the server they own a Motherlode piece when they own a
 * Volcanic one. The server maps ids to slots itself and holds the full table.
 *
 * <p>Only genuine duplicates belong here. An uncharged or empty form that is the
 * ONLY id for its slot is not a duplicate and must not be collapsed, or the
 * count drops below the truth.
 */
public final class VariantIds {

  private VariantIds() {}

  /** Variant id to the id the log counts it as. */
  private static final Map<Integer, Integer> CANONICAL = new HashMap<>();

  static {
    // Volcanic Mine's Prospector set, against Motherlode Mine's.
    CANONICAL.put(29472, 12013); // helmet
    CANONICAL.put(29474, 12014); // jacket
    CANONICAL.put(29476, 12015); // legs
    CANONICAL.put(29478, 12016); // boots
  }

  /** The id the log counts this one as, or the id itself. */
  public static int canonical(int itemId) {
    return CANONICAL.getOrDefault(itemId, itemId);
  }

  /** How many log slots these ids fill, which is what the player sees in game. */
  public static int countEntries(Collection<Integer> itemIds) {
    Set<Integer> entries = new HashSet<>();
    for (Integer id : itemIds) {
      if (id != null) entries.add(canonical(id));
    }
    return entries.size();
  }
}
