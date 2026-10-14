package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bankstand.BankstandCommands.CommandAction;
import org.junit.Test;

/** Which {@code ::bstand} command is ours and which word maps to which action. */
public class CommandDispatchTest {

  @Test
  public void recognisesTheMainCommandName() {
    assertTrue(BankstandCommands.isBankstandCommand("bstand"));
  }

  @Test
  public void recognisesTheAliasCommandName() {
    assertTrue(BankstandCommands.isBankstandCommand("stand"));
  }

  @Test
  public void commandNameMatchingIsCaseInsensitive() {
    assertTrue(BankstandCommands.isBankstandCommand("BSTAND"));
    assertTrue(BankstandCommands.isBankstandCommand("Stand"));
  }

  @Test
  public void ignoresAnUnrelatedCommandName() {
    assertFalse(BankstandCommands.isBankstandCommand("bank"));
  }

  @Test
  public void defaultsToStatusWithNoArguments() {
    assertEquals(CommandAction.STATUS, BankstandCommands.actionFor(new String[0]));
  }

  @Test
  public void resolvesEveryNamedAction() {
    assertEquals(CommandAction.STATUS, BankstandCommands.actionFor(new String[] {"status"}));
    assertEquals(CommandAction.SYNC, BankstandCommands.actionFor(new String[] {"sync"}));
    assertEquals(CommandAction.LINK, BankstandCommands.actionFor(new String[] {"link"}));
    assertEquals(CommandAction.LOG, BankstandCommands.actionFor(new String[] {"log"}));
    assertEquals(CommandAction.REPAIR, BankstandCommands.actionFor(new String[] {"repair"}));
    assertEquals(CommandAction.EXPORT, BankstandCommands.actionFor(new String[] {"export"}));
    assertEquals(CommandAction.HELP, BankstandCommands.actionFor(new String[] {"help"}));
  }

  @Test
  public void commandsIsAnAliasForHelp() {
    assertEquals(CommandAction.HELP, BankstandCommands.actionFor(new String[] {"commands"}));
  }

  @Test
  public void actionWordMatchingIsCaseInsensitive() {
    assertEquals(CommandAction.SYNC, BankstandCommands.actionFor(new String[] {"SYNC"}));
    assertEquals(CommandAction.LOG, BankstandCommands.actionFor(new String[] {"Log"}));
  }

  @Test
  public void resolvesAnUnrecognisedActionWordToUnknown() {
    assertEquals(CommandAction.UNKNOWN, BankstandCommands.actionFor(new String[] {"nonsense"}));
  }

  @Test
  public void ignoresArgumentsPastTheFirst() {
    // Only the first word is the action.
    assertEquals(
        CommandAction.SYNC, BankstandCommands.actionFor(new String[] {"sync", "now", "please"}));
  }
}
