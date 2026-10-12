package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves a diary task broadcast's area text ("...an elite task in the Western Provinces
 * area...") to the wire region key. A miss falls back to the plain tier/area event.
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
    // Unconfirmed against a live broadcast, guessed from the other "&" names.
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
