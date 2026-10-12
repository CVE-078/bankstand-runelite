package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** A capability goes on the wire only when that capability itself changed. */
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

  /** A never-acknowledged block counts as changed, so it resends until stored. */
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

  /** Absent means "not observed", so an empty block is never sent. */
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
   * Combat achievements opted out pass an empty map. The server never acks an empty block,
   * so sending one would make the client resubmit forever.
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
   * A complete guided read that found no new ids still needs a block, or the completeness
   * fact never reaches the server.
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

  /** The pending signal cannot create a block for an account with no collection log. */
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
