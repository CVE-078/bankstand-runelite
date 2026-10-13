/*
 * The two title patterns and the name fixes in parseScrollTitle are adapted from RuneLite's
 * screenshot plugin. Copyright (c) 2016-2017, Adam <Adam@sigterm.info>; Copyright (c) 2018,
 * Lotto <https://github.com/devLotto>. BSD 2-Clause, the same terms as this project's LICENSE.
 */
package com.bankstand;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Captures a quest completion by name from the quest-complete scroll, so a quest RuneLite's
 * Quest list does not have yet still reaches the server. A quest the list does know is skipped:
 * the quests snapshot already reports it.
 */
public class QuestCompletionCapture extends BaseCapture {

  // "You have completed The Corsair Curse!"
  private static final Pattern TITLE_PATTERN_1 =
      Pattern.compile(
          ".+?ve\\.*? (?<verb>been|rebuilt|.+?ed)? ?(?:the )?'?(?<quest>.+?)'?(?: [Qq]uest)?[!.]?$");
  // "'One Small Favour' completed!"
  private static final Pattern TITLE_PATTERN_2 =
      Pattern.compile("'?(?<quest>.+?)'?(?: [Qq]uest)? (?<verb>[a-z]\\w+?ed)?(?: f.*?)?[!.]?$");
  private static final List<String> RFD_TAGS =
      List.of("Another Cook", "freed", "defeated", "saved");
  private static final List<String> WORD_QUEST_IN_NAME_TAGS =
      List.of("Another Cook", "Doric", "Heroes", "Legends", "Observatory", "Olaf", "Waterfall");
  private static final Map<String, String> NAME_FIXES =
      Map.of(
          "Lumbridge Cook... again", "Another Cook's",
          "Skrach 'Bone Crusher' Uglogwee", "Skrach Uglogwee");

  // Matches the server's bound.
  private static final int MAX_QUEST_NAME_LENGTH = 128;

  private final Client client;
  private final LongSupplier accountHash;
  private final Predicate<String> knownToRuneLite;
  private final Set<String> sent = new HashSet<>();

  public QuestCompletionCapture(
      EventOutbox outbox,
      BooleanSupplier enabled,
      LongSupplier accountHash,
      Client client,
      Predicate<String> knownToRuneLite,
      Consumer<TransientEvent> onEmit) {
    super(outbox, enabled, accountHash, onEmit);
    this.client = client;
    this.accountHash = accountHash;
    this.knownToRuneLite = knownToRuneLite;
  }

  @Subscribe
  public void onWidgetLoaded(WidgetLoaded event) {
    if (event.getGroupId() != InterfaceID.QUESTSCROLL) {
      return;
    }
    Widget title = client.getWidget(InterfaceID.Questscroll.QUEST_TITLE);
    if (title != null && title.getText() != null) {
      handleTitle(Text.removeTags(title.getText()));
    }
  }

  void handleTitle(String text) {
    if (!isEnabled()) {
      return;
    }
    String quest = parseScrollTitle(text);
    if (quest == null || quest.length() > MAX_QUEST_NAME_LENGTH || knownToRuneLite.test(quest)) {
      return;
    }
    // One per quest per account per run; the server also dedupes.
    if (sent.add(accountHash.getAsLong() + ":" + quest.toLowerCase(Locale.ROOT))) {
      emit(TransientEvent.TYPE_QUEST_COMPLETED, Map.of("questName", quest));
    }
  }

  /** The quest a scroll title names, or null when it is not a full completion. */
  static String parseScrollTitle(String text) {
    if (text == null) {
      return null;
    }
    Matcher match = TITLE_PATTERN_1.matcher(text);
    if (!match.matches()) {
      match = TITLE_PATTERN_2.matcher(text);
      if (!match.matches()) {
        return null;
      }
    }
    String quest = match.group("quest");
    quest = NAME_FIXES.getOrDefault(quest, quest);
    String verb = match.group("verb") == null ? "" : match.group("verb");
    if (verb.contains("kind of")) {
      return null;
    }
    if (verb.contains("completely")) {
      quest += " II";
    }
    String questAndVerb = quest + verb;
    if (RFD_TAGS.stream().anyMatch(questAndVerb::contains)) {
      quest = "Recipe for Disaster - " + quest;
    }
    if (WORD_QUEST_IN_NAME_TAGS.stream().anyMatch(quest::contains)) {
      quest += " Quest";
    }
    quest = quest.trim();
    return quest.isEmpty() ? null : quest;
  }
}
