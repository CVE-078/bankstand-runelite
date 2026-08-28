package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Maps the same wire region keys {@link DiaryVarbits}/{@link DiaryTaskVarbits} use to the
 * varplayer(s) that pack per-task completion state for that region.
 *
 * <p>Per-task identity is not a varbit anywhere in the client: {@code VarbitID} names only
 * {@code *_COMPLETE}/{@code *_REWARD}/{@code *_COUNT} per tier, never an individual task.
 * Individual tasks are bits packed into these varplayers instead, with no name RuneLite
 * exposes for what any single bit means. That mapping is {@link DiaryTaskManifest}'s job;
 * this table only says which varplayer(s) to read for a region, verified directly against
 * {@code VarPlayerID} rather than assumed.
 *
 * <p>Ten regions use exactly two ({@code _ACHIEVEMENT_DIARY}, {@code _ACHIEVEMENT_DIARY2}).
 * Kourend & Kebos has a third ({@code _MULTISTAGE}); Karamja uses four differently-named
 * ones ({@code ATJUN_TASKS_1..4}) because its diary predates the standard naming, the same
 * split {@link DiaryVarbits} and {@link DiaryTaskVarbits} already document for their own
 * ids. 20 + 3 + 4 = 27 varplayers total.
 *
 * <p>Two of {@code VarPlayerID}'s own names are worth flagging so a future reader does not
 * mistake them for typos introduced here: Jagex's own constant spells Ardougne as {@code
 * ARDOUNGE_ACHIEVEMENT_DIARY}, and Lumbridge & Draynor's is {@code
 * LUMB_DRAY_ACHIEVEMENT_DIARY}. Both are copied verbatim from the field, not corrected.
 *
 * <p>A plain data table with no {@code Client} dependency, unit-testable without a live
 * game client, the same shape as {@link DiaryVarbits} and {@link DiaryTaskVarbits}.
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
