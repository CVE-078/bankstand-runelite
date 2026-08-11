package com.bankstand.dto;

import java.util.Collections;
import java.util.List;

/**
 * The response from a v1 events batch submit: whether the account hash routed to
 * a claimed character at all, and each event's own ack. Populated by Gson.
 */
public class SubmitEventsResponse {
  private boolean routed;
  private List<EventAck> acks;
  private String serverTime;

  public boolean isRouted() {
    return routed;
  }

  /** Never null, so a caller does not have to decide what an absent list means. */
  public List<EventAck> getAcks() {
    return acks == null ? Collections.emptyList() : acks;
  }

  public String getServerTime() {
    return serverTime;
  }
}
