package com.bankstand.dto;

import java.util.List;

/**
 * The response from a v1 skills submit: whether the server accepted it, whether it
 * stored the update, a machine reason (persisted|duplicate|cooldown|stale|
 * regression|unclaimed|not_applied), which capability blocks it actually wrote, and
 * pacing hints. Populated by Gson.
 */
public class SubmitSnapshotResponse {
  private boolean accepted;
  private boolean stored;
  private String reason;
  private int eventsCreated;
  private String serverTime;
  private String nextSubmitAfter;
  private List<String> storedBlocks;

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

  /**
   * Whether the server actually wrote the named capability block ("skills", "quests",
   * "diaries") for this submission.
   *
   * The whole-submission {@link #isStored()} verdict cannot answer this: it is decided
   * by skills freshness, so it reads true even when the server dropped a block whose
   * rollout flag is off. Reads false when the field is absent, which is what an older
   * server returns; treating an unknown ack as "not written" makes the client re-send
   * rather than silently lose a one-shot fact like a diary tier completing.
   */
  /** Never null, so a caller does not have to decide what an absent list means. */
  public List<String> getStoredBlocks() {
    return storedBlocks == null ? java.util.Collections.emptyList() : storedBlocks;
  }

  public boolean isBlockStored(String block) {
    return storedBlocks != null && storedBlocks.contains(block);
  }
}
