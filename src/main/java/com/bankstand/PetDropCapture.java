package com.bankstand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Detects pet drops with a prime-then-resolve pattern, because the game's first message never
 * names the pet. A prime line arms the detector for the next {@link #WINDOW} game messages, since
 * other lines (pet insurance) can come before the collection-log or untradeable-drop line. The
 * first known pet in that window resolves and consumes the prime, so one prime emits once. Only
 * names in {@link #KNOWN_PETS} emit; extend it when the game adds a pet to the collection log.
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

  // Case-insensitive: game text casing differs from these entries. The emitted name still comes
  // from the message itself.
  static final Set<String> KNOWN_PETS = knownPets();

  private static Set<String> knownPets() {
    Set<String> pets = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    pets.addAll(
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
              "Pet snakeling",
              "Aggy",
              "Baby chinchompa",
              "Baron",
              "Beef",
              "Dom",
              "Gull",
              "Lil' Creator",
              "Maggot marquess",
              "Moxi",
              "Mr McGroot",
              "Pet dark core",
              "Pet General Graardor",
              "Pet K'ril Tsutsaroth",
              "Pet Kree'arra",
              "Pet Penance Queen",
              "Pet Zilyana",
              "Phoenix",
              "Quetzin",
              "Scurry",
              "Smol Heredit",
              "Soup",
              "Tiny tempor",
              "Tumeken's guardian",
              "Yami"));
    return pets;
  }

  // Big enough for the kill-count, insurance and collection-log lines; small enough that a stale
  // prime cannot resolve an unrelated later drop.
  static final int WINDOW = 4;

  private int primedFor;

  public PetDropCapture(EventOutbox outbox, BooleanSupplier enabled, LongSupplier accountHash) {
    super(outbox, enabled, accountHash);
  }

  public PetDropCapture(
      EventOutbox outbox,
      BooleanSupplier enabled,
      LongSupplier accountHash,
      Consumer<TransientEvent> onEmit) {
    super(outbox, enabled, accountHash, onEmit);
  }

  @Subscribe
  public void onChatMessage(ChatMessage event) {
    if (event.getType() != ChatMessageType.GAMEMESSAGE) {
      return;
    }
    handleMessage(Text.removeTags(event.getMessage()));
  }

  /**
   * The toggle gates only emitting, never the prime/consume tracking: otherwise disabling between
   * prime and resolve would leave {@link #primedFor} stuck and a later message could resolve it.
   */
  void handleMessage(String message) {
    if (isPrimeMessage(message)) {
      primedFor = WINDOW;
      return;
    }
    if (primedFor == 0) {
      return;
    }
    primedFor--;
    String name = resolvePetName(message);
    if (name == null) {
      return;
    }
    primedFor = 0;
    if (!isEnabled()) return;
    emit(TransientEvent.TYPE_PET_DROP, payload(name));
  }

  static boolean isPrimeMessage(String message) {
    return PRIME_PATTERN.matcher(message).matches();
  }

  /** Null means nothing to emit: not a resolve line, or not a known pet. */
  static String resolvePetName(String message) {
    String name = matchGroup(COLLECTION_LOG_PATTERN, message);
    if (name == null) {
      name = matchGroup(UNTRADEABLE_DROP_PATTERN, message);
    }
    return name != null && KNOWN_PETS.contains(name) ? name : null;
  }

  private static String matchGroup(Pattern pattern, String message) {
    Matcher matcher = pattern.matcher(message);
    return matcher.matches() ? matcher.group(1) : null;
  }

  static Map<String, Object> payload(String petName) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("petName", petName);
    // Neither resolve line names the source NPC.
    payload.put("source", null);
    return payload;
  }
}
