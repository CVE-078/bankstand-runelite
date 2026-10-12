package com.bankstand;

import java.util.ArrayList;
import java.util.List;

/** The lines the {@code ::bstand} commands print, built from plain values so they are testable. */
public final class StatusReport {

  private StatusReport() {}

  /**
   * @param paired whether a device token is stored
   * @param serverUrl where submissions go; shown because a wrong one is silent otherwise
   * @param linkedName the character bound to this pairing, or null if none has been linked
   * @param lastSubmitDescription e.g. "4 minutes ago", or null when nothing has been submitted
   * @param lastFailure the last failure reason, or null when the last attempt succeeded
   * @param enabled which capability names are switched on, in display order
   * @param collectionLogSlots how many slots the last guided read saw, or -1 when never read
   * @param accountTypeLine what the game reports this account to be, or null when logged out
   * @param manifestLine the capability manifest in use, so it is never a guess in a bug report
   */
  public static List<String> lines(
      boolean paired,
      String serverUrl,
      String linkedName,
      String lastSubmitDescription,
      String lastFailure,
      List<String> enabled,
      int collectionLogSlots,
      String accountTypeLine,
      String manifestLine) {
    List<String> out = new ArrayList<>();

    if (!paired) {
      out.add("Not paired. Paste a pairing code in the Bankstand settings to link this client.");
      return out;
    }

    out.add("Paired with " + serverUrl + ".");
    out.add(
        linkedName == null
            ? "No character linked yet. Log in and it links on the next tick, or run ::bstand link."
            : "Linked character: " + linkedName + ".");

    out.add(
        enabled.isEmpty()
            ? "No capabilities switched on, so nothing is being sent."
            : "Sending: " + String.join(", ", enabled) + ".");

    out.add(
        lastSubmitDescription == null
            ? "Nothing submitted yet this session."
            : "Last submitted " + lastSubmitDescription + ".");

    if (lastFailure != null) {
      out.add("Last failure: " + lastFailure);
    }

    // Always shown: a manual sync cannot refresh the log. Says "items", not "entries": it counts
    // distinct item ids, which can exceed the game's entry count because of variant ids.
    out.add(
        collectionLogSlots < 0
            ? "Collection log not read yet. Open it in game and use its Search to read the whole log."
            : "Collection log: "
                + collectionLogSlots
                + " items from the last read. Use the log's own Search to read it again.");

    // From the game, not the hiscores (which cannot see Group Ironman). Null while logged out,
    // where the varbit reads 0 and would wrongly print "regular".
    if (accountTypeLine != null) {
      out.add(accountTypeLine);
    }

    // Gates are this manifest intersected with the player's toggles.
    if (manifestLine != null) {
      out.add(manifestLine);
    }

    return out;
  }

  /**
   * What {@code ::bstand export} prints. Toggles only: never the pairing code, device token or
   * server URL, since the output is meant to be shared.
   */
  public static List<String> exportLines(
      boolean skills,
      boolean quests,
      boolean diaries,
      boolean collectionLog,
      boolean combatAchievements,
      boolean accountType,
      boolean notableDrops,
      int notableDropThreshold,
      boolean petDrops) {
    List<String> out = new ArrayList<>();
    out.add("Bankstand collect/events config:");
    out.add(onOff("Skill XP", skills));
    out.add(onOff("Quest progress", quests));
    out.add(onOff("Diary progress", diaries));
    out.add(onOff("Collection log", collectionLog));
    out.add(onOff("Combat achievements", combatAchievements));
    out.add(onOff("Account type", accountType));
    out.add(onOff("Notable drops", notableDrops) + " (threshold " + notableDropThreshold + " gp)");
    out.add(onOff("Pet drops", petDrops));
    return out;
  }

  private static String onOff(String label, boolean value) {
    return "- " + label + ": " + (value ? "on" : "off");
  }

  /**
   * What {@code ::bstand help} prints, in {@code CommandAction} order. A fixed list, kept in step
   * with the enum by {@code HelpLinesTest}.
   */
  public static List<String> helpLines() {
    List<String> out = new ArrayList<>();
    out.add("Bankstand commands:");
    out.add("- ::bstand - shows connection status: account, character, last sync.");
    out.add("- ::bstand sync - sends now instead of waiting for the next cycle.");
    out.add("- ::bstand link - re-links this character to your account.");
    out.add(
        "- ::bstand log - arms a guided collection log read; open the log and click Search.");
    out.add("- ::bstand export - prints and copies your current Collect/Events toggle state.");
    out.add("- ::bstand repair - clears a stale pairing so you can paste a fresh code.");
    out.add("- ::bstand help - shows this list.");
    return out;
  }

  /**
   * What {@code ::bstand sync} reports. Says plainly that the collection log is not included, so
   * the sync does not look broken.
   */
  public static List<String> syncLines(boolean paired, List<String> enabled) {
    List<String> out = new ArrayList<>();
    if (!paired) {
      out.add("Not paired, so there is nothing to sync.");
      return out;
    }
    if (enabled.isEmpty()) {
      out.add("No capabilities switched on, so there is nothing to sync.");
      return out;
    }
    out.add("Syncing " + String.join(", ", enabled) + " now.");
    out.add("The collection log is not included: it can only be read from the log's own Search.");
    return out;
  }
}
