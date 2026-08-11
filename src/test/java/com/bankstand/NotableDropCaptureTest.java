package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemStack;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The pure detection logic behind notable drops (#659), tested without a live client. */
public class NotableDropCaptureTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final Set<String> NO_ALLOWLIST = Collections.emptySet();

  @Test
  public void aTradeableDropQualifiesWhenTotalValueClearsTheThreshold() {
    assertTrue(NotableDropCapture.qualifies("Rune platebody", true, 5000L, 200, 1_000_000L, NO_ALLOWLIST));
  }

  @Test
  public void aTradeableDropBelowThresholdDoesNotQualify() {
    assertFalse(NotableDropCapture.qualifies("Rune platebody", true, 5000L, 1, 1_000_000L, NO_ALLOWLIST));
  }

  @Test
  public void exactlyAtTheThresholdQualifies() {
    assertTrue(NotableDropCapture.qualifies("Item", true, 1000L, 1000, 1_000_000L, NO_ALLOWLIST));
  }

  @Test
  public void anUntradeableItemNeverQualifiesOnValueRegardlessOfPrice() {
    // unitValue null models "no GE price", which is what an untradeable item reports.
    assertFalse(NotableDropCapture.qualifies("Random junk", false, null, 1, 1L, NO_ALLOWLIST));
  }

  @Test
  public void anUntradeableItemOnTheAllowlistQualifies() {
    Set<String> allowlist = new HashSet<>(Collections.singletonList("Ancient icon"));
    assertTrue(NotableDropCapture.qualifies("Ancient icon", false, null, 1, 1_000_000L, allowlist));
  }

  @Test
  public void anUntradeableItemNotOnTheAllowlistIsIgnored() {
    Set<String> allowlist = new HashSet<>(Collections.singletonList("Ancient icon"));
    assertFalse(NotableDropCapture.qualifies("Some other untradeable", false, null, 1, 0L, allowlist));
  }

  @Test
  public void payloadCarriesNullValueForAnUntradeableDrop() {
    Map<String, Object> payload = NotableDropCapture.payload("Ancient icon", 12791, 1, null, "Nex");
    assertEquals("Ancient icon", payload.get("itemName"));
    assertEquals(12791, payload.get("itemId"));
    assertEquals(1, payload.get("quantity"));
    assertEquals(null, payload.get("value"));
    assertEquals("Nex", payload.get("source"));
  }

  @Test
  public void payloadCarriesTheTotalValueForATradeableDrop() {
    Map<String, Object> payload = NotableDropCapture.payload("Dragon warhammer", 13576, 1, 30_000_000L, "Wintertodt");
    assertEquals(30_000_000L, payload.get("value"));
  }

  /**
   * The toggle must gate the read itself, not only {@code emit}. {@code itemManager}
   * is deliberately {@code null}: the current code's first touch on a qualifying
   * loot event is {@code itemManager.getItemComposition(...)}, so if the gate ran
   * after that (as it did before this fix) this throws a NullPointerException
   * instead of returning quietly.
   */
  @Test
  public void handleLootTouchesNothingWhenDisabled() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    NotableDropCapture capture =
        new NotableDropCapture(outbox, () -> false, () -> 1L, null, () -> 0L, NO_ALLOWLIST);

    capture.handleLoot("Nex", Collections.singletonList(new ItemStack(12791, 1)));

    assertTrue(outbox.pending().isEmpty());
  }
}
