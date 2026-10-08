package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** The status and sync lines must name every capability the plugin actually sends. */
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

  /** A capability added to the envelope but not here fails on the count. */
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
    // Drops drain through the event outbox, but the status line has one list for everything.
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
