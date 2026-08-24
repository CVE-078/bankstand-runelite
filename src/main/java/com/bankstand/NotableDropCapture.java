package com.bankstand;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

/**
 * Captures notable drops: a unique or untradeable item on a curated
 * allowlist, or any tradeable drop whose total GE value clears a configurable
 * threshold.
 *
 * <p><b>Scope, deliberately narrow</b>, matching the issue: tradeable-value
 * threshold and a curated untradeable allowlist. Collection-log-unlock framing
 * and pet drops are NOT this class; a pet is a separate detector because
 * the first chat line never names it (prime-then-resolve), and a collection-log
 * "first observed" trigger would need to cross-reference the slot tracking
 * that already ships, which is a bigger, separate design question.
 *
 * <p><b>Only these two events, deliberately.</b> The built-in Loot Tracker
 * plugin's own {@code LootReceived} looked like a third source worth also
 * subscribing to, but it is not: Loot Tracker constructs and posts it from the
 * exact same NPC-kill and player-kill moments the client already posts as
 * {@link NpcLootReceived}/{@link PlayerLootReceived}, so subscribing to all
 * three double-fired {@link #handleLoot} for every qualifying drop, one call
 * per event. It covers no loot source the other two miss (chests and some
 * reward interfaces are not covered by any of them), so it added duplication
 * with no coverage in return. GE price is client-side and can differ from the
 * server's view at the moment of the drop.
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

  @Subscribe
  public void onNpcLootReceived(NpcLootReceived event) {
    handleLoot(event.getNpc().getName(), event.getItems());
  }

  @Subscribe
  public void onPlayerLootReceived(PlayerLootReceived event) {
    handleLoot(event.getPlayer().getName(), event.getItems());
  }

  /** Package-private, not private: {@code NotableDropCaptureTest} calls this directly
   *  with a poisoned {@code itemManager} to prove the gate below runs before any read. */
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
   * Pure and tested without a live client. A tradeable item qualifies on total
   * value (unit price times quantity); an untradeable one has no GE price at
   * all, so it qualifies only by name on the curated allowlist. {@code value}
   * is the UNIT price here (multiplied by quantity by the caller for the final
   * payload), null exactly when the item is untradeable.
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
