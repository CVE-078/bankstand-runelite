package com.bankstand;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PanelPresentationTest {

  @Test
  public void notPairedIsAlwaysGreyWhateverElseIsTrue() {
    assertEquals(
        PanelPresentation.SyncDot.GREY,
        PanelPresentation.resolveDot(false, true, false));
    assertEquals(
        PanelPresentation.SyncDot.GREY,
        PanelPresentation.resolveDot(false, false, true));
  }

  @Test
  public void pairedAndSucceededIsGreen() {
    assertEquals(
        PanelPresentation.SyncDot.GREEN,
        PanelPresentation.resolveDot(true, true, false));
  }

  @Test
  public void pairedAndTheMostRecentAttemptFailedIsAmberEvenAfterAnEarlierSuccess() {
    assertEquals(
        PanelPresentation.SyncDot.AMBER,
        PanelPresentation.resolveDot(true, true, true));
  }

  @Test
  public void pairedButNothingHasEverSucceededIsGreyRatherThanAFalseGreen() {
    assertEquals(
        PanelPresentation.SyncDot.GREY,
        PanelPresentation.resolveDot(true, false, false));
  }

  @Test
  public void underAMinuteReadsAsJustNow() {
    assertEquals("just now", PanelPresentation.formatAge(0));
    assertEquals("just now", PanelPresentation.formatAge(59_999));
  }

  @Test
  public void minutesRoundDown() {
    assertEquals("1m ago", PanelPresentation.formatAge(60_000));
    assertEquals("2m ago", PanelPresentation.formatAge(179_999));
  }

  @Test
  public void hoursTakeOverAtSixtyMinutes() {
    assertEquals("59m ago", PanelPresentation.formatAge(59 * 60_000L));
    assertEquals("1h ago", PanelPresentation.formatAge(60 * 60_000L));
  }

  @Test
  public void daysTakeOverAtTwentyFourHours() {
    assertEquals("23h ago", PanelPresentation.formatAge(23 * 3_600_000L));
    assertEquals("1d ago", PanelPresentation.formatAge(24 * 3_600_000L));
    assertEquals("5d ago", PanelPresentation.formatAge(5 * 24 * 3_600_000L));
  }
}
