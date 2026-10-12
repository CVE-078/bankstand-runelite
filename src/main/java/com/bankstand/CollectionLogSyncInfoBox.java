package com.bankstand;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/** Shows a running count during a guided collection log read; removed when the read ends. */
class CollectionLogSyncInfoBox extends InfoBox {

  private final CollectionLogSync sync;

  CollectionLogSyncInfoBox(BufferedImage image, Plugin plugin, CollectionLogSync sync) {
    super(image, plugin);
    this.sync = sync;
    setPriority(net.runelite.client.ui.overlay.infobox.InfoBoxPriority.LOW);
  }

  @Override
  public String getText() {
    // A dash, not zero, while nothing has been read yet.
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
