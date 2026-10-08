package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Varbits;

/**
 * Maps each combat achievement tier's wire key to the varbit counting its completed tasks. The
 * game exposes a count per tier, never which tasks.
 *
 * <p>Not {@code COMBAT_ACHIEVEMENT_TIER_*}: those track whether the tier's rewards were claimed,
 * a different fact.
 */
public final class CombatAchievementVarbits {
  private CombatAchievementVarbits() {}

  /** Ordered: wire key (server contract, lowercase) to varbit id. */
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
