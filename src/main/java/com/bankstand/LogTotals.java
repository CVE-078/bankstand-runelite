package com.bankstand;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The collection log's own obtained-over-total, taken from its title bar.
 *
 * <p>The log header reads "Collection Log - 189/1712", which is the figure the player
 * recognises and the one our item-id matching gets wrong (it produced 193 on the account
 * that title was read from).
 *
 * <p>The title is the right source rather than the overview screen: the overview is a
 * separate interface with no Search on it, so it is never on screen during a sync. It is
 * also the right source rather than walking the log's widgets, because the detail panel
 * shows per-source pairs like "Obtained: 1/9" that would be swept up as totals.
 *
 * <p>Pure and deterministic.
 */
public final class LogTotals {

  private static final Pattern PAIR = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
  private static final Pattern COLOUR_TAG = Pattern.compile("<[^>]*>");

  private final int obtained;
  private final int total;

  private LogTotals(int obtained, int total) {
    this.obtained = obtained;
    this.total = total;
  }

  /**
   * Reads the pair out of a log title.
   *
   * @return null unless the text holds exactly one pair. Two would mean this is not the
   *     title, and guessing which one is the total is how a per-source "1/9" becomes a
   *     whole-log figure.
   */
  public static LogTotals fromTitle(String title) {
    if (title == null) {
      return null;
    }
    Matcher m = PAIR.matcher(COLOUR_TAG.matcher(title).replaceAll(""));
    if (!m.find()) {
      return null;
    }
    int obtained = Integer.parseInt(m.group(1));
    int total = Integer.parseInt(m.group(2));
    if (m.find()) {
      return null;
    }
    // A total of zero is not a log, and an obtained above the total is not either.
    if (total <= 0 || obtained > total) {
      return null;
    }
    return new LogTotals(obtained, total);
  }

  public int getObtained() {
    return obtained;
  }

  public int getTotal() {
    return total;
  }
}
