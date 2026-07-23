package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class SkillBaselineTest {
  private static Map<String, Integer> of(String k, int v) {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put(k, v);
    return m;
  }

  @Test
  public void everythingIsChangedAgainstAFreshBaseline() {
    assertTrue(new SkillBaseline().changedSince(of("attack", 100)));
  }

  @Test
  public void unchangedAfterAdvance() {
    SkillBaseline b = new SkillBaseline();
    b.advance(of("attack", 100));
    assertFalse(b.changedSince(of("attack", 100)));
  }

  @Test
  public void anXpIncreaseIsAChange() {
    SkillBaseline b = new SkillBaseline();
    b.advance(of("attack", 100));
    assertTrue(b.changedSince(of("attack", 200)));
  }

  @Test
  public void aNewSkillKeyIsAChange() {
    SkillBaseline b = new SkillBaseline();
    b.advance(of("attack", 100));
    Map<String, Integer> more = of("attack", 100);
    more.put("slayer", 5);
    assertTrue(b.changedSince(more));
  }

  @Test
  public void resetForgetsTheBaseline() {
    SkillBaseline b = new SkillBaseline();
    b.advance(of("attack", 100));
    b.reset();
    assertTrue(b.changedSince(of("attack", 100)));
  }
}
