package com.bankstand;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The guided collection log read: what the plugin knows about a sync the player started,
 * and when that read is over.
 *
 * <p>The plugin cannot make the log enumerate itself. Driving the game's own Search
 * needs {@code client.menuAction}, which the Plugin Hub rejects, so the player clicks
 * Search and the plugin watches. Nothing here triggers anything; every method is fed by
 * an event the client raised on its own.
 *
 * <p>Item script {@code 4100} fires for every entry whoever ran the Search, and it also
 * fires while a player simply browses a page. Those two cases have to be told apart or
 * a stroll through one boss tab would report itself as a whole-log sync. So an item only
 * counts toward a read once the player has armed one, and a read only claims to be
 * complete when the search interface was seen open while entries were streaming.
 *
 * <p>That last signal is a widget read, and a widget read cannot be verified outside a
 * running client. The rule is therefore written to fail toward {@code PARTIAL}: an
 * unseen search downgrades an otherwise clean read rather than a missed signal
 * promoting a half-read log to complete. Under-claiming costs the player one more
 * click; over-claiming stores a log that silently is not the whole log.
 *
 * <p>Pure and deterministic: no client, no clock, no scheduling. The plugin passes the
 * two facts it reads per tick and gets back the outcome, if the read just ended.
 * Single-threaded by construction, like the rest of the session state, and touched only
 * from the client thread.
 */
public class CollectionLogSync {

  /**
   * Ticks without a new entry before a read counts as over. The game streams the whole
   * log in a burst, so any real gap means the burst finished; a few ticks of slack
   * absorbs a stagger without making the player wait for a timeout.
   */
  public static final int QUIET_TICKS = 5;

  /**
   * Ticks an armed sync waits for a Search that never comes, about two minutes. Without
   * it, a player who opens the menu and changes their mind leaves the infobox up until
   * they close the log.
   */
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
   * Records one entry the client reported.
   *
   * <p><b>A search starts a read on its own.</b> Arming was never a gate on capture,
   * only on reporting, so requiring it made the player click twice before the one click
   * that actually does the work. WikiSync gets away with a single button because it
   * drives the Search with {@code client.menuAction}; the Hub does not allow us that, so
   * the fewest possible actions is the player clicking the game's own Search, and that
   * is now the whole flow. The menu entry stays as a hint for anyone who does not know.
   *
   * <p>Entries arriving with the search closed are ordinary page browsing. They still
   * reach the accumulator, but they must not start or extend a read, or turning one page
   * would report itself as a whole-log sync.
   *
   * <p>Counted by distinct id, not by event. A real enumeration fires the script twice
   * per entry on the first read of a session, so counting events reported "Synced 422
   * entries" for a log holding 211.
   */
  public void onItemObserved(int itemId, boolean searchOpen) {
    if (state == State.IDLE) {
      if (!searchOpen) {
        return;
      }
      sawSearch = true;
    }
    state = State.READING;
    // Canonical, so the running count is slots filled rather than ids seen. The
    // accumulator keeps the raw id; this set only ever feeds a number on screen.
    observed.add(VariantIds.canonical(itemId));
    quietTicks = 0;
  }

  /**
   * Advances the read by one tick.
   *
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
    // Closing the log ends the read either way. Mid-stream that is an interruption worth
    // reporting; before any entry arrived there is nothing to report at all.
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

  /**
   * Abandons anything in flight. A read belongs to the character that started it, so a
   * logout or an account switch drops it rather than carrying it across.
   */
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
