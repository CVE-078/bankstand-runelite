package com.bankstand;

import java.util.Objects;

/**
 * Decides whether a submit outcome is worth a chat line, so a persistent failure is announced
 * once rather than every capture. A failure is announced when it is new or its reason changed; a
 * recovery only when something was outstanding. Client thread only.
 */
final class NoticeGate {

  /** Null when the last outcome was a success. */
  private String outstanding;

  private boolean healthy = true;

  /** Returns true when the failure should be announced. {@code reason} may be null. */
  boolean onFailure(String reason) {
    boolean announce = healthy || !Objects.equals(outstanding, reason);
    outstanding = reason;
    healthy = false;
    return announce;
  }

  boolean onSuccess() {
    boolean announce = !healthy;
    outstanding = null;
    healthy = true;
    return announce;
  }
}
