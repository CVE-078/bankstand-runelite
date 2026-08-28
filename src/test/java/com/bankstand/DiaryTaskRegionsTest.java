package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DiaryTaskRegionsTest {

  @Test
  public void resolvesEveryRegionDiaryTaskVarplayersKnows() {
    for (String region : DiaryTaskVarplayers.ALL.keySet()) {
      boolean found = false;
      for (String areaText : new String[] {
          "Ardougne", "Desert", "Falador", "Fremennik", "Kandarin", "Kourend & Kebos",
          "Lumbridge & Draynor", "Morytania", "Varrock", "Western Provinces",
          "Wilderness", "Karamja"}) {
        if (region.equals(DiaryTaskRegions.forAreaText(areaText))) {
          found = true;
          break;
        }
      }
      assertEquals(region, true, found);
    }
  }

  @Test
  public void resolvesTheDoubleBarrelledAreasVerifiedByDiaryTaskCompletionCapture() {
    assertEquals("WESTERN_PROVINCES", DiaryTaskRegions.forAreaText("Western Provinces"));
    assertEquals("LUMBRIDGE_DRAYNOR", DiaryTaskRegions.forAreaText("Lumbridge & Draynor"));
    assertEquals("KANDARIN", DiaryTaskRegions.forAreaText("Kandarin"));
    assertEquals("VARROCK", DiaryTaskRegions.forAreaText("Varrock"));
  }

  @Test
  public void returnsNullForAnUnrecognisedAreaText() {
    // A safe miss: DiaryTaskCompletionCapture falls through to the plain tier/area event
    // exactly as it did before this table existed.
    assertNull(DiaryTaskRegions.forAreaText("Not A Real Area"));
    assertNull(DiaryTaskRegions.forAreaText(""));
  }
}
