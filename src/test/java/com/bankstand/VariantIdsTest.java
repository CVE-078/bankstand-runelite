package com.bankstand;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class VariantIdsTest {

  /** Measured on a real account: 193 ids, and the log's own title said 189. */
  @Test
  public void countsBothProspectorSetsAsFourSlots() {
    assertEquals(
        4,
        VariantIds.countEntries(
            Arrays.asList(12013, 12014, 12015, 12016, 29472, 29474, 29476, 29478)));
  }

  @Test
  public void countsOneProspectorSetAsFourSlotsToo() {
    assertEquals(4, VariantIds.countEntries(Arrays.asList(29472, 29474, 29476, 29478)));
    assertEquals(4, VariantIds.countEntries(Arrays.asList(12013, 12014, 12015, 12016)));
  }

  @Test
  public void leavesAnythingItDoesNotKnowAlone() {
    assertEquals(3, VariantIds.countEntries(Arrays.asList(1, 2, 3)));
    assertEquals(5, VariantIds.canonical(5));
  }

  @Test
  public void countsNothingAsZero() {
    assertEquals(0, VariantIds.countEntries(Collections.emptyList()));
  }

  @Test
  public void dedupesARepeatedId() {
    assertEquals(1, VariantIds.countEntries(Arrays.asList(4151, 4151, 4151)));
  }
}
