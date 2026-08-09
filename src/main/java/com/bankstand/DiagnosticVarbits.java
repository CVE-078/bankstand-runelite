package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * The varbits {@code ::bstand debug} prints, and why each one is on the list.
 *
 * <p><b>This exists to answer one question in a single login.</b> RuneLite's account type
 * enum stops at hardcore group ironman, and Bankstand has a seventh type for an
 * <i>unranked</i> group ironman. Whether the game distinguishes it, and where, is not
 * documented anywhere we can read. Guessing costs a login per guess on an account most
 * people do not have, so this prints every candidate at once instead.
 *
 * <p>{@link #GROUP_SIZE} is the control. If it reads a real group size then this family
 * of varbits is loaded and readable, which means a zero anywhere else is a genuine zero
 * rather than a varbit that was never populated. Without a control, an all-zero result
 * says nothing at all.
 *
 * <p>A plain data table with no {@code Client} dependency, so it is unit-testable without
 * a live game client.
 */
public final class DiagnosticVarbits {

  private DiagnosticVarbits() {}

  /** The control: a real group size proves the rest of the family is populated. */
  public static final int GROUP_SIZE = VarbitID.GIM_GROUPSIZE;

  /**
   * Candidate carriers of the ranked/unranked distinction, plus the account type itself.
   *
   * <p>"Casual" is the interesting one: it is Jagex's own word in the varbit table where
   * the game's interface says "Unranked", so {@code GIM_CREATING_CASUAL_GROUP} is the
   * closest thing to a name match. Its name says <i>creating</i>, though, so it may well
   * be a transient flag during group setup rather than a lasting property, which is
   * exactly the kind of thing a single reading settles and reasoning does not.
   *
   * <p>The prestige and hiscore-leave varbits are here because ranked and unranked differ
   * in precisely one respect, whether the group appears on the hiscores, so a flag about
   * hiscore participation is a plausible home for it.
   *
   * <p>Insertion-ordered, so the printed report reads the same way every time.
   */
  public static final Map<String, Integer> PROBES;

  static {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("account type", AccountTypes.ACCOUNT_TYPE_VARBIT);
    m.put("group size (control)", GROUP_SIZE);
    m.put("casual group", VarbitID.GIM_CREATING_CASUAL_GROUP);
    m.put("prestiged", VarbitID.GIM_IS_PRESTIGED);
    m.put("opted out of prestige", VarbitID.GIM_OPT_OUT_OF_PRESTIGE);
    m.put("affinity status", VarbitID.GIM_AFFINITYSTATUS);
    m.put("awaiting hiscore leave", VarbitID.GIM_AWAITING_HISCORE_LEAVE);
    m.put("i am leader", VarbitID.GIM_I_AM_LEADER);
    m.put("i am member", VarbitID.GIM_I_AM_MEMBER);
    PROBES = Collections.unmodifiableMap(m);
  }

  /**
   * One line per probe: its label, its varbit id and the value read.
   *
   * <p>Takes the already-read values rather than a {@code Client}, so the formatting is
   * testable and the plugin keeps the single place that touches the game.
   */
  public static java.util.List<String> lines(Map<String, Integer> values) {
    java.util.List<String> out = new java.util.ArrayList<>();
    out.add("Varbit readings, for the unranked group ironman question:");
    for (Map.Entry<String, Integer> probe : PROBES.entrySet()) {
      Integer value = values.get(probe.getKey());
      out.add(
          "  "
              + probe.getKey()
              + " ("
              + probe.getValue()
              + ") = "
              + (value == null ? "unread" : value.toString()));
    }
    return out;
  }
}
