package com.bankstand;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Item ids that fill a collection log slot another id already fills (Volcanic Mine's Prospector
 * pieces against Motherlode Mine's).
 *
 * <p>For counting only: submissions keep the real ids. Add only genuine duplicates, never the sole
 * id for a slot.
 */
public final class VariantIds {

  private VariantIds() {}

  private static final Map<Integer, Integer> CANONICAL = new HashMap<>();

  static {
    // Volcanic Mine's Prospector set, against Motherlode Mine's.
    CANONICAL.put(29472, 12013); // helmet
    CANONICAL.put(29474, 12014); // jacket
    CANONICAL.put(29476, 12015); // legs
    CANONICAL.put(29478, 12016); // boots
  }

  public static int canonical(int itemId) {
    return CANONICAL.getOrDefault(itemId, itemId);
  }

  /** How many log slots these ids fill, as the game counts them. */
  public static int countEntries(Collection<Integer> itemIds) {
    Set<Integer> entries = new HashSet<>();
    for (Integer id : itemIds) {
      if (id != null) entries.add(canonical(id));
    }
    return entries.size();
  }
}
