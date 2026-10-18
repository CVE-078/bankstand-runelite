package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.bankstand.session.AccountSession;
import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Proxy;
import java.time.Instant;
import net.runelite.api.Client;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SlayerTaskStartTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final long ACCOUNT = 1L;
  private static final Instant T0 = Instant.parse("2026-10-18T09:00:00Z");
  private static final Instant T1 = Instant.parse("2026-10-18T09:30:00Z");
  private static final SlayerTask DUST =
      new SlayerTask(true, "Dust devils", 187, 187, "duradel", 1240, 312, null);
  private static final SlayerTask DUST_LATER =
      new SlayerTask(true, "Dust devils", 64, 187, "duradel", 1240, 312, null);
  private static final SlayerTask KURASK =
      new SlayerTask(true, "Kurask", 120, 120, "duradel", 1255, 313, null);

  private File file;

  private SlayerTaskStart tracker() throws Exception {
    if (file == null) {
      file = new File(folder.newFolder("bankstand"), "slayer-task-start.json");
    }
    // Synchronous IO, so a second tracker reads what the first wrote.
    return new SlayerTaskStart(file, new Gson(), Runnable::run, null, new AccountSession());
  }

  @Test
  public void aTaskAlreadyRunningAtFirstLookHasNoStart() throws Exception {
    assertNull(tracker().observe(ACCOUNT, 1, DUST, T0).startedAt);
  }

  @Test
  public void aTaskSeenArrivingAfterNoneStartsThenAndKeepsIt() throws Exception {
    SlayerTaskStart t = tracker();
    t.observe(ACCOUNT, 1, SlayerTask.NONE, T0);
    assertEquals("2026-10-18T09:00:00Z", t.observe(ACCOUNT, 1, DUST, T0).startedAt);
    assertEquals("2026-10-18T09:00:00Z", t.observe(ACCOUNT, 1, DUST_LATER, T1).startedAt);
  }

  @Test
  public void aTaskReplacingAnotherStartsWhenSeen() throws Exception {
    SlayerTaskStart t = tracker();
    t.observe(ACCOUNT, 1, DUST, T0);
    assertEquals("2026-10-18T09:30:00Z", t.observe(ACCOUNT, 1, KURASK, T1).startedAt);
  }

  @Test
  public void aRestartKeepsTheStart() throws Exception {
    SlayerTaskStart t = tracker();
    t.observe(ACCOUNT, 1, SlayerTask.NONE, T0);
    t.observe(ACCOUNT, 1, DUST, T0);

    assertEquals("2026-10-18T09:00:00Z", tracker().observe(ACCOUNT, 1, DUST_LATER, T1).startedAt);
  }

  @Test
  public void aTaskChangedWhileClosedHasNoStartAndDropsTheOldOne() throws Exception {
    SlayerTaskStart t = tracker();
    t.observe(ACCOUNT, 1, SlayerTask.NONE, T0);
    t.observe(ACCOUNT, 1, DUST, T0);

    SlayerTaskStart restarted = tracker();
    assertNull(restarted.observe(ACCOUNT, 1, KURASK, T1).startedAt);
    assertNull(tracker().observe(ACCOUNT, 1, DUST, T1).startedAt);
  }

  @Test
  public void anotherAccountNeverInheritsWhatThisOneSaw() throws Exception {
    SlayerTaskStart t = tracker();
    t.observe(ACCOUNT, 1, SlayerTask.NONE, T0);
    assertNull(t.observe(2L, 1, DUST, T0).startedAt);
  }

  @Test
  public void aPartialReadDoesNotRestampARunningTask() throws Exception {
    SlayerTaskStart t = tracker();
    t.observe(ACCOUNT, 1, SlayerTask.NONE, T0);
    t.observe(ACCOUNT, 1, DUST, T0);
    SlayerTask partial = new SlayerTask(true, null, 186, null, "duradel", 1240, 312, null);
    assertNull(t.observe(ACCOUNT, 1, partial, T1).startedAt);
    assertEquals("2026-10-18T09:00:00Z", t.observe(ACCOUNT, 1, DUST_LATER, T1).startedAt);
  }

  @Test
  public void aTaskChangedWhileCaptureWasOffIsNotStamped() throws Exception {
    AccountSession session = new AccountSession();
    session.onLogin(ACCOUNT);
    SlayerTaskStart t =
        new SlayerTaskStart(
            new File(folder.newFolder("off"), "s.json"), new Gson(), Runnable::run,
            noTaskClient(), session);
    t.observe(ACCOUNT, session.getGeneration(), DUST, T0);
    t.read(false);
    assertNull(t.observe(ACCOUNT, session.getGeneration(), KURASK, T1).startedAt);
  }

  private static Client noTaskClient() {
    return (Client)
        Proxy.newProxyInstance(
            Client.class.getClassLoader(),
            new Class<?>[] {Client.class},
            (proxy, method, args) -> method.getReturnType() == int.class ? 0 : null);
  }

  @Test
  public void aChangeAcrossALogoutIsNotSeenLive() throws Exception {
    SlayerTaskStart t = tracker();
    t.observe(ACCOUNT, 1, SlayerTask.NONE, T0);
    assertNull(t.observe(ACCOUNT, 2, DUST, T1).startedAt);
  }
}
