package com.bankstand;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Aggregates loot per NPC into windows closed on each drain. A window closes before a kill
 * would break a server bound, so every record is valid and holds at least one kill.
 * Thread-safe.
 */
final class LootLedger {

  static final int MAX_KILLS_PER_RECORD = 1_000;
  static final int MAX_ITEMS_PER_RECORD = 64;
  static final int MAX_QUANTITY = 1_000_000;
  static final int MAX_NPC_NAME_LENGTH = 64;
  // Under the server's ten-minute bound; only reached when drains stall.
  static final Duration MAX_WINDOW = Duration.ofMinutes(5);

  private final Map<Key, Window> open = new LinkedHashMap<>();
  private volatile Supplier<String> pairing = () -> null;

  /** Where a new window reads the pairing its record is tagged with. */
  void setPairing(Supplier<String> pairing) {
    this.pairing = pairing;
  }
  private volatile CaptureGenerations generations = new CaptureGenerations();

  /** Where a window reads the capture generation it is stamped with when it opens. */
  void setCaptureGenerations(CaptureGenerations generations) {
    this.generations = generations;
  }

  /**
   * Records one kill and returns any window it closed to make room.
   *
   * @param items canonical item id to quantity for this kill alone
   */
  synchronized List<LootEntry> record(
      long accountHash, int npcId, String npcName, Map<Integer, Integer> items, Instant at) {
    List<LootEntry> closed = new ArrayList<>();
    String name = cleanName(npcName);
    Map<Integer, Integer> kill = boundedKill(items);
    if (name == null || kill.isEmpty() || npcId < 0) {
      return closed;
    }
    Key key = new Key(accountHash, npcId);
    Window window = open.get(key);
    // A kill after a new generation was acked never joins a window from before it.
    Integer generation = generations.get(accountHash);
    if (window != null
        && (!window.accepts(kill, at) || !Objects.equals(window.generation, generation))) {
      closed.add(window.close(accountHash));
      open.remove(key);
      window = null;
    }
    if (window == null) {
      window = new Window(npcId, name, at, generation, pairing.get());
      open.put(key, window);
    }
    window.add(kill, at);
    return closed;
  }

  /** Closes one NPC's window, for a notable kill that should not wait for the drain. */
  synchronized List<LootEntry> close(long accountHash, int npcId) {
    List<LootEntry> closed = new ArrayList<>();
    Window window = open.remove(new Key(accountHash, npcId));
    if (window != null) {
      closed.add(window.close(accountHash));
    }
    return closed;
  }

  /** Closes every open window: on each drain, and before a logout is sent. */
  synchronized List<LootEntry> closeAll() {
    List<LootEntry> closed = new ArrayList<>();
    for (Iterator<Map.Entry<Key, Window>> it = open.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<Key, Window> entry = it.next();
      closed.add(entry.getValue().close(entry.getKey().accountHash));
      it.remove();
    }
    return closed;
  }

  /** Forgets every open window without sending it, for when the player turns loot off. */
  synchronized void discard() {
    open.clear();
  }

  private static String cleanName(String npcName) {
    if (npcName == null) {
      return null;
    }
    String trimmed = npcName.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() > MAX_NPC_NAME_LENGTH
        ? trimmed.substring(0, MAX_NPC_NAME_LENGTH)
        : trimmed;
  }

  /** Trims one kill's loot to the per-record bounds; only a corrupt event ever hits them. */
  private static Map<Integer, Integer> boundedKill(Map<Integer, Integer> items) {
    Map<Integer, Integer> kept = new LinkedHashMap<>();
    if (items == null) {
      return kept;
    }
    for (Map.Entry<Integer, Integer> e : items.entrySet()) {
      Integer id = e.getKey();
      Integer qty = e.getValue();
      if (id == null || id < 0 || qty == null || qty < 1 || qty > MAX_QUANTITY) {
        continue;
      }
      if (kept.size() == MAX_ITEMS_PER_RECORD) {
        break;
      }
      kept.put(id, qty);
    }
    return kept;
  }

  private static final class Key {
    final long accountHash;
    final int npcId;

    Key(long accountHash, int npcId) {
      this.accountHash = accountHash;
      this.npcId = npcId;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Key)) {
        return false;
      }
      Key other = (Key) o;
      return accountHash == other.accountHash && npcId == other.npcId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(accountHash, npcId);
    }
  }

  private static final class Window {
    final int npcId;
    final String npcName;
    final Instant start;
    final String pairingId;
    // The capture generation known when the window's first kill was recorded.
    final Integer generation;
    Instant last;
    int kills;
    final Map<Integer, Long> items = new LinkedHashMap<>();

    Window(int npcId, String npcName, Instant start, Integer generation, String pairingId) {
      this.pairingId = pairingId;
      this.npcId = npcId;
      this.npcName = npcName;
      this.start = start;
      this.generation = generation;
      this.last = start;
    }

    boolean accepts(Map<Integer, Integer> kill, Instant at) {
      if (kills + 1 > MAX_KILLS_PER_RECORD) {
        return false;
      }
      if (at.isBefore(start) || Duration.between(start, at).compareTo(MAX_WINDOW) > 0) {
        return false;
      }
      int distinct = items.size();
      for (Map.Entry<Integer, Integer> e : kill.entrySet()) {
        Long held = items.get(e.getKey());
        if (held == null) {
          distinct++;
        } else if (held + e.getValue() > MAX_QUANTITY) {
          return false;
        }
      }
      return distinct <= MAX_ITEMS_PER_RECORD;
    }

    void add(Map<Integer, Integer> kill, Instant at) {
      kills++;
      if (at.isAfter(last)) {
        last = at;
      }
      for (Map.Entry<Integer, Integer> e : kill.entrySet()) {
        items.merge(e.getKey(), (long) e.getValue(), Long::sum);
      }
    }

    LootEntry close(long accountHash) {
      List<LootEvent.Item> list = new ArrayList<>(items.size());
      for (Map.Entry<Integer, Long> e : items.entrySet()) {
        list.add(new LootEvent.Item(e.getKey(), (int) (long) e.getValue()));
      }
      return new LootEntry(
          accountHash,
          new LootEvent(
              UuidV7.generate(), npcId, npcName, kills, start.toString(), last.toString(), list),
          generation,
          pairingId);
    }
  }
}
