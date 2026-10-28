package com.bankstand.dto;

import java.util.List;

public class SubmitSnapshotResponse {
  private boolean accepted;
  private boolean stored;
  private String reason;
  private int eventsCreated;
  private String serverTime;
  private String nextSubmitAfter;
  private List<String> storedBlocks;
  // Received but kept back as lower than what the server has. Absent from older servers.
  private List<String> heldBlocks;

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

  /** Never null. */
  public List<String> getStoredBlocks() {
    return storedBlocks == null ? java.util.Collections.emptyList() : storedBlocks;
  }

  /** Never null; empty from a server that predates it. */
  public List<String> getHeldBlocks() {
    return heldBlocks == null ? java.util.Collections.emptyList() : heldBlocks;
  }

  /**
   * Whether the server wrote this capability block. Use this, not {@link #isStored()}, for a
   * per-block ack. False when absent, so an unknown ack means resend.
   */
  public boolean isBlockStored(String block) {
    return storedBlocks != null && storedBlocks.contains(block);
  }
}
