package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/** The master key and the three streak counters, read from a stubbed client. */
public class SlayerTaskReaderTest {

  private final Map<Integer, Integer> varps = new HashMap<>();
  private final Map<Integer, Integer> varbits = new HashMap<>();

  private Client client() {
    return (Client)
        Proxy.newProxyInstance(
            Client.class.getClassLoader(),
            new Class<?>[] {Client.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getVarpValue":
                  return varps.getOrDefault((Integer) args[0], 0);
                case "getVarbitValue":
                  return varbits.getOrDefault((Integer) args[0], 0);
                case "getDBRowsByValue":
                  return Collections.emptyList();
                default:
                  return null;
              }
            });
  }

  private void activeTask(int master) {
    varps.put(VarPlayerID.SLAYER_COUNT, 64);
    varps.put(VarPlayerID.SLAYER_COUNT_ORIGINAL, 187);
    varbits.put(VarbitID.SLAYER_MASTER, master);
    varbits.put(VarbitID.SLAYER_TASKS_COMPLETED, 312);
    varbits.put(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED, 14);
    varps.put(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED, 2);
  }

  @Test
  public void mapsEveryKnownMasterValue() {
    String[] expected = {
      "turael", "mazchna", "vannaka", "chaeldar", "duradel",
      "nieve", "krystilia", "konar", "spria", "mortimer"
    };
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], SlayerTaskReader.masterKey(i + 1));
    }
  }

  @Test
  public void anUnknownMasterValueIsNull() {
    assertNull(SlayerTaskReader.masterKey(0));
    assertNull(SlayerTaskReader.masterKey(11));
    assertNull(SlayerTaskReader.masterKey(-1));
  }

  @Test
  public void readsTheMasterAndAllThreeStreaks() {
    activeTask(5);
    SlayerTask task = SlayerTaskReader.read(client());
    assertEquals("duradel", task.master);
    assertEquals(Integer.valueOf(312), task.streak);
    assertEquals(SlayerTask.streaks(312, 14, 2), task.streaks);
  }

  @Test
  public void theCurrentStreakFollowsTheMaster() {
    activeTask(7);
    assertEquals(Integer.valueOf(14), SlayerTaskReader.read(client()).streak);
    activeTask(10);
    assertEquals(Integer.valueOf(2), SlayerTaskReader.read(client()).streak);
  }

  @Test
  public void anUnmappedMasterIsSentAsNull() {
    activeTask(42);
    assertNull(SlayerTaskReader.read(client()).master);
  }

  @Test
  public void noActiveTaskSendsNoMasterEvenIfTheVarbitStillHoldsOne() {
    activeTask(5);
    varps.put(VarPlayerID.SLAYER_COUNT, 0);
    SlayerTask task = SlayerTaskReader.read(client());
    assertSame(SlayerTask.NONE, task);
    assertFalse(task.toWire().containsKey("master"));
  }

  @Test
  public void anOutOfRangeStreakIsNull() {
    activeTask(5);
    varbits.put(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED, SlayerTaskReader.MAX_STREAK + 1);
    assertEquals(SlayerTask.streaks(312, null, 2), SlayerTaskReader.read(client()).streaks);
  }
}
