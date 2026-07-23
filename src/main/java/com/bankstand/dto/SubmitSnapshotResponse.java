package com.bankstand.dto;

/**
 * The response from a v1 skills submit: whether the server accepted it, whether it
 * stored the update, a machine reason (persisted|duplicate|cooldown|stale|
 * regression|unclaimed|not_applied), and pacing hints. Populated by Gson.
 */
public class SubmitSnapshotResponse {
  private boolean accepted;
  private boolean stored;
  private String reason;
  private int eventsCreated;
  private String serverTime;
  private String nextSubmitAfter;

  public boolean isAccepted() {
    return accepted;
  }

  public boolean isStored() {
    return stored;
  }

  public String getReason() {
    return reason;
  }

  public int getEventsCreated() {
    return eventsCreated;
  }

  public String getServerTime() {
    return serverTime;
  }

  public String getNextSubmitAfter() {
    return nextSubmitAfter;
  }
}
