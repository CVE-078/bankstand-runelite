package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.SubmitSnapshotResponse;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Test;

/**
 * Developer-mode entry point: launches RuneLite with this plugin sideloaded. Lives
 * in the test source set so it is never part of the shipped plugin. Run it with
 * {@code ./gradlew run}.
 *
 * <p>Also covers the plugin's capture-decision logic. {@link BankstandPlugin}'s
 * capture-and-submit wiring reads a live {@code Client} and a Guice-constructed
 * {@code ConfigManager} (whose constructor is private, so it cannot be faked
 * without a mocking library); the decision logic that gates a submit and a
 * baseline advance is exposed as small package-private static methods so it is
 * unit-testable on its own, using the real {@link SkillBaseline} and {@link
 * QuestBaseline}.
 */
public class BankstandPluginTest {
  public static void main(String[] args) throws Exception {
    ExternalPluginManager.loadBuiltin(BankstandPlugin.class);
    RuneLite.main(args);
  }

  private static Map<String, Integer> skills(String key, int xp) {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put(key, xp);
    return m;
  }

  private static Map<String, String> quests(String key, String state) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put(key, state);
    return m;
  }

  private static Map<String, String> diaries(String key, String state) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put(key, state);
    return m;
  }

  /** A set of {@code count} distinct observed item ids. */
  private static Set<Integer> logItems(int count) {
    Set<Integer> ids = new LinkedHashSet<>();
    for (int i = 1; i <= count; i++) {
      ids.add(i);
    }
    return ids;
  }

  /**
   * The submit decision alone, which is what every test below this line is about.
   *
   * <p>{@code plan} now answers two questions at once: whether to submit, and which
   * optional blocks ride along. The per-block choice is covered by {@link
   * SubmitPlanTest}; these cases predate it and are the regression net for the decision
   * itself, so they are left asserting exactly what they always asserted rather than
   * being rewritten around the wider return type.
   */
  private static boolean shouldSubmit(
      SkillBaseline skillBaseline,
      Map<String, Integer> skills,
      QuestBaseline questBaseline,
      Map<String, String> quests,
      DiaryBaseline diaryBaseline,
      Map<String, String> diaries,
      CollectionLogBaseline collectionLogBaseline,
      int collectionLogCount) {
    return BankstandPlugin.plan(
            skillBaseline,
            skills,
            questBaseline,
            quests,
            diaryBaseline,
            diaries,
            collectionLogBaseline,
            logItems(collectionLogCount))
        .shouldSubmit();
  }

  private static SubmitSnapshotResponse response(boolean accepted, boolean stored, String reason) {
    String json =
        String.format(
            "{\"accepted\":%s,\"stored\":%s,\"reason\":\"%s\"}", accepted, stored, reason);
    return new Gson().fromJson(json, SubmitSnapshotResponse.class);
  }

  /** A response that stored the submission and acknowledged the named blocks. */
  private static SubmitSnapshotResponse storedBlocks(String... blocks) {
    StringBuilder list = new StringBuilder();
    for (String block : blocks) {
      if (list.length() > 0) {
        list.append(",");
      }
      list.append("\"").append(block).append("\"");
    }
    String json =
        String.format(
            "{\"accepted\":true,\"stored\":true,\"reason\":\"persisted\",\"storedBlocks\":[%s]}",
            list);
    return new Gson().fromJson(json, SubmitSnapshotResponse.class);
  }

  @Test
  public void submitsWhenSkillsChangedAndQuestsAreNotIncluded() {
    // The opt-in is off: the capture path passes a null quests map. A skill change
    // alone is still enough to submit.
    assertTrue(
        shouldSubmit(
            new SkillBaseline(),
            skills("attack", 100),
            new QuestBaseline(),
            null,
            new DiaryBaseline(),
            null, new CollectionLogBaseline(), 0));
  }

  @Test
  public void ignoresAQuestChangeWhenQuestCaptureIsOff() {
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    // Skills are unchanged and quests are null (opt-in off): a quest change can never
    // be observed, so nothing should trigger a submit.
    assertFalse(
        shouldSubmit(
            skillBaseline,
            skills("attack", 100),
            new QuestBaseline(),
            null,
            new DiaryBaseline(),
            null, new CollectionLogBaseline(), 0));
  }

  @Test
  public void submitsOnAQuestChangeAloneWhenQuestCaptureIsOn() {
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    // Skills are identical to the baseline; only the quest state changed.
    assertTrue(
        shouldSubmit(
            skillBaseline,
            skills("attack", 100),
            new QuestBaseline(),
            quests("COOKS_ASSISTANT", "IN_PROGRESS"),
            new DiaryBaseline(),
            null, new CollectionLogBaseline(), 0));
  }

  @Test
  public void doesNotSubmitWhenNeitherSkillsNorQuestsChanged() {
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    QuestBaseline questBaseline = new QuestBaseline();
    questBaseline.advance(quests("COOKS_ASSISTANT", "IN_PROGRESS"));
    assertFalse(
        shouldSubmit(
            skillBaseline,
            skills("attack", 100),
            questBaseline,
            quests("COOKS_ASSISTANT", "IN_PROGRESS"),
            new DiaryBaseline(),
            null, new CollectionLogBaseline(), 0));
  }

  @Test
  public void ignoresADiaryChangeWhenDiaryCaptureIsOff() {
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    // Skills and quests are unchanged and diaries are null (opt-in off): a diary
    // change can never be observed, so nothing should trigger a submit.
    assertFalse(
        shouldSubmit(
            skillBaseline,
            skills("attack", 100),
            new QuestBaseline(),
            null,
            new DiaryBaseline(),
            null, new CollectionLogBaseline(), 0));
  }

  @Test
  public void submitsOnADiaryChangeAloneWhenDiaryCaptureIsOn() {
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    // Skills are identical to the baseline; only the diary state changed.
    assertTrue(
        shouldSubmit(
            skillBaseline,
            skills("attack", 100),
            new QuestBaseline(),
            null,
            new DiaryBaseline(),
            diaries("ARDOUGNE_EASY", "COMPLETE"),
            new CollectionLogBaseline(),
            0));
  }

  @Test
  public void submitsWhenOnlyTheCollectionLogGrew() {
    // The case that made this gate necessary. A player syncs their log and gains no
    // xp; without the collection log in the gate the submission would wait for the
    // next level, which is exactly when they are not looking at their log.
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    assertTrue(
        shouldSubmit(
            skillBaseline,
            skills("attack", 100),
            new QuestBaseline(),
            null,
            new DiaryBaseline(),
            null,
            new CollectionLogBaseline(),
            42));
  }

  @Test
  public void doesNotSubmitForAnEmptyCollectionLog() {
    // Nothing observed is not nothing owned, so an empty log is not a change worth
    // sending on every capture.
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    assertFalse(
        shouldSubmit(
            skillBaseline,
            skills("attack", 100),
            new QuestBaseline(),
            null,
            new DiaryBaseline(),
            null,
            new CollectionLogBaseline(),
            0));
  }

  @Test
  public void doesNotSubmitWhenNeitherSkillsNorQuestsNorDiariesChanged() {
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    QuestBaseline questBaseline = new QuestBaseline();
    questBaseline.advance(quests("COOKS_ASSISTANT", "IN_PROGRESS"));
    DiaryBaseline diaryBaseline = new DiaryBaseline();
    diaryBaseline.advance(diaries("ARDOUGNE_EASY", "COMPLETE"));
    assertFalse(
        shouldSubmit(
            skillBaseline,
            skills("attack", 100),
            questBaseline,
            quests("COOKS_ASSISTANT", "IN_PROGRESS"),
            diaryBaseline,
            diaries("ARDOUGNE_EASY", "COMPLETE"),
            new CollectionLogBaseline(),
            0));
  }

  @Test
  public void advancesSkillsWhenTheServerAcknowledgedTheBlock() {
    assertTrue(BankstandPlugin.shouldAdvanceSkills(storedBlocks("skills")));
  }

  @Test
  public void doesNotAdvanceSkillsOnACooldown() {
    assertFalse(BankstandPlugin.shouldAdvanceSkills(response(true, false, "cooldown")));
  }

  @Test
  public void doesNotAdvanceSkillsWhenTheAccountIsUnclaimed() {
    // The real server shape: accepted, HTTP 200, but nothing stored. Asserting this
    // with accepted=false would pass while missing the bug, because the server never
    // returns accepted=false for a reason it recognises.
    assertFalse(BankstandPlugin.shouldAdvanceSkills(response(true, false, "unclaimed")));
  }

  @Test
  public void doesNotAdvanceSkillsWhenIngestIsNotApplied() {
    assertFalse(BankstandPlugin.shouldAdvanceSkills(response(true, false, "not_applied")));
  }

  @Test
  public void advancesQuestsWhenTheServerAcknowledgedTheBlock() {
    assertTrue(BankstandPlugin.shouldAdvanceQuests(storedBlocks("skills", "quests"), true));
  }

  @Test
  public void doesNotAdvanceQuestsWhenStoredButTheBlockWasNotAcknowledged() {
    // The submission stored (skills were fresh) but the server dropped the quests block
    // because its rollout flag is off. Advancing here would acknowledge data that was
    // never written.
    assertFalse(BankstandPlugin.shouldAdvanceQuests(storedBlocks("skills"), true));
  }

  @Test
  public void doesNotAdvanceQuestsWhenTheServerSendsNoAcknowledgement() {
    // An older server omits the field entirely. Treating that as "not written" re-sends
    // rather than risking a silent loss.
    assertFalse(BankstandPlugin.shouldAdvanceQuests(response(true, true, "persisted"), true));
  }

  @Test
  public void doesNotAdvanceQuestsWhenIncludedButNotStored() {
    assertFalse(BankstandPlugin.shouldAdvanceQuests(response(true, false, "not_applied"), true));
  }

  @Test
  public void doesNotAdvanceQuestsWhenIncludedButStale() {
    assertFalse(BankstandPlugin.shouldAdvanceQuests(response(true, false, "stale"), true));
  }

  @Test
  public void doesNotAdvanceQuestsWhenNotIncludedEvenIfAcknowledged() {
    assertFalse(BankstandPlugin.shouldAdvanceQuests(storedBlocks("skills", "quests"), false));
  }

  @Test
  public void advancesDiariesWhenTheServerAcknowledgedTheBlock() {
    assertTrue(BankstandPlugin.shouldAdvanceDiaries(storedBlocks("skills", "diaries"), true));
  }

  @Test
  public void doesNotAdvanceDiariesWhenStoredButTheBlockWasNotAcknowledged() {
    // The case that loses data if it advances: a completed diary tier is a one-shot
    // fact, so a false acknowledgement means it is never re-sent.
    assertFalse(BankstandPlugin.shouldAdvanceDiaries(storedBlocks("skills", "quests"), true));
  }

  @Test
  public void doesNotAdvanceDiariesWhenTheServerSendsNoAcknowledgement() {
    assertFalse(BankstandPlugin.shouldAdvanceDiaries(response(true, true, "persisted"), true));
  }

  @Test
  public void doesNotAdvanceDiariesWhenIncludedButNotStored() {
    assertFalse(BankstandPlugin.shouldAdvanceDiaries(response(true, false, "not_applied"), true));
  }

  @Test
  public void doesNotAdvanceDiariesWhenNotIncludedEvenIfAcknowledged() {
    assertFalse(BankstandPlugin.shouldAdvanceDiaries(storedBlocks("skills", "diaries"), false));
  }
}
