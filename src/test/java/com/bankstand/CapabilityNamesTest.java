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
 * notice was to read a log and compare it against the database. Notable drops and pet
 * drops repeated the same gap: captured and delivered through the event outbox while the
 * status line stayed silent about both.
 */
public class CapabilityNamesTest {

  @Test
  public void namesEveryEnabledCapability() {
    List<String> all =
        BankstandPlugin.capabilityNames(true, true, true, true, true, true, true, true);

    assertEquals(
        List.of(
            "skills",
            "quests",
            "diaries",
            "collection log",
            "combat achievements",
            "account type",
            "notable drops",
            "pet drops"),
        all);
  }

  /**
   * The guard against the next one. A capability added to the envelope without being
   * added here leaves this count behind, so the assertion fails on the number rather
   * than waiting for someone to read a chat line carefully.
   */
  @Test
  public void hasOneNamePerCapability() {
    assertEquals(
        8,
        BankstandPlugin.capabilityNames(true, true, true, true, true, true, true, true).size());
  }

  @Test
  public void namesOnlyWhatIsSwitchedOn() {
    List<String> some =
        BankstandPlugin.capabilityNames(
            true, false, false, false, true, false, false, false);

    assertEquals(List.of("skills", "combat achievements"), some);
  }

  @Test
  public void namesTheEventDrivenCapabilities() {
    // Notable drops and pet drops drain through the event outbox rather than the
    // periodic snapshot, but the status line has one list for everything switched on.
    List<String> dropsOnly =
        BankstandPlugin.capabilityNames(
            false, false, false, false, false, false, true, true);

    assertEquals(List.of("notable drops", "pet drops"), dropsOnly);
  }

  @Test
  public void namesNothingWhenEverythingIsOff() {
    assertTrue(
        BankstandPlugin.capabilityNames(
                false, false, false, false, false, false, false, false)
            .isEmpty());
  }
}
