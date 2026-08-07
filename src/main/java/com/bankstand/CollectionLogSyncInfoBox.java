package com.bankstand;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * The live state of a guided collection log read.
 *
 * <p>A chat line cannot carry a running count, and printing one per entry would flood
 * the box with roughly seventeen hundred lines. The read is also the one moment where
 * the player is waiting on the plugin and has no other way to tell whether anything is
 * happening, so it is worth a transient surface of its own. It is added when a sync is
 * armed and removed the moment the read ends, so nothing lingers.
 *
 * <p>Reads its text straight off the sync rather than holding a copy, so the count
 * cannot drift from the state machine that owns it.
 */
class CollectionLogSyncInfoBox extends InfoBox {

  private final CollectionLogSync sync;

  CollectionLogSyncInfoBox(BufferedImage image, Plugin plugin, CollectionLogSync sync) {
    super(image, plugin);
    this.sync = sync;
    setPriority(net.runelite.client.ui.overlay.infobox.InfoBoxPriority.LOW);
  }

  @Override
  public String getText() {
    // A dash while waiting, not a zero: nothing has been read yet, which is a different
    // fact from having read none, and the same rule the rest of Bankstand renders by.
    return sync.isAwaitingSearch() ? "-" : Integer.toString(sync.observedCount());
  }

  @Override
  public Color getTextColor() {
    return Color.WHITE;
  }

  @Override
  public String getTooltip() {
    return sync.isAwaitingSearch()
        ? "Bankstand: click Search in your collection log to sync it"
        : "Bankstand: reading your collection log, " + sync.observedCount() + " entries observed";
  }
}
