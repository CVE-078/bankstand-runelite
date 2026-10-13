package com.bankstand;

/**
 * Every capability the plugin syncs, in panel and status order. The key is the wire and
 * last-synced key, so it must never change for an existing entry.
 */
enum Capability {
  SKILLS("skills", "Skills", "skills", null, "Skills synced"),
  QUESTS("quests", "Quests", "quests", TransientEvent.TYPE_QUEST_COMPLETED, "Quests synced"),
  DIARIES(
      "diaries", "Diaries", "diaries", TransientEvent.TYPE_DIARY_TASK_COMPLETED, "Diaries synced"),
  // No synced line: its chat capture already logs a more specific one.
  COLLECTION_LOG(
      "collectionLog",
      "Collection log",
      "collection log",
      TransientEvent.TYPE_COLLECTION_LOG_UNLOCK,
      null),
  COMBAT_ACHIEVEMENTS(
      "combatAchievements",
      "Combat achievements",
      "combat achievements",
      TransientEvent.TYPE_COMBAT_ACHIEVEMENT_COMPLETED,
      null),
  ACCOUNT_TYPE("accountType", "Account type", "account type", null, "Account type synced"),
  NOTABLE_DROPS(
      "notableDrops", "Notable drops", "notable drops", TransientEvent.TYPE_NOTABLE_DROP, null),
  PET_DROPS("petDrops", "Pet drops", "pet drops", TransientEvent.TYPE_PET_DROP, null),
  SESSIONS("sessions", "Play sessions", "play sessions", null, null),
  LOOT("loot", "Full loot", "full loot", null, null),
  SLAYER("slayer", "Slayer task", "slayer task", TransientEvent.TYPE_SLAYER_TASK_COMPLETED, null);

  /** Wire and last-synced key. */
  final String key;
  /** Panel row label. */
  final String label;
  /** Name in the status and sync chat lines. */
  final String statusName;
  /** The chat-triggered event that stamps this capability's last-synced time, if any. */
  final String eventType;
  /** Recent-activity line when a snapshot ack stores this block, or null for none. */
  final String syncedLine;

  Capability(String key, String label, String statusName, String eventType, String syncedLine) {
    this.key = key;
    this.label = label;
    this.statusName = statusName;
    this.eventType = eventType;
    this.syncedLine = syncedLine;
  }

  /** The capability an emitted event stamps, or null when it stamps none. */
  static Capability forEventType(String type) {
    for (Capability capability : values()) {
      if (capability.eventType != null && capability.eventType.equals(type)) {
        return capability;
      }
    }
    return null;
  }
}
