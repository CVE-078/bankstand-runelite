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
 * Developer-mode entry point ({@code ./gradlew run}) that sideloads the plugin; test sources
 * only, never shipped. Also tests the plugin's package-private capture-decision logic.
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

  private static Set<Integer> logItems(int count) {
    Set<Integer> ids = new LinkedHashSet<>();
    for (int i = 1; i <= count; i++) {
      ids.add(i);
    }
    return ids;
  }

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

  /** Built through Gson, since EventAck has no public constructor. */
  private static EventAck ack(String id, String outcome, String reason) {
    String json =
        reason == null
            ? String.format("{\"id\":\"%s\",\"outcome\":\"%s\"}", id, outcome)
            : String.format(
                "{\"id\":\"%s\",\"outcome\":\"%s\",\"reason\":\"%s\"}", id, outcome, reason);
    return new Gson().fromJson(json, EventAck.class);
  }

  /** The submit decision alone. The per-block choice is covered by {@link SubmitPlanTest}. */
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
    // Without the log in the gate, a synced log would wait for the next xp gain.
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
    // Nothing observed is not nothing owned, so an empty log is not a change.
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
    // The real server shape: accepted, HTTP 200, nothing stored.
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
    // Stored overall, but the quests block was dropped by its rollout flag.
    assertFalse(BankstandPlugin.shouldAdvanceQuests(storedBlocks("skills"), true));
  }

  @Test
  public void doesNotAdvanceQuestsWhenTheServerSendsNoAcknowledgement() {
    // An older server omits the field: re-send rather than risk a silent loss.
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
    // A false ack on a one-shot diary tier means it is never re-sent.
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

  // --- Event outbox draining ---

  @Test
  public void chunkEventsKeepsAGroupAtExactlyTheCapInOneChunk() {
    List<List<TransientEvent>> chunks = BankstandPlugin.chunkEvents(events(50), 50);

    assertEquals(1, chunks.size());
    assertEquals(50, chunks.get(0).size());
  }

  @Test
  public void chunkEventsSplitsAGroupOverTheCapIntoTwoChunks() {
    // Over the server's per-request cap, the whole request is rejected every time.
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
    // Age only increases, so a stale event can never become deliverable.
    Set<String> ids = BankstandPlugin.idsToAck(Collections.singletonList(ack("a", "rejected", "stale")));

    assertTrue(ids.contains("a"));
  }

  @Test
  public void idsToAckExcludesARejectedNotAppliedOutcome() {
    // The capability flag can be turned on later, so this stays worth retrying.
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
    assertFalse(BankstandPlugin.NOTABLE_UNTRADEABLE_ALLOWLIST.isEmpty());
  }

  @Test
  public void notableUntradeableAllowlistUsesRealInGameCasing() {
    // Exact cache names (e.g. "Baby Mole", not "Baby mole"), since the check is Set#contains.
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
    // Exact count, so a newly added skill fails here rather than going silently unsent.
    assertEquals(24, BankstandPlugin.CAPTURED_SKILLS.size());
    assertTrue(BankstandPlugin.CAPTURED_SKILLS.contains(Skill.SAILING));
  }
}
