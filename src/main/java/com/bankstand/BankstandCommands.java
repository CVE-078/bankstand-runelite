package com.bankstand;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The {@code ::bstand} chat commands: routing, dispatch and export. */
final class BankstandCommands {

  /** What the commands ask the plugin to do. Client thread only. */
  interface Actions {
    void sync();

    void link();

    void armCollectionLogRead();

    void disconnect();
  }

  private final BankstandConfig config;
  private final Consumer<String> notice;
  private final Supplier<List<String>> statusLines;
  private final Actions actions;

  BankstandCommands(
      BankstandConfig config,
      Consumer<String> notice,
      Supplier<List<String>> statusLines,
      Actions actions) {
    this.config = config;
    this.notice = notice;
    this.statusLines = statusLines;
    this.actions = actions;
  }

  /** Which action a {@code ::bstand} invocation resolves to. Pure routing, testable. */
  enum CommandAction {
    STATUS,
    SYNC,
    LINK,
    LOG,
    REPAIR,
    EXPORT,
    HELP,
    UNKNOWN
  }

  static boolean isBankstandCommand(String command) {
    return BankstandKeys.COMMAND.equalsIgnoreCase(command)
        || BankstandKeys.COMMAND_ALIAS.equalsIgnoreCase(command);
  }

  static CommandAction actionFor(String[] args) {
    String action = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "status";
    switch (action) {
      case "status":
        return CommandAction.STATUS;
      case "sync":
        return CommandAction.SYNC;
      case "link":
        return CommandAction.LINK;
      case "log":
        return CommandAction.LOG;
      case "repair":
        return CommandAction.REPAIR;
      case "export":
        return CommandAction.EXPORT;
      case "help":
      case "commands":
        return CommandAction.HELP;
      default:
        return CommandAction.UNKNOWN;
    }
  }

  /**
   * Client thread only. Runs a {@code ::bstand} command; ignores any other. A :: command, not !,
   * so these private account actions are never broadcast to public chat.
   */
  void handle(String command, String[] args) {
    if (!isBankstandCommand(command)) {
      return;
    }
    switch (actionFor(args)) {
      case STATUS:
        for (String line : statusLines.get()) {
          notice.accept(line);
        }
        break;
      case SYNC:
        actions.sync();
        break;
      case LINK:
        actions.link();
        break;
      case LOG:
        actions.armCollectionLogRead();
        break;
      case REPAIR:
        actions.disconnect();
        notice.accept("Paste a fresh pairing code in the Bankstand settings to reconnect.");
        break;
      case EXPORT:
        exportConfig();
        break;
      case HELP:
        for (String line : StatusReport.helpLines()) {
          notice.accept(line);
        }
        break;
      case UNKNOWN:
      default:
        notice.accept("Unknown command. Try ::bstand help to see what's available.");
        break;
    }
  }

  /**
   * Prints the Collect/Events toggle state and copies it to the clipboard where one is
   * reachable. Chat gets the lines either way.
   */
  void exportConfig() {
    List<String> lines =
        StatusReport.exportLines(
            config.collectSkills(),
            config.collectQuests(),
            config.collectDiaries(),
            config.collectCollectionLog(),
            config.collectCombatAchievements(),
            config.collectAccountType(),
            config.collectNotableDrops(),
            config.notableDropThreshold(),
            config.collectPetDrops(),
            config.collectSessions(),
            config.collectLoot(),
            config.collectSlayer());
    for (String line : lines) {
      notice.accept(line);
    }
    if (copyToClipboard(String.join("\n", lines))) {
      notice.accept("Copied to clipboard.");
    }
  }

  /** Best effort: a headless environment throws rather than failing quietly. */
  private static boolean copyToClipboard(String text) {
    try {
      java.awt.Toolkit.getDefaultToolkit()
          .getSystemClipboard()
          .setContents(new java.awt.datatransfer.StringSelection(text), null);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
