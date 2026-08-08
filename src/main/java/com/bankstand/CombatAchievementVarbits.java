package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Varbits;

/**
 * Maps each combat achievement tier's wire key to the varbit holding how many of that
 * tier's tasks are done.
 *
 * <p><b>Counts, not task ids.</b> RuneLite exposes {@code COMBAT_TASK_*}, a completed
 * count per tier, and nothing at all per task; Jagex publishes neither. So a count is
 * the honest limit of what can be observed here, and the server is written to treat it
 * as exactly that: a tier reading 23/41 says nothing about <i>which</i> 23, and the
 * per-task grid on the website keeps its placeholders even with this populated.
 *
 * <p>Do not be tempted by {@code COMBAT_ACHIEVEMENT_TIER_*}, which sit beside these.
 * Those track whether the tier's <i>rewards</i> have been claimed from the taskmaster,
 * which is a different fact: a player can finish every Easy task and never claim, and
 * reading those would report them at zero. Same trap as the diary table's
 * {@code *_REWARD} flags, called out there for the same reason.
 *
 * <p>Wire keys are lowercase to match the server's {@code CA_TIER_KEYS}, which come
 * from Bankstand's own tier ids rather than from the RuneLite constant names.
 *
 * <p>A plain data table with no {@code Client} dependency, so it is unit-testable
 * without a live game client.
 */
public final class CombatAchievementVarbits {
  private CombatAchievementVarbits() {}

  /** Ordered, unmodifiable: wire key (server's contract) to varbit id. */
  public static final Map<String, Integer> ALL = Collections.unmodifiableMap(build());

  private static Map<String, Integer> build() {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("easy", Varbits.COMBAT_TASK_EASY);
    m.put("medium", Varbits.COMBAT_TASK_MEDIUM);
    m.put("hard", Varbits.COMBAT_TASK_HARD);
    m.put("elite", Varbits.COMBAT_TASK_ELITE);
    m.put("master", Varbits.COMBAT_TASK_MASTER);
    m.put("grandmaster", Varbits.COMBAT_TASK_GRANDMASTER);
    return m;
  }
}
