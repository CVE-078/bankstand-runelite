package com.bankstand;

import java.util.ArrayDeque;
import java.util.Deque;
import lombok.extern.slf4j.Slf4j;

/**
 * What presence still has to send. In memory only, deliberately: nothing here is written
 * to disk, so a crash or an uninstall loses at most a few minutes of session precision.
 *
 * <p>Two kinds of value with two different policies:
 *
 * <ul>
 *   <li><b>Transitions</b> ({@code login}, {@code logout}, {@code off}) are a FIFO of at most
 *       {@link #MAX_TRANSITIONS}, sent in order and kept at the head until acknowledged. A
 *       {@code login} never replaces an unacknowledged {@code logout}: both are sent, so a
 *       logout and a later relog stay two separate facts.
 *   <li><b>The heartbeat</b> ({@code active}) is one latest value. A newer one replaces it,
 *       and a failed one is dropped rather than retried, since the next minute sends a
 *       fresh one anyway.
 * </ul>
 *
 * <p>The heartbeat only goes once no transition is waiting, so the server never sees an
 * "online" mark overtake the logout it follows. Thread-safe: values arrive on the client
 * thread and are sent from the executor.
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
    // A heartbeat captured before this transition says nothing the transition does not.
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
