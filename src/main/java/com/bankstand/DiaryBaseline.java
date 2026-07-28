package com.bankstand;

import java.util.HashMap;
import java.util.Map;

/**
 * The last diary-state vector the server acknowledged, used as a change gate so the
 * capture loop only submits when something moved. The plugin resets this on an
 * account switch and advances it only when a submit is acknowledged, so a dropped
 * submit is retried on the next capture (the baseline never moved).
 */
public class DiaryBaseline {
  private final Map<String, String> acked = new HashMap<>();

  /** True when any diary tier in {@code current} is new or differs from the baseline. */
  public boolean changedSince(Map<String, String> current) {
    for (Map.Entry<String, String> e : current.entrySet()) {
      String prev = acked.get(e.getKey());
      if (prev == null || !prev.equals(e.getValue())) {
        return true;
      }
    }
    return false;
  }

  public void advance(Map<String, String> ackedNow) {
    acked.clear();
    acked.putAll(ackedNow);
  }

  public void reset() {
    acked.clear();
  }
}
