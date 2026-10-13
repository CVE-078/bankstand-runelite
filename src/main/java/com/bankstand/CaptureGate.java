package com.bankstand;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.WorldType;

/**
 * The world and login-settle check every capture shares. Nothing is read or sent until it
 * passes.
 */
final class CaptureGate {

  // Not main-game progress, and may share the main account hash. Fresh Start is its own account.
  static final Set<WorldType> SKIPPED_WORLDS =
      Collections.unmodifiableSet(
          EnumSet.of(
              WorldType.SEASONAL,
              WorldType.DEADMAN,
              WorldType.TOURNAMENT_WORLD,
              WorldType.PVP_ARENA,
              WorldType.BETA_WORLD,
              WorldType.NOSAVE_MODE,
              WorldType.QUEST_SPEEDRUNNING));

  /** Ticks after the welcome screen closes before reads count. Varps replay just after login. */
  static final int SETTLE_TICKS = 3;

  // Ticked on the client thread, read by the scheduled capture.
  private volatile int settledTicks;

  static boolean allowsWorld(Set<WorldType> world) {
    return world != null && Collections.disjoint(world, SKIPPED_WORLDS);
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

  boolean allows(Set<WorldType> world) {
    return isSettled() && allowsWorld(world);
  }
}
