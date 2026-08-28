package com.bankstand;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines {@code ::bstand} prints, built from plain values.
 *
 * <p>Separated from the plugin so the wording is testable without a client. Half the value of a
 * manual trigger is being able to see what it did, and during an earlier incident there was no way
 * to see anything at all: every action was implicit, on a schedule or a game event, and a failure
 * left nothing on screen to read.
 *
 * <p>Every line answers a question a player asks when it looks broken: am I paired, which
 * character is linked, did anything reach the server, and what went wrong last time.
 */
public final class StatusReport {

  private StatusReport() {}

  /**
   * Builds the status lines.
   *
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
      // Nothing else is worth printing: every other line describes a pairing that
      // does not exist, and listing them reads as though something is configured.
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

    // The collection log is the one capability a manual sync cannot refresh, so it
    // gets its own line whether or not it has ever been read. Without this a player
    // runs a sync, sees no new slots and concludes the whole thing is broken.
    //
    // **"items", not "entries".** This counts distinct item ids observed, and the
    // game counts ENTRIES, which is a different number: one account read 193 ids
    // for the 189 entries the client displayed, because several ids are variant
    // forms of a slot already owned under another id. The plugin has no manifest
    // and cannot resolve one into the other, so it names what it actually counted.
    // Saying "entries" put 193 beside the game's 189 in the same chat window.
    out.add(
        collectionLogSlots < 0
            ? "Collection log not read yet. Open it in game and use its Search to read the whole log."
            : "Collection log: "
                + collectionLogSlots
                + " items from the last read. Use the log's own Search to read it again.");

    // Read from the game rather than from the hiscores, which cannot see Group
    // Ironman at all. Null while logged out: the varbit reads 0 with no account
    // loaded, and printing "regular" for a logged-out client would be a wrong
    // answer rather than a missing one.
    if (accountTypeLine != null) {
      out.add(accountTypeLine);
    }

    // Which capabilities the server currently ingests, and how often it wants them. The
    // gates above are an intersection of this and the player's own toggles, so without it
    // a capability switched on in the config but absent from the manifest reads as a bug.
    if (manifestLine != null) {
      out.add(manifestLine);
    }

    return out;
  }

  /**
   * What {@code ::bstand export} prints: the current Collect/Events toggle state,
   * plain booleans and the one numeric threshold so this is testable without a live
   * config. Meant to be copied to another device or into a support message, so it
   * names only the toggles themselves and never the pairing code, device token, or
   * server URL: none of the three would mean anything on a different account or
   * client anyway, and two of them are secrets.
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
   * What {@code ::bstand sync} reports.
   *
   * <p>States plainly that the collection log is not included. A sync that silently leaves it out
   * looks like a sync that did not work, because the log is the capability a player is most likely
   * to be watching.
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
