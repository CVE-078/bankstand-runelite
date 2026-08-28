package com.bankstand;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What one character has already had accepted by the server, and what its client has
 * observed, in the form that survives a restart.
 *
 * <p>Two kinds of thing live here and the difference matters. The three digests are
 * *verdicts*: skills, quests and diaries are re-read live on every capture, so losing a
 * digest costs one redundant submission and nothing else. The collection log entries
 * are *observations*: the game reveals the log only while the player is looking at it,
 * so losing them cannot be recovered by asking the client again.
 *
 * <p><b>{@code collectionLogAcked} and {@code collectionLogItems} must persist together
 * or not at all.</b> The acked figure is a count over the observed set, valid only while
 * that set grows monotonically. Persist the count while the set resets and a later
 * partial browse reads as a change and churns every session; persist the set while
 * deriving the count from it and a log whose submission failed is silently treated as
 * delivered and never sent again. A baseline and the state it is a baseline of need the
 * same lifetime.
 *
 * <p>Gson populates this by field, so the field names are the on-disk format.
 */
public class AckedState {

  private String skills;
  private String quests;
  private String diaries;
  private String combatAchievements;

  /**
   * The last account type the server acknowledged.
   *
   * <p>The value itself, not a digest, unlike every collection-shaped field here: it is
   * one short word, so hashing it would cost the ability to read this file in a bug
   * report and buy nothing.
   */
  private String accountType;
  private Set<Integer> collectionLogItems;
  private int collectionLogAcked;

  /**
   * When each capability last had a fresh acknowledgement from the server, epoch millis,
   * keyed by the same capability name {@code manifest.allows(...)} and {@code
   * SubmitSnapshotResponse#isBlockStored(...)} already use ("skills", "collectionLog", and
   * so on).
   *
   * <p>A digest says whether a capability's last-known value differs from what the server
   * has; it says nothing about when that last happened, which is what the panel's
   * per-capability list needs. Stamped only on a cycle where the server actually
   * acknowledged fresh data for that capability, never on a resend of something already
   * acknowledged, so it answers "when did this last genuinely sync" rather than "when did
   * the plugin last try".
   */
  private Map<String, Long> lastSyncedAt;

  /** The state of a character nothing is known about yet: everything resends once. */
  public static AckedState empty() {
    AckedState state = new AckedState();
    state.collectionLogItems = new LinkedHashSet<>();
    state.collectionLogAcked = -1;
    state.lastSyncedAt = new LinkedHashMap<>();
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

  /**
   * Never null, even when the stored document omitted the field or set it null, so a
   * hand-edited or older file cannot hand a caller a null set to iterate.
   */
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

  /**
   * Never null, even when the stored document omitted the field or set it null, so a
   * hand-edited or older file cannot hand a caller a null map to iterate.
   */
  public Map<String, Long> getLastSyncedAt() {
    if (lastSyncedAt == null) {
      lastSyncedAt = new LinkedHashMap<>();
    }
    return lastSyncedAt;
  }

  /** Copied, so a caller mutating the map it passed in cannot reach back into this state. */
  public void setLastSyncedAt(Map<String, Long> lastSyncedAt) {
    this.lastSyncedAt = new LinkedHashMap<>(lastSyncedAt);
  }
}
