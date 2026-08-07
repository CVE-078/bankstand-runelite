package com.bankstand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The counts the collection log overview already shows, read as text.
 *
 * <p>Worth having because our own figure is wrong. Matching enumerated item ids against
 * the wiki-derived manifest produced 193 on an account whose overview said 189, off in
 * both directions per category, with fifteen ids the manifest could not place at all.
 * The game's own number is the one a player recognises, and it is sitting on screen the
 * moment they open the log, before any search.
 *
 * <p>Pure and deterministic. The widget reading lives in the plugin; this is only the
 * parsing, which is the part that can be tested.
 */
public final class OverviewCounts {

  /** The five tabs that exist today. Fewer than this means a half-built interface. */
  private static final int KNOWN_CATEGORIES = 5;

  private static final Pattern PROGRESS =
      Pattern.compile("^\\s*(\\d+)\\s*/\\s*(\\d+)\\s*$");
  private static final Pattern COLOUR_TAG = Pattern.compile("<[^>]*>");

  /** One category's obtained-over-total. */
  public static final class Progress {
    private final int obtained;
    private final int total;

    Progress(int obtained, int total) {
      this.obtained = obtained;
      this.total = total;
    }

    public int getObtained() {
      return obtained;
    }

    public int getTotal() {
      return total;
    }
  }

  private final int obtained;
  private final int total;
  private final int categories;

  private OverviewCounts(int obtained, int total, int categories) {
    this.obtained = obtained;
    this.total = total;
    this.categories = categories;
  }

  /**
   * Reads "35/340", tolerating the colour tags the interface wraps figures in.
   *
   * @return null when the text is not a pair of numbers, which includes the progress
   *     bar's own "Collections Logged: 189/300". That figure is rank progress, not a
   *     log total, and reading it as one would report a maxed log at 300 slots.
   */
  public static Progress parseProgress(String text) {
    if (text == null) {
      return null;
    }
    Matcher m = PROGRESS.matcher(COLOUR_TAG.matcher(text).replaceAll(""));
    if (!m.matches()) {
      return null;
    }
    return new Progress(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
  }

  /** Sums whatever categories were readable. Unparseable entries are skipped, not zeroed. */
  public static OverviewCounts of(Collection<String> categoryTexts) {
    List<Progress> parsed = new ArrayList<>();
    for (String text : categoryTexts) {
      Progress p = parseProgress(text);
      if (p != null) {
        parsed.add(p);
      }
    }
    int obtained = 0;
    int total = 0;
    for (Progress p : parsed) {
      obtained += p.getObtained();
      total += p.getTotal();
    }
    return new OverviewCounts(obtained, total, parsed.size());
  }

  /**
   * True when every category was readable.
   *
   * <p>A partly built interface must not be reported as a smaller log, or a player would
   * watch their total shrink every time they opened the log mid-render.
   */
  public boolean isComplete() {
    return categories >= KNOWN_CATEGORIES;
  }

  public int getObtained() {
    return obtained;
  }

  public int getTotal() {
    return total;
  }

  public int getCategories() {
    return categories;
  }
}
