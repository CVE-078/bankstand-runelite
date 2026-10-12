package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * Maps each diary tier's wire key to the varbit counting its completed tasks (a count, not which
 * tasks). Keys must match {@link DiaryVarbits} exactly so a count joins to its completion flag.
 *
 * <p>Karamja's easy count (2423) sits outside the block the rest use because its diary is older;
 * the id is correct.
 */
public final class DiaryTaskVarbits {
  private DiaryTaskVarbits() {}

  /** Ordered: wire key to varbit id. */
  public static final Map<String, Integer> ALL = Collections.unmodifiableMap(build());

  private static Map<String, Integer> build() {
    Map<String, Integer> m = new LinkedHashMap<>();
    region(m, "ARDOUGNE", VarbitID.ARDOUGNE_EASY_COUNT, VarbitID.ARDOUGNE_MED_COUNT,
        VarbitID.ARDOUGNE_HARD_COUNT, VarbitID.ARDOUGNE_ELITE_COUNT);
    region(m, "DESERT", VarbitID.DESERT_EASY_COUNT, VarbitID.DESERT_MED_COUNT,
        VarbitID.DESERT_HARD_COUNT, VarbitID.DESERT_ELITE_COUNT);
    region(m, "FALADOR", VarbitID.FALADOR_EASY_COUNT, VarbitID.FALADOR_MED_COUNT,
        VarbitID.FALADOR_HARD_COUNT, VarbitID.FALADOR_ELITE_COUNT);
    region(m, "FREMENNIK", VarbitID.FREMENNIK_EASY_COUNT, VarbitID.FREMENNIK_MED_COUNT,
        VarbitID.FREMENNIK_HARD_COUNT, VarbitID.FREMENNIK_ELITE_COUNT);
    region(m, "KANDARIN", VarbitID.KANDARIN_EASY_COUNT, VarbitID.KANDARIN_MED_COUNT,
        VarbitID.KANDARIN_HARD_COUNT, VarbitID.KANDARIN_ELITE_COUNT);
    region(m, "KOUREND_KEBOS", VarbitID.KOUREND_EASY_COUNT, VarbitID.KOUREND_MED_COUNT,
        VarbitID.KOUREND_HARD_COUNT, VarbitID.KOUREND_ELITE_COUNT);
    region(m, "LUMBRIDGE_DRAYNOR", VarbitID.LUMBRIDGE_EASY_COUNT, VarbitID.LUMBRIDGE_MED_COUNT,
        VarbitID.LUMBRIDGE_HARD_COUNT, VarbitID.LUMBRIDGE_ELITE_COUNT);
    region(m, "MORYTANIA", VarbitID.MORYTANIA_EASY_COUNT, VarbitID.MORYTANIA_MED_COUNT,
        VarbitID.MORYTANIA_HARD_COUNT, VarbitID.MORYTANIA_ELITE_COUNT);
    region(m, "VARROCK", VarbitID.VARROCK_EASY_COUNT, VarbitID.VARROCK_MED_COUNT,
        VarbitID.VARROCK_HARD_COUNT, VarbitID.VARROCK_ELITE_COUNT);
    region(m, "WESTERN_PROVINCES", VarbitID.WESTERN_EASY_COUNT, VarbitID.WESTERN_MED_COUNT,
        VarbitID.WESTERN_HARD_COUNT, VarbitID.WESTERN_ELITE_COUNT);
    region(m, "WILDERNESS", VarbitID.WILDERNESS_EASY_COUNT, VarbitID.WILDERNESS_MED_COUNT,
        VarbitID.WILDERNESS_HARD_COUNT, VarbitID.WILDERNESS_ELITE_COUNT);
    region(m, "KARAMJA", VarbitID.KARAMJA_EASY_COUNT, VarbitID.KARAMJA_MED_COUNT,
        VarbitID.KARAMJA_HARD_COUNT, VarbitID.KARAMJA_ELITE_COUNT);
    return m;
  }

  private static void region(Map<String, Integer> m, String key, int easy, int medium, int hard,
      int elite) {
    m.put(key + "_EASY", easy);
    m.put(key + "_MEDIUM", medium);
    m.put(key + "_HARD", hard);
    m.put(key + "_ELITE", elite);
  }
}
