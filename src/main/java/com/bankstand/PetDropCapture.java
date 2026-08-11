package com.bankstand;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Detects pet drops with the prime-then-resolve pattern (#660). Non-obvious
 * enough to be worth copying rather than re-deriving: the game's first message
 * never contains the pet's name.
 *
 * <ol>
 *   <li>Match the "funny feeling like you are being followed" /
 *       "something weird sneaking into your backpack" family and prime.
 *   <li>Resolve the name from the NEXT game message only, via the
 *       untradeable-drop line or the collection-log line. One-shot: whatever
 *       that next message is, primed state is consumed by it, matching or not.
 *   <li>Validate against a known pet-name set, cross-checkable against the
 *       collection log since pets appear there too.
 * </ol>
 *
 * <p>A primed message with no following drop line emits nothing, and a
 * non-pet untradeable is ignored: both fall out of requiring the resolved
 * name to be in {@link #KNOWN_PETS} before emitting anything.
 *
 * <p><b>{@link #KNOWN_PETS} is a starting set, not a claimed-complete one.</b>
 * OSRS has added pets steadily since release; this list is the well-known
 * boss and skilling pets as of the tier this plugin targets, and is meant to
 * be extended, not treated as exhaustive.
 */
public class PetDropCapture extends BaseCapture {

  private static final Pattern PRIME_PATTERN =
      Pattern.compile(
          "^(You have a funny feeling like you're being followed\\.|"
              + "You feel something weird sneaking into your backpack\\.)$");
  private static final Pattern COLLECTION_LOG_PATTERN =
      Pattern.compile("^New item added to your collection log: (.+)$");
  private static final Pattern UNTRADEABLE_DROP_PATTERN =
      Pattern.compile("^Untradeable drop: (.+)$");

  static final Set<String> KNOWN_PETS =
      new HashSet<>(
          java.util.Arrays.asList(
              "Baby mole",
              "Prince black dragon",
              "Callisto cub",
              "Venenatis spiderling",
              "Vet'ion jr.",
              "Scorpia's offspring",
              "Vorki",
              "Hellpuppy",
              "Baby Kraken",
              "Ikkle Hydra",
              "Chompy chick",
              "Abyssal orphan",
              "Herbi",
              "Skotos",
              "Tzrek-jad",
              "TzRek-Xil",
              "Jal-nib-rek",
              "Nexling",
              "Muphin",
              "Bloodhound",
              "Rift guardian",
              "Rocky",
              "Beaver",
              "Giant squirrel",
              "Heron",
              "Rock golem",
              "Tangleroot",
              "Sraracha",
              "Smolcano",
              "Youngllef",
              "Pet dagannoth prime",
              "Pet dagannoth rex",
              "Pet dagannoth supreme",
              "Pet chaos elemental",
              "Pet kraken",
              "Baby Zilyana",
              "Bran",
              "Butch",
              "Lil' Zik",
              "Little nightmare",
              "Kalphite princess",
              "Olmlet",
              "Nid",
              "Noon",
              "Huberte",
              "Lil'viathan",
              "Smolder",
              "Wisp",
              "Abyssal protector",
              "Pet smoke devil",
              "Pet snakeling"));

  private boolean primed;

  public PetDropCapture(EventOutbox outbox, BooleanSupplier enabled, LongSupplier accountHash) {
    super(outbox, enabled, accountHash);
  }

  @Subscribe
  public void onChatMessage(ChatMessage event) {
    if (event.getType() != ChatMessageType.GAMEMESSAGE) {
      return;
    }
    String message = Text.removeTags(event.getMessage());

    if (isPrimeMessage(message)) {
      primed = true;
      return;
    }
    if (!primed) {
      return;
    }
    // One-shot: consumed by this message whether or not it resolves anything,
    // so a stray later message can never wrongly resolve a stale prime.
    primed = false;
    String name = resolvePetName(message);
    if (name == null) {
      return;
    }
    emit(TransientEvent.TYPE_PET_DROP, payload(name));
  }

  static boolean isPrimeMessage(String message) {
    return PRIME_PATTERN.matcher(message).matches();
  }

  /** Null when the message is not a recognised resolve line, or names something not
   *  in {@link #KNOWN_PETS}: both are "nothing to emit", never an error. */
  static String resolvePetName(String message) {
    String fromLog = matchGroup(COLLECTION_LOG_PATTERN, message);
    if (fromLog != null && KNOWN_PETS.contains(fromLog)) {
      return fromLog;
    }
    String fromDrop = matchGroup(UNTRADEABLE_DROP_PATTERN, message);
    if (fromDrop != null && KNOWN_PETS.contains(fromDrop)) {
      return fromDrop;
    }
    return null;
  }

  private static String matchGroup(Pattern pattern, String message) {
    Matcher matcher = pattern.matcher(message);
    return matcher.matches() ? matcher.group(1) : null;
  }

  static Map<String, Object> payload(String petName) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("petName", petName);
    // Neither resolve line names an NPC: the collection-log broadcast and the
    // untradeable-drop line both carry only the item, never its source.
    payload.put("source", null);
    return payload;
  }
}
