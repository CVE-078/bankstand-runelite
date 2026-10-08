package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.vars.AccountType;
import org.junit.Test;

/**
 * Joins against RuneLite's deprecated {@code AccountType} on purpose: it is the authoritative
 * ordinal table, and removal fails compilation. Plugin code reads the varbit, not the enum.
 */
@SuppressWarnings("deprecation")
public class AccountTypesTest {

  @Test
  public void readsTheVarbitRuneliteNames() {
    assertEquals(1777, AccountTypes.ACCOUNT_TYPE_VARBIT);
  }

  @Test
  public void mapsEveryValueRuneliteKnowsAbout() {
    // The varbit value is the ordinal of RuneLite's enum, so an inserted member is caught here.
    assertEquals("regular", AccountTypes.keyFor(AccountType.NORMAL.ordinal()));
    assertEquals("ironman", AccountTypes.keyFor(AccountType.IRONMAN.ordinal()));
    assertEquals("ultimate", AccountTypes.keyFor(AccountType.ULTIMATE_IRONMAN.ordinal()));
    assertEquals("hardcore", AccountTypes.keyFor(AccountType.HARDCORE_IRONMAN.ordinal()));
    assertEquals("group", AccountTypes.keyFor(AccountType.GROUP_IRONMAN.ordinal()));
    assertEquals(
        "hardcore_group", AccountTypes.keyFor(AccountType.HARDCORE_GROUP_IRONMAN.ordinal()));
  }

  @Test
  public void coversEveryMemberRuneliteDeclares() {
    for (AccountType type : AccountType.values()) {
      assertTrue(type.name(), AccountTypes.keyFor(type.ordinal()) != null);
    }
  }

  @Test
  public void mapsTheUnrankedGroupIronmanRuneliteDoesNotDeclare() {
    // Measured on a real account: unranked group ironman reports 6, past RuneLite's enum.
    assertEquals(
        "hardcore_group", AccountTypes.keyFor(AccountType.HARDCORE_GROUP_IRONMAN.ordinal()));
    assertEquals("unranked_group", AccountTypes.keyFor(6));
  }

  @Test
  public void refusesToGuessAnUnknownValue() {
    // Not a fallback to "regular": an unknown mode must not become a confident wrong answer.
    assertNull(AccountTypes.keyFor(7));
    assertNull(AccountTypes.keyFor(99));
    assertNull(AccountTypes.keyFor(-1));
  }

  @Test
  public void showsTheRawValueSoAnUnknownCanBeReported() {
    // VarInspector logs changes only and 1777 never changes in a session, so this is the
    // only way to see what an account reports.
    assertTrue(AccountTypes.describe(4).contains("group"));
    assertTrue(AccountTypes.describe(4).contains("1777"));
    assertTrue(AccountTypes.describe(4).contains("4"));

    String unknown = AccountTypes.describe(7);
    assertTrue(unknown.contains("unrecognised"));
    assertTrue(unknown.contains("7"));
    assertTrue(unknown.contains("report"));
  }

  @Test
  public void neverClaimsToVerifyOwnership() {
    // The plugin proves a connection, not ownership, so no status line says "verified".
    for (int value = -1; value <= 7; value++) {
      String line = AccountTypes.describe(value).toLowerCase(java.util.Locale.ROOT);
      assertTrue(line, !line.contains("verified"));
      assertTrue(line, !line.contains("confirmed"));
    }
  }
}
