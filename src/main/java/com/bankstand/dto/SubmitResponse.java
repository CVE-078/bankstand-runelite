package com.bankstand.dto;

/** {@code linkedRsn} is null when nothing matched; {@code outcome} is null from an older server. */
public class SubmitResponse {
  private boolean verified;
  private String linkedRsn;
  private String outcome;
  private Integer captureGeneration;

  public boolean isVerified() {
    return verified;
  }

  public String getLinkedRsn() {
    return linkedRsn;
  }

  public String getOutcome() {
    return outcome;
  }

  /** The linked character's capture generation, or null when unbound or from an older server. */
  public Integer getCaptureGeneration() {
    return captureGeneration;
  }
}
