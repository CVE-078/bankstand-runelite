package com.bankstand.dto;

import java.util.Collections;
import java.util.List;

/**
 * The response from a loot send: one ack per record, in the same shape as the events
 * route's, so a record is acknowledged, retried or dropped on its own. Populated by Gson.
 */
public class SubmitLootResponse {
  private List<EventAck> acks;

  /** Never null, so a caller does not have to decide what an absent list means. */
  public List<EventAck> getAcks() {
    return acks == null ? Collections.emptyList() : acks;
  }
}
