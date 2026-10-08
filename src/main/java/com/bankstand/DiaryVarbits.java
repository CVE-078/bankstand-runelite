package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Varbits;

/**
 * Maps each diary tier's wire key to its completion varbit (every task done). Not the
 * {@code *_REWARD} flag, which only tracks whether rewards were claimed.
 *
 * <p>Wire keys are the server's, so KOUREND, LUMBRIDGE and WESTERN become KOUREND_KEBOS,
 * LUMBRIDGE_DRAYNOR and WESTERN_PROVINCES.
 */
public final class DiaryVarbits {
  private DiaryVarbits() {}

  private static final Map<String, Integer> BY_WIRE_KEY = build();

  /** Ordered: wire key (server contract) to varbit id. */
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
    region(m, "KOUREND_KEBOS", Varbits.DIARY_KOUREND_EASY, Varbits.DIARY_KOUREND_MEDIUM,
        Varbits.DIARY_KOUREND_HARD, Varbits.DIARY_KOUREND_ELITE);
    region(m, "LUMBRIDGE_DRAYNOR", Varbits.DIARY_LUMBRIDGE_EASY, Varbits.DIARY_LUMBRIDGE_MEDIUM,
        Varbits.DIARY_LUMBRIDGE_HARD, Varbits.DIARY_LUMBRIDGE_ELITE);
    region(m, "MORYTANIA", Varbits.DIARY_MORYTANIA_EASY, Varbits.DIARY_MORYTANIA_MEDIUM,
        Varbits.DIARY_MORYTANIA_HARD, Varbits.DIARY_MORYTANIA_ELITE);
    region(m, "VARROCK", Varbits.DIARY_VARROCK_EASY, Varbits.DIARY_VARROCK_MEDIUM,
        Varbits.DIARY_VARROCK_HARD, Varbits.DIARY_VARROCK_ELITE);
    region(m, "WESTERN_PROVINCES", Varbits.DIARY_WESTERN_EASY, Varbits.DIARY_WESTERN_MEDIUM,
        Varbits.DIARY_WESTERN_HARD, Varbits.DIARY_WESTERN_ELITE);
    region(m, "WILDERNESS", Varbits.DIARY_WILDERNESS_EASY, Varbits.DIARY_WILDERNESS_MEDIUM,
        Varbits.DIARY_WILDERNESS_HARD, Varbits.DIARY_WILDERNESS_ELITE);
    // Karamja's ids sit outside the other regions' block (older diary) but are still
    // completion flags, not task counts.
    region(m, "KARAMJA", Varbits.DIARY_KARAMJA_EASY, Varbits.DIARY_KARAMJA_MEDIUM,
        Varbits.DIARY_KARAMJA_HARD, Varbits.DIARY_KARAMJA_ELITE);
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
