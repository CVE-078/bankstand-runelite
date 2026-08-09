package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class SubmitEnvelopeTest {
  private static Map<String, Integer> skills() {
    Map<String, Integer> s = new LinkedHashMap<>();
    s.put("attack", 5500000);
    s.put("slayer", 101333);
    return s;
  }

  @Test
  public void buildsTheV1WireShape() {
    Map<String, Object> body =
        SubmitEnvelope.body(
            "018f9c8e-7b7a-7c00-8000-000000000abc",
            1,
            "1.0.0",
            "2026-07-23T10:00:00.000Z",
            123456789012345L,
            "Zezima",
            skills());
    JsonObject json = new Gson().toJsonTree(body).getAsJsonObject();

    assertEquals("018f9c8e-7b7a-7c00-8000-000000000abc", json.get("submissionId").getAsString());
    assertEquals(1, json.get("schemaVersion").getAsInt());
    assertEquals("1.0.0", json.get("pluginVersion").getAsString());
    assertEquals("2026-07-23T10:00:00.000Z", json.get("capturedAt").getAsString());
    // account hash is a decimal STRING, not a number.
    assertTrue(json.get("accountHash").getAsJsonPrimitive().isString());
    assertEquals("123456789012345", json.get("accountHash").getAsString());
    assertEquals("Zezima", json.get("displayName").getAsString());
    assertEquals(5500000, json.getAsJsonObject("skills").getAsJsonObject("attack").get("xp").getAsInt());
  }

  @Test
  public void omitsDisplayNameWhenBlank() {
    Map<String, Object> body =
        SubmitEnvelope.body("id", 1, "1.0.0", "t", 1L, null, skills());
    assertFalse(new Gson().toJsonTree(body).getAsJsonObject().has("displayName"));
  }

  @Test
  public void omitsDiariesWhenNull() {
    Map<String, Object> body =
        SubmitEnvelope.body("id", 1, "1.0.0", "t", 1L, "Zezima", skills(), null, null);
    assertFalse(new Gson().toJsonTree(body).getAsJsonObject().has("diaries"));
  }

  @Test
  public void omitsDiariesWhenEmpty() {
    Map<String, Object> body =
        SubmitEnvelope.body(
            "id", 1, "1.0.0", "t", 1L, "Zezima", skills(), null, new LinkedHashMap<>());
    assertFalse(new Gson().toJsonTree(body).getAsJsonObject().has("diaries"));
  }

  @Test
  public void carriesDiaryTaskCountsWhenThereAreAny() {
    java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
    counts.put("ARDOUGNE_EASY", 9);
    counts.put("ARDOUGNE_MEDIUM", 0);
    Map<String, Object> body =
        SubmitEnvelope.body(
            "id", 1, "1.0.0", "2026-08-09T10:00:00Z", 42L, "Zezima",
            java.util.Collections.singletonMap("attack", 100),
            null, null, null, null, counts);

    @SuppressWarnings("unchecked")
    Map<String, Object> sent = (Map<String, Object>) body.get("diaryTasks");
    assertEquals(Integer.valueOf(9), sent.get("ARDOUGNE_EASY"));
    // Zero is a real reading and must survive. A tier with none done is a different
    // fact from a tier that was not read, and only the block's absence says the latter.
    assertEquals(Integer.valueOf(0), sent.get("ARDOUGNE_MEDIUM"));
  }

  @Test
  public void omitsDiaryTaskCountsWhenThereAreNone() {
    // Absent means "not observed". A present-but-empty block is treated as an erase by
    // the per-capability merge, which is the trap every other block here avoids too.
    Map<String, Object> none =
        SubmitEnvelope.body(
            "id", 1, "1.0.0", "2026-08-09T10:00:00Z", 42L, "Zezima",
            java.util.Collections.singletonMap("attack", 100),
            null, null, null, null, new java.util.LinkedHashMap<>());
    assertFalse(none.containsKey("diaryTasks"));

    Map<String, Object> nullCounts =
        SubmitEnvelope.body(
            "id", 1, "1.0.0", "2026-08-09T10:00:00Z", 42L, "Zezima",
            java.util.Collections.singletonMap("attack", 100),
            null, null, null, null, null);
    assertFalse(nullCounts.containsKey("diaryTasks"));
  }
}
