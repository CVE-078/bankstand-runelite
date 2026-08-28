package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the diary task broadcast's own area text ("...completed an elite task in the
 * Western Provinces area...") to the wire region key {@link DiaryVarbits}/
 * {@link DiaryTaskVarbits}/{@link DiaryTaskVarplayers} use.
 *
 * <p>Nine of twelve match wording {@code DiaryTaskCompletionCapture}'s own tests already
 * confirm as real chat text. {@code KOUREND_KEBOS} is not confirmed, guessed by analogy to
 * the other two ampersand-joined names. Not yet checked against a live broadcast.
 *
 * <p>A miss (unrecognised text, including a wrong Kourend &amp; Kebos guess) just falls
 * through to the plain tier/area event, same as before this table existed.
 */
public final class DiaryTaskRegions {
  private DiaryTaskRegions() {}

  private static final Map<String, String> BY_AREA_TEXT = build();

  private static Map<String, String> build() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("Ardougne", "ARDOUGNE");
    m.put("Desert", "DESERT");
    m.put("Falador", "FALADOR");
    m.put("Fremennik", "FREMENNIK");
    m.put("Kandarin", "KANDARIN");
    // Not independently corroborated; see the class javadoc.
    m.put("Kourend & Kebos", "KOUREND_KEBOS");
    m.put("Lumbridge & Draynor", "LUMBRIDGE_DRAYNOR");
    m.put("Morytania", "MORYTANIA");
    m.put("Varrock", "VARROCK");
    m.put("Western Provinces", "WESTERN_PROVINCES");
    m.put("Wilderness", "WILDERNESS");
    m.put("Karamja", "KARAMJA");
    return Collections.unmodifiableMap(m);
  }

  /** The wire region key for this area text, or null when it matches none of the twelve. */
  public static String forAreaText(String areaText) {
    return BY_AREA_TEXT.get(areaText);
  }
}
