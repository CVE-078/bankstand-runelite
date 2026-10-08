package com.bankstand;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

/**
 * Captures notable drops: an untradeable item on a curated allowlist, or a tradeable drop whose
 * total GE value clears the configured threshold. Pets are handled by {@link PetDropCapture}.
 *
 * <p>NPC kills only: {@code PlayerLootReceived} would send another player's name, and Loot
 * Tracker's {@code LootReceived} double-fires the same kill. GE price is client-side and may
 * differ from the server's.
 */
public class NotableDropCapture extends BaseCapture {

  private final ItemManager itemManager;
  private final LongSupplier thresholdValue;
  private final Set<String> untradeableAllowlist;

  public NotableDropCapture(
      EventOutbox outbox,
      BooleanSupplier enabled,
      LongSupplier accountHash,
      ItemManager itemManager,
      LongSupplier thresholdValue,
      Set<String> untradeableAllowlist) {
    super(outbox, enabled, accountHash);
    this.itemManager = itemManager;
    this.thresholdValue = thresholdValue;
    this.untradeableAllowlist = untradeableAllowlist;
  }

  public NotableDropCapture(
      EventOutbox outbox,
      BooleanSupplier enabled,
      LongSupplier accountHash,
      ItemManager itemManager,
      LongSupplier thresholdValue,
      Set<String> untradeableAllowlist,
      Consumer<TransientEvent> onEmit) {
    super(outbox, enabled, accountHash, onEmit);
    this.itemManager = itemManager;
    this.thresholdValue = thresholdValue;
    this.untradeableAllowlist = untradeableAllowlist;
  }

  @Subscribe
  public void onNpcLootReceived(NpcLootReceived event) {
    handleLoot(event.getNpc().getName(), event.getItems());
  }

  // The enabled gate must run before any itemManager read.
  void handleLoot(String source, Collection<ItemStack> items) {
    if (!isEnabled()) return;
    for (ItemStack stack : items) {
      ItemComposition comp = itemManager.getItemComposition(stack.getId());
      if (comp == null) continue;
      Long value =
          comp.isGeTradeable() ? (long) itemManager.getItemPrice(stack.getId()) : null;
      if (!qualifies(
          comp.getName(), comp.isGeTradeable(), value, stack.getQuantity(),
          thresholdValue.getAsLong(), untradeableAllowlist)) {
        continue;
      }
      emit(
          TransientEvent.TYPE_NOTABLE_DROP,
          payload(
              comp.getName(),
              stack.getId(),
              stack.getQuantity(),
              value == null ? null : value * stack.getQuantity(),
              source == null || source.isEmpty() ? "Unknown" : source));
    }
  }

  /**
   * A tradeable item qualifies on unit price times quantity; an untradeable one (null
   * {@code unitValue}) only by name on the allowlist.
   */
  static boolean qualifies(
      String itemName,
      boolean tradeable,
      Long unitValue,
      int quantity,
      long thresholdValue,
      Set<String> untradeableAllowlist) {
    if (!tradeable || unitValue == null) {
      return untradeableAllowlist.contains(itemName);
    }
    return unitValue * quantity >= thresholdValue;
  }

  static Map<String, Object> payload(
      String itemName, int itemId, int quantity, Long totalValue, String source) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("itemName", itemName);
    payload.put("itemId", itemId);
    payload.put("quantity", quantity);
    payload.put("value", totalValue);
    payload.put("source", source);
    return payload;
  }
}
