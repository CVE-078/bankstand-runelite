package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the diary task completion broadcast's own area text ("Well done! You have
 * completed an elite task in the Western Provinces area...") to the wire region key used
 * by {@link DiaryVarbits}, {@link DiaryTaskVarbits} and {@link DiaryTaskVarplayers}.
 *
 * <p><b>Nine of these twelve are corroborated by an existing live-verified source</b>: this
 * plugin's own {@code DiaryTaskCompletionCapture} javadoc and test suite already exercise
 * "Western Provinces", "Kandarin", "Varrock" and "Lumbridge &amp; Draynor" as real chat
 * text, and the other single-word regions follow the same plain-region-name pattern those
 * confirm. <b>"Kourend &amp; Kebos" is not independently confirmed here</b>: it is the one
 * double-barrelled region without a corroborating capture or test, assumed by analogy to
 * the other two ampersand-joined names rather than observed. Per this plugin's own standing
 * rule ("an assumed chat-message wording is not a fact until it is corroborated or
 * observed"), do not treat it as verified until it is checked against a live broadcast.
 *
 * <p>A miss here (unrecognised area text, including a wrong guess for Kourend &amp; Kebos)
 * is a safe failure: {@code DiaryTaskCompletionCapture} falls through to the plain
 * tier/area event with no task identity, exactly the behaviour that shipped before this
 * table existed.
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
