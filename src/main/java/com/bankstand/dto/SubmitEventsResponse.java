package com.bankstand.dto;

import java.util.Collections;
import java.util.List;

public class SubmitEventsResponse {
  private boolean routed;
  private List<EventAck> acks;
  private String serverTime;
  private Integer captureGeneration;

  /** Null from an older server, or when no event reached the write. */
  public Integer getCaptureGeneration() {
    return captureGeneration;
  }

  public boolean isRouted() {
    return routed;
  }

  /** Never null. */
  public List<EventAck> getAcks() {
    return acks == null ? Collections.emptyList() : acks;
  }

  public String getServerTime() {
    return serverTime;
  }
}
