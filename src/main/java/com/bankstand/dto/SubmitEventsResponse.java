package com.bankstand.dto;

import java.util.Collections;
import java.util.List;

public class SubmitEventsResponse {
  private boolean routed;
  private List<EventAck> acks;
  private String serverTime;

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
