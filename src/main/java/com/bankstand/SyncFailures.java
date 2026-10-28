package com.bankstand;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The panel's failure line. Snapshot and event failures are kept apart, so one path's success
 * never hides the other's failure. Set from HTTP threads.
 */
final class SyncFailures {

  static final String FALLBACK = "Sync failed.";

  private final Runnable onChange;
  private volatile String snapshot;
  private volatile String events;

  SyncFailures(Runnable onChange) {
    this.onChange = onChange;
  }

  /** A failure with no message is still a failure. */
  static String reason(Throwable failure) {
    String message = failure.getMessage();
    return message == null ? FALLBACK : message;
  }

  void snapshotFailed(Throwable failure) {
    snapshot = reason(failure);
    onChange.run();
  }

  void snapshotSucceeded() {
    snapshot = null;
  }

  /** Pairing or disconnecting starts over. */
  void clear() {
    snapshot = null;
    events = null;
  }

  boolean any() {
    return snapshot != null || events != null;
  }

  /** The snapshot's failure first, otherwise the events'. */
  String shown() {
    return snapshot != null ? snapshot : events;
  }

  Drain startDrain() {
    return new Drain();
  }

  /** One drain's chunks. */
  final class Drain {
    private volatile String failed;

    void failed(Throwable failure) {
      if (failed == null) {
        failed = reason(failure);
      }
    }

    /** Records the outcome, then releases the flight. Clears only if every sent chunk succeeded. */
    void finish(int sentChunks, AtomicBoolean flight) {
      if (failed != null) {
        events = failed;
      } else if (sentChunks > 0) {
        events = null;
      }
      flight.set(false);
      onChange.run();
    }
  }
}
