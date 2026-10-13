package com.bankstand.dto;

import java.util.Collections;
import java.util.List;

/** One ack per loot record, shaped like the events route's. */
public class SubmitLootResponse {
  private List<EventAck> acks;

  public List<EventAck> getAcks() {
    return acks == null ? Collections.emptyList() : acks;
  }
}
