package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.vars.AccountType;
import org.junit.Test;

/**
 * Deliberately joins against RuneLite's own {@code AccountType}, which is deprecated.
 *
 * <p>The enum is the authoritative statement of which ordinal means what, and asserting
 * our table against it is the entire value of this suite: a hand-copied list would agree
 * with itself forever. Deprecated is not removed, and if RuneLite does remove it this
 * fails to compile, which is a loud prompt to re-check the mapping rather than a silent
 * drift. The plugin's own code reads the varbit and never touches the enum.
 */
@SuppressWarnings("deprecation")
public class AccountTypesTest {

  @Test
  public void readsTheVarbitRuneliteNames() {
    // 1777 under both names: `ACCOUNT_TYPE` in the older table, `IRONMAN` in VarbitID.
    assertEquals(1777, AccountTypes.ACCOUNT_TYPE_VARBIT);
  }

  @Test
  public void mapsEveryValueRuneliteKnowsAbout() {
    // The varbit value IS the ordinal of RuneLite's own enum, so this asserts the join
    // against RuneLite rather than against a list copied out of it by hand. A RuneLite
    // update that inserts a member would shift every ordinal after it, and this is what
    // would notice.
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
    // Measured on a real account, not inferred. RuneLite's enum stops at hardcore group
    // ironman; the game reports 6 for an unranked group ironman. A ranked HCGIM reading
    // exactly 5, where RuneLite declares it, is what confirms the ordinal and the varbit
    // are the same number and makes 6 the next mode along rather than a coincidence.
    assertEquals(
        "hardcore_group", AccountTypes.keyFor(AccountType.HARDCORE_GROUP_IRONMAN.ordinal()));
    assertEquals("unranked_group", AccountTypes.keyFor(6));
  }

  @Test
  public void refusesToGuessAnUnknownValue() {
    // Not a fallback to "regular". An unmapped value means the game reported a mode this
    // build has never seen, and naming it regular turns an unknown into a confident wrong
    // answer. That is exactly how the unranked case was found.
    assertNull(AccountTypes.keyFor(7));
    assertNull(AccountTypes.keyFor(99));
    assertNull(AccountTypes.keyFor(-1));
  }

  @Test
  public void showsTheRawValueSoAnUnknownCanBeReported() {
    // The whole point of the diagnostic. VarInspector logs var CHANGES and 1777 never
    // changes during a session, so this line is the only way to see what a real account
    // reports.
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
    // The label rule: the plugin proves a connection, not ownership. A status line
    // saying "verified" would be the one claim the trust model refuses to make.
    for (int value = -1; value <= 7; value++) {
      String line = AccountTypes.describe(value).toLowerCase(java.util.Locale.ROOT);
      assertTrue(line, !line.contains("verified"));
      assertTrue(line, !line.contains("confirmed"));
    }
  }
}
