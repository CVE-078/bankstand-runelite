package com.bankstand.dto;

/** {@code linkedRsn} is null when nothing matched; {@code outcome} is null from an older server. */
public class SubmitResponse {
  private boolean verified;
  private String linkedRsn;
  private String outcome;

  public boolean isVerified() {
    return verified;
  }

  public String getLinkedRsn() {
    return linkedRsn;
  }

  public String getOutcome() {
    return outcome;
  }
}
