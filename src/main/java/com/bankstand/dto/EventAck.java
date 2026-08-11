package com.bankstand.dto;

/**
 * One event's outcome from a batch submit: the client's own id echoed back,
 * whether it stored, and (only on rejected) a machine reason. Populated by Gson.
 */
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
