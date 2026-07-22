package com.bankstand.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccountSessionTest {

  @Test
  public void ignoresTheLoggedOutSentinel() {
    AccountSession s = new AccountSession();
    assertFalse(s.onLogin(AccountSession.LOGGED_OUT));
    assertFalse(s.isActive());
    assertEquals(AccountSession.LOGGED_OUT, s.getAccountHash());
  }

  @Test
  public void adoptsTheFirstRealHash() {
    AccountSession s = new AccountSession();
    assertTrue(s.onLogin(123L));
    assertTrue(s.isActive());
    assertEquals(123L, s.getAccountHash());
  }

  @Test
  public void theSameHashIsANoOp() {
    AccountSession s = new AccountSession();
    s.onLogin(123L);
    assertFalse(s.onLogin(123L));
    assertEquals(123L, s.getAccountHash());
  }

  @Test
  public void aSwitchResetsAndAdoptsTheNewHash() {
    AccountSession s = new AccountSession();
    s.onLogin(123L);
    assertTrue(s.onLogin(456L));
    assertEquals(456L, s.getAccountHash());
  }

  @Test
  public void logoutClearsTheSession() {
    AccountSession s = new AccountSession();
    s.onLogin(123L);
    s.onLogout();
    assertFalse(s.isActive());
    assertEquals(AccountSession.LOGGED_OUT, s.getAccountHash());
  }

  @Test
  public void isCurrentForTheAdoptedHashAndGeneration() {
    AccountSession s = new AccountSession();
    s.onLogin(123L);
    assertTrue(s.isCurrent(123L, s.getGeneration()));
  }

  @Test
  public void notCurrentForADifferentHash() {
    AccountSession s = new AccountSession();
    s.onLogin(123L);
    assertFalse(s.isCurrent(456L, s.getGeneration()));
  }

  @Test
  public void notCurrentAfterSwitchingAccounts() {
    AccountSession s = new AccountSession();
    s.onLogin(123L);
    int gen = s.getGeneration();
    s.onLogin(456L);
    // A stale in-flight submit for the old account must not be treated as current.
    assertFalse(s.isCurrent(123L, gen));
    assertTrue(s.isCurrent(456L, s.getGeneration()));
  }

  @Test
  public void notCurrentAfterLogout() {
    AccountSession s = new AccountSession();
    s.onLogin(123L);
    int gen = s.getGeneration();
    s.onLogout();
    assertFalse(s.isCurrent(123L, gen));
  }

  @Test
  public void theLoggedOutSentinelIsNeverCurrent() {
    AccountSession s = new AccountSession();
    assertFalse(s.isCurrent(AccountSession.LOGGED_OUT, s.getGeneration()));
  }

  @Test
  public void aStalePriorInstanceIsNotCurrentAfterReloggingTheSameAccount() {
    AccountSession s = new AccountSession();
    s.onLogin(123L);
    int staleGen = s.getGeneration();
    // Log out and back into the SAME account: a fresh instance with a new generation.
    s.onLogout();
    s.onLogin(123L);
    // The prior instance's in-flight submit must not be treated as current even
    // though the account hash matches again.
    assertFalse(s.isCurrent(123L, staleGen));
    assertTrue(s.isCurrent(123L, s.getGeneration()));
  }

  @Test
  public void generationIsStableAcrossASameHashNoOpLogin() {
    AccountSession s = new AccountSession();
    s.onLogin(123L);
    int gen = s.getGeneration();
    // A world hop stays on the same account: onLogin is a no-op and must not advance
    // the generation, so an in-flight submit for this instance stays current.
    assertFalse(s.onLogin(123L));
    assertEquals(gen, s.getGeneration());
    assertTrue(s.isCurrent(123L, gen));
  }

  @Test
  public void tracksTheSubmittedFlagPerAccount() {
    AccountSession s = new AccountSession();
    s.onLogin(123L);
    assertFalse(s.isSubmitted());
    s.markSubmitted();
    assertTrue(s.isSubmitted());

    // Same account stays submitted; a switch resets it; so does logout.
    assertFalse(s.onLogin(123L));
    assertTrue(s.isSubmitted());
    assertTrue(s.onLogin(456L));
    assertFalse(s.isSubmitted());

    s.markSubmitted();
    s.onLogout();
    assertFalse(s.isSubmitted());
  }
}
