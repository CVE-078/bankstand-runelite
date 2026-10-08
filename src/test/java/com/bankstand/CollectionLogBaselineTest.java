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
    // Nothing observed is not nothing owned; submitting would just send noise.
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
    // The baseline only moves on a server ack, so a dropped submit is retried.
    CollectionLogBaseline b = new CollectionLogBaseline();
    assertTrue(b.changedSince(5));
    assertTrue(b.changedSince(5));
  }
}
