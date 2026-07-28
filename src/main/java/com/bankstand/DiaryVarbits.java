package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Varbits;

/**
 * Maps the server's diary wire keys to the varbit that carries each tier's completion
 * flag. Bankstand derives its wire keys from its own guide slugs, not from the
 * RuneLite {@link Varbits} constant names, so three regions are renamed here:
 * KOUREND becomes KOUREND_KEBOS, LUMBRIDGE becomes LUMBRIDGE_DRAYNOR, and WESTERN
 * becomes WESTERN_PROVINCES. The other nine regions keep their name.
 *
 * <p>Karamja's easy/medium/hard varbits are deliberately excluded. Unlike every other
 * region, Karamja's diary predates the flag-per-tier redesign and is task-counted:
 * its easy/medium/hard varbits (3578/3599/3611) sit far outside the 4458-4498 block
 * the rest of the tiers occupy, and the newer {@code VarbitID} table declines to name
 * them completion flags, exposing separate {@code KARAMJA_*_COUNT} varbits instead.
 * Reading one of those as {@code != 0} would report a diary as complete the moment a
 * single task was done, which is wrong data, worse than the "not observed" the server
 * already treats an omitted entry as. Only Karamja's elite tier is a real completion
 * flag, so it is the one Karamja entry captured. That keeps this table at 45 of the
 * 48 possible tiers.
 *
 * <p>A plain data table with no {@code Client} dependency, so it is unit-testable
 * without a live game client.
 */
public final class DiaryVarbits {
  private DiaryVarbits() {}

  private static final Map<String, Integer> BY_WIRE_KEY = build();

  /** Ordered, unmodifiable: wire key (server's contract) to varbit id. */
  public static final Map<String, Integer> ALL = Collections.unmodifiableMap(BY_WIRE_KEY);

  private static Map<String, Integer> build() {
    Map<String, Integer> m = new LinkedHashMap<>();
    region(m, "ARDOUGNE", Varbits.DIARY_ARDOUGNE_EASY, Varbits.DIARY_ARDOUGNE_MEDIUM,
        Varbits.DIARY_ARDOUGNE_HARD, Varbits.DIARY_ARDOUGNE_ELITE);
    region(m, "DESERT", Varbits.DIARY_DESERT_EASY, Varbits.DIARY_DESERT_MEDIUM,
        Varbits.DIARY_DESERT_HARD, Varbits.DIARY_DESERT_ELITE);
    region(m, "FALADOR", Varbits.DIARY_FALADOR_EASY, Varbits.DIARY_FALADOR_MEDIUM,
        Varbits.DIARY_FALADOR_HARD, Varbits.DIARY_FALADOR_ELITE);
    region(m, "FREMENNIK", Varbits.DIARY_FREMENNIK_EASY, Varbits.DIARY_FREMENNIK_MEDIUM,
        Varbits.DIARY_FREMENNIK_HARD, Varbits.DIARY_FREMENNIK_ELITE);
    region(m, "KANDARIN", Varbits.DIARY_KANDARIN_EASY, Varbits.DIARY_KANDARIN_MEDIUM,
        Varbits.DIARY_KANDARIN_HARD, Varbits.DIARY_KANDARIN_ELITE);
    // KOUREND is the RuneLite constant prefix; the wire key is KOUREND_KEBOS.
    region(m, "KOUREND_KEBOS", Varbits.DIARY_KOUREND_EASY, Varbits.DIARY_KOUREND_MEDIUM,
        Varbits.DIARY_KOUREND_HARD, Varbits.DIARY_KOUREND_ELITE);
    // LUMBRIDGE is the RuneLite constant prefix; the wire key is LUMBRIDGE_DRAYNOR.
    region(m, "LUMBRIDGE_DRAYNOR", Varbits.DIARY_LUMBRIDGE_EASY, Varbits.DIARY_LUMBRIDGE_MEDIUM,
        Varbits.DIARY_LUMBRIDGE_HARD, Varbits.DIARY_LUMBRIDGE_ELITE);
    region(m, "MORYTANIA", Varbits.DIARY_MORYTANIA_EASY, Varbits.DIARY_MORYTANIA_MEDIUM,
        Varbits.DIARY_MORYTANIA_HARD, Varbits.DIARY_MORYTANIA_ELITE);
    region(m, "VARROCK", Varbits.DIARY_VARROCK_EASY, Varbits.DIARY_VARROCK_MEDIUM,
        Varbits.DIARY_VARROCK_HARD, Varbits.DIARY_VARROCK_ELITE);
    // WESTERN is the RuneLite constant prefix; the wire key is WESTERN_PROVINCES.
    region(m, "WESTERN_PROVINCES", Varbits.DIARY_WESTERN_EASY, Varbits.DIARY_WESTERN_MEDIUM,
        Varbits.DIARY_WESTERN_HARD, Varbits.DIARY_WESTERN_ELITE);
    region(m, "WILDERNESS", Varbits.DIARY_WILDERNESS_EASY, Varbits.DIARY_WILDERNESS_MEDIUM,
        Varbits.DIARY_WILDERNESS_HARD, Varbits.DIARY_WILDERNESS_ELITE);
    // Karamja: elite only. See the class comment for why easy/medium/hard are excluded.
    m.put("KARAMJA_ELITE", Varbits.DIARY_KARAMJA_ELITE);
    return m;
  }

  private static void region(
      Map<String, Integer> m, String prefix, int easy, int medium, int hard, int elite) {
    m.put(prefix + "_EASY", easy);
    m.put(prefix + "_MEDIUM", medium);
    m.put(prefix + "_HARD", hard);
    m.put(prefix + "_ELITE", elite);
  }
}
