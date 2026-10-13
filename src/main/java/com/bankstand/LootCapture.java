package com.bankstand;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.NPC;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;

/**
 * Captures loot from {@link NpcLootReceived} only: never {@code PlayerLootReceived}, which
 * names another player. The gate is checked before any read.
 */
public class LootCapture {

  /** The item lookups this needs, behind a seam so the logic is testable without a client. */
  interface Items {
    /** The base id for a noted or placeholder variant, so the server joins on one id. */
    int canonical(int itemId);

    /** Whether this stack would count as a notable drop under the player's own rule. */
    boolean isNotable(int itemId, int quantity);
  }

  private final BooleanSupplier enabled;
  private final LongSupplier accountHash;
  private final Items items;
  private final LootLedger ledger;
  private final LootOutbox outbox;
  private final Runnable onNotable;
  private final Supplier<Instant> clock;

  LootCapture(
      BooleanSupplier enabled,
      LongSupplier accountHash,
      Items items,
      LootLedger ledger,
      LootOutbox outbox,
      Runnable onNotable,
      Supplier<Instant> clock) {
    this.enabled = enabled;
    this.accountHash = accountHash;
    this.items = items;
    this.ledger = ledger;
    this.outbox = outbox;
    this.onNotable = onNotable;
    this.clock = clock;
  }

  @Subscribe
  public void onNpcLootReceived(NpcLootReceived event) {
    if (!enabled.getAsBoolean()) {
      return;
    }
    NPC npc = event.getNpc();
    if (npc == null) {
      return;
    }
    handleKill(npc.getId(), npc.getName(), event.getItems());
  }

  /** Package-private so a test can drive one kill without a live client. */
  void handleKill(int npcId, String npcName, Collection<ItemStack> stacks) {
    if (!enabled.getAsBoolean()) {
      return;
    }
    Map<Integer, Integer> kill = new LinkedHashMap<>();
    boolean notable = false;
    for (ItemStack stack : stacks) {
      if (stack.getQuantity() < 1) {
        continue;
      }
      notable |= items.isNotable(stack.getId(), stack.getQuantity());
      kill.merge(items.canonical(stack.getId()), stack.getQuantity(), LootCapture::addCapped);
    }
    long hash = accountHash.getAsLong();
    List<LootEntry> closed = ledger.record(hash, npcId, npcName, kill, clock.get());
    if (notable) {
      closed.addAll(ledger.close(hash, npcId));
    }
    outbox.addAll(closed);
    if (notable) {
      onNotable.run();
    }
  }

  private static int addCapped(int a, int b) {
    long sum = (long) a + b;
    return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
  }
}
