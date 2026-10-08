package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class LootLedgerTest {

  private static final Instant T0 = Instant.parse("2026-10-08T12:00:00Z");
  private static final long ACCOUNT = 42L;
  private static final int CAVE_HORROR = 1047;

  private static Map<Integer, Integer> loot(int... idQtyPairs) {
    Map<Integer, Integer> items = new LinkedHashMap<>();
    for (int i = 0; i < idQtyPairs.length; i += 2) {
      items.put(idQtyPairs[i], idQtyPairs[i + 1]);
    }
    return items;
  }

  private static Map<Integer, Long> itemsOf(LootEvent event) {
    Map<Integer, Long> items = new LinkedHashMap<>();
    for (LootEvent.Item item : event.getItems()) {
      items.put(item.getId(), (long) item.getQty());
    }
    return items;
  }

  @Test
  public void killsOfOneNpcAggregateIntoOneRecordPerWindow() {
    LootLedger ledger = new LootLedger();
    assertTrue(ledger.record(ACCOUNT, CAVE_HORROR, "Cave horror", loot(995, 100), T0).isEmpty());
    ledger.record(ACCOUNT, CAVE_HORROR, "Cave horror", loot(995, 50, 5304, 1), T0.plusSeconds(20));
    ledger.record(ACCOUNT, CAVE_HORROR, "Cave horror", loot(5304, 2), T0.plusSeconds(40));

    List<LootEntry> closed = ledger.closeAll();

    assertEquals(1, closed.size());
    LootEvent event = closed.get(0).getEvent();
    assertEquals(ACCOUNT, closed.get(0).getAccountHash());
    assertEquals(CAVE_HORROR, event.getNpcId());
    assertEquals("Cave horror", event.getNpcName());
    assertEquals(3, event.getKills());
    assertEquals(T0.toString(), event.getWindowStart());
    assertEquals(T0.plusSeconds(40).toString(), event.getOccurredAt());
    assertEquals(150L, (long) itemsOf(event).get(995));
    assertEquals(3L, (long) itemsOf(event).get(5304));
    assertTrue("a closed window is gone", ledger.closeAll().isEmpty());
  }

  @Test
  public void eachNpcAndEachAccountHasItsOwnWindow() {
    LootLedger ledger = new LootLedger();
    ledger.record(ACCOUNT, 1, "Rat", loot(526, 1), T0);
    ledger.record(ACCOUNT, 2, "Goblin", loot(526, 1), T0);
    ledger.record(7L, 1, "Rat", loot(526, 1), T0);
    assertEquals(3, ledger.closeAll().size());
  }

  @Test
  public void anEmptyRollIsNotAKill() {
    LootLedger ledger = new LootLedger();
    ledger.record(ACCOUNT, CAVE_HORROR, "Cave horror", loot(), T0);
    assertTrue(ledger.closeAll().isEmpty());
  }

  @Test
  public void aNamelessNpcIsNotRecorded() {
    LootLedger ledger = new LootLedger();
    ledger.record(ACCOUNT, CAVE_HORROR, "  ", loot(995, 1), T0);
    ledger.record(ACCOUNT, CAVE_HORROR, null, loot(995, 1), T0);
    assertTrue(ledger.closeAll().isEmpty());
  }

  @Test
  public void aLongNameIsCutToTheBound() {
    LootLedger ledger = new LootLedger();
    StringBuilder name = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      name.append('x');
    }
    ledger.record(ACCOUNT, CAVE_HORROR, name.toString(), loot(995, 1), T0);
    assertEquals(
        LootLedger.MAX_NPC_NAME_LENGTH, ledger.closeAll().get(0).getEvent().getNpcName().length());
  }

  @Test
  public void aWindowSplitsOnAKillBoundaryBeforeItWouldPass64DistinctItems() {
    LootLedger ledger = new LootLedger();
    List<LootEntry> closed = new ArrayList<>();
    // Each kill drops two new distinct items: 32 kills reach exactly 64.
    for (int kill = 0; kill < 33; kill++) {
      closed.addAll(
          ledger.record(
              ACCOUNT, CAVE_HORROR, "Cave horror", loot(kill * 2, 1, kill * 2 + 1, 1), T0));
    }
    assertEquals("the 33rd kill closed the full window", 1, closed.size());
    assertEquals(64, closed.get(0).getEvent().getItems().size());
    assertEquals(32, closed.get(0).getEvent().getKills());
    List<LootEntry> rest = ledger.closeAll();
    assertEquals(1, rest.get(0).getEvent().getKills());
    assertEquals(2, rest.get(0).getEvent().getItems().size());
  }

  @Test
  public void aWindowSplitsBeforeAQuantityWouldPassItsBound() {
    LootLedger ledger = new LootLedger();
    ledger.record(ACCOUNT, CAVE_HORROR, "Cave horror", loot(995, 900_000), T0);
    List<LootEntry> closed =
        ledger.record(ACCOUNT, CAVE_HORROR, "Cave horror", loot(995, 200_000), T0.plusSeconds(1));
    assertEquals(1, closed.size());
    assertEquals(900_000L, (long) itemsOf(closed.get(0).getEvent()).get(995));
    assertEquals(200_000L, (long) itemsOf(ledger.closeAll().get(0).getEvent()).get(995));
  }

  @Test
  public void aWindowSplitsBeforeItWouldPassAThousandKills() {
    LootLedger ledger = new LootLedger();
    List<LootEntry> closed = new ArrayList<>();
    for (int kill = 0; kill < LootLedger.MAX_KILLS_PER_RECORD + 1; kill++) {
      closed.addAll(ledger.record(ACCOUNT, 1, "Chicken", loot(526, 1), T0));
    }
    assertEquals(1, closed.size());
    assertEquals(LootLedger.MAX_KILLS_PER_RECORD, closed.get(0).getEvent().getKills());
  }

  @Test
  public void aWindowNeverSpansMoreThanItsMaximumLength() {
    LootLedger ledger = new LootLedger();
    ledger.record(ACCOUNT, 1, "Chicken", loot(526, 1), T0);
    List<LootEntry> closed =
        ledger.record(
            ACCOUNT, 1, "Chicken", loot(526, 1), T0.plus(LootLedger.MAX_WINDOW).plusSeconds(1));
    assertEquals(1, closed.size());
  }

  @Test
  public void aSingleKillIsHeldToTheRecordBoundsOnItsOwn() {
    LootLedger ledger = new LootLedger();
    Map<Integer, Integer> huge = new LinkedHashMap<>();
    for (int id = 0; id < 70; id++) {
      huge.put(id, 1);
    }
    huge.put(9999, LootLedger.MAX_QUANTITY + 1);
    ledger.record(ACCOUNT, 1, "Chicken", huge, T0);
    LootEvent event = ledger.closeAll().get(0).getEvent();
    assertEquals(LootLedger.MAX_ITEMS_PER_RECORD, event.getItems().size());
    for (LootEvent.Item item : event.getItems()) {
      assertTrue(item.getQty() <= LootLedger.MAX_QUANTITY);
    }
  }

  @Test
  public void closingOneNpcLeavesTheOthersOpen() {
    LootLedger ledger = new LootLedger();
    ledger.record(ACCOUNT, 1, "Rat", loot(526, 1), T0);
    ledger.record(ACCOUNT, 2, "Goblin", loot(526, 1), T0);
    assertEquals(1, ledger.close(ACCOUNT, 1).size());
    assertEquals(2, ledger.closeAll().get(0).getEvent().getNpcId());
  }

  @Test
  public void discardForgetsOpenWindows() {
    LootLedger ledger = new LootLedger();
    ledger.record(ACCOUNT, 1, "Rat", loot(526, 1), T0);
    ledger.discard();
    assertTrue(ledger.closeAll().isEmpty());
  }
}
