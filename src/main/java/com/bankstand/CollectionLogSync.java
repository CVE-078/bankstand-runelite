package com.bankstand;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * State of a guided collection log read, which the player starts by clicking the log's Search.
 * The plugin only observes: driving Search needs {@code client.menuAction}, which the Plugin Hub
 * rejects.
 *
 * <p>Script 4100 also fires while browsing a page, so a read is only {@code COMPLETE} when the
 * search was seen open while entries streamed. The rule fails toward {@code PARTIAL}, because
 * over-claiming stores a log that is not the whole log. Pure; client thread only.
 */
public class CollectionLogSync {

  /** Ticks without a new entry before a read counts as over (the log streams in one burst). */
  public static final int QUIET_TICKS = 5;

  /** Ticks an armed sync waits for a Search (about two minutes). */
  public static final int ARM_TIMEOUT_TICKS = 200;

  /** How a read ended, and how much of the log it saw. */
  public enum Outcome {
    /** Entries streamed while the search was open, and the stream ended on its own. */
    COMPLETE,
    /** The read was cut short, or ran without the search ever being seen. */
    PARTIAL;

    private int observed;

    /** Distinct entries seen during the read this outcome describes. */
    public int getObserved() {
      return observed;
    }

    private Outcome with(int count) {
      this.observed = count;
      return this;
    }
  }

  private enum State {
    IDLE,
    AWAITING_SEARCH,
    READING
  }

  private State state = State.IDLE;
  private final Set<Integer> observed = new LinkedHashSet<>();
  private int quietTicks;
  private int armedTicks;
  private boolean sawSearch;

  /** Starts a guided read. Arming again abandons whatever the last one had seen. */
  public void arm() {
    state = State.AWAITING_SEARCH;
    observed.clear();
    quietTicks = 0;
    armedTicks = 0;
    sawSearch = false;
  }

  /**
   * Records one entry. An open search starts a read without arming; entries with the search
   * closed are page browsing and never start or extend one. Counted by distinct id, since a
   * real enumeration can fire the script twice per entry.
   */
  public void onItemObserved(int itemId, boolean searchOpen) {
    if (state == State.IDLE) {
      if (!searchOpen) {
        return;
      }
      sawSearch = true;
    }
    state = State.READING;
    // Canonical id, so the count is slots filled. Only feeds the on-screen number.
    observed.add(VariantIds.canonical(itemId));
    quietTicks = 0;
  }

  /**
   * @param searchOpen whether the log's own search interface is on screen
   * @param logOpen whether the collection log is still on screen at all
   * @return the outcome when this tick ended the read, otherwise null. A read that ends
   *     with nothing to report (armed, never acted on) returns null and simply stops.
   */
  public Outcome onTick(boolean searchOpen, boolean logOpen) {
    if (state == State.IDLE) {
      return null;
    }
    if (searchOpen) {
      sawSearch = true;
    }
    // Closing the log ends the read; only a read that saw entries reports PARTIAL.
    if (!logOpen) {
      boolean reading = state == State.READING;
      Outcome outcome = reading ? Outcome.PARTIAL.with(observed.size()) : null;
      finish();
      return outcome;
    }
    if (state == State.AWAITING_SEARCH) {
      armedTicks++;
      if (armedTicks >= ARM_TIMEOUT_TICKS) {
        finish();
      }
      return null;
    }
    quietTicks++;
    if (quietTicks < QUIET_TICKS) {
      return null;
    }
    Outcome outcome = (sawSearch ? Outcome.COMPLETE : Outcome.PARTIAL).with(observed.size());
    finish();
    return outcome;
  }

  /** True while a read is armed or streaming. */
  public boolean isActive() {
    return state != State.IDLE;
  }

  /** True while waiting for the player to run the Search. */
  public boolean isAwaitingSearch() {
    return state == State.AWAITING_SEARCH;
  }

  /** Distinct entries seen so far in the current read. */
  public int observedCount() {
    return observed.size();
  }

  /** Abandons anything in flight, on logout or account switch. */
  public void reset() {
    finish();
  }

  private void finish() {
    state = State.IDLE;
    observed.clear();
    quietTicks = 0;
    armedTicks = 0;
    sawSearch = false;
  }
}
