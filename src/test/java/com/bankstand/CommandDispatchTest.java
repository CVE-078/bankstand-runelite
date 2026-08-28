package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.BankstandPlugin.CommandAction;
import org.junit.Test;

/**
 * Covers the pure half of {@code ::bstand} dispatch (#834): which command string is ours,
 * and which action word resolves to which {@link CommandAction}. What each action actually
 * does (armCollectionLogRead, requestManualCapture, ...) still needs a live Client/
 * ConfigManager and stays outside this file's reach, same as before this split.
 */
public class CommandDispatchTest {

  @Test
  public void recognisesTheMainCommandName() {
    assertTrue(BankstandPlugin.isBankstandCommand("bstand"));
  }

  @Test
  public void recognisesTheAliasCommandName() {
    assertTrue(BankstandPlugin.isBankstandCommand("stand"));
  }

  @Test
  public void commandNameMatchingIsCaseInsensitive() {
    assertTrue(BankstandPlugin.isBankstandCommand("BSTAND"));
    assertTrue(BankstandPlugin.isBankstandCommand("Stand"));
  }

  @Test
  public void ignoresAnUnrelatedCommandName() {
    assertFalse(BankstandPlugin.isBankstandCommand("bank"));
  }

  @Test
  public void defaultsToStatusWithNoArguments() {
    assertEquals(CommandAction.STATUS, BankstandPlugin.actionFor(new String[0]));
  }

  @Test
  public void resolvesEveryNamedAction() {
    assertEquals(CommandAction.STATUS, BankstandPlugin.actionFor(new String[] {"status"}));
    assertEquals(CommandAction.SYNC, BankstandPlugin.actionFor(new String[] {"sync"}));
    assertEquals(CommandAction.LINK, BankstandPlugin.actionFor(new String[] {"link"}));
    assertEquals(CommandAction.LOG, BankstandPlugin.actionFor(new String[] {"log"}));
    assertEquals(CommandAction.REPAIR, BankstandPlugin.actionFor(new String[] {"repair"}));
    assertEquals(CommandAction.EXPORT, BankstandPlugin.actionFor(new String[] {"export"}));
    assertEquals(CommandAction.HELP, BankstandPlugin.actionFor(new String[] {"help"}));
  }

  @Test
  public void commandsIsAnAliasForHelp() {
    // "help" is the conventional word; "commands" is the one a player reaching for
    // a full list is just as likely to try first. Same listing either way.
    assertEquals(CommandAction.HELP, BankstandPlugin.actionFor(new String[] {"commands"}));
  }

  @Test
  public void actionWordMatchingIsCaseInsensitive() {
    assertEquals(CommandAction.SYNC, BankstandPlugin.actionFor(new String[] {"SYNC"}));
    assertEquals(CommandAction.LOG, BankstandPlugin.actionFor(new String[] {"Log"}));
  }

  @Test
  public void resolvesAnUnrecognisedActionWordToUnknown() {
    assertEquals(CommandAction.UNKNOWN, BankstandPlugin.actionFor(new String[] {"nonsense"}));
  }

  @Test
  public void ignoresArgumentsPastTheFirst() {
    // A stray extra word (a typo'd second argument, say) must not change which
    // action runs; only the first word is the action.
    assertEquals(
        CommandAction.SYNC, BankstandPlugin.actionFor(new String[] {"sync", "now", "please"}));
  }
}
