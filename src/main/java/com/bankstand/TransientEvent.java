package com.bankstand;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One discrete, one-shot fact for the outbox (#656): a notable drop, a pet drop,
 * and whatever else joins later. Unlike the skills/quests/diaries snapshot, this
 * is never overwritten by a newer capture of the same thing; each occurrence is
 * its own event with its own {@link UuidV7} id, which is what lets the server ack
 * (and the outbox drop) entries independently rather than as one submission.
 *
 * <p>Immutable and Gson-serializable as-is: the field names here are exactly the
 * wire shape {@code lib/plugin/events-envelope.ts} validates, so no separate DTO
 * mapping step exists to drift from it.
 */
public final class TransientEvent {

  public static final String TYPE_NOTABLE_DROP = "notable_drop";
  public static final String TYPE_PET_DROP = "pet_drop";
  public static final String TYPE_COLLECTION_LOG_UNLOCK = "collection_log_unlock";
  public static final String TYPE_COMBAT_ACHIEVEMENT_COMPLETED = "combat_achievement_completed";

  private final String id;
  private final String type;
  private final String occurredAt;
  private final Map<String, Object> payload;

  public TransientEvent(String type, Map<String, Object> payload) {
    this(UuidV7.generate(), type, Instant.now().toString(), payload);
  }

  /** For deserializing a persisted entry, where the id and time must survive a restart. */
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
