package com.bankstand;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generated per-task bit positions for {@link DiaryTaskManifest#shipped()}. Task names are
 * ordinal placeholders, not game text. Karamja has Elite only: its other tiers are varbits,
 * which the diary capture does not read. Desert medium task 11 has a second, ironman bit.
 */
final class DiaryTaskManifestData {
  private DiaryTaskManifestData() {}

  static Map<String, Map<Integer, Map<Integer, DiaryTaskManifest.Entry>>> build() {
    Map<String, Map<Integer, Map<Integer, DiaryTaskManifest.Entry>>> byRegion =
        new LinkedHashMap<>();
    byRegion.put("ARDOUGNE", ardougne());
    byRegion.put("DESERT", desert());
    byRegion.put("FALADOR", falador());
    byRegion.put("FREMENNIK", fremennik());
    byRegion.put("KANDARIN", kandarin());
    byRegion.put("KOUREND_KEBOS", kourendKebos());
    byRegion.put("LUMBRIDGE_DRAYNOR", lumbridgeDraynor());
    byRegion.put("MORYTANIA", morytania());
    byRegion.put("VARROCK", varrock());
    byRegion.put("WESTERN_PROVINCES", westernProvinces());
    byRegion.put("WILDERNESS", wilderness());
    byRegion.put("KARAMJA", karamja());
    return byRegion;
  }

  private static void put(
      Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m,
      int varplayerId,
      int bitIndex,
      String tier,
      String taskName) {
    m.computeIfAbsent(varplayerId, k -> new LinkedHashMap<>())
        .put(bitIndex, new DiaryTaskManifest.Entry(tier, taskName));
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> ardougne() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1196, 0, "easy", "Ardougne easy task 1");
    put(m, 1196, 1, "easy", "Ardougne easy task 2");
    put(m, 1196, 2, "easy", "Ardougne easy task 3");
    put(m, 1196, 4, "easy", "Ardougne easy task 4");
    put(m, 1196, 5, "easy", "Ardougne easy task 5");
    put(m, 1196, 6, "easy", "Ardougne easy task 6");
    put(m, 1196, 7, "easy", "Ardougne easy task 7");
    put(m, 1196, 9, "easy", "Ardougne easy task 8");
    put(m, 1196, 11, "easy", "Ardougne easy task 9");
    put(m, 1196, 12, "easy", "Ardougne easy task 10");
    put(m, 1196, 13, "medium", "Ardougne medium task 1");
    put(m, 1196, 14, "medium", "Ardougne medium task 2");
    put(m, 1196, 15, "medium", "Ardougne medium task 3");
    put(m, 1196, 16, "medium", "Ardougne medium task 4");
    put(m, 1196, 17, "medium", "Ardougne medium task 5");
    put(m, 1196, 18, "medium", "Ardougne medium task 6");
    put(m, 1196, 19, "medium", "Ardougne medium task 7");
    put(m, 1196, 20, "medium", "Ardougne medium task 8");
    put(m, 1196, 21, "medium", "Ardougne medium task 9");
    put(m, 1196, 23, "medium", "Ardougne medium task 10");
    put(m, 1196, 24, "medium", "Ardougne medium task 11");
    put(m, 1196, 25, "medium", "Ardougne medium task 12");
    put(m, 1196, 26, "hard", "Ardougne hard task 1");
    put(m, 1196, 27, "hard", "Ardougne hard task 2");
    put(m, 1196, 28, "hard", "Ardougne hard task 3");
    put(m, 1196, 29, "hard", "Ardougne hard task 4");
    put(m, 1196, 30, "hard", "Ardougne hard task 5");
    put(m, 1196, 31, "hard", "Ardougne hard task 6");
    put(m, 1197, 0, "hard", "Ardougne hard task 7");
    put(m, 1197, 1, "hard", "Ardougne hard task 8");
    put(m, 1197, 2, "hard", "Ardougne hard task 9");
    put(m, 1197, 3, "hard", "Ardougne hard task 10");
    put(m, 1197, 4, "hard", "Ardougne hard task 11");
    put(m, 1197, 5, "hard", "Ardougne hard task 12");
    put(m, 1197, 6, "elite", "Ardougne elite task 1");
    put(m, 1197, 7, "elite", "Ardougne elite task 2");
    put(m, 1197, 9, "elite", "Ardougne elite task 3");
    put(m, 1197, 8, "elite", "Ardougne elite task 4");
    put(m, 1197, 10, "elite", "Ardougne elite task 5");
    put(m, 1197, 11, "elite", "Ardougne elite task 6");
    put(m, 1197, 12, "elite", "Ardougne elite task 7");
    put(m, 1197, 13, "elite", "Ardougne elite task 8");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> desert() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1198, 1, "easy", "Desert easy task 1");
    put(m, 1198, 2, "easy", "Desert easy task 2");
    put(m, 1198, 3, "easy", "Desert easy task 3");
    put(m, 1198, 4, "easy", "Desert easy task 4");
    put(m, 1198, 5, "easy", "Desert easy task 5");
    put(m, 1198, 6, "easy", "Desert easy task 6");
    put(m, 1198, 7, "easy", "Desert easy task 7");
    put(m, 1198, 8, "easy", "Desert easy task 8");
    put(m, 1198, 9, "easy", "Desert easy task 9");
    put(m, 1198, 10, "easy", "Desert easy task 10");
    put(m, 1198, 11, "easy", "Desert easy task 11");
    put(m, 1198, 12, "medium", "Desert medium task 1");
    put(m, 1198, 13, "medium", "Desert medium task 2");
    put(m, 1198, 14, "medium", "Desert medium task 3");
    put(m, 1198, 15, "medium", "Desert medium task 4");
    put(m, 1198, 16, "medium", "Desert medium task 5");
    put(m, 1198, 17, "medium", "Desert medium task 6");
    put(m, 1198, 18, "medium", "Desert medium task 7");
    put(m, 1198, 19, "medium", "Desert medium task 8");
    put(m, 1198, 20, "medium", "Desert medium task 9");
    put(m, 1198, 21, "medium", "Desert medium task 10");
    put(m, 1198, 22, "medium", "Desert medium task 11");
    // Ironman variant of the same task.
    put(m, 1199, 9, "medium", "Desert medium task 11");
    put(m, 1198, 23, "medium", "Desert medium task 12");
    put(m, 1198, 24, "hard", "Desert hard task 1");
    put(m, 1198, 25, "hard", "Desert hard task 2");
    put(m, 1198, 26, "hard", "Desert hard task 3");
    put(m, 1198, 27, "hard", "Desert hard task 4");
    put(m, 1198, 28, "hard", "Desert hard task 5");
    put(m, 1198, 29, "hard", "Desert hard task 6");
    put(m, 1198, 30, "hard", "Desert hard task 7");
    put(m, 1198, 31, "hard", "Desert hard task 8");
    put(m, 1199, 0, "hard", "Desert hard task 9");
    put(m, 1199, 1, "hard", "Desert hard task 10");
    put(m, 1199, 2, "elite", "Desert elite task 1");
    put(m, 1199, 4, "elite", "Desert elite task 2");
    put(m, 1199, 5, "elite", "Desert elite task 3");
    put(m, 1199, 6, "elite", "Desert elite task 4");
    put(m, 1199, 7, "elite", "Desert elite task 5");
    put(m, 1199, 8, "elite", "Desert elite task 6");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> falador() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1186, 0, "easy", "Falador easy task 1");
    put(m, 1186, 1, "easy", "Falador easy task 2");
    put(m, 1186, 2, "easy", "Falador easy task 3");
    put(m, 1186, 3, "easy", "Falador easy task 4");
    put(m, 1186, 4, "easy", "Falador easy task 5");
    put(m, 1186, 5, "easy", "Falador easy task 6");
    put(m, 1186, 6, "easy", "Falador easy task 7");
    put(m, 1186, 7, "easy", "Falador easy task 8");
    put(m, 1186, 8, "easy", "Falador easy task 9");
    put(m, 1186, 9, "easy", "Falador easy task 10");
    put(m, 1186, 10, "easy", "Falador easy task 11");
    put(m, 1186, 11, "medium", "Falador medium task 1");
    put(m, 1186, 12, "medium", "Falador medium task 2");
    put(m, 1186, 13, "medium", "Falador medium task 3");
    put(m, 1186, 14, "medium", "Falador medium task 4");
    put(m, 1186, 15, "medium", "Falador medium task 5");
    put(m, 1186, 16, "medium", "Falador medium task 6");
    put(m, 1186, 17, "medium", "Falador medium task 7");
    put(m, 1186, 18, "medium", "Falador medium task 8");
    put(m, 1186, 20, "medium", "Falador medium task 9");
    put(m, 1186, 21, "medium", "Falador medium task 10");
    put(m, 1186, 22, "medium", "Falador medium task 11");
    put(m, 1186, 23, "medium", "Falador medium task 12");
    put(m, 1186, 24, "medium", "Falador medium task 13");
    put(m, 1186, 25, "medium", "Falador medium task 14");
    put(m, 1186, 26, "hard", "Falador hard task 1");
    put(m, 1186, 27, "hard", "Falador hard task 2");
    put(m, 1186, 28, "hard", "Falador hard task 3");
    put(m, 1186, 29, "hard", "Falador hard task 4");
    put(m, 1186, 30, "hard", "Falador hard task 5");
    put(m, 1186, 31, "hard", "Falador hard task 6");
    put(m, 1187, 0, "hard", "Falador hard task 7");
    put(m, 1187, 1, "hard", "Falador hard task 8");
    put(m, 1187, 2, "hard", "Falador hard task 9");
    put(m, 1187, 3, "hard", "Falador hard task 10");
    put(m, 1187, 4, "hard", "Falador hard task 11");
    put(m, 1187, 5, "elite", "Falador elite task 1");
    put(m, 1187, 6, "elite", "Falador elite task 2");
    put(m, 1187, 7, "elite", "Falador elite task 3");
    put(m, 1187, 8, "elite", "Falador elite task 4");
    put(m, 1187, 9, "elite", "Falador elite task 5");
    put(m, 1187, 10, "elite", "Falador elite task 6");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> fremennik() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1184, 1, "easy", "Fremennik easy task 1");
    put(m, 1184, 2, "easy", "Fremennik easy task 2");
    put(m, 1184, 3, "easy", "Fremennik easy task 3");
    put(m, 1184, 4, "easy", "Fremennik easy task 4");
    put(m, 1184, 5, "easy", "Fremennik easy task 5");
    put(m, 1184, 6, "easy", "Fremennik easy task 6");
    put(m, 1184, 7, "easy", "Fremennik easy task 7");
    put(m, 1184, 8, "easy", "Fremennik easy task 8");
    put(m, 1184, 9, "easy", "Fremennik easy task 9");
    put(m, 1184, 10, "easy", "Fremennik easy task 10");
    put(m, 1184, 11, "medium", "Fremennik medium task 1");
    put(m, 1184, 12, "medium", "Fremennik medium task 2");
    put(m, 1184, 13, "medium", "Fremennik medium task 3");
    put(m, 1184, 14, "medium", "Fremennik medium task 4");
    put(m, 1184, 15, "medium", "Fremennik medium task 5");
    put(m, 1184, 17, "medium", "Fremennik medium task 6");
    put(m, 1184, 18, "medium", "Fremennik medium task 7");
    put(m, 1184, 19, "medium", "Fremennik medium task 8");
    put(m, 1184, 20, "medium", "Fremennik medium task 9");
    put(m, 1184, 21, "hard", "Fremennik hard task 1");
    put(m, 1184, 23, "hard", "Fremennik hard task 2");
    put(m, 1184, 24, "hard", "Fremennik hard task 3");
    put(m, 1184, 25, "hard", "Fremennik hard task 4");
    put(m, 1184, 26, "hard", "Fremennik hard task 5");
    put(m, 1184, 27, "hard", "Fremennik hard task 6");
    put(m, 1184, 28, "hard", "Fremennik hard task 7");
    put(m, 1184, 29, "hard", "Fremennik hard task 8");
    put(m, 1184, 30, "hard", "Fremennik hard task 9");
    put(m, 1184, 31, "elite", "Fremennik elite task 1");
    put(m, 1185, 0, "elite", "Fremennik elite task 2");
    put(m, 1185, 1, "elite", "Fremennik elite task 3");
    put(m, 1185, 2, "elite", "Fremennik elite task 4");
    put(m, 1185, 3, "elite", "Fremennik elite task 5");
    put(m, 1185, 4, "elite", "Fremennik elite task 6");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> kandarin() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1178, 1, "easy", "Kandarin easy task 1");
    put(m, 1178, 2, "easy", "Kandarin easy task 2");
    put(m, 1178, 3, "easy", "Kandarin easy task 3");
    put(m, 1178, 4, "easy", "Kandarin easy task 4");
    put(m, 1178, 5, "easy", "Kandarin easy task 5");
    put(m, 1178, 6, "easy", "Kandarin easy task 6");
    put(m, 1178, 7, "easy", "Kandarin easy task 7");
    put(m, 1178, 8, "easy", "Kandarin easy task 8");
    put(m, 1178, 9, "easy", "Kandarin easy task 9");
    put(m, 1178, 10, "easy", "Kandarin easy task 10");
    put(m, 1178, 11, "easy", "Kandarin easy task 11");
    put(m, 1178, 12, "medium", "Kandarin medium task 1");
    put(m, 1178, 13, "medium", "Kandarin medium task 2");
    put(m, 1178, 14, "medium", "Kandarin medium task 3");
    put(m, 1178, 15, "medium", "Kandarin medium task 4");
    put(m, 1178, 16, "medium", "Kandarin medium task 5");
    put(m, 1178, 17, "medium", "Kandarin medium task 6");
    put(m, 1178, 18, "medium", "Kandarin medium task 7");
    put(m, 1178, 19, "medium", "Kandarin medium task 8");
    put(m, 1178, 20, "medium", "Kandarin medium task 9");
    put(m, 1178, 21, "medium", "Kandarin medium task 10");
    put(m, 1178, 22, "medium", "Kandarin medium task 11");
    put(m, 1178, 23, "medium", "Kandarin medium task 12");
    put(m, 1178, 24, "medium", "Kandarin medium task 13");
    put(m, 1178, 25, "medium", "Kandarin medium task 14");
    put(m, 1178, 26, "hard", "Kandarin hard task 1");
    put(m, 1178, 27, "hard", "Kandarin hard task 2");
    put(m, 1178, 28, "hard", "Kandarin hard task 3");
    put(m, 1178, 29, "hard", "Kandarin hard task 4");
    put(m, 1178, 30, "hard", "Kandarin hard task 5");
    put(m, 1178, 31, "hard", "Kandarin hard task 6");
    put(m, 1179, 0, "hard", "Kandarin hard task 7");
    put(m, 1179, 1, "hard", "Kandarin hard task 8");
    put(m, 1179, 2, "hard", "Kandarin hard task 9");
    put(m, 1179, 3, "hard", "Kandarin hard task 10");
    put(m, 1179, 4, "hard", "Kandarin hard task 11");
    put(m, 1179, 5, "elite", "Kandarin elite task 1");
    put(m, 1179, 6, "elite", "Kandarin elite task 2");
    put(m, 1179, 7, "elite", "Kandarin elite task 3");
    put(m, 1179, 8, "elite", "Kandarin elite task 4");
    put(m, 1179, 9, "elite", "Kandarin elite task 5");
    put(m, 1179, 10, "elite", "Kandarin elite task 6");
    put(m, 1179, 11, "elite", "Kandarin elite task 7");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> kourendKebos() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 2085, 1, "easy", "Kourend & Kebos easy task 1");
    put(m, 2085, 2, "easy", "Kourend & Kebos easy task 2");
    put(m, 2085, 3, "easy", "Kourend & Kebos easy task 3");
    put(m, 2085, 4, "easy", "Kourend & Kebos easy task 4");
    put(m, 2085, 5, "easy", "Kourend & Kebos easy task 5");
    put(m, 2085, 6, "easy", "Kourend & Kebos easy task 6");
    put(m, 2085, 7, "easy", "Kourend & Kebos easy task 7");
    put(m, 2085, 8, "easy", "Kourend & Kebos easy task 8");
    put(m, 2085, 9, "easy", "Kourend & Kebos easy task 9");
    put(m, 2085, 10, "easy", "Kourend & Kebos easy task 10");
    put(m, 2085, 11, "easy", "Kourend & Kebos easy task 11");
    put(m, 2085, 12, "easy", "Kourend & Kebos easy task 12");
    put(m, 2085, 25, "medium", "Kourend & Kebos medium task 1");
    put(m, 2085, 13, "medium", "Kourend & Kebos medium task 2");
    put(m, 2085, 14, "medium", "Kourend & Kebos medium task 3");
    put(m, 2085, 15, "medium", "Kourend & Kebos medium task 4");
    put(m, 2085, 21, "medium", "Kourend & Kebos medium task 5");
    put(m, 2085, 16, "medium", "Kourend & Kebos medium task 6");
    put(m, 2085, 17, "medium", "Kourend & Kebos medium task 7");
    put(m, 2085, 18, "medium", "Kourend & Kebos medium task 8");
    put(m, 2085, 19, "medium", "Kourend & Kebos medium task 9");
    put(m, 2085, 22, "medium", "Kourend & Kebos medium task 10");
    put(m, 2085, 20, "medium", "Kourend & Kebos medium task 11");
    put(m, 2085, 23, "medium", "Kourend & Kebos medium task 12");
    put(m, 2085, 24, "medium", "Kourend & Kebos medium task 13");
    put(m, 2085, 26, "hard", "Kourend & Kebos hard task 1");
    put(m, 2085, 27, "hard", "Kourend & Kebos hard task 2");
    put(m, 2085, 28, "hard", "Kourend & Kebos hard task 3");
    put(m, 2085, 29, "hard", "Kourend & Kebos hard task 4");
    put(m, 2085, 31, "hard", "Kourend & Kebos hard task 5");
    put(m, 2085, 30, "hard", "Kourend & Kebos hard task 6");
    put(m, 2086, 0, "hard", "Kourend & Kebos hard task 7");
    put(m, 2086, 1, "hard", "Kourend & Kebos hard task 8");
    put(m, 2086, 2, "hard", "Kourend & Kebos hard task 9");
    put(m, 2086, 3, "hard", "Kourend & Kebos hard task 10");
    put(m, 2086, 4, "elite", "Kourend & Kebos elite task 1");
    put(m, 2086, 5, "elite", "Kourend & Kebos elite task 2");
    put(m, 2086, 6, "elite", "Kourend & Kebos elite task 3");
    put(m, 2086, 7, "elite", "Kourend & Kebos elite task 4");
    put(m, 2086, 8, "elite", "Kourend & Kebos elite task 5");
    put(m, 2086, 9, "elite", "Kourend & Kebos elite task 6");
    put(m, 2086, 10, "elite", "Kourend & Kebos elite task 7");
    put(m, 2086, 11, "elite", "Kourend & Kebos elite task 8");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> lumbridgeDraynor() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1194, 1, "easy", "Lumbridge & Draynor easy task 1");
    put(m, 1194, 2, "easy", "Lumbridge & Draynor easy task 2");
    put(m, 1194, 3, "easy", "Lumbridge & Draynor easy task 3");
    put(m, 1194, 4, "easy", "Lumbridge & Draynor easy task 4");
    put(m, 1194, 5, "easy", "Lumbridge & Draynor easy task 5");
    put(m, 1194, 6, "easy", "Lumbridge & Draynor easy task 6");
    put(m, 1194, 7, "easy", "Lumbridge & Draynor easy task 7");
    put(m, 1194, 8, "easy", "Lumbridge & Draynor easy task 8");
    put(m, 1194, 9, "easy", "Lumbridge & Draynor easy task 9");
    put(m, 1194, 10, "easy", "Lumbridge & Draynor easy task 10");
    put(m, 1194, 11, "easy", "Lumbridge & Draynor easy task 11");
    put(m, 1194, 12, "easy", "Lumbridge & Draynor easy task 12");
    put(m, 1194, 13, "medium", "Lumbridge & Draynor medium task 1");
    put(m, 1194, 14, "medium", "Lumbridge & Draynor medium task 2");
    put(m, 1194, 15, "medium", "Lumbridge & Draynor medium task 3");
    put(m, 1194, 16, "medium", "Lumbridge & Draynor medium task 4");
    put(m, 1194, 17, "medium", "Lumbridge & Draynor medium task 5");
    put(m, 1194, 18, "medium", "Lumbridge & Draynor medium task 6");
    put(m, 1194, 19, "medium", "Lumbridge & Draynor medium task 7");
    put(m, 1194, 20, "medium", "Lumbridge & Draynor medium task 8");
    put(m, 1194, 21, "medium", "Lumbridge & Draynor medium task 9");
    put(m, 1194, 22, "medium", "Lumbridge & Draynor medium task 10");
    put(m, 1194, 23, "medium", "Lumbridge & Draynor medium task 11");
    put(m, 1194, 24, "medium", "Lumbridge & Draynor medium task 12");
    put(m, 1194, 25, "hard", "Lumbridge & Draynor hard task 1");
    put(m, 1194, 26, "hard", "Lumbridge & Draynor hard task 2");
    put(m, 1194, 27, "hard", "Lumbridge & Draynor hard task 3");
    put(m, 1194, 28, "hard", "Lumbridge & Draynor hard task 4");
    put(m, 1194, 29, "hard", "Lumbridge & Draynor hard task 5");
    put(m, 1194, 30, "hard", "Lumbridge & Draynor hard task 6");
    put(m, 1194, 31, "hard", "Lumbridge & Draynor hard task 7");
    put(m, 1195, 0, "hard", "Lumbridge & Draynor hard task 8");
    put(m, 1195, 1, "hard", "Lumbridge & Draynor hard task 9");
    put(m, 1195, 2, "hard", "Lumbridge & Draynor hard task 10");
    put(m, 1195, 3, "hard", "Lumbridge & Draynor hard task 11");
    put(m, 1195, 4, "elite", "Lumbridge & Draynor elite task 1");
    put(m, 1195, 5, "elite", "Lumbridge & Draynor elite task 2");
    put(m, 1195, 6, "elite", "Lumbridge & Draynor elite task 3");
    put(m, 1195, 7, "elite", "Lumbridge & Draynor elite task 4");
    put(m, 1195, 8, "elite", "Lumbridge & Draynor elite task 5");
    put(m, 1195, 9, "elite", "Lumbridge & Draynor elite task 6");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> morytania() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1180, 1, "easy", "Morytania easy task 1");
    put(m, 1180, 2, "easy", "Morytania easy task 2");
    put(m, 1180, 3, "easy", "Morytania easy task 3");
    put(m, 1180, 4, "easy", "Morytania easy task 4");
    put(m, 1180, 5, "easy", "Morytania easy task 5");
    put(m, 1180, 6, "easy", "Morytania easy task 6");
    put(m, 1180, 7, "easy", "Morytania easy task 7");
    put(m, 1180, 8, "easy", "Morytania easy task 8");
    put(m, 1180, 9, "easy", "Morytania easy task 9");
    put(m, 1180, 10, "easy", "Morytania easy task 10");
    put(m, 1180, 11, "easy", "Morytania easy task 11");
    put(m, 1180, 12, "medium", "Morytania medium task 1");
    put(m, 1180, 13, "medium", "Morytania medium task 2");
    put(m, 1180, 14, "medium", "Morytania medium task 3");
    put(m, 1180, 15, "medium", "Morytania medium task 4");
    put(m, 1180, 16, "medium", "Morytania medium task 5");
    put(m, 1180, 17, "medium", "Morytania medium task 6");
    put(m, 1180, 18, "medium", "Morytania medium task 7");
    put(m, 1180, 19, "medium", "Morytania medium task 8");
    put(m, 1180, 20, "medium", "Morytania medium task 9");
    put(m, 1180, 21, "medium", "Morytania medium task 10");
    put(m, 1180, 22, "medium", "Morytania medium task 11");
    put(m, 1180, 23, "hard", "Morytania hard task 1");
    put(m, 1180, 24, "hard", "Morytania hard task 2");
    put(m, 1180, 25, "hard", "Morytania hard task 3");
    put(m, 1180, 26, "hard", "Morytania hard task 4");
    put(m, 1180, 27, "hard", "Morytania hard task 5");
    put(m, 1180, 28, "hard", "Morytania hard task 6");
    put(m, 1180, 29, "hard", "Morytania hard task 7");
    put(m, 1180, 30, "hard", "Morytania hard task 8");
    put(m, 1181, 1, "hard", "Morytania hard task 9");
    put(m, 1181, 2, "hard", "Morytania hard task 10");
    put(m, 1181, 3, "elite", "Morytania elite task 1");
    put(m, 1181, 4, "elite", "Morytania elite task 2");
    put(m, 1181, 5, "elite", "Morytania elite task 3");
    put(m, 1181, 6, "elite", "Morytania elite task 4");
    put(m, 1181, 7, "elite", "Morytania elite task 5");
    put(m, 1181, 8, "elite", "Morytania elite task 6");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> varrock() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1176, 1, "easy", "Varrock easy task 1");
    put(m, 1176, 2, "easy", "Varrock easy task 2");
    put(m, 1176, 3, "easy", "Varrock easy task 3");
    put(m, 1176, 4, "easy", "Varrock easy task 4");
    put(m, 1176, 5, "easy", "Varrock easy task 5");
    put(m, 1176, 6, "easy", "Varrock easy task 6");
    put(m, 1176, 7, "easy", "Varrock easy task 7");
    put(m, 1176, 8, "easy", "Varrock easy task 8");
    put(m, 1176, 9, "easy", "Varrock easy task 9");
    put(m, 1176, 10, "easy", "Varrock easy task 10");
    put(m, 1176, 11, "easy", "Varrock easy task 11");
    put(m, 1176, 12, "easy", "Varrock easy task 12");
    put(m, 1176, 13, "easy", "Varrock easy task 13");
    put(m, 1176, 14, "easy", "Varrock easy task 14");
    put(m, 1176, 15, "medium", "Varrock medium task 1");
    put(m, 1176, 16, "medium", "Varrock medium task 2");
    put(m, 1176, 18, "medium", "Varrock medium task 3");
    put(m, 1176, 19, "medium", "Varrock medium task 4");
    put(m, 1176, 20, "medium", "Varrock medium task 5");
    put(m, 1176, 21, "medium", "Varrock medium task 6");
    put(m, 1176, 22, "medium", "Varrock medium task 7");
    put(m, 1176, 23, "medium", "Varrock medium task 8");
    put(m, 1176, 24, "medium", "Varrock medium task 9");
    put(m, 1176, 25, "medium", "Varrock medium task 10");
    put(m, 1176, 26, "medium", "Varrock medium task 11");
    put(m, 1176, 27, "medium", "Varrock medium task 12");
    put(m, 1176, 28, "medium", "Varrock medium task 13");
    put(m, 1176, 29, "hard", "Varrock hard task 1");
    put(m, 1176, 30, "hard", "Varrock hard task 2");
    put(m, 1176, 31, "hard", "Varrock hard task 3");
    put(m, 1177, 0, "hard", "Varrock hard task 4");
    put(m, 1177, 1, "hard", "Varrock hard task 5");
    put(m, 1177, 2, "hard", "Varrock hard task 6");
    put(m, 1177, 3, "hard", "Varrock hard task 7");
    put(m, 1177, 4, "hard", "Varrock hard task 8");
    put(m, 1177, 5, "hard", "Varrock hard task 9");
    put(m, 1177, 6, "hard", "Varrock hard task 10");
    put(m, 1177, 7, "elite", "Varrock elite task 1");
    put(m, 1177, 8, "elite", "Varrock elite task 2");
    put(m, 1177, 9, "elite", "Varrock elite task 3");
    put(m, 1177, 10, "elite", "Varrock elite task 4");
    put(m, 1177, 11, "elite", "Varrock elite task 5");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> westernProvinces() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1182, 1, "easy", "Western Provinces easy task 1");
    put(m, 1182, 2, "easy", "Western Provinces easy task 2");
    put(m, 1182, 3, "easy", "Western Provinces easy task 3");
    put(m, 1182, 4, "easy", "Western Provinces easy task 4");
    put(m, 1182, 5, "easy", "Western Provinces easy task 5");
    put(m, 1182, 6, "easy", "Western Provinces easy task 6");
    put(m, 1182, 7, "easy", "Western Provinces easy task 7");
    put(m, 1182, 8, "easy", "Western Provinces easy task 8");
    put(m, 1182, 9, "easy", "Western Provinces easy task 9");
    put(m, 1182, 10, "easy", "Western Provinces easy task 10");
    put(m, 1182, 11, "easy", "Western Provinces easy task 11");
    put(m, 1182, 12, "medium", "Western Provinces medium task 1");
    put(m, 1182, 13, "medium", "Western Provinces medium task 2");
    put(m, 1182, 14, "medium", "Western Provinces medium task 3");
    put(m, 1182, 15, "medium", "Western Provinces medium task 4");
    put(m, 1182, 16, "medium", "Western Provinces medium task 5");
    put(m, 1182, 17, "medium", "Western Provinces medium task 6");
    put(m, 1182, 18, "medium", "Western Provinces medium task 7");
    put(m, 1182, 19, "medium", "Western Provinces medium task 8");
    put(m, 1182, 20, "medium", "Western Provinces medium task 9");
    put(m, 1182, 21, "medium", "Western Provinces medium task 10");
    put(m, 1182, 22, "medium", "Western Provinces medium task 11");
    put(m, 1182, 23, "medium", "Western Provinces medium task 12");
    put(m, 1182, 24, "medium", "Western Provinces medium task 13");
    put(m, 1182, 25, "hard", "Western Provinces hard task 1");
    put(m, 1182, 26, "hard", "Western Provinces hard task 2");
    put(m, 1182, 27, "hard", "Western Provinces hard task 3");
    put(m, 1182, 28, "hard", "Western Provinces hard task 4");
    put(m, 1182, 29, "hard", "Western Provinces hard task 5");
    put(m, 1182, 30, "hard", "Western Provinces hard task 6");
    put(m, 1182, 31, "hard", "Western Provinces hard task 7");
    put(m, 1183, 0, "hard", "Western Provinces hard task 8");
    put(m, 1183, 1, "hard", "Western Provinces hard task 9");
    put(m, 1183, 2, "hard", "Western Provinces hard task 10");
    put(m, 1183, 3, "hard", "Western Provinces hard task 11");
    put(m, 1183, 4, "hard", "Western Provinces hard task 12");
    put(m, 1183, 5, "hard", "Western Provinces hard task 13");
    put(m, 1183, 6, "elite", "Western Provinces elite task 1");
    put(m, 1183, 7, "elite", "Western Provinces elite task 2");
    put(m, 1183, 8, "elite", "Western Provinces elite task 3");
    put(m, 1183, 9, "elite", "Western Provinces elite task 4");
    put(m, 1183, 12, "elite", "Western Provinces elite task 5");
    put(m, 1183, 13, "elite", "Western Provinces elite task 6");
    put(m, 1183, 14, "elite", "Western Provinces elite task 7");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> wilderness() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1192, 1, "easy", "Wilderness easy task 1");
    put(m, 1192, 2, "easy", "Wilderness easy task 2");
    put(m, 1192, 3, "easy", "Wilderness easy task 3");
    put(m, 1192, 4, "easy", "Wilderness easy task 4");
    put(m, 1192, 5, "easy", "Wilderness easy task 5");
    put(m, 1192, 6, "easy", "Wilderness easy task 6");
    put(m, 1192, 7, "easy", "Wilderness easy task 7");
    put(m, 1192, 8, "easy", "Wilderness easy task 8");
    put(m, 1192, 9, "easy", "Wilderness easy task 9");
    put(m, 1192, 10, "easy", "Wilderness easy task 10");
    put(m, 1192, 11, "easy", "Wilderness easy task 11");
    put(m, 1192, 12, "easy", "Wilderness easy task 12");
    put(m, 1192, 13, "medium", "Wilderness medium task 1");
    put(m, 1192, 14, "medium", "Wilderness medium task 2");
    put(m, 1192, 15, "medium", "Wilderness medium task 3");
    put(m, 1192, 16, "medium", "Wilderness medium task 4");
    put(m, 1192, 18, "medium", "Wilderness medium task 5");
    put(m, 1192, 19, "medium", "Wilderness medium task 6");
    put(m, 1192, 20, "medium", "Wilderness medium task 7");
    put(m, 1192, 21, "medium", "Wilderness medium task 8");
    put(m, 1192, 22, "medium", "Wilderness medium task 9");
    put(m, 1192, 23, "medium", "Wilderness medium task 10");
    put(m, 1192, 24, "medium", "Wilderness medium task 11");
    put(m, 1192, 25, "hard", "Wilderness hard task 1");
    put(m, 1192, 26, "hard", "Wilderness hard task 2");
    put(m, 1192, 27, "hard", "Wilderness hard task 3");
    put(m, 1192, 28, "hard", "Wilderness hard task 4");
    put(m, 1192, 29, "hard", "Wilderness hard task 5");
    put(m, 1192, 30, "hard", "Wilderness hard task 6");
    put(m, 1192, 31, "hard", "Wilderness hard task 7");
    put(m, 1193, 0, "hard", "Wilderness hard task 8");
    put(m, 1193, 1, "hard", "Wilderness hard task 9");
    put(m, 1193, 2, "hard", "Wilderness hard task 10");
    put(m, 1193, 3, "elite", "Wilderness elite task 1");
    put(m, 1193, 5, "elite", "Wilderness elite task 2");
    put(m, 1193, 7, "elite", "Wilderness elite task 3");
    put(m, 1193, 8, "elite", "Wilderness elite task 4");
    put(m, 1193, 9, "elite", "Wilderness elite task 5");
    put(m, 1193, 10, "elite", "Wilderness elite task 6");
    put(m, 1193, 11, "elite", "Wilderness elite task 7");
    return m;
  }

  private static Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> karamja() {
    Map<Integer, Map<Integer, DiaryTaskManifest.Entry>> m = new LinkedHashMap<>();
    put(m, 1200, 1, "elite", "Karamja elite task 1");
    put(m, 1200, 2, "elite", "Karamja elite task 2");
    put(m, 1200, 3, "elite", "Karamja elite task 3");
    put(m, 1200, 4, "elite", "Karamja elite task 4");
    put(m, 1200, 5, "elite", "Karamja elite task 5");
    return m;
  }
}
