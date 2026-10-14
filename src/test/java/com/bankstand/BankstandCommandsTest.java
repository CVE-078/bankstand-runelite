package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class BankstandCommandsTest {

  private final List<String> calls = new ArrayList<>();
  private final List<String> notices = new ArrayList<>();

  private final BankstandCommands commands =
      new BankstandCommands(
          null,
          notices::add,
          () -> List.of("status one", "status two"),
          new BankstandCommands.Actions() {
            @Override
            public void sync() {
              calls.add("sync");
            }

            @Override
            public void link() {
              calls.add("link");
            }

            @Override
            public void armCollectionLogRead() {
              calls.add("log");
            }

            @Override
            public void disconnect() {
              calls.add("disconnect");
            }
          });

  @Test
  public void ignoresOtherCommands() {
    commands.handle("bank", new String[] {"sync"});

    assertTrue(calls.isEmpty());
    assertTrue(notices.isEmpty());
  }

  @Test
  public void noArgumentPrintsStatus() {
    commands.handle("bstand", new String[0]);

    assertEquals(List.of("status one", "status two"), notices);
  }

  @Test
  public void routesEachActionToThePlugin() {
    commands.handle("bstand", new String[] {"sync"});
    commands.handle("bstand", new String[] {"link"});
    commands.handle("stand", new String[] {"log"});

    assertEquals(List.of("sync", "link", "log"), calls);
  }

  @Test
  public void repairDisconnectsThenSaysHowToReconnect() {
    commands.handle("bstand", new String[] {"repair"});

    assertEquals(List.of("disconnect"), calls);
    assertEquals(
        List.of("Paste a fresh pairing code in the Bankstand settings to reconnect."), notices);
  }

  @Test
  public void helpPrintsTheHelpLines() {
    commands.handle("bstand", new String[] {"help"});

    assertEquals(StatusReport.helpLines(), notices);
  }

  @Test
  public void anUnknownActionSaysSo() {
    commands.handle("bstand", new String[] {"dance"});

    assertTrue(calls.isEmpty());
    assertEquals(
        List.of("Unknown command. Try ::bstand help to see what's available."), notices);
  }
}
