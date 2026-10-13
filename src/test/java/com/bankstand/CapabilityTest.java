package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Which chat-triggered event stamps which capability's last-synced time. */
public class CapabilityTest {

  @Test
  public void eachChatEventStampsItsOwnCapability() {
    assertEquals(Capability.NOTABLE_DROPS, Capability.forEventType(TransientEvent.TYPE_NOTABLE_DROP));
    assertEquals(Capability.PET_DROPS, Capability.forEventType(TransientEvent.TYPE_PET_DROP));
    assertEquals(
        Capability.COLLECTION_LOG,
        Capability.forEventType(TransientEvent.TYPE_COLLECTION_LOG_UNLOCK));
    assertEquals(
        Capability.COMBAT_ACHIEVEMENTS,
        Capability.forEventType(TransientEvent.TYPE_COMBAT_ACHIEVEMENT_COMPLETED));
    assertEquals(
        Capability.DIARIES, Capability.forEventType(TransientEvent.TYPE_DIARY_TASK_COMPLETED));
    assertEquals(Capability.QUESTS, Capability.forEventType(TransientEvent.TYPE_QUEST_COMPLETED));
    assertEquals(
        Capability.SLAYER, Capability.forEventType(TransientEvent.TYPE_SLAYER_TASK_COMPLETED));
  }

  @Test
  public void aTierCompletionOrUnknownEventStampsNothing() {
    assertNull(Capability.forEventType(TransientEvent.TYPE_COMBAT_ACHIEVEMENT_TIER_COMPLETED));
    assertNull(Capability.forEventType("something_else"));
    assertNull(Capability.forEventType(null));
  }
}
