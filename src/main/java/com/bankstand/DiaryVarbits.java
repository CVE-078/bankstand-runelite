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
 * <p>All 48 tiers are captured, Karamja included. Its easy/medium/hard flags do sit
 * outside the 4458-4498 block the other regions occupy, at 3578/3599/3611, because the
 * Karamja diary predates that block. They were once excluded on the theory that they
 * were task counters rather than completion flags; the newer {@code VarbitID} table
 * disproves it by naming those exact ids {@code ATJUN_EASY_DONE},
 * {@code ATJUN_MED_DONE} and {@code ATJUN_HARD_DONE}. The {@code KARAMJA_*_COUNT}
 * varbits that prompted the doubt are separate ids entirely (2423/6288/6289/6290), so
 * reading 3578 never reports a diary complete on a single task.
 *
 * <p>Worth knowing for anything built on top: the game keeps THREE varbits per tier,
 * and this table reads only the first. {@code *_COMPLETE} (here) means every task is
 * done. {@code *_REWARD} is a separate flag for whether the player has claimed the
 * rewards from the taskmaster. {@code *_COUNT} holds how many of the tier's tasks are
 * done, and exists for all 48 tiers. A tier can therefore be complete but unclaimed,
 * which this table alone cannot express.
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
    // Karamja's easy/medium/hard flags sit outside the block the other regions use
    // (3578/3599/3611 rather than 4458-4498), because its diary predates them. They
    // are still completion flags. See the class comment.
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
