package com.bankstand;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.WorldType;

/**
 * The world and login-settle check every capture shares. Nothing is read or sent until it
 * passes.
 */
final class CaptureGate {

  // Main-save worlds only. Seasonal, Deadman, beta, LMS, Fresh Start and any flag added
  // later may share the main account hash or a temporary save, so they fail closed.
  static final Set<WorldType> MAIN_WORLDS =
      Collections.unmodifiableSet(
          EnumSet.of(
              WorldType.MEMBERS,
              WorldType.PVP,
              WorldType.BOUNTY,
              WorldType.SKILL_TOTAL,
              WorldType.HIGH_RISK,
              WorldType.LEGACY_ONLY,
              WorldType.EOC_ONLY));

  // Last Man Standing arenas: a temporary loadout on a normal world.
  private static final Set<Integer> LAST_MAN_STANDING_REGIONS =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920, 14174, 14175,
                  14176, 14430, 14431, 14432)));

  /** Ticks after the welcome screen closes before reads count. Varps replay just after login. */
  static final int SETTLE_TICKS = 3;

  // Ticked on the client thread, read by the scheduled capture.
  private volatile int settledTicks;

  static boolean allowsWorld(Set<WorldType> world) {
    return world != null && MAIN_WORLDS.containsAll(world);
  }

  static boolean inLastManStanding(int[] mapRegions) {
    if (mapRegions != null) {
      for (int region : mapRegions) {
        if (LAST_MAN_STANDING_REGIONS.contains(region)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Call on login, hop or logout: the next reads wait for a fresh settle. */
  void reset() {
    settledTicks = 0;
  }

  void onTick(boolean welcomeScreenOpen) {
    if (welcomeScreenOpen) {
      settledTicks = 0;
    } else if (settledTicks < SETTLE_TICKS) {
      settledTicks++;
    }
  }

  boolean isSettled() {
    return settledTicks >= SETTLE_TICKS;
  }

  boolean allows(Set<WorldType> world, int[] mapRegions) {
    return isSettled() && allowsWorld(world) && !inLastManStanding(mapRegions);
  }
}
