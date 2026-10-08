package com.bankstand.dto;

/** Whether the server applied a presence signal. Either answer is final. */
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
