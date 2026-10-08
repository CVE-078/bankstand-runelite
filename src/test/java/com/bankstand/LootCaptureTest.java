package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemStack;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LootCaptureTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
  private static final int NOTED_COINS = 996;

  /** Canonicalises one noted id and calls one item notable; everything else is plain. */
  private static final LootCapture.Items ITEMS =
      new LootCapture.Items() {
        @Override
        public int canonical(int itemId) {
          return itemId == NOTED_COINS ? 995 : itemId;
        }

        @Override
        public boolean isNotable(int itemId, int quantity) {
          return itemId == 11286;
        }
      };

  private LootOutbox outbox() throws Exception {
    return new LootOutbox(new File(folder.newFolder("bankstand"), "loot-outbox.json"), new Gson());
  }

  @Test
  public void touchesNothingWhileOff() throws Exception {
    LootOutbox outbox = outbox();
    // Null item lookups: any read before the gate would throw.
    LootCapture capture =
        new LootCapture(() -> false, () -> 1L, null, new LootLedger(), outbox, () -> {}, () -> NOW);
    capture.handleKill(1, "Rat", Collections.singletonList(new ItemStack(526, 1)));
    assertTrue(outbox.pending().isEmpty());
  }

  @Test
  public void anOrdinaryKillWaitsForTheDrain() throws Exception {
    LootOutbox outbox = outbox();
    LootLedger ledger = new LootLedger();
    AtomicInteger fast = new AtomicInteger();
    LootCapture capture =
        new LootCapture(() -> true, () -> 1L, ITEMS, ledger, outbox, fast::incrementAndGet, () -> NOW);

    capture.handleKill(1, "Rat", Arrays.asList(new ItemStack(526, 1), new ItemStack(NOTED_COINS, 5)));

    assertTrue(outbox.pending().isEmpty());
    assertEquals(0, fast.get());
    LootEvent event = ledger.closeAll().get(0).getEvent();
    assertEquals("a noted id is sent as its base id", 995, event.getItems().get(1).getId());
  }

  @Test
  public void aNotableKillClosesItsWindowAndAsksForAnEarlySend() throws Exception {
    LootOutbox outbox = outbox();
    LootLedger ledger = new LootLedger();
    AtomicInteger fast = new AtomicInteger();
    LootCapture capture =
        new LootCapture(() -> true, () -> 1L, ITEMS, ledger, outbox, fast::incrementAndGet, () -> NOW);

    capture.handleKill(50, "Mithril dragon", Collections.singletonList(new ItemStack(526, 1)));
    capture.handleKill(50, "Mithril dragon", Collections.singletonList(new ItemStack(11286, 1)));

    assertEquals(1, fast.get());
    assertEquals(1, outbox.pending().size());
    assertEquals(
        "the notable kill rides with the rest of its window", 2, outbox.pending().get(0).getEvent().getKills());
    assertTrue(ledger.closeAll().isEmpty());
  }

  /** Loot from a player kill names that player, so neither capture may listen for it. */
  @Test
  public void noLootCaptureListensForPlayerKills() {
    for (Class<?> capture : new Class<?>[] {LootCapture.class, NotableDropCapture.class}) {
      for (Method method : capture.getDeclaredMethods()) {
        for (Class<?> parameter : method.getParameterTypes()) {
          assertTrue(
              capture.getSimpleName() + "." + method.getName() + " takes a PlayerLootReceived",
              parameter != PlayerLootReceived.class);
        }
      }
    }
  }
}
