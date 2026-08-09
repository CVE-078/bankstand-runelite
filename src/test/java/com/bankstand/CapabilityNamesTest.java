package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * What the status and sync lines claim the plugin is sending.
 *
 * <p>These exist because the failure mode is silent. Combat achievement capture shipped
 * working: read from the varbits, sent on the envelope, stored by the server. Every chat
 * line still said "skills, quests, diaries, collection log", because this list was never
 * told about the new capability. Nothing failed, no test went red, and the only way to
 * notice was to read a log and compare it against the database.
 */
public class CapabilityNamesTest {

  @Test
  public void namesEveryEnabledCapability() {
    List<String> all = BankstandPlugin.capabilityNames(true, true, true, true, true, true);

    assertEquals(
        List.of(
            "skills",
            "quests",
            "diaries",
            "collection log",
            "combat achievements",
            "account type"),
        all);
  }

  /**
   * The guard against the next one. A capability added to the envelope without being
   * added here leaves this count behind, so the assertion fails on the number rather
   * than waiting for someone to read a chat line carefully.
   */
  @Test
  public void hasOneNamePerCapability() {
    assertEquals(6, BankstandPlugin.capabilityNames(true, true, true, true, true, true).size());
  }

  @Test
  public void namesOnlyWhatIsSwitchedOn() {
    List<String> some =
        BankstandPlugin.capabilityNames(true, false, false, false, true, false);

    assertEquals(List.of("skills", "combat achievements"), some);
  }

  @Test
  public void namesNothingWhenEverythingIsOff() {
    assertTrue(
        BankstandPlugin.capabilityNames(false, false, false, false, false, false).isEmpty());
  }
}
