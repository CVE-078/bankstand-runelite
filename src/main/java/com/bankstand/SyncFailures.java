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
  // An auth failure halts every account, so a switch keeps it.
  private volatile boolean snapshotAuth;
  // Bumped by clear(), so a drain from before a re-pair or disconnect writes nothing.
  private volatile int epoch;

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
    snapshotAuth = failure instanceof SubmitException && ((SubmitException) failure).isAuthFailure();
    onChange.run();
  }

  void snapshotSucceeded() {
    snapshot = null;
    onChange.run();
  }

  /** The previous account's snapshot failure is not the new one's. */
  void accountChanged() {
    if (!snapshotAuth) {
      snapshot = null;
    }
    onChange.run();
  }

  /** Pairing or disconnecting starts over. */
  void clear() {
    epoch++;
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
    private final int startEpoch = epoch;
    private volatile String failed;

    void failed(Throwable failure) {
      if (failed == null) {
        failed = reason(failure);
      }
    }

    /** Records the outcome, then releases the flight. Clears only if every sent chunk succeeded. */
    void finish(int sentChunks, AtomicBoolean flight) {
      // A drain started under a pairing since replaced or removed says nothing about this one.
      if (startEpoch == epoch && failed != null) {
        events = failed;
      } else if (startEpoch == epoch && sentChunks > 0) {
        events = null;
      }
      flight.set(false);
      onChange.run();
    }
  }
}
