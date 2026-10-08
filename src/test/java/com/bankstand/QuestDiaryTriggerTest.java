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
    // Level-ups and clue rewards also start with "Congratulations" and must not trigger.
    assertFalse(
        BankstandPlugin.isQuestOrDiaryCompletionMessage(
            "Congratulations, you've reached level 99 in Woodcutting!"));
  }

  @Test
  public void ignoresAnUnrelatedMessage() {
    assertFalse(BankstandPlugin.isQuestOrDiaryCompletionMessage("You feel more experienced."));
  }
}
