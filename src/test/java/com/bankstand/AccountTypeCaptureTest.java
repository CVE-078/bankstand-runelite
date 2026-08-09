package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * The account type as a capability, end to end on the client side.
 *
 * <p>It is the one block where a missed acknowledgement can never self-heal. Every other
 * capability describes something that moves again later, so a baseline advanced in error
 * is corrected by the next real change; an account's type changes once if ever, so a
 * wrong baseline is permanent. That is the same shape that made the collection log's
 * missing ack a live bug rather than a slow one.
 */
public class AccountTypeCaptureTest {

  private static final Set<Integer> NO_LOG = Collections.emptySet();

  private static Map<String, Integer> skills(int xp) {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put("attack", xp);
    return map;
  }

  /** Baselines with everything else already acknowledged, so only the type can move. */
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
    // Has to count toward the decision on its own. The type is read at login and never
    // changes after, so a capture waiting on xp would leave a Group Ironman reading as a
    // main until they happened to train something.
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
    // A hardcore ironman dying is the case that matters: the account really does become
    // a different type, and it is the single most consequential state change it has.
    AccountTypeBaseline acked = new AccountTypeBaseline();
    acked.advance("hardcore");

    BankstandPlugin.SubmitPlan plan = planWith(acked, "ironman");

    assertTrue(plan.shouldSubmit());
    assertTrue(plan.includesAccountType());
  }

  @Test
  public void sendsNothingWhenTheGameNamedNoType() {
    // Null is an unrecognised varbit value or the opt-in being off. Both mean "not
    // observed", and neither is a reason to submit.
    assertFalse(planWith(new AccountTypeBaseline(), null).shouldSubmit());
    assertFalse(planWith(new AccountTypeBaseline(), "").shouldSubmit());
  }

  @Test
  public void advancesOnlyOnThisBlocksOwnAcknowledgement() {
    // The whole-submission verdict is decided by skills freshness, so it reads stored
    // even when the server dropped this block behind its rollout flag. Advancing on it
    // would lose the fact outright: there is no later value to force a resend.
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
    // Without this the client resends the same word on every capture forever, which is
    // the failure that self-heals for xp and does not for a one-shot fact.
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
    // A type belongs to one character. Carrying it across would let one account's answer
    // stand in for another's, which for a group account is the whole point of the read.
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
