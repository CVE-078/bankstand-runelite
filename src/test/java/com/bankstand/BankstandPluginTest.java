package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.EventAck;
import com.bankstand.dto.SubmitSnapshotResponse;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
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

  /** {@code count} distinct transient events, ids ordered "id-0".."id-(count-1)". */
  private static List<TransientEvent> events(int count) {
    List<TransientEvent> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("itemName", "Dragon warhammer");
      list.add(
          new TransientEvent(
              "id-" + i, TransientEvent.TYPE_NOTABLE_DROP, "2026-08-10T10:00:00Z", payload));
    }
    return list;
  }

  /** An {@link EventAck} as Gson would populate it from the wire, since the type has
   *  no public constructor of its own. */
  private static EventAck ack(String id, String outcome, String reason) {
    String json =
        reason == null
            ? String.format("{\"id\":\"%s\",\"outcome\":\"%s\"}", id, outcome)
            : String.format(
                "{\"id\":\"%s\",\"outcome\":\"%s\",\"reason\":\"%s\"}", id, outcome, reason);
    return new Gson().fromJson(json, EventAck.class);
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

  // --- Event outbox draining: chunking against the server's per-request cap, and
  // which acked ids stop being retried (the two halves of the #770 review fix). ---

  @Test
  public void chunkEventsKeepsAGroupAtExactlyTheCapInOneChunk() {
    List<List<TransientEvent>> chunks = BankstandPlugin.chunkEvents(events(50), 50);

    assertEquals(1, chunks.size());
    assertEquals(50, chunks.get(0).size());
  }

  @Test
  public void chunkEventsSplitsAGroupOverTheCapIntoTwoChunks() {
    // The bug this guards: a group of 51 submitted whole gets one 400 for the whole
    // group, forever, because the server's own MAX_EVENTS_PER_BATCH is 50.
    List<List<TransientEvent>> chunks = BankstandPlugin.chunkEvents(events(51), 50);

    assertEquals(2, chunks.size());
    assertEquals(50, chunks.get(0).size());
    assertEquals(1, chunks.get(1).size());
  }

  @Test
  public void chunkEventsPreservesOrderWithinAndAcrossChunks() {
    List<List<TransientEvent>> chunks = BankstandPlugin.chunkEvents(events(120), 50);

    assertEquals(3, chunks.size());
    assertEquals("id-0", chunks.get(0).get(0).getId());
    assertEquals("id-49", chunks.get(0).get(49).getId());
    assertEquals("id-50", chunks.get(1).get(0).getId());
    assertEquals("id-99", chunks.get(1).get(49).getId());
    assertEquals("id-100", chunks.get(2).get(0).getId());
    assertEquals("id-119", chunks.get(2).get(19).getId());
  }

  @Test
  public void idsToAckIncludesStoredAndDuplicateOutcomes() {
    Set<String> ids =
        BankstandPlugin.idsToAck(Arrays.asList(ack("a", "stored", null), ack("b", "duplicate", null)));

    assertTrue(ids.contains("a"));
    assertTrue(ids.contains("b"));
  }

  @Test
  public void idsToAckIncludesARejectedStaleOutcome() {
    // Aged past the server's retention window: age only increases, so resubmitting
    // the exact same event can never become deliverable. Leaving it queued would
    // waste outbox capacity forever, bounded only by the 200-entry cap eventually
    // evicting it.
    Set<String> ids = BankstandPlugin.idsToAck(Collections.singletonList(ack("a", "rejected", "stale")));

    assertTrue(ids.contains("a"));
  }

  @Test
  public void idsToAckExcludesARejectedNotAppliedOutcome() {
    // A capability flag can be turned on later, which makes this reason legitimately
    // worth retrying, unlike a stale rejection.
    Set<String> ids =
        BankstandPlugin.idsToAck(Collections.singletonList(ack("a", "rejected", "not_applied")));

    assertTrue(ids.isEmpty());
  }

  @Test
  public void idsToAckExcludesAnUnrecognisedOutcome() {
    Set<String> ids = BankstandPlugin.idsToAck(Collections.singletonList(ack("a", "pending", null)));

    assertTrue(ids.isEmpty());
  }

  @Test
  public void notableUntradeableAllowlistIsNotEmpty() {
    // #1096: the allowlist shipped empty for a while, so a tradeable-value-only
    // notable drop capture silently missed every pet/untradeable unique. This just
    // guards against that regressing again, not against a specific curation choice.
    assertFalse(BankstandPlugin.NOTABLE_UNTRADEABLE_ALLOWLIST.isEmpty());
  }

  @Test
  public void notableUntradeableAllowlistUsesRealInGameCasing() {
    // Every entry was cross-checked against the game's own cache item definitions
    // (exact name text, tradeable=false), not typed from a wiki-disambiguated
    // collection-log display name: a first draft pulled from that source had
    // inconsistent Title Case on roughly a third of its entries (e.g. "Baby mole"
    // vs. the real "Baby Mole"), which would have silently never matched a real
    // drop, since the check this backs is a plain Set#contains against
    // ItemComposition#getName(). Spot-checking a representative sample here, not
    // exhaustively: the point is to catch a wholesale reintroduction of that class
    // of mistake, not to duplicate the verification script's own coverage.
    Set<String> allowlist = BankstandPlugin.NOTABLE_UNTRADEABLE_ALLOWLIST;
    assertTrue(allowlist.contains("Baby Mole"));
    assertTrue(allowlist.contains("Pet Kree'arra"));
    assertTrue(allowlist.contains("TzRek-Jad"));
    assertTrue(allowlist.contains("Rift guardian"));
    assertFalse(allowlist.contains("Baby mole"));
    assertFalse(allowlist.contains("Rift guardian (fire)"));
  }

  @Test
  public void capturedSkillsIncludesSailing() {
    // Sailing was OSRS's 24th skill and this allowlist missed it for a while,
    // silently never reading or sending its XP even though nothing else in the
    // pipeline was broken. This guards against that regressing again. The exact
    // count (24) is asserted too, since a future skill addition should fail this
    // test rather than pass it silently.
    assertEquals(24, BankstandPlugin.CAPTURED_SKILLS.size());
    assertTrue(BankstandPlugin.CAPTURED_SKILLS.contains(Skill.SAILING));
  }
}
