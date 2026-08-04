package com.bankstand;

import java.util.Objects;

/**
 * Decides whether a submit outcome is worth telling the player about.
 *
 * <p>With no side panel to hold a status, outcomes surface as chat lines, and the
 * capture cycle runs every 60 seconds. An unreachable server would print the same
 * line forever, which trains the player to ignore it. So a failure is announced when
 * it is NEW (the first one, or a different reason from the one currently outstanding)
 * and a recovery is announced only when something was actually outstanding.
 *
 * <p>A failure that returns after a recovery is announced again, even with the same
 * reason: the player watched it clear, so its coming back is news rather than noise.
 *
 * <p>Not thread-safe, and deliberately so: it is only ever touched from the client
 * thread, alongside the rest of the session state.
 */
final class NoticeGate {

  /** The reason currently outstanding, or null when the last outcome was a success. */
  private String outstanding;

  private boolean healthy = true;

  /**
   * Records a failed submit. Returns true when it should be announced.
   *
   * @param reason the failure message, which may be null
   */
  boolean onFailure(String reason) {
    boolean announce = healthy || !Objects.equals(outstanding, reason);
    outstanding = reason;
    healthy = false;
    return announce;
  }

  /** Records a successful submit. Returns true when a recovery should be announced. */
  boolean onSuccess() {
    boolean announce = !healthy;
    outstanding = null;
    healthy = true;
    return announce;
  }
}
