package com.bankstand.dto;

import java.util.Collections;
import java.util.List;

/** One ack per loot record, shaped like the events route's. */
public class SubmitLootResponse {
  private List<EventAck> acks;
  private Integer captureGeneration;

  public List<EventAck> getAcks() {
    return acks == null ? Collections.emptyList() : acks;
  }

  /** Null from an older server, or when the request never reached the write. */
  public Integer getCaptureGeneration() {
    return captureGeneration;
  }
}
