package com.bankstand;

import java.util.ArrayDeque;
import java.util.Deque;
import lombok.extern.slf4j.Slf4j;

/**
 * Presence values still to send, in memory only. Transitions are a capped FIFO kept until
 * answered; the heartbeat is one latest value, dropped on failure, and sent only once no
 * transition is waiting so it never overtakes a logout. Thread-safe.
 */
@Slf4j
final class PresenceQueue {

  static final int MAX_TRANSITIONS = 8;

  private final Deque<PresenceSignal> transitions = new ArrayDeque<>();
  private PresenceSignal heartbeat;

  synchronized void addTransition(PresenceSignal signal) {
    if (transitions.size() == MAX_TRANSITIONS) {
      transitions.removeFirst();
      log.debug("presence queue full: dropped the oldest transition");
    }
    transitions.addLast(signal);
    heartbeat = null;
  }

  synchronized void offerHeartbeat(PresenceSignal signal) {
    heartbeat = signal;
  }

  /** The next value to send, or null when there is nothing. Does not remove it. */
  synchronized PresenceSignal peek() {
    PresenceSignal head = transitions.peekFirst();
    return head != null ? head : heartbeat;
  }

  /** The server answered: the value is final whatever it said, so it leaves the queue. */
  synchronized void onAnswered(PresenceSignal signal) {
    remove(signal);
  }

  /** A failure a later try might fix: a transition stays at the head, a heartbeat goes. */
  synchronized void onRetryableFailure(PresenceSignal signal) {
    if (signal == heartbeat) {
      heartbeat = null;
    }
  }

  /** A failure no later try can fix. */
  synchronized void onTerminalFailure(PresenceSignal signal) {
    remove(signal);
  }

  synchronized int size() {
    return transitions.size() + (heartbeat == null ? 0 : 1);
  }

  synchronized void clear() {
    transitions.clear();
    heartbeat = null;
  }

  private void remove(PresenceSignal signal) {
    if (transitions.peekFirst() == signal) {
      transitions.removeFirst();
    } else if (signal == heartbeat) {
      heartbeat = null;
    } else {
      transitions.remove(signal);
    }
  }
}
