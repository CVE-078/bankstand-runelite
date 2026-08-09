package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * Maps the server's diary wire keys to the varbit holding how many of that tier's tasks
 * are done.
 *
 * <p><b>The second of the three varbits per tier</b>, and the one that makes a partly
 * finished tier expressible at all. {@link DiaryVarbits} reads {@code *_COMPLETE}, which
 * is true only when every task is done, so a tier at 21 of 22 has been reporting as
 * nothing. This table is what fixes that, and it is a table extension rather than a
 * research project: the counts exist for all 48 tiers and RuneLite names every one.
 *
 * <p><b>These are counts, not task identities.</b> A tier reading 21 says nothing about
 * which 21, exactly as the combat achievement tier counts do not say which tasks moved.
 * Per-task state stays with #444, and the reader's per-task marks stay unmarked.
 *
 * <p>Wire keys match {@link DiaryVarbits} exactly, because the two halves describe the
 * same 48 tiers and a key that existed in one and not the other would be a tier whose
 * count could never be joined to its completion flag. {@code DiaryTaskVarbitsTest}
 * asserts they agree rather than leaving it to review.
 *
 * <p>Names differ from the completion table in two ways worth knowing. These come from
 * the newer {@code VarbitID} table rather than {@code Varbits}, which is where the counts
 * are named; and the tier is spelled {@code MED} rather than {@code MEDIUM}. Three
 * regions are renamed to the wire key for the same reason as next door: KOUREND becomes
 * KOUREND_KEBOS, LUMBRIDGE becomes LUMBRIDGE_DRAYNOR, WESTERN becomes WESTERN_PROVINCES.
 *
 * <p>Karamja's easy count sits at 2423, far outside the 6288-6330 block the rest occupy,
 * because its diary predates them. That is the same split the completion table documents
 * and is not a sign of a wrong id.
 *
 * <p>A plain data table with no {@code Client} dependency, so it is unit-testable without
 * a live game client.
 */
public final class DiaryTaskVarbits {
  private DiaryTaskVarbits() {}

  /** Ordered, unmodifiable: wire key to the varbit holding that tier's completed count. */
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
