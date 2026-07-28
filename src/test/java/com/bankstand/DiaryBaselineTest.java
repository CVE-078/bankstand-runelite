package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class DiaryBaselineTest {
  private static Map<String, String> of(String k, String v) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put(k, v);
    return m;
  }

  @Test
  public void everythingIsChangedAgainstAFreshBaseline() {
    assertTrue(new DiaryBaseline().changedSince(of("ARDOUGNE_EASY", "INCOMPLETE")));
  }

  @Test
  public void unchangedAfterAdvance() {
    DiaryBaseline b = new DiaryBaseline();
    b.advance(of("ARDOUGNE_EASY", "INCOMPLETE"));
    assertFalse(b.changedSince(of("ARDOUGNE_EASY", "INCOMPLETE")));
  }

  @Test
  public void aDiaryStateChangeIsAChange() {
    DiaryBaseline b = new DiaryBaseline();
    b.advance(of("ARDOUGNE_EASY", "INCOMPLETE"));
    assertTrue(b.changedSince(of("ARDOUGNE_EASY", "COMPLETE")));
  }

  @Test
  public void aNewDiaryKeyIsAChange() {
    DiaryBaseline b = new DiaryBaseline();
    b.advance(of("ARDOUGNE_EASY", "INCOMPLETE"));
    Map<String, String> more = of("ARDOUGNE_EASY", "INCOMPLETE");
    more.put("ARDOUGNE_MEDIUM", "INCOMPLETE");
    assertTrue(b.changedSince(more));
  }

  @Test
  public void resetForgetsTheBaseline() {
    DiaryBaseline b = new DiaryBaseline();
    b.advance(of("ARDOUGNE_EASY", "INCOMPLETE"));
    b.reset();
    assertTrue(b.changedSince(of("ARDOUGNE_EASY", "INCOMPLETE")));
  }
}
