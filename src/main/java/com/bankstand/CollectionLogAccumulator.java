package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The collection log items observed on this account so far.
 *
 * <p>Unlike every other capability, this ACCUMULATES rather than snapshots. Skills,
 * quests and diaries are read whole on every capture, so a fresh read legitimately
 * replaces the last one. The collection log is not resident in the client: the game
 * only reveals it while the log interface enumerates, and an enumeration can be
 * partial (a cancelled search, one page browsed). Replacing on a partial read would
 * discard everything previously known, so a read only ever adds.
 *
 * <p>Ownership of an item is also one-way. Nothing in the game removes an entry from
 * a collection log, so "observed once" stays true and there is no un-set to model.
 *
 * <p>The corollary is that this cannot report absence. Not-yet-seen and not-owned are
 * indistinguishable from here, which is why the server treats an omitted item as "not
 * observed" and never as "does not have it".
 */
public class CollectionLogAccumulator {
  private final Set<Integer> observed = new LinkedHashSet<>();

  /** Records one item the client enumerated. Returns true when it was not already known. */
  public boolean observe(int itemId) {
    return observed.add(itemId);
  }

  /** Every item observed so far, in first-seen order. */
  public Set<Integer> observed() {
    return Collections.unmodifiableSet(observed);
  }

  public int size() {
    return observed.size();
  }

  public boolean isEmpty() {
    return observed.isEmpty();
  }

  /**
   * Forgets everything. Called on an account switch: a collection log belongs to one
   * character, and carrying it across would attribute one account's items to another.
   */
  public void reset() {
    observed.clear();
  }
}
