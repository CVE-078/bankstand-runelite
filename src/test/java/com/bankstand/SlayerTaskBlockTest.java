package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.SubmitSnapshotResponse;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/** The {@code slayerTask} block: its shape, its change gate and its acknowledgement. */
public class SlayerTaskBlockTest {

  private static final SlayerTask TASK = new SlayerTask(true, "Cave horrors", 40, 120, null, 1200, 55);

  private static Map<String, Integer> skills() {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("slayer", 100);
    return m;
  }

  private static BankstandPlugin.SubmitPlan plan(
      SkillBaseline skills, SlayerTaskBaseline baseline, SlayerTask task) {
    return BankstandPlugin.plan(
        skills,
        skills(),
        new QuestBaseline(),
        null,
        new DiaryBaseline(),
        null,
        new CollectionLogBaseline(),
        Collections.emptySet(),
        new CombatAchievementBaseline(),
        null,
        new AccountTypeBaseline(),
        null,
        false,
        baseline,
        task);
  }

  private static SkillBaseline ackedSkills() {
    SkillBaseline baseline = new SkillBaseline();
    baseline.advance(skills());
    return baseline;
  }

  private static SubmitSnapshotResponse stored(String... blocks) {
    StringBuilder json = new StringBuilder("{\"accepted\":true,\"stored\":true,\"storedBlocks\":[");
    for (int i = 0; i < blocks.length; i++) {
      json.append(i == 0 ? "" : ",").append('"').append(blocks[i]).append('"');
    }
    return new Gson().fromJson(json.append("]}").toString(), SubmitSnapshotResponse.class);
  }

  @Test
  public void noTaskIsTheObservedStateActiveFalse() {
    Map<String, Object> wire = SlayerTask.NONE.toWire();
    assertEquals(1, wire.size());
    assertEquals(false, wire.get("active"));
  }

  @Test
  public void anActiveTaskCarriesEveryFieldNullsIncluded() {
    Map<String, Object> wire = TASK.toWire();
    assertEquals(true, wire.get("active"));
    assertEquals("Cave horrors", wire.get("creature"));
    assertEquals(40, wire.get("remaining"));
    assertEquals(120, wire.get("assigned"));
    assertTrue("an unknown master is sent as null", wire.containsKey("master"));
    assertEquals(1200, wire.get("points"));
    assertEquals(55, wire.get("streak"));
  }

  @Test
  public void aChangedTaskSubmitsOnItsOwnWithNoXpChange() {
    BankstandPlugin.SubmitPlan plan = plan(ackedSkills(), new SlayerTaskBaseline(), TASK);
    assertTrue(plan.shouldSubmit());
    assertTrue(plan.includesSlayerTask());
  }

  @Test
  public void anAcknowledgedTaskIsNotResent() {
    SlayerTaskBaseline baseline = new SlayerTaskBaseline();
    baseline.advance(TASK);
    BankstandPlugin.SubmitPlan plan = plan(ackedSkills(), baseline, TASK);
    assertFalse(plan.shouldSubmit());
    assertFalse(plan.includesSlayerTask());
  }

  @Test
  public void aFinishedTaskReplacesTheStoredOne() {
    SlayerTaskBaseline baseline = new SlayerTaskBaseline();
    baseline.advance(TASK);
    assertTrue(plan(ackedSkills(), baseline, SlayerTask.NONE).includesSlayerTask());
  }

  @Test
  public void anUnreadTaskIsNeverSent() {
    BankstandPlugin.SubmitPlan plan = plan(ackedSkills(), new SlayerTaskBaseline(), null);
    assertFalse(plan.shouldSubmit());
    assertFalse(plan.includesSlayerTask());
  }

  @Test
  public void advancesOnlyOnItsOwnBlockAcknowledgement() {
    assertTrue(BankstandPlugin.shouldAdvanceSlayerTask(stored("skills", "slayerTask"), true));
    assertFalse(BankstandPlugin.shouldAdvanceSlayerTask(stored("skills"), true));
    assertFalse(BankstandPlugin.shouldAdvanceSlayerTask(stored("skills", "slayerTask"), false));
  }

  @Test
  public void isNotReadForTheFirstTenTicksAfterALogin() {
    assertFalse(BankstandPlugin.isSlayerTaskReadable(0));
    assertFalse(BankstandPlugin.isSlayerTaskReadable(BankstandPlugin.SLAYER_READ_DELAY_TICKS - 1));
    assertTrue(BankstandPlugin.isSlayerTaskReadable(BankstandPlugin.SLAYER_READ_DELAY_TICKS));
  }

  @Test
  public void theEnvelopeCarriesTheBlockOnlyWhenPresent() {
    Map<String, Object> with =
        SubmitEnvelope.body(
            "id", 1, "dev", "2026-10-08T12:00:00Z", 1L, null, skills(), null, null, null, null,
            null, null, false, null, SlayerTask.NONE.toWire());
    assertEquals(SlayerTask.NONE.toWire(), with.get("slayerTask"));
    Map<String, Object> without =
        SubmitEnvelope.body(
            "id", 1, "dev", "2026-10-08T12:00:00Z", 1L, null, skills(), null, null, null, null,
            null, null, false, null, null);
    assertFalse(without.containsKey("slayerTask"));
  }
}
