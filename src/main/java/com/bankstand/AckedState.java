package com.bankstand;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-character state that survives a restart: what the server has accepted, and what the client
 * has observed. Digests are cheap to lose (one redundant submit); collection log observations are
 * not, since the game reveals the log only while the player looks at it.
 *
 * <p>{@code collectionLogAcked} and {@code collectionLogItems} must persist together: the acked
 * count is only valid against the set it counted.
 *
 * <p>Gson populates this by field, so the field names are the on-disk format.
 */
public class AckedState {

  private String skills;
  private String quests;
  private String diaries;
  private String combatAchievements;

  // The value itself, not a digest: one short word, readable in a bug report.
  private String accountType;
  private Set<Integer> collectionLogItems;
  private int collectionLogAcked;

  /**
   * Epoch millis of each capability's last fresh server acknowledgement, keyed by capability
   * name. Never stamped on a resend of already-acked data.
   */
  private Map<String, Long> lastSyncedAt;

  /**
   * Last raw value of each diary varplayer, keyed by id. Raw, not a digest, because per-task
   * detection XORs against the prior bits.
   */
  private Map<Integer, Integer> diaryTaskBits;

  /** The state of a character nothing is known about yet: everything resends once. */
  public static AckedState empty() {
    AckedState state = new AckedState();
    state.collectionLogItems = new LinkedHashSet<>();
    state.collectionLogAcked = -1;
    state.lastSyncedAt = new LinkedHashMap<>();
    state.diaryTaskBits = new LinkedHashMap<>();
    return state;
  }

  public String getSkills() {
    return skills;
  }

  public void setSkills(String skills) {
    this.skills = skills;
  }

  public String getQuests() {
    return quests;
  }

  public void setQuests(String quests) {
    this.quests = quests;
  }

  public String getDiaries() {
    return diaries;
  }

  public void setDiaries(String diaries) {
    this.diaries = diaries;
  }

  public String getCombatAchievements() {
    return combatAchievements;
  }

  public void setCombatAchievements(String combatAchievements) {
    this.combatAchievements = combatAchievements;
  }

  public String getAccountType() {
    return accountType;
  }

  public void setAccountType(String accountType) {
    this.accountType = accountType;
  }

  // Never null, even if the stored file omits the field.
  public Set<Integer> getCollectionLogItems() {
    if (collectionLogItems == null) {
      collectionLogItems = new LinkedHashSet<>();
    }
    return collectionLogItems;
  }

  public void setCollectionLogItems(Set<Integer> items) {
    this.collectionLogItems = new LinkedHashSet<>(items);
  }

  /** The observed count the server last acknowledged, or -1 for none. */
  public int getCollectionLogAcked() {
    return collectionLogAcked;
  }

  public void setCollectionLogAcked(int acked) {
    this.collectionLogAcked = acked;
  }

  // Never null, even if the stored file omits the field.
  public Map<String, Long> getLastSyncedAt() {
    if (lastSyncedAt == null) {
      lastSyncedAt = new LinkedHashMap<>();
    }
    return lastSyncedAt;
  }

  public void setLastSyncedAt(Map<String, Long> lastSyncedAt) {
    this.lastSyncedAt = new LinkedHashMap<>(lastSyncedAt);
  }

  // Never null, even if the stored file omits the field.
  public Map<Integer, Integer> getDiaryTaskBits() {
    if (diaryTaskBits == null) {
      diaryTaskBits = new LinkedHashMap<>();
    }
    return diaryTaskBits;
  }

  public void setDiaryTaskBits(Map<Integer, Integer> bits) {
    this.diaryTaskBits = new LinkedHashMap<>(bits);
  }
}
