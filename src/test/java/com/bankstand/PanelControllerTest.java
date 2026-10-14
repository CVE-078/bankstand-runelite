package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PanelControllerTest {

  /** Fixed plugin state; fields are flipped per test. */
  private static final class FakeInputs implements PanelController.Inputs {
    boolean paired = true;
    String name = "Crusty Jobby";
    EnumSet<Capability> enabled = EnumSet.of(Capability.SKILLS, Capability.QUESTS);
    boolean halted;
    int accountTypeReads;

    @Override
    public boolean isPaired() {
      return paired;
    }

    @Override
    public String serverUrl() {
      return "https://example.test";
    }

    @Override
    public String playerName() {
      return name;
    }

    @Override
    public EnumSet<Capability> enabledCapabilities() {
      return enabled;
    }

    @Override
    public boolean isSubmitHalted() {
      return halted;
    }

    @Override
    public int collectionLogSize() {
      return -1;
    }

    @Override
    public String accountType() {
      accountTypeReads++;
      return "Account type: ironman.";
    }

    @Override
    public String manifestDescription() {
      return "skills, quests";
    }

    @Override
    public boolean offerSessions() {
      return true;
    }
  }

  private final FakeInputs inputs = new FakeInputs();
  private final PanelController controller = new PanelController(Runnable::run, inputs);

  @Test
  public void buildsTheModelFromPluginStateAndItsOwnStamps() {
    controller.stamp(Capability.QUESTS);

    PanelModel model = controller.buildModel();

    assertTrue(model.paired);
    assertEquals("Crusty Jobby", model.linkedName);
    assertEquals("https://example.test", model.serverUrl);
    assertTrue(model.offerSessions);
    assertEquals(2, model.capabilities.size());
    assertNull(model.capabilities.get(0).lastSyncedAtMs);
    assertTrue(model.capabilities.get(1).lastSyncedAtMs != null);
    assertNull(model.lastFailureReason);
  }

  @Test
  public void aFailureShowsUntilTheNextSuccess() {
    controller.submitFailed("Server unreachable.");
    assertEquals("Server unreachable.", controller.buildModel().lastFailureReason);

    controller.submitSucceeded();
    assertNull(controller.buildModel().lastFailureReason);
  }

  @Test
  public void recordSyncedStampsAndLogsOnlyWhatAdvanced() {
    controller.recordSynced(EnumSet.of(Capability.SKILLS, Capability.QUESTS), false);

    Map<String, Long> stamps = controller.syncedAt();
    assertFalse(stamps.containsKey(Capability.SKILLS.key));
    assertTrue(stamps.containsKey(Capability.QUESTS.key));
    assertEquals(1, controller.buildModel().recentActivity.size());
  }

  @Test
  public void aNewAccountStartsWithNoStampsOrActivity() {
    controller.recordSynced(EnumSet.of(Capability.QUESTS), false);

    controller.clearForNewAccount();

    assertTrue(controller.syncedAt().isEmpty());
    assertTrue(controller.buildModel().recentActivity.isEmpty());
  }

  @Test
  public void restoreReplacesTheStamps() {
    controller.stamp(Capability.SKILLS);

    controller.restoreSyncedAt(Map.of(Capability.QUESTS.key, 5L));

    assertEquals(Map.of(Capability.QUESTS.key, 5L), controller.syncedAt());
  }

  @Test
  public void statusReadsTheAccountTypeOnlyWithACharacterLoaded() {
    List<String> lines = controller.statusLines();
    assertTrue(lines.contains("Account type: ironman."));
    assertEquals(1, inputs.accountTypeReads);

    inputs.name = null;
    assertFalse(controller.statusLines().contains("Account type: ironman."));
    assertEquals(1, inputs.accountTypeReads);
  }

  @Test
  public void statusSaysWhenSubmissionIsHalted() {
    inputs.halted = true;

    assertTrue(
        controller.statusLines().stream().anyMatch(l -> l.contains("authentication failed")));
  }

  @Test
  public void refreshDoesNothingWhileDetached() {
    PanelController detached =
        new PanelController(
            r -> {
              throw new AssertionError("must not touch the client thread");
            },
            inputs);

    detached.refresh();
  }

  @Test
  public void describesAgeCoarsely() {
    assertEquals("less than a minute ago", PanelController.describeAge(59_000L));
    assertEquals("1 minute ago", PanelController.describeAge(60_000L));
    assertEquals("59 minutes ago", PanelController.describeAge(59 * 60_000L));
    assertEquals("1 hour ago", PanelController.describeAge(119 * 60_000L));
    assertEquals("2 hours ago", PanelController.describeAge(120 * 60_000L));
  }
}
