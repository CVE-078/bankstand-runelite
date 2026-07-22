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
}
