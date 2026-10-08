package com.bankstand;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Collection log items observed on this account so far. Accumulates rather than snapshots: the
 * game reveals the log only while its interface is open, often partially, so a read only adds.
 * It cannot report absence; an omitted item means "not observed", never "not owned".
 */
public class CollectionLogAccumulator {
  private final Set<Integer> observed = new LinkedHashSet<>();

  /** Returns true when the item was not already known. */
  public boolean observe(int itemId) {
    return observed.add(itemId);
  }

  /** In first-seen order. */
  public Set<Integer> observed() {
    return Collections.unmodifiableSet(observed);
  }

  public int size() {
    return observed.size();
  }

  public boolean isEmpty() {
    return observed.isEmpty();
  }

  /** Must be persisted: an observation lost to a restart cannot be read again. */
  public void restore(Set<Integer> items) {
    observed.clear();
    observed.addAll(items);
  }

  /** Called on an account switch, so one account's items never reach another. */
  public void reset() {
    observed.clear();
  }
}
