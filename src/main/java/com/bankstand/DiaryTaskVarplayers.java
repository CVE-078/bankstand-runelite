package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Maps the same wire region keys {@link DiaryVarbits}/{@link DiaryTaskVarbits} use to the
 * varplayer(s) that pack that region's per-task completion bits.
 *
 * <p>No varbit names an individual task, only {@code *_COMPLETE}/{@code *_REWARD}/
 * {@code *_COUNT} per tier. The bits live in these varplayers instead, with no RuneLite
 * name for what any one means; that mapping is {@link DiaryTaskManifest}'s job. This table
 * just says which varplayer(s) to read, verified against {@code VarPlayerID} rather than
 * assumed.
 *
 * <p>Ten regions use two ({@code _ACHIEVEMENT_DIARY}/{@code _ACHIEVEMENT_DIARY2}). Kourend
 * & Kebos has a third ({@code _MULTISTAGE}); Karamja predates the standard naming and uses
 * four ({@code ATJUN_TASKS_1..4}). 20 + 3 + 4 = 27.
 *
 * <p>{@code VarPlayerID} really does spell Ardougne's constant {@code
 * ARDOUNGE_ACHIEVEMENT_DIARY} and Lumbridge & Draynor's {@code LUMB_DRAY_ACHIEVEMENT_DIARY}.
 * Copied as-is, not typos.
 */
public final class DiaryTaskVarplayers {
  private DiaryTaskVarplayers() {}

  /** Ordered, unmodifiable: wire region key to its varplayer ids, region-canonical order. */
  public static final Map<String, int[]> ALL = Collections.unmodifiableMap(build());

  private static Map<String, int[]> build() {
    Map<String, int[]> m = new LinkedHashMap<>();
    m.put("ARDOUGNE", new int[] {
        VarPlayerID.ARDOUNGE_ACHIEVEMENT_DIARY, VarPlayerID.ARDOUNGE_ACHIEVEMENT_DIARY2});
    m.put("DESERT", new int[] {
        VarPlayerID.DESERT_ACHIEVEMENT_DIARY, VarPlayerID.DESERT_ACHIEVEMENT_DIARY2});
    m.put("FALADOR", new int[] {
        VarPlayerID.FALADOR_ACHIEVEMENT_DIARY, VarPlayerID.FALADOR_ACHIEVEMENT_DIARY2});
    m.put("FREMENNIK", new int[] {
        VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY, VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY2});
    m.put("KANDARIN", new int[] {
        VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY, VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2});
    // KOUREND is the RuneLite constant prefix; the wire key is KOUREND_KEBOS, matching
    // DiaryVarbits/DiaryTaskVarbits. The third varplayer is this region's own irregularity.
    m.put("KOUREND_KEBOS", new int[] {
        VarPlayerID.KOUREND_ACHIEVEMENT_DIARY, VarPlayerID.KOUREND_ACHIEVEMENT_DIARY2,
        VarPlayerID.KOUREND_ACHIEVEMENT_DIARY_MULTISTAGE});
    // LUMB_DRAY is the RuneLite constant prefix; the wire key is LUMBRIDGE_DRAYNOR.
    m.put("LUMBRIDGE_DRAYNOR", new int[] {
        VarPlayerID.LUMB_DRAY_ACHIEVEMENT_DIARY, VarPlayerID.LUMB_DRAY_ACHIEVEMENT_DIARY2});
    m.put("MORYTANIA", new int[] {
        VarPlayerID.MORYTANIA_ACHIEVEMENT_DIARY, VarPlayerID.MORYTANIA_ACHIEVEMENT_DIARY2});
    m.put("VARROCK", new int[] {
        VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, VarPlayerID.VARROCK_ACHIEVEMENT_DIARY2});
    m.put("WESTERN_PROVINCES", new int[] {
        VarPlayerID.WESTERN_ACHIEVEMENT_DIARY, VarPlayerID.WESTERN_ACHIEVEMENT_DIARY2});
    m.put("WILDERNESS", new int[] {
        VarPlayerID.WILDERNESS_ACHIEVEMENT_DIARY, VarPlayerID.WILDERNESS_ACHIEVEMENT_DIARY2});
    // Karamja's diary predates the standard two-varplayer layout, hence four
    // differently-named ids instead of a region-prefixed DIARY/DIARY2 pair.
    m.put("KARAMJA", new int[] {
        VarPlayerID.ATJUN_TASKS_1, VarPlayerID.ATJUN_TASKS_2,
        VarPlayerID.ATJUN_TASKS_3, VarPlayerID.ATJUN_TASKS_4});
    return m;
  }
}
