package com.bankstand;

import net.runelite.api.Varbits;

/**
 * Reads the account type the game itself reports, and names it the way Bankstand does.
 *
 * <p><b>Why this is worth reading at all.</b> Group Ironman accounts do not appear on
 * the ironman hiscores, so Wise Old Man reports every one of them as a regular account.
 * Three of Bankstand's seven account types are therefore undetectable from any public
 * source, and until now the only way to record one was for the owner to pick it from a
 * dropdown. This varbit is what the game says.
 *
 * <p><b>It confirms; it does not verify.</b> Like everything else the plugin sends, this
 * is client-asserted: it says what a client reported, never who owns the account. It
 * must never become the public account type on a profile.
 *
 * <p>A plain mapping with no {@code Client} dependency in the pure part, so the table is
 * unit-testable without a live game client.
 */
public final class AccountTypes {

  private AccountTypes() {}

  /**
   * Varbit 1777, named {@code ACCOUNT_TYPE} in the older table and {@code IRONMAN} in
   * {@code VarbitID}. The value is the ordinal of RuneLite's own
   * {@code net.runelite.api.vars.AccountType}.
   */
  public static final int ACCOUNT_TYPE_VARBIT = Varbits.ACCOUNT_TYPE;

  /**
   * Bankstand's own type keys, indexed by varbit value.
   *
   * <p>These are Bankstand's names, not RuneLite's: the server's {@code ACCOUNT_TYPES}
   * calls them {@code hardcore} and {@code ultimate} where RuneLite says
   * {@code HARDCORE_IRONMAN} and {@code ULTIMATE_IRONMAN}, and the wire has to match the
   * server.
   *
   * <p><b>6 is measured, not inherited.</b> RuneLite's enum has six members and stops at
   * hardcore group ironman, so the last entry here comes from reading the varbit on real
   * accounts rather than from the enum:
   *
   * <pre>
   *   a normal account        1777 = 0
   *   a ranked HCGIM          1777 = 5
   *   an unranked GIM         1777 = 6
   * </pre>
   *
   * The middle reading is what makes the last one trustworthy. 5 landing exactly where
   * RuneLite declares hardcore group ironman confirms the ordinal and the varbit are the
   * same number, so 6 is the next mode along and not a coincidence.
   */
  private static final String[] BY_VALUE = {
    "regular",
    "ironman",
    "ultimate",
    "hardcore",
    "group",
    "hardcore_group",
    "unranked_group",
  };

  /**
   * The Bankstand type key for a varbit value, or null when the value is one this build
   * has never seen.
   *
   * <p>Null rather than a fallback to {@code regular}. An unmapped value means the game
   * reported something new, and calling that "regular" would quietly turn an unknown into
   * a confident wrong answer, which is the one outcome the whole account type feature
   * exists to stop.
   */
  public static String keyFor(int varbitValue) {
    if (varbitValue < 0 || varbitValue >= BY_VALUE.length) {
      return null;
    }
    return BY_VALUE[varbitValue];
  }

  /**
   * One status line naming the type and the raw varbit value.
   *
   * <p>The raw value is shown deliberately, and is the entire point of the line while the
   * unranked Group Ironman question is open: RuneLite's {@code VarInspector} logs var
   * <i>changes</i>, and 1777 does not change during a session, so there is no other way
   * to see what a real account reports.
   */
  public static String describe(int varbitValue) {
    String key = keyFor(varbitValue);
    if (key == null) {
      return "Account type: unrecognised (varbit "
          + ACCOUNT_TYPE_VARBIT
          + " = "
          + varbitValue
          + "). Please report this value.";
    }
    return "Account type: " + key + " (varbit " + ACCOUNT_TYPE_VARBIT + " = " + varbitValue + ").";
  }
}
