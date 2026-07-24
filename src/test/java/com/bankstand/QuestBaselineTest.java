package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class QuestBaselineTest {
  private static Map<String, String> of(String k, String v) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put(k, v);
    return m;
  }

  @Test
  public void everythingIsChangedAgainstAFreshBaseline() {
    assertTrue(new QuestBaseline().changedSince(of("COOKS_ASSISTANT", "NOT_STARTED")));
  }

  @Test
  public void unchangedAfterAdvance() {
    QuestBaseline b = new QuestBaseline();
    b.advance(of("COOKS_ASSISTANT", "NOT_STARTED"));
    assertFalse(b.changedSince(of("COOKS_ASSISTANT", "NOT_STARTED")));
  }

  @Test
  public void aQuestStateChangeIsAChange() {
    QuestBaseline b = new QuestBaseline();
    b.advance(of("COOKS_ASSISTANT", "NOT_STARTED"));
    assertTrue(b.changedSince(of("COOKS_ASSISTANT", "IN_PROGRESS")));
  }

  @Test
  public void aNewQuestKeyIsAChange() {
    QuestBaseline b = new QuestBaseline();
    b.advance(of("COOKS_ASSISTANT", "NOT_STARTED"));
    Map<String, String> more = of("COOKS_ASSISTANT", "NOT_STARTED");
    more.put("DRAGON_SLAYER_I", "IN_PROGRESS");
    assertTrue(b.changedSince(more));
  }

  @Test
  public void resetForgetsTheBaseline() {
    QuestBaseline b = new QuestBaseline();
    b.advance(of("COOKS_ASSISTANT", "NOT_STARTED"));
    b.reset();
    assertTrue(b.changedSince(of("COOKS_ASSISTANT", "NOT_STARTED")));
  }
}
