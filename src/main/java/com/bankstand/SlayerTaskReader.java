package com.bankstand;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads the current slayer task from the same varps, varbits and game tables RuneLite's
 * own Slayer plugin reads. Client thread only.
 *
 * <p>Reads only the task itself: the creature, how many are left and were assigned, the
 * player's points and streak. Never the task's location, the block list or unlocks.
 */
final class SlayerTaskReader {

  // The task id the game uses for "a boss of your choice", whose real target is in a
  // second table keyed by a separate varbit.
  private static final int BOSS_TASK_ID = 98;
  // The assignment modifier id the game uses for an extended or shortened count.
  private static final int COUNT_MODIFIER_ID = 2;
  // The two masters whose streak the game keeps in its own counter.
  private static final int KRYSTILIA = 7;
  private static final int MORTIMER = 10;

  private SlayerTaskReader() {}

  /**
   * The task as the game currently reports it. Before the game has sent the slayer varps
   * after a login they read zero, indistinguishable from "no task", which is why the
   * caller waits several ticks after login before trusting this.
   */
  static SlayerTask read(Client client) {
    int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
    if (remaining <= 0) {
      return SlayerTask.NONE;
    }
    int assigned = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
    if (client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID) == COUNT_MODIFIER_ID) {
      int modifier = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
      boolean negative = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1;
      assigned += negative ? -modifier : modifier;
    }
    return new SlayerTask(
        true,
        creatureName(client),
        remaining,
        assigned > 0 ? assigned : null,
        null,
        client.getVarbitValue(VarbitID.SLAYER_POINTS),
        streak(client));
  }

  private static String creatureName(Client client) {
    int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
    Integer row;
    if (taskId == BOSS_TASK_ID) {
      List<Integer> bossRows =
          client.getDBRowsByValue(
              DBTableID.SlayerTaskSublist.ID,
              DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
              0,
              client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
      if (bossRows.isEmpty()) {
        return null;
      }
      Object[] task =
          client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0);
      row = task.length > 0 && task[0] instanceof Integer ? (Integer) task[0] : null;
    } else {
      List<Integer> rows =
          client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
      row = rows.isEmpty() ? null : rows.get(0);
    }
    if (row == null) {
      return null;
    }
    Object[] name = client.getDBTableField(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
    return name.length > 0 && name[0] instanceof String && !((String) name[0]).isEmpty()
        ? (String) name[0]
        : null;
  }

  private static Integer streak(Client client) {
    switch (client.getVarbitValue(VarbitID.SLAYER_MASTER)) {
      case KRYSTILIA:
        return client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
      case MORTIMER:
        return client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
      default:
        return client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
    }
  }
}
