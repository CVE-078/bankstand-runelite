package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Maps each wire region key to the varplayers that pack its per-task completion bits (27 in
 * total). Which bit is which task is {@link DiaryTaskManifest}'s job.
 *
 * <p>{@code ARDOUNGE} is RuneLite's own spelling, not a typo.
 */
public final class DiaryTaskVarplayers {
  private DiaryTaskVarplayers() {}

  /** Ordered: wire region key to its varplayer ids. */
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
    // Kourend has a third varplayer.
    m.put("KOUREND_KEBOS", new int[] {
        VarPlayerID.KOUREND_ACHIEVEMENT_DIARY, VarPlayerID.KOUREND_ACHIEVEMENT_DIARY2,
        VarPlayerID.KOUREND_ACHIEVEMENT_DIARY_MULTISTAGE});
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
    // Karamja predates the standard layout and uses four.
    m.put("KARAMJA", new int[] {
        VarPlayerID.ATJUN_TASKS_1, VarPlayerID.ATJUN_TASKS_2,
        VarPlayerID.ATJUN_TASKS_3, VarPlayerID.ATJUN_TASKS_4});
    return m;
  }
}
