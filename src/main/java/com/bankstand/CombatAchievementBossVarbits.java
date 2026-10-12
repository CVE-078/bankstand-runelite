package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * Maps a combat achievement source's name (as the server's task corpus spells it) to the varbit
 * holding how many of its tasks are done.
 *
 * <p>The tier totals ({@code EASY} to {@code GRANDMASTER}) are omitted because they share ids
 * with {@link CombatAchievementVarbits}. {@code COWBOSS}, {@code DOM} and {@code MAD_ANGEL} are
 * omitted because their boss could not be identified with confidence, and a count on the wrong
 * boss is a visible bug. Ids are symbolic {@code VarbitID} references so a renamed constant fails
 * the compile (reflection is not allowed on the Plugin Hub).
 */
public final class CombatAchievementBossVarbits {
  private CombatAchievementBossVarbits() {}

  /** Ordered, unmodifiable: wire key (the corpus's own source name) to varbit id. */
  public static final Map<String, Integer> ALL = Collections.unmodifiableMap(build());

  private static Map<String, Integer> build() {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("Abyssal Sire", VarbitID.CA_TOTAL_TASKS_COMPLETED_ABYSSALSIRE);
    m.put("Amoxliatl", VarbitID.CA_TOTAL_TASKS_COMPLETED_AMOXLIATL);
    m.put("Araxxor", VarbitID.CA_TOTAL_TASKS_COMPLETED_ARAXXOR);
    // Godwars generals are keyed by god here (RuneLite's own naming) but by the boss
    // NPC's name in the corpus.
    m.put("Kree'arra", VarbitID.CA_TOTAL_TASKS_COMPLETED_ARMADYL);
    m.put("General Graardor", VarbitID.CA_TOTAL_TASKS_COMPLETED_BANDOS);
    m.put("Commander Zilyana", VarbitID.CA_TOTAL_TASKS_COMPLETED_SARADOMIN);
    m.put("K'ril Tsutsaroth", VarbitID.CA_TOTAL_TASKS_COMPLETED_ZAMORAK);
    m.put("Barrows", VarbitID.CA_TOTAL_TASKS_COMPLETED_BARROWS);
    m.put("Bryophyta", VarbitID.CA_TOTAL_TASKS_COMPLETED_BRYOPHYTA);
    m.put("Callisto", VarbitID.CA_TOTAL_TASKS_COMPLETED_CALLISTO);
    // CATA_BOSS (12918), not the much older DOM (3209).
    m.put("Doom of Mokhaiotl", VarbitID.CA_TOTAL_TASKS_COMPLETED_CATA_BOSS);
    m.put("Cerberus", VarbitID.CA_TOTAL_TASKS_COMPLETED_CERBERUS);
    m.put("Chaos Elemental", VarbitID.CA_TOTAL_TASKS_COMPLETED_CHAOSELE);
    m.put("Chaos Fanatic", VarbitID.CA_TOTAL_TASKS_COMPLETED_CHAOSFANATIC);
    m.put("Fortis Colosseum", VarbitID.CA_TOTAL_TASKS_COMPLETED_COLOSSEUM);
    m.put("Corporeal Beast", VarbitID.CA_TOTAL_TASKS_COMPLETED_CORP);
    m.put("Crazy Archaeologist", VarbitID.CA_TOTAL_TASKS_COMPLETED_CRAZYARCHAEOLOGIST);
    m.put("Deranged Archaeologist", VarbitID.CA_TOTAL_TASKS_COMPLETED_DERANGEDARCHAEOLOGIST);
    m.put("Duke Sucellus", VarbitID.CA_TOTAL_TASKS_COMPLETED_DUKESUCELLUS);
    // GARGBOSS is the Grotesque Guardians encounter, not the slayer monster.
    m.put("Grotesque Guardians", VarbitID.CA_TOTAL_TASKS_COMPLETED_GARGBOSS);
    // Base varbit is the normal Gauntlet, _HM is Corrupted.
    m.put("Crystalline Hunllef", VarbitID.CA_TOTAL_TASKS_COMPLETED_GAUNTLET);
    m.put("Corrupted Hunllef", VarbitID.CA_TOTAL_TASKS_COMPLETED_GAUNTLET_HM);
    m.put("Shellbane gryphon", VarbitID.CA_TOTAL_TASKS_COMPLETED_GRYPHON_BOSS);
    m.put("Hespori", VarbitID.CA_TOTAL_TASKS_COMPLETED_HESPORI);
    // The corpus's one "Giants" task covers several giants; this is the closest match.
    m.put("Giants", VarbitID.CA_TOTAL_TASKS_COMPLETED_HILLGIANT_BOSS);
    m.put("The Hueycoatl", VarbitID.CA_TOTAL_TASKS_COMPLETED_HUEYCOATL);
    m.put("Alchemical Hydra", VarbitID.CA_TOTAL_TASKS_COMPLETED_HYDRABOSS);
    m.put("TzTok-Jad", VarbitID.CA_TOTAL_TASKS_COMPLETED_JAD);
    m.put("Kalphite Queen", VarbitID.CA_TOTAL_TASKS_COMPLETED_KALPHITE);
    m.put("King Black Dragon", VarbitID.CA_TOTAL_TASKS_COMPLETED_KBD);
    m.put("Kraken", VarbitID.CA_TOTAL_TASKS_COMPLETED_KRAKEN_BOSS);
    m.put("Leviathan", VarbitID.CA_TOTAL_TASKS_COMPLETED_LEVIATHAN);
    m.put("Maggot King", VarbitID.CA_TOTAL_TASKS_COMPLETED_MAGGOTKING);
    m.put("The Mimic", VarbitID.CA_TOTAL_TASKS_COMPLETED_MIMIC);
    m.put("Giant Mole", VarbitID.CA_TOTAL_TASKS_COMPLETED_MOLE);
    m.put("Phantom Muspah", VarbitID.CA_TOTAL_TASKS_COMPLETED_MUSPAH);
    m.put("Nex", VarbitID.CA_TOTAL_TASKS_COMPLETED_NEX);
    m.put("The Nightmare", VarbitID.CA_TOTAL_TASKS_COMPLETED_NIGHTMARE);
    m.put("Phosani's Nightmare", VarbitID.CA_TOTAL_TASKS_COMPLETED_PHOSANIS);
    m.put("Moons of Peril", VarbitID.CA_TOTAL_TASKS_COMPLETED_PERILOUS_MOONS);
    m.put("Dagannoth Prime", VarbitID.CA_TOTAL_TASKS_COMPLETED_PRIME);
    m.put("Dagannoth Rex", VarbitID.CA_TOTAL_TASKS_COMPLETED_REX);
    m.put("Dagannoth Supreme", VarbitID.CA_TOTAL_TASKS_COMPLETED_SUPREME);
    m.put("Zulrah", VarbitID.CA_TOTAL_TASKS_COMPLETED_SNAKEBOSS);
    m.put("Scurrius", VarbitID.CA_TOTAL_TASKS_COMPLETED_RAT_BOSS);
    m.put("Royal Titans", VarbitID.CA_TOTAL_TASKS_COMPLETED_ROYAL_TITANS);
    m.put("Sarachnis", VarbitID.CA_TOTAL_TASKS_COMPLETED_SARACHNIS);
    m.put("Scorpia", VarbitID.CA_TOTAL_TASKS_COMPLETED_SCORPIA);
    m.put("Tempoross", VarbitID.CA_TOTAL_TASKS_COMPLETED_TEMPOROSS);
    m.put("Theatre of Blood", VarbitID.CA_TOTAL_TASKS_COMPLETED_THEATREOFBLOOD);
    m.put("Theatre of Blood: Hard Mode", VarbitID.CA_TOTAL_TASKS_COMPLETED_THEATREOFBLOOD_HARD);
    m.put(
        "Theatre of Blood: Entry Mode", VarbitID.CA_TOTAL_TASKS_COMPLETED_THEATREOFBLOOD_STORY);
    m.put("Thermonuclear Smoke Devil", VarbitID.CA_TOTAL_TASKS_COMPLETED_THERMY);
    m.put("Tombs of Amascut", VarbitID.CA_TOTAL_TASKS_COMPLETED_TOMBSOFAMASCUT);
    m.put(
        "Tombs of Amascut: Entry Mode", VarbitID.CA_TOTAL_TASKS_COMPLETED_TOMBSOFAMASCUT_ENTRY);
    m.put(
        "Tombs of Amascut: Expert Mode", VarbitID.CA_TOTAL_TASKS_COMPLETED_TOMBSOFAMASCUT_EXPERT);
    m.put("TzHaar-Ket-Rak's Challenges", VarbitID.CA_TOTAL_TASKS_COMPLETED_TZHAARKETRAK);
    m.put("Vardorvis", VarbitID.CA_TOTAL_TASKS_COMPLETED_VARDORVIS);
    m.put("Venenatis", VarbitID.CA_TOTAL_TASKS_COMPLETED_VENENATIS);
    m.put("Vet'ion", VarbitID.CA_TOTAL_TASKS_COMPLETED_VETION);
    m.put("Vorkath", VarbitID.CA_TOTAL_TASKS_COMPLETED_VORKATH);
    m.put("Whisperer", VarbitID.CA_TOTAL_TASKS_COMPLETED_WHISPERER);
    m.put("Wintertodt", VarbitID.CA_TOTAL_TASKS_COMPLETED_WINTERTODT);
    m.put("Chambers of Xeric", VarbitID.CA_TOTAL_TASKS_COMPLETED_XERICCHAMBERS);
    m.put(
        "Chambers of Xeric: Challenge Mode",
        VarbitID.CA_TOTAL_TASKS_COMPLETED_XERICCHAMBERS_CHALLENGE);
    m.put("Yama", VarbitID.CA_TOTAL_TASKS_COMPLETED_YAMA);
    m.put("Zalcano", VarbitID.CA_TOTAL_TASKS_COMPLETED_ZALCANO);
    m.put("TzKal-Zuk", VarbitID.CA_TOTAL_TASKS_COMPLETED_ZUK);
    return m;
  }
}
