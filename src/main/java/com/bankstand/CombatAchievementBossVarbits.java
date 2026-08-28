package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * Maps a combat achievement source's real name (the same string the website's own task
 * corpus keys its {@code source} field on, e.g. "Vorkath", "Theatre of Blood: Hard Mode")
 * to the varbit holding how many of that source's tasks are done.
 *
 * <p>The wire key is the boss/activity name itself, not a RuneLite-internal code, so the
 * server's join against its own corpus needs no separate name-translation table: the
 * plugin is the one place that has to know {@code VarbitID.CA_TOTAL_TASKS_COMPLETED_KBD}
 * means King Black Dragon, not the website.
 *
 * <p><b>Not every {@code CA_TOTAL_TASKS_COMPLETED_*} constant is listed here.</b> Six of
 * the 77 ({@code EASY}/{@code MEDIUM}/{@code HARD}/{@code ELITE}/{@code MASTER}/{@code
 * GRANDMASTER}) are the exact same varbit ids {@link CombatAchievementVarbits} already
 * reads for the tier-count block (confirmed by id, not name: {@code
 * CA_TOTAL_TASKS_COMPLETED_EASY} and {@code COMBAT_TASK_EASY} are both 12885), so they add
 * nothing new here. Three more are left out deliberately because a confident name could
 * not be established: {@code COWBOSS} sits among 25th-anniversary varbits with no
 * matching task in the corpus at all; {@code DOM} is a varbit id in the 3000s, a range
 * that predates Combat Achievements as a system by years, so despite superficially
 * spelling out "Doom of Mokhaiotl" it almost certainly is not that boss (see {@code
 * CATA_BOSS} below, whose id sits correctly among the 2023-era combat achievement block
 * and is used for that boss instead); {@code MAD_ANGEL} sits among brand-new
 * Sailing-skill varbits, most likely a boss too recent for the corpus this was
 * generated from. Misattributing a task count to the wrong boss is a real, visible
 * correctness bug, not a cosmetic gap, so an uncertain id is left out rather than
 * guessed.
 *
 * <p>Every id below is a symbolic reference to the real RuneLite constant, not a bare
 * number: if a future RuneLite release renames or drops one, this file fails to
 * <i>compile</i> against the new API, which is a stronger, earlier failure than a
 * runtime check could give (and reflection, which would dodge this trap a different way,
 * is off the table: the Plugin Hub review disallows it).
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
    // See the class doc: id 12918 sits in the 2023-era combat achievement block, unlike
    // the similarly-spelled but far older CA_TOTAL_TASKS_COMPLETED_DOM (id 3209), which
    // is left out for that reason.
    m.put("Doom of Mokhaiotl", VarbitID.CA_TOTAL_TASKS_COMPLETED_CATA_BOSS);
    m.put("Cerberus", VarbitID.CA_TOTAL_TASKS_COMPLETED_CERBERUS);
    m.put("Chaos Elemental", VarbitID.CA_TOTAL_TASKS_COMPLETED_CHAOSELE);
    m.put("Chaos Fanatic", VarbitID.CA_TOTAL_TASKS_COMPLETED_CHAOSFANATIC);
    m.put("Fortis Colosseum", VarbitID.CA_TOTAL_TASKS_COMPLETED_COLOSSEUM);
    m.put("Corporeal Beast", VarbitID.CA_TOTAL_TASKS_COMPLETED_CORP);
    m.put("Crazy Archaeologist", VarbitID.CA_TOTAL_TASKS_COMPLETED_CRAZYARCHAEOLOGIST);
    m.put("Deranged Archaeologist", VarbitID.CA_TOTAL_TASKS_COMPLETED_DERANGEDARCHAEOLOGIST);
    m.put("Duke Sucellus", VarbitID.CA_TOTAL_TASKS_COMPLETED_DUKESUCELLUS);
    m.put("Gargoyle", VarbitID.CA_TOTAL_TASKS_COMPLETED_GARGBOSS);
    // Gauntlet's normal and Corrupted modes have different boss forms and different
    // corpus sources.
    m.put("Corrupted Hunllef", VarbitID.CA_TOTAL_TASKS_COMPLETED_GAUNTLET);
    m.put("Crystalline Hunllef", VarbitID.CA_TOTAL_TASKS_COMPLETED_GAUNTLET_HM);
    m.put("Shellbane gryphon", VarbitID.CA_TOTAL_TASKS_COMPLETED_GRYPHON_BOSS);
    m.put("Hespori", VarbitID.CA_TOTAL_TASKS_COMPLETED_HESPORI);
    // The corpus's one "Giants" task covers hill, moss and fire giants together; this
    // is the closest real match, not a perfect one.
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
    // Wiki-confirmed: Zulrah was pitched to players pre-release as "the solo snake
    // boss", which is almost certainly where this internal name comes from.
    m.put("Zulrah", VarbitID.CA_TOTAL_TASKS_COMPLETED_SNAKEBOSS);
    // Scurrius, the Rat King: a giant rat boss in the Varrock Sewers, community and
    // internally both just "the rat boss".
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
