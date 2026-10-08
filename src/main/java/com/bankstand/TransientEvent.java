package com.bankstand;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A one-shot event for the outbox (a drop, a pet, a completion). Each has its own {@link UuidV7}
 * id so the server acks it independently. Field names are the wire format; do not rename them.
 */
public final class TransientEvent {

  public static final String TYPE_NOTABLE_DROP = "notable_drop";
  public static final String TYPE_PET_DROP = "pet_drop";
  public static final String TYPE_COLLECTION_LOG_UNLOCK = "collection_log_unlock";
  public static final String TYPE_COMBAT_ACHIEVEMENT_COMPLETED = "combat_achievement_completed";
  public static final String TYPE_COMBAT_ACHIEVEMENT_TIER_COMPLETED =
      "combat_achievement_tier_completed";
  public static final String TYPE_DIARY_TASK_COMPLETED = "diary_task_completed";
  public static final String TYPE_SLAYER_TASK_COMPLETED = "slayer_task_completed";

  private final String id;
  private final String type;
  private final String occurredAt;
  private final Map<String, Object> payload;

  public TransientEvent(String type, Map<String, Object> payload) {
    this(UuidV7.generate(), type, Instant.now().toString(), payload);
  }

  /** For a persisted entry, so the id and time survive a restart. */
  public TransientEvent(String id, String type, String occurredAt, Map<String, Object> payload) {
    this.id = id;
    this.type = type;
    this.occurredAt = occurredAt;
    this.payload = new LinkedHashMap<>(payload);
  }

  public String getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public String getOccurredAt() {
    return occurredAt;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }
}
