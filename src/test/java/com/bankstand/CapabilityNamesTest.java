package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

/** The status and sync lines must name every capability the plugin actually sends. */
public class CapabilityNamesTest {

  @Test
  public void namesEveryEnabledCapability() {
    List<String> all =
        BankstandPlugin.capabilityNames(EnumSet.allOf(Capability.class));

    assertEquals(
        List.of(
            "skills",
            "quests",
            "diaries",
            "collection log",
            "combat achievements",
            "account type",
            "notable drops",
            "pet drops",
            "play sessions",
            "full loot",
            "slayer task"),
        all);
  }

  /** A capability added to the envelope but not here fails on the count. */
  @Test
  public void hasOneNamePerCapability() {
    assertEquals(
        11,
        BankstandPlugin.capabilityNames(EnumSet.allOf(Capability.class)).size());
  }

  @Test
  public void namesOnlyWhatIsSwitchedOn() {
    List<String> some =
        BankstandPlugin.capabilityNames(
            EnumSet.of(Capability.SKILLS, Capability.COMBAT_ACHIEVEMENTS));

    assertEquals(List.of("skills", "combat achievements"), some);
  }

  @Test
  public void namesTheEventDrivenCapabilities() {
    // Drops drain through the event outbox, but the status line has one list for everything.
    List<String> dropsOnly =
        BankstandPlugin.capabilityNames(
            EnumSet.of(Capability.NOTABLE_DROPS, Capability.PET_DROPS));

    assertEquals(List.of("notable drops", "pet drops"), dropsOnly);
  }

  @Test
  public void namesTheLiveSessionCapabilities() {
    List<String> live =
        BankstandPlugin.capabilityNames(
            EnumSet.of(Capability.SESSIONS, Capability.LOOT, Capability.SLAYER));

    assertEquals(List.of("play sessions", "full loot", "slayer task"), live);
  }

  @Test
  public void namesNothingWhenEverythingIsOff() {
    assertTrue(
        BankstandPlugin.capabilityNames(
                EnumSet.noneOf(Capability.class))
            .isEmpty());
  }
}
