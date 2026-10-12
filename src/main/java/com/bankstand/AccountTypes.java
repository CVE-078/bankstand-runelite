package com.bankstand;

import net.runelite.api.Varbits;

/**
 * Maps the game's account type varbit to Bankstand's type keys. Needed because Group Ironman
 * accounts are invisible to public hiscores. Client-asserted, so never the public account type.
 */
public final class AccountTypes {

  private AccountTypes() {}

  /** Varbit 1777 ({@code IRONMAN} in {@code VarbitID}). */
  public static final int ACCOUNT_TYPE_VARBIT = Varbits.ACCOUNT_TYPE;

  /**
   * Server type keys (wire contract), indexed by varbit value. RuneLite's enum stops at 5; the
   * value 6 (unranked group) was measured on a real account.
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

  /** Null for an unknown value, never a fallback to {@code regular}. */
  public static String keyFor(int varbitValue) {
    if (varbitValue < 0 || varbitValue >= BY_VALUE.length) {
      return null;
    }
    return BY_VALUE[varbitValue];
  }

  /** Includes the raw value, since the varbit never changes in a session to be inspected. */
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
