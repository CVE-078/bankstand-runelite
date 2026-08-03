package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CollectionLogBaselineTest {

  @Test
  public void reportsAChangeUntilTheServerAcknowledges() {
    CollectionLogBaseline b = new CollectionLogBaseline();
    assertTrue(b.changedSince(12));
    b.advance(12);
    assertFalse(b.changedSince(12));
  }

  @Test
  public void reportsAChangeWhenTheLogGrows() {
    CollectionLogBaseline b = new CollectionLogBaseline();
    b.advance(12);
    assertTrue(b.changedSince(13));
  }

  @Test
  public void neverReportsAChangeForAnEmptyLog() {
    // Nothing observed is not the same as nothing owned. Submitting here would send
    // an empty block, which the server reads as "not observed" anyway, so it would
    // be pure noise on every capture for a player who never opens their log.
    CollectionLogBaseline b = new CollectionLogBaseline();
    assertFalse(b.changedSince(0));
  }

  @Test
  public void resendsAfterAResetSoAnAccountSwitchStartsClean() {
    CollectionLogBaseline b = new CollectionLogBaseline();
    b.advance(12);
    b.reset();
    assertTrue(b.changedSince(12));
  }

  @Test
  public void doesNotAdvanceOnAnUnacknowledgedSubmit() {
    // The baseline only moves when the server says it stored the block, so a dropped
    // submit is retried rather than silently forgotten.
    CollectionLogBaseline b = new CollectionLogBaseline();
    assertTrue(b.changedSince(5));
    assertTrue(b.changedSince(5));
  }
}
