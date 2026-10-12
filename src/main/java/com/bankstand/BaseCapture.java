package com.bankstand;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Base for the one-class-per-event-type detectors. A capture only detects and hands events to
 * the {@link EventOutbox}; it never touches the network. {@code enabled} is the injected
 * config-and-manifest gate.
 */
public abstract class BaseCapture {

  private final EventOutbox outbox;
  private final BooleanSupplier enabled;
  private final LongSupplier accountHash;
  // Nullable. Called after outbox.add, once per emit.
  private final Consumer<TransientEvent> onEmit;

  protected BaseCapture(EventOutbox outbox, BooleanSupplier enabled, LongSupplier accountHash) {
    this(outbox, enabled, accountHash, null);
  }

  protected BaseCapture(
      EventOutbox outbox,
      BooleanSupplier enabled,
      LongSupplier accountHash,
      Consumer<TransientEvent> onEmit) {
    this.outbox = outbox;
    this.enabled = enabled;
    this.accountHash = accountHash;
    this.onEmit = onEmit;
  }

  /** A no-op while the capability is off. */
  protected final void emit(String type, Map<String, Object> payload) {
    if (!enabled.getAsBoolean()) return;
    TransientEvent event = new TransientEvent(type, payload);
    outbox.add(accountHash.getAsLong(), event);
    if (onEmit != null) {
      onEmit.accept(event);
    }
  }

  /** Check before doing any read, so nothing is examined while the capability is off. */
  protected final boolean isEnabled() {
    return enabled.getAsBoolean();
  }
}
