package com.bankstand.dto;

/**
 * The response from a presence send: whether the server applied it. A {@code false} is
 * still final for that signal (the capability is off, the character is unclaimed, or a
 * quota was reached), so the client drops it rather than retrying. Populated by Gson.
 */
public class PresenceResponse {
  private boolean applied;
  private String reason;

  public boolean isApplied() {
    return applied;
  }

  public String getReason() {
    return reason;
  }
}
