package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** The account type changes once if ever, so a missed ack here never self-heals. */
public class AccountTypeCaptureTest {

  private static final Set<Integer> NO_LOG = Collections.emptySet();

  private static Map<String, Integer> skills(int xp) {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put("attack", xp);
    return map;
  }

  private static BankstandPlugin.SubmitPlan planWith(
      AccountTypeBaseline baseline, String accountType) {
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills(100));
    return BankstandPlugin.plan(
        skillBaseline,
        skills(100),
        new QuestBaseline(),
        null,
        new DiaryBaseline(),
        null,
        new CollectionLogBaseline(),
        NO_LOG,
        new CombatAchievementBaseline(),
        null,
        baseline,
        accountType);
  }

  @Test
  public void submitsOnTheTypeAloneWhenNothingElseMoved() {
    // Must submit on its own; the type never changes after login.
    BankstandPlugin.SubmitPlan plan = planWith(new AccountTypeBaseline(), "group");

    assertTrue(plan.shouldSubmit());
    assertTrue(plan.includesAccountType());
  }

  @Test
  public void staysQuietOnceTheServerHasAcknowledgedIt() {
    AccountTypeBaseline acked = new AccountTypeBaseline();
    acked.advance("group");

    BankstandPlugin.SubmitPlan plan = planWith(acked, "group");

    assertFalse(plan.shouldSubmit());
    assertFalse(plan.includesAccountType());
  }

  @Test
  public void sendsAgainWhenTheAnswerChanges() {
    AccountTypeBaseline acked = new AccountTypeBaseline();
    acked.advance("hardcore");

    BankstandPlugin.SubmitPlan plan = planWith(acked, "ironman");

    assertTrue(plan.shouldSubmit());
    assertTrue(plan.includesAccountType());
  }

  @Test
  public void sendsNothingWhenTheGameNamedNoType() {
    // Null means "not observed", which is no reason to submit.
    assertFalse(planWith(new AccountTypeBaseline(), null).shouldSubmit());
    assertFalse(planWith(new AccountTypeBaseline(), "").shouldSubmit());
  }

  @Test
  public void advancesOnlyOnThisBlocksOwnAcknowledgement() {
    // The whole-submission verdict can read stored while this block was dropped; advancing
    // on it would lose the fact for good.
    assertTrue(
        BankstandPlugin.shouldAdvanceAccountType(response("persisted", true, "accountType"), true));
    assertFalse(
        BankstandPlugin.shouldAdvanceAccountType(response("persisted", true, "skills"), true));
  }

  @Test
  public void neverAdvancesForABlockItDidNotSend() {
    assertFalse(
        BankstandPlugin.shouldAdvanceAccountType(
            response("persisted", true, "accountType"), false));
  }

  @Test
  public void survivesARestartThroughTheAckedState() {
    // Without this the client resends the same word forever.
    AccountTypeBaseline baseline = new AccountTypeBaseline();
    baseline.advance("unranked_group");

    AckedState state = new AckedState();
    state.setAccountType(baseline.ackedValue());

    AccountTypeBaseline restored = new AccountTypeBaseline();
    restored.restore(state.getAccountType());

    assertEquals("unranked_group", restored.ackedValue());
    assertFalse(restored.changedSince("unranked_group"));
  }

  @Test
  public void forgetsTheTypeOnAnAccountSwitch() {
    // A type belongs to one character.
    AccountTypeBaseline baseline = new AccountTypeBaseline();
    baseline.advance("ironman");
    baseline.reset();

    assertTrue(baseline.changedSince("group"));
    assertEquals(null, baseline.ackedValue());
  }

  private static com.bankstand.dto.SubmitSnapshotResponse response(
      String reason, boolean stored, String... blocks) {
    return new com.google.gson.Gson()
        .fromJson(
            "{\"accepted\":true,\"stored\":"
                + stored
                + ",\"reason\":\""
                + reason
                + "\",\"storedBlocks\":[\""
                + String.join("\",\"", blocks)
                + "\"]}",
            com.bankstand.dto.SubmitSnapshotResponse.class);
  }
}
