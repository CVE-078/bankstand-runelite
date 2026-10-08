package com.bankstand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One loot record: what one NPC dropped across one window. Field names are the wire
 * contract. Item ids and quantities only; the server prices them.
 */
public final class LootEvent {

  private final String id;
  private final int npcId;
  private final String npcName;
  private final int kills;
  private final String windowStart;
  private final String occurredAt;
  private final List<Item> items;

  LootEvent(
      String id,
      int npcId,
      String npcName,
      int kills,
      String windowStart,
      String occurredAt,
      List<Item> items) {
    this.id = id;
    this.npcId = npcId;
    this.npcName = npcName;
    this.kills = kills;
    this.windowStart = windowStart;
    this.occurredAt = occurredAt;
    this.items = Collections.unmodifiableList(new ArrayList<>(items));
  }

  public String getId() {
    return id;
  }

  public int getNpcId() {
    return npcId;
  }

  public String getNpcName() {
    return npcName;
  }

  public int getKills() {
    return kills;
  }

  public String getWindowStart() {
    return windowStart;
  }

  public String getOccurredAt() {
    return occurredAt;
  }

  public List<Item> getItems() {
    return items;
  }

  /** One canonical item id and the quantity received of it in the window. */
  public static final class Item {
    private final int id;
    private final int qty;

    Item(int id, int qty) {
      this.id = id;
      this.qty = qty;
    }

    public int getId() {
      return id;
    }

    public int getQty() {
      return qty;
    }
  }
}
