package com.bankstand;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * The raw values behind per-task diary state, sent as read. The server maps them to tasks and
 * rewards, so a mapping fix needs no plugin release. Keys must match the server's allowlist.
 */
final class DiaryTaskVars {

  private DiaryTaskVars() {}

  /** The task varplayers of every region but Karamja's older tiers. */
  static final int[] VARPS = {
    VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, VarPlayerID.VARROCK_ACHIEVEMENT_DIARY2,
    VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY, VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2,
    VarPlayerID.MORYTANIA_ACHIEVEMENT_DIARY, VarPlayerID.MORYTANIA_ACHIEVEMENT_DIARY2,
    VarPlayerID.WESTERN_ACHIEVEMENT_DIARY, VarPlayerID.WESTERN_ACHIEVEMENT_DIARY2,
    VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY, VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY2,
    VarPlayerID.FALADOR_ACHIEVEMENT_DIARY, VarPlayerID.FALADOR_ACHIEVEMENT_DIARY2,
    VarPlayerID.WILDERNESS_ACHIEVEMENT_DIARY, VarPlayerID.WILDERNESS_ACHIEVEMENT_DIARY2,
    VarPlayerID.LUMB_DRAY_ACHIEVEMENT_DIARY, VarPlayerID.LUMB_DRAY_ACHIEVEMENT_DIARY2,
    VarPlayerID.ARDOUNGE_ACHIEVEMENT_DIARY, VarPlayerID.ARDOUNGE_ACHIEVEMENT_DIARY2,
    VarPlayerID.DESERT_ACHIEVEMENT_DIARY, VarPlayerID.DESERT_ACHIEVEMENT_DIARY2,
    // Karamja elite.
    VarPlayerID.ATJUN_TASKS_4,
    VarPlayerID.KOUREND_ACHIEVEMENT_DIARY, VarPlayerID.KOUREND_ACHIEVEMENT_DIARY2,
  };

  /** Karamja easy, medium and hard: one varbit per task. Then every tier's reward claim. */
  static final int[] VARBITS = {
    VarbitID.ATJUN_EASY_BANANA, VarbitID.ATJUN_EASY_SWING, VarbitID.ATJUN_EASY_GOLD,
    VarbitID.ATJUN_EASY_BOAT_SARIM, VarbitID.ATJUN_EASY_BOAT_ARDY, VarbitID.ATJUN_EASY_CAIRN,
    VarbitID.ATJUN_EASY_FISHING, VarbitID.ATJUN_EASY_SEAWEED, VarbitID.ATJUN_EASY_TZHAAR,
    VarbitID.ATJUN_EASY_JOGRE,
    VarbitID.ATJUN_MED_AGILITY, VarbitID.ATJUN_MED_VOLCANO, VarbitID.ATJUN_MED_CRANDOR,
    VarbitID.ATJUN_MED_CART, VarbitID.ATJUN_MED_CLEANUP, VarbitID.ATJUN_MED_SPIDER,
    VarbitID.ATJUN_MED_TOPAZ, VarbitID.ATJUN_MED_TEAK, VarbitID.ATJUN_MED_MAHOGANY,
    VarbitID.ATJUN_MED_KARAMBWAN, VarbitID.ATJUN_MED_MACHETTE, VarbitID.ATJUN_MED_GLIDER,
    VarbitID.ATJUN_MED_FARMING, VarbitID.ATJUN_MED_GRAAHK, VarbitID.ATJUN_MED_SHILO_VINES,
    VarbitID.ATJUN_MED_SHILO_LAVA, VarbitID.ATJUN_MED_SHILO_STAIRS, VarbitID.ATJUN_MED_KHAZARD,
    VarbitID.ATJUN_MED_CHARTER,
    VarbitID.ATJUN_HARD_FIGHTPITS, VarbitID.ATJUN_HARD_FIGHTCAVE, VarbitID.ATJUN_HARD_OOMLIE,
    VarbitID.ATJUN_HARD_NATURE, VarbitID.ATJUN_HARD_KARAMBWAN, VarbitID.ATJUN_HARD_DEATHWING,
    VarbitID.ATJUN_HARD_XBOW, VarbitID.ATJUN_HARD_PALM, VarbitID.ATJUN_HARD_DURADEL,
    VarbitID.ATJUN_HARD_DRAGON,
    VarbitID.ARDOUGNE_EASY_REWARD, VarbitID.ARDOUGNE_MEDIUM_REWARD,
    VarbitID.ARDOUGNE_HARD_REWARD, VarbitID.ARDOUGNE_ELITE_REWARD,
    VarbitID.DESERT_EASY_REWARD, VarbitID.DESERT_MEDIUM_REWARD,
    VarbitID.DESERT_HARD_REWARD, VarbitID.DESERT_ELITE_REWARD,
    VarbitID.FALADOR_EASY_REWARD, VarbitID.FALADOR_MEDIUM_REWARD,
    VarbitID.FALADOR_HARD_REWARD, VarbitID.FALADOR_ELITE_REWARD,
    VarbitID.FREMENNIK_EASY_REWARD, VarbitID.FREMENNIK_MEDIUM_REWARD,
    VarbitID.FREMENNIK_HARD_REWARD, VarbitID.FREMENNIK_ELITE_REWARD,
    VarbitID.KANDARIN_EASY_REWARD, VarbitID.KANDARIN_MEDIUM_REWARD,
    VarbitID.KANDARIN_HARD_REWARD, VarbitID.KANDARIN_ELITE_REWARD,
    VarbitID.ATJUN_EASY_REWARD, VarbitID.ATJUN_MED_REWARD,
    VarbitID.ATJUN_HARD_REWARD, VarbitID.KARAMJA_ELITE_REWARD,
    VarbitID.KOUREND_EASY_REWARD, VarbitID.KOUREND_MEDIUM_REWARD,
    VarbitID.KOUREND_HARD_REWARD, VarbitID.KOUREND_ELITE_REWARD,
    VarbitID.LUMBRIDGE_EASY_REWARD, VarbitID.LUMBRIDGE_MEDIUM_REWARD,
    VarbitID.LUMBRIDGE_HARD_REWARD, VarbitID.LUMBRIDGE_ELITE_REWARD,
    VarbitID.MORYTANIA_EASY_REWARD, VarbitID.MORYTANIA_MEDIUM_REWARD,
    VarbitID.MORYTANIA_HARD_REWARD, VarbitID.MORYTANIA_ELITE_REWARD,
    VarbitID.VARROCK_EASY_REWARD, VarbitID.VARROCK_MEDIUM_REWARD,
    VarbitID.VARROCK_HARD_REWARD, VarbitID.VARROCK_ELITE_REWARD,
    VarbitID.WESTERN_EASY_REWARD, VarbitID.WESTERN_MEDIUM_REWARD,
    VarbitID.WESTERN_HARD_REWARD, VarbitID.WESTERN_ELITE_REWARD,
    VarbitID.WILDERNESS_EASY_REWARD, VarbitID.WILDERNESS_MEDIUM_REWARD,
    VarbitID.WILDERNESS_HARD_REWARD, VarbitID.WILDERNESS_ELITE_REWARD,
  };

  /** The wire block, every key read. Sorted, so its digest is stable. Client thread. */
  static Map<String, Object> read(IntUnaryOperator varp, IntUnaryOperator varbit) {
    Map<String, Object> block = new TreeMap<>();
    block.put("varps", values(VARPS, varp));
    block.put("varbits", values(VARBITS, varbit));
    return block;
  }

  private static Map<String, Integer> values(int[] ids, IntUnaryOperator reader) {
    Map<String, Integer> values = new TreeMap<>();
    for (int id : ids) {
      values.put(Integer.toString(id), reader.applyAsInt(id));
    }
    return values;
  }
}
