package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuestDiaryTriggerTest {

  @Test
  public void recognisesAQuestCompletionMessage() {
    assertTrue(
        BankstandPlugin.isQuestOrDiaryCompletionMessage(
            "Congratulations, you have completed the Cook's Assistant quest!"));
  }

  @Test
  public void recognisesADiaryTierCompletionMessage() {
    assertTrue(
        BankstandPlugin.isQuestOrDiaryCompletionMessage(
            "Congratulations, you have completed all of the Easy tasks in the Varrock area."
                + " Speak to Aeonisig Raddan to claim your reward."));
  }

  @Test
  public void ignoresAnUnrelatedCongratulationsMessage() {
    // The word "Congratulations" alone is not enough: a level-up or a clue-scroll
    // reward also starts this way and must not trigger an early resubmit for
    // something captureSkills() was never going to change anyway.
    assertFalse(
        BankstandPlugin.isQuestOrDiaryCompletionMessage(
            "Congratulations, you've reached level 99 in Woodcutting!"));
  }

  @Test
  public void ignoresAnUnrelatedMessage() {
    assertFalse(BankstandPlugin.isQuestOrDiaryCompletionMessage("You feel more experienced."));
  }
}
