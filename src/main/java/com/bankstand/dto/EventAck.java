package com.bankstand.dto;

public class EventAck {
  private String id;
  private String outcome;
  private String reason;

  public String getId() {
    return id;
  }

  public String getOutcome() {
    return outcome;
  }

  public boolean isStored() {
    return "stored".equals(outcome) || "duplicate".equals(outcome);
  }

  public String getReason() {
    return reason;
  }
}
