package com.bankstand;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * One single-purpose class per transient event type, all extending this.
 * Dink's structure: rather than a growing switch in the plugin class, each
 * detector is its own listener the plugin registers with the event bus.
 *
 * <p><b>Capture classes detect and emit domain events only. They do not
 * deliver.</b> {@link #emit} hands a built event to the {@link EventOutbox} and
 * returns; nothing here touches the network, retries, or persists beyond that.
 * Delivery belongs to the outbox, which is what makes a detector unit-testable
 * without a live client: feed it a synthetic RuneLite event and assert what it
 * handed the (fake) outbox, the same shape {@code DiaryVarbits} and {@code
 * CollectionLogAccumulator} already prove for the snapshot captures.
 *
 * <p>{@link #enabled} is the caller's own {@code config.collectX() &&
 * manifest.allows("x")} check, injected rather than read here, so a capture
 * never needs to know about {@code BankstandConfig} or {@code
 * CapabilityManifest} to be tested.
 */
public abstract class BaseCapture {

  private final EventOutbox outbox;
  private final BooleanSupplier enabled;
  private final LongSupplier accountHash;
  // Nullable: every existing test constructs a capture with the 3-arg constructor,
  // which has no notion of the panel's recent-activity list and does not need one.
  // Notified AFTER outbox.add, on the same emit, never re-invoked later from a
  // stored id: this is the one moment a human-readable description is cheaply
  // available (see RecentActivityLog's own javadoc for why that matters).
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

  /** Builds and emits one event, gated on {@link #enabled}. A no-op while off, so a
   *  detector can keep listening and simply drop what it observes without a second
   *  gate at every call site. */
  protected final void emit(String type, Map<String, Object> payload) {
    if (!enabled.getAsBoolean()) return;
    TransientEvent event = new TransientEvent(type, payload);
    outbox.add(accountHash.getAsLong(), event);
    if (onEmit != null) {
      onEmit.accept(event);
    }
  }

  /** Whether the toggle + manifest gate is currently open. A subclass checks this
   *  BEFORE doing any read (an item lookup, a regex match against chat), not only
   *  before {@link #emit}, so nothing is examined at all while the capability is
   *  off, matching the convention every other capture in this plugin already
   *  follows. */
  protected final boolean isEnabled() {
    return enabled.getAsBoolean();
  }
}
