package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * What persistence is actually for, exercised end to end over the real baselines, the
 * real accumulator and a real file: a client restart must not re-send what the server
 * already took, and the collection log must not be forgotten.
 *
 * <p>The plugin itself needs a live {@code Client}, so the composition is assembled here
 * the way the plugin assembles it. That is the point: every piece below is the shipped
 * one, and only the wiring is local.
 */
public class AckedStateRestartTest {

  private static final long ACCOUNT = 4_242L;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private File file;

  @Before
  public void setUp() {
    file = new File(folder.getRoot(), "acked-state.json");
  }

  /** One client's worth of the state a capture reads and a restart has to rebuild. */
  private static class Client {
    final SkillBaseline skills = new SkillBaseline();
    final QuestBaseline quests = new QuestBaseline();
    final DiaryBaseline diaries = new DiaryBaseline();
    final CollectionLogAccumulator log = new CollectionLogAccumulator();
    final CollectionLogBaseline logBaseline = new CollectionLogBaseline();

    BankstandPlugin.SubmitPlan plan(
        Map<String, Integer> s, Map<String, String> q, Map<String, String> d) {
      return BankstandPlugin.plan(skills, s, quests, q, diaries, d, logBaseline, log.observed());
    }

    /** The server accepted everything in this capture. */
    void ackAll(Map<String, Integer> s, Map<String, String> q, Map<String, String> d) {
      skills.advance(s);
      quests.advance(q);
      diaries.advance(d);
      logBaseline.advance(log.size());
    }

    void save(AckedStateStore store) {
      AckedState state = AckedState.empty();
      state.setSkills(skills.ackedDigest());
      state.setQuests(quests.ackedDigest());
      state.setDiaries(diaries.ackedDigest());
      state.setCollectionLogItems(log.observed());
      state.setCollectionLogAcked(logBaseline.ackedCount());
      store.save(ACCOUNT, state);
    }

    void restore(AckedStateStore store) {
      AckedState state = store.load(ACCOUNT);
      skills.restore(state.getSkills());
      quests.restore(state.getQuests());
      diaries.restore(state.getDiaries());
      log.restore(state.getCollectionLogItems());
      logBaseline.restore(state.getCollectionLogAcked());
    }
  }

  private static Map<String, Integer> skills(int attackXp) {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("attack", attackXp);
    m.put("cooking", 500);
    return m;
  }

  private static Map<String, String> quests(String state) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("COOKS_ASSISTANT", state);
    return m;
  }

  private static Map<String, String> diaries() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("VARROCK_EASY", "COMPLETE");
    return m;
  }

  private static Set<Integer> wholeLog() {
    Set<Integer> ids = new LinkedHashSet<>();
    for (int i = 1; i <= 1700; i++) {
      ids.add(i);
    }
    return ids;
  }

  private AckedStateStore store() {
    return new AckedStateStore(file, new Gson());
  }

  /** The headline. Today every client start re-sends every block once; it must not. */
  @Test
  public void aRestartWithNothingChangedSendsNothing() {
    Client first = new Client();
    first.log.restore(wholeLog());
    first.ackAll(skills(100), quests("FINISHED"), diaries());
    first.save(store());

    Client afterRestart = new Client();
    afterRestart.log.restore(wholeLog());
    afterRestart.restore(store());

    assertFalse(afterRestart.plan(skills(100), quests("FINISHED"), diaries()).shouldSubmit());
  }

  @Test
  public void aRestartStillSendsWhatActuallyChangedWhileAway() {
    Client first = new Client();
    first.ackAll(skills(100), quests("FINISHED"), diaries());
    first.save(store());

    Client afterRestart = new Client();
    afterRestart.restore(store());
    BankstandPlugin.SubmitPlan plan =
        afterRestart.plan(skills(999), quests("FINISHED"), diaries());

    assertTrue(plan.shouldSubmit());
    assertFalse(plan.includesQuests());
    assertFalse(plan.includesDiaries());
  }

  /** The observations that cannot be re-read from the client if they are lost. */
  @Test
  public void aRestartRemembersTheCollectionLogItself() {
    Client first = new Client();
    first.log.restore(wholeLog());
    first.ackAll(skills(100), quests("FINISHED"), diaries());
    first.save(store());

    Client afterRestart = new Client();
    afterRestart.restore(store());

    assertEquals(1700, afterRestart.log.size());
  }

  /**
   * The trap, pinned. A count is a valid change gate only over a monotonically growing
   * observed set. Restore the count while the accumulator starts empty, and the next
   * partial browse reads as a change, sends a short block, acks, and does it again every
   * session. Restoring both is what keeps the invariant the count rests on.
   */
  @Test
  public void restoringTheCountWithoutTheAccumulatorWouldChurn() {
    Client first = new Client();
    first.log.restore(wholeLog());
    first.ackAll(skills(100), quests("FINISHED"), diaries());
    first.save(store());

    // The broken half: baseline restored, accumulator left empty, as it would be after
    // a restart if only the count persisted.
    Client broken = new Client();
    broken.logBaseline.restore(store().load(ACCOUNT).getCollectionLogAcked());
    for (int id = 1; id <= 30; id++) {
      broken.log.observe(id);
    }
    assertTrue(broken.plan(skills(100), quests("FINISHED"), diaries()).includesCollectionLog());

    // Both restored, which is what ships: the same browse reveals nothing new.
    Client correct = new Client();
    correct.restore(store());
    for (int id = 1; id <= 30; id++) {
      correct.log.observe(id);
    }
    assertFalse(correct.plan(skills(100), quests("FINISHED"), diaries()).includesCollectionLog());
  }

  /** Growth after a restart still goes out, as a whole block, never as a delta. */
  @Test
  public void aLogThatGrowsAfterARestartIsSentWhole() {
    Client first = new Client();
    first.log.restore(wholeLog());
    first.ackAll(skills(100), quests("FINISHED"), diaries());
    first.save(store());

    Client afterRestart = new Client();
    afterRestart.restore(store());
    afterRestart.log.observe(9001);

    assertTrue(
        afterRestart.plan(skills(100), quests("FINISHED"), diaries()).includesCollectionLog());
    assertEquals(1701, afterRestart.log.size());
  }

  /**
   * An unsent log is not treated as delivered. This is why the acked count is stored
   * rather than derived from the restored accumulator, which would read 1700 observed as
   * 1700 acknowledged and never send them.
   */
  @Test
  public void aLogWhoseSubmitFailedIsStillSentAfterARestart() {
    Client first = new Client();
    first.log.restore(wholeLog());
    // Skills acked, the log block was not: exactly what a per-block ack reports when
    // that capability's storage dropped it.
    first.skills.advance(skills(100));
    first.quests.advance(quests("FINISHED"));
    first.diaries.advance(diaries());
    first.save(store());

    Client afterRestart = new Client();
    afterRestart.restore(store());

    assertTrue(
        afterRestart.plan(skills(100), quests("FINISHED"), diaries()).includesCollectionLog());
  }

  @Test
  public void anUnreadableFileCostsOneResendAndNothingMore() throws IOException {
    Client first = new Client();
    first.log.restore(wholeLog());
    first.ackAll(skills(100), quests("FINISHED"), diaries());
    first.save(store());
    java.nio.file.Files.write(file.toPath(), "corrupt".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    Client afterRestart = new Client();
    afterRestart.restore(store());

    // Everything reads as changed, which is exactly what the client did before any of
    // this persisted. The safe direction.
    assertTrue(afterRestart.plan(skills(100), quests("FINISHED"), diaries()).shouldSubmit());
  }

  @Test
  public void anotherCharacterOnTheSameMachineIsUnaffected() {
    Client first = new Client();
    first.log.restore(wholeLog());
    first.ackAll(skills(100), quests("FINISHED"), diaries());
    first.save(store());

    AckedState other = store().load(9_999L);

    assertTrue(other.getCollectionLogItems().isEmpty());
    assertEquals(-1, other.getCollectionLogAcked());
  }
}
