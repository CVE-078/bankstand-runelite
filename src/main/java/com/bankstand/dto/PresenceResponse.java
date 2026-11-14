package com.bankstand.dto;

/** Whether the server applied a presence signal. Either answer is final. */
public class PresenceResponse {
  private boolean applied;
  private String reason;
  private Integer captureGeneration;

  /** Null from an older server, or when the request never reached the write. */
  public Integer getCaptureGeneration() {
    return captureGeneration;
  }

  public boolean isApplied() {
    return applied;
  }

  public String getReason() {
    return reason;
  }
}
