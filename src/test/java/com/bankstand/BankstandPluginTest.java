package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.dto.SubmitSnapshotResponse;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
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

  private static SubmitSnapshotResponse response(boolean accepted, boolean stored, String reason) {
    String json =
        String.format(
            "{\"accepted\":%s,\"stored\":%s,\"reason\":\"%s\"}", accepted, stored, reason);
    return new Gson().fromJson(json, SubmitSnapshotResponse.class);
  }

  @Test
  public void submitsWhenSkillsChangedAndQuestsAreNotIncluded() {
    // The opt-in is off: the capture path passes a null quests map. A skill change
    // alone is still enough to submit.
    assertTrue(
        BankstandPlugin.shouldSubmit(
            new SkillBaseline(), skills("attack", 100), new QuestBaseline(), null));
  }

  @Test
  public void ignoresAQuestChangeWhenSharingIsOff() {
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    // Skills are unchanged and quests are null (opt-in off): a quest change can never
    // be observed, so nothing should trigger a submit.
    assertFalse(
        BankstandPlugin.shouldSubmit(
            skillBaseline, skills("attack", 100), new QuestBaseline(), null));
  }

  @Test
  public void submitsOnAQuestChangeAloneWhenSharingIsOn() {
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    // Skills are identical to the baseline; only the quest state changed.
    assertTrue(
        BankstandPlugin.shouldSubmit(
            skillBaseline,
            skills("attack", 100),
            new QuestBaseline(),
            quests("COOKS_ASSISTANT", "IN_PROGRESS")));
  }

  @Test
  public void doesNotSubmitWhenNeitherSkillsNorQuestsChanged() {
    SkillBaseline skillBaseline = new SkillBaseline();
    skillBaseline.advance(skills("attack", 100));
    QuestBaseline questBaseline = new QuestBaseline();
    questBaseline.advance(quests("COOKS_ASSISTANT", "IN_PROGRESS"));
    assertFalse(
        BankstandPlugin.shouldSubmit(
            skillBaseline,
            skills("attack", 100),
            questBaseline,
            quests("COOKS_ASSISTANT", "IN_PROGRESS")));
  }

  @Test
  public void aStoredResponseIsAnAcceptThatIsNotOnCooldown() {
    assertTrue(BankstandPlugin.isStoredAccept(response(true, true, "persisted")));
  }

  @Test
  public void aCooldownResponseIsNotAStoredAccept() {
    assertFalse(BankstandPlugin.isStoredAccept(response(true, false, "cooldown")));
  }

  @Test
  public void aRejectedResponseIsNotAStoredAccept() {
    assertFalse(BankstandPlugin.isStoredAccept(response(false, false, "unclaimed")));
  }
}
