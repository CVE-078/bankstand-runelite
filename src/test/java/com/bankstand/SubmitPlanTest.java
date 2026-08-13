package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * What a capture decides to put on the wire.
 *
 * <p>The old rule was "if anything changed, send everything". That resent the whole
 * collection log, around seventeen hundred ids, on every cycle where a single xp drop
 * moved, which is every cycle a player is training. These tests pin the replacement:
 * a capability rides along only when that capability itself changed.
 */
public class SubmitPlanTest {

  private static Map<String, Integer> skills(String name, int xp) {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put(name, xp);
    return map;
  }

  private static Map<String, String> states(String key, String value) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  private static final Set<Integer> NO_LOG = Collections.emptySet();

  /** Baselines with everything already acknowledged, so only real changes show up. */
  private static class Acked {
    final SkillBaseline skills = new SkillBaseline();
    final QuestBaseline quests = new QuestBaseline();
    final DiaryBaseline diaries = new DiaryBaseline();
    final CollectionLogBaseline log = new CollectionLogBaseline();

    Acked() {
      skills.advance(SubmitPlanTest.skills("attack", 100));
      quests.advance(states("COOKS_ASSISTANT", "FINISHED"));
      diaries.advance(states("VARROCK_EASY", "COMPLETE"));
      log.advance(2);
    }

    BankstandPlugin.SubmitPlan plan(
        Map<String, Integer> s, Map<String, String> q, Map<String, String> d, Set<Integer> c) {
      return BankstandPlugin.plan(skills, s, quests, q, diaries, d, log, c);
    }
  }

  @Test
  public void sendsNothingWhenNothingMoved() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan =
        acked.plan(
            skills("attack", 100),
            states("COOKS_ASSISTANT", "FINISHED"),
            states("VARROCK_EASY", "COMPLETE"),
            new java.util.HashSet<>(Arrays.asList(1, 2)));

    assertFalse(plan.shouldSubmit());
  }

  /**
   * The headline. An xp change submits, and carries only skills: the other three are
   * exactly as the server last acknowledged them, so re-sending them is pure payload.
   */
  @Test
  public void anXpChangeCarriesSkillsAlone() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan =
        acked.plan(
            skills("attack", 200),
            states("COOKS_ASSISTANT", "FINISHED"),
            states("VARROCK_EASY", "COMPLETE"),
            new java.util.HashSet<>(Arrays.asList(1, 2)));

    assertTrue(plan.shouldSubmit());
    assertFalse(plan.includesQuests());
    assertFalse(plan.includesDiaries());
    assertFalse(plan.includesCollectionLog());
  }

  @Test
  public void aQuestChangeCarriesQuestsAndNotTheOtherRiders() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan =
        acked.plan(
            skills("attack", 100),
            states("COOKS_ASSISTANT", "IN_PROGRESS"),
            states("VARROCK_EASY", "COMPLETE"),
            new java.util.HashSet<>(Arrays.asList(1, 2)));

    assertTrue(plan.shouldSubmit());
    assertTrue(plan.includesQuests());
    assertFalse(plan.includesDiaries());
    assertFalse(plan.includesCollectionLog());
  }

  @Test
  public void aDiaryChangeCarriesDiariesAlone() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan =
        acked.plan(
            skills("attack", 100),
            states("COOKS_ASSISTANT", "FINISHED"),
            states("VARROCK_EASY", "INCOMPLETE"),
            new java.util.HashSet<>(Arrays.asList(1, 2)));

    assertTrue(plan.shouldSubmit());
    assertTrue(plan.includesDiaries());
    assertFalse(plan.includesQuests());
  }

  @Test
  public void aGrownCollectionLogCarriesTheLogAlone() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan =
        acked.plan(
            skills("attack", 100),
            states("COOKS_ASSISTANT", "FINISHED"),
            states("VARROCK_EASY", "COMPLETE"),
            new java.util.HashSet<>(Arrays.asList(1, 2, 3)));

    assertTrue(plan.shouldSubmit());
    assertTrue(plan.includesCollectionLog());
    assertFalse(plan.includesQuests());
    assertFalse(plan.includesDiaries());
  }

  @Test
  public void anOptInThatIsOffNeverContributesAndIsNeverSent() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan = acked.plan(skills("attack", 100), null, null, NO_LOG);

    assertFalse(plan.shouldSubmit());
    assertFalse(plan.includesQuests());
    assertFalse(plan.includesDiaries());
    assertFalse(plan.includesCollectionLog());
  }

  /**
   * A block the server has never acknowledged counts as changed, so it keeps riding
   * along until it is stored. That is what makes an unstored block self-heal the moment
   * its rollout flag comes on, and omitting unchanged blocks must not weaken it.
   */
  @Test
  public void aBlockThatWasNeverAcknowledgedKeepsBeingSent() {
    SkillBaseline skills = new SkillBaseline();
    skills.advance(skills("attack", 100));

    BankstandPlugin.SubmitPlan plan =
        BankstandPlugin.plan(
            skills,
            skills("attack", 100),
            new QuestBaseline(),
            states("COOKS_ASSISTANT", "FINISHED"),
            new DiaryBaseline(),
            states("VARROCK_EASY", "COMPLETE"),
            new CollectionLogBaseline(),
            new java.util.HashSet<>(Arrays.asList(1)));

    assertTrue(plan.shouldSubmit());
    assertTrue(plan.includesQuests());
    assertTrue(plan.includesDiaries());
    assertTrue(plan.includesCollectionLog());
  }

  /**
   * An empty block is omitted whatever the baseline says. Absent means "not observed"
   * on this wire, so sending an empty map would assert the player has no quests rather
   * than that nothing was read.
   */
  @Test
  public void anEmptyBlockIsNeverSent() {
    BankstandPlugin.SubmitPlan plan =
        BankstandPlugin.plan(
            new SkillBaseline(),
            skills("attack", 100),
            new QuestBaseline(),
            new LinkedHashMap<>(),
            new DiaryBaseline(),
            new LinkedHashMap<>(),
            new CollectionLogBaseline(),
            NO_LOG);

    assertFalse(plan.includesQuests());
    assertFalse(plan.includesDiaries());
    assertFalse(plan.includesCollectionLog());
  }

  @Test
  public void severalChangesRideTogether() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan =
        acked.plan(
            skills("attack", 200),
            states("COOKS_ASSISTANT", "IN_PROGRESS"),
            states("VARROCK_EASY", "COMPLETE"),
            new java.util.HashSet<>(Arrays.asList(1, 2, 3)));

    assertTrue(plan.shouldSubmit());
    assertTrue(plan.includesQuests());
    assertFalse(plan.includesDiaries());
    assertTrue(plan.includesCollectionLog());
  }

  /**
   * The combat achievement opt-in is enforced by never reading the counts, so a capture
   * with it off passes an empty map here. Two things must hold for that to be safe.
   *
   * <p>It must not read as "every tier went to zero", and it must not put an empty block
   * on the wire: the server drops an empty block without acknowledging it, so a client
   * that sent one would never advance its baseline and would resubmit forever. The rest
   * of the capture carries on regardless, which is the point of a per-capability opt-in.
   */
  @Test
  public void leavesCombatAchievementsOutWhileTheOptInIsOff() {
    Acked acked = new Acked();
    CombatAchievementBaseline combat = new CombatAchievementBaseline();
    combat.advance(skills("easy", 23));

    BankstandPlugin.SubmitPlan plan =
        BankstandPlugin.plan(
            acked.skills,
            skills("attack", 200),
            acked.quests,
            null,
            acked.diaries,
            null,
            acked.log,
            NO_LOG,
            combat,
            Collections.emptyMap());

    assertFalse(plan.includesCombatAchievements());
    assertTrue(plan.shouldSubmit());
  }

  /**
   * The case #466 exists for: a COMPLETE guided read that revealed zero new ids, because
   * the account's partial reads had already, coincidentally, covered everything a full
   * search would show. Without the pending signal counting toward the decision, this
   * capture would carry no collection log block at all and the completeness fact would
   * never reach the server.
   */
  @Test
  public void aCompleteEnumerationCarriesTheLogEvenWithNoNewItems() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan =
        BankstandPlugin.plan(
            acked.skills,
            skills("attack", 100),
            acked.quests,
            states("COOKS_ASSISTANT", "FINISHED"),
            acked.diaries,
            states("VARROCK_EASY", "COMPLETE"),
            acked.log,
            new java.util.HashSet<>(Arrays.asList(1, 2)),
            new CombatAchievementBaseline(),
            null,
            new AccountTypeBaseline(),
            null,
            true);

    assertTrue(plan.shouldSubmit());
    assertTrue(plan.includesCollectionLog());
    assertTrue(plan.includesFullEnumeration());
  }

  /**
   * The pending signal cannot manufacture a block out of nothing: a genuinely empty
   * account has no collection log to attach the fact to, the same "empty means not
   * observed" limitation every other capability already accepts.
   */
  @Test
  public void aPendingEnumerationNeverCarriesAnEmptyLog() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan =
        BankstandPlugin.plan(
            acked.skills,
            skills("attack", 200),
            acked.quests,
            states("COOKS_ASSISTANT", "FINISHED"),
            acked.diaries,
            states("VARROCK_EASY", "COMPLETE"),
            new CollectionLogBaseline(),
            NO_LOG,
            new CombatAchievementBaseline(),
            null,
            new AccountTypeBaseline(),
            null,
            true);

    assertFalse(plan.includesCollectionLog());
    assertFalse(plan.includesFullEnumeration());
  }

  /**
   * An ordinary grown log, with no enumeration pending, must not claim one it was never
   * told about.
   */
  @Test
  public void anOrdinaryLogChangeNeverClaimsFullEnumeration() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan =
        BankstandPlugin.plan(
            acked.skills,
            skills("attack", 100),
            acked.quests,
            states("COOKS_ASSISTANT", "FINISHED"),
            acked.diaries,
            states("VARROCK_EASY", "COMPLETE"),
            acked.log,
            new java.util.HashSet<>(Arrays.asList(1, 2, 3)),
            new CombatAchievementBaseline(),
            null,
            new AccountTypeBaseline(),
            null,
            false);

    assertTrue(plan.includesCollectionLog());
    assertFalse(plan.includesFullEnumeration());
  }

  /** The pre-#466 overload still omits the signal entirely, as every existing caller does. */
  @Test
  public void theShorterOverloadNeverClaimsFullEnumeration() {
    Acked acked = new Acked();

    BankstandPlugin.SubmitPlan plan =
        acked.plan(
            skills("attack", 100),
            states("COOKS_ASSISTANT", "FINISHED"),
            states("VARROCK_EASY", "COMPLETE"),
            new java.util.HashSet<>(Arrays.asList(1, 2, 3)));

    assertTrue(plan.includesCollectionLog());
    assertFalse(plan.includesFullEnumeration());
  }
}
