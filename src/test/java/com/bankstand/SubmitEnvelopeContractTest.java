package com.bankstand;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class SubmitEnvelopeContractTest {
  private static JsonObject fixture(String name) throws Exception {
    try (Reader r =
        new InputStreamReader(
            SubmitEnvelopeContractTest.class.getResourceAsStream("/contracts/" + name),
            StandardCharsets.UTF_8)) {
      return new JsonParser().parse(r).getAsJsonObject();
    }
  }

  @Test
  public void javaEnvelopeMatchesTheFrozenValidFixture() throws Exception {
    JsonObject want = fixture("submit-v1.valid.json");
    Map<String, Integer> skills = new LinkedHashMap<>();
    skills.put("attack", 5500000);
    skills.put("slayer", 101333);
    // Build the envelope from the fixture's own field values.
    Map<String, Object> body =
        SubmitEnvelope.body(
            want.get("submissionId").getAsString(),
            want.get("schemaVersion").getAsInt(),
            want.get("pluginVersion").getAsString(),
            want.get("capturedAt").getAsString(),
            Long.parseLong(want.get("accountHash").getAsString()),
            want.get("displayName").getAsString(),
            skills);
    JsonObject got = new Gson().toJsonTree(body).getAsJsonObject();
    // JsonObject equality is order-insensitive, so this pins the shape, keys, and
    // value types against the frozen server contract.
    assertEquals(want, got);
  }

  @Test
  public void javaEnvelopeMatchesTheFrozenMinimalFixture() throws Exception {
    JsonObject want = fixture("submit-v1.minimal.json");
    Map<String, Object> body =
        SubmitEnvelope.body(
            want.get("submissionId").getAsString(),
            want.get("schemaVersion").getAsInt(),
            want.get("pluginVersion").getAsString(),
            want.get("capturedAt").getAsString(),
            Long.parseLong(want.get("accountHash").getAsString()),
            null,
            new LinkedHashMap<>());
    JsonObject got = new Gson().toJsonTree(body).getAsJsonObject();
    assertEquals(want, got);
  }

  @Test
  public void javaEnvelopeMatchesTheFrozenQuestsFixture() throws Exception {
    JsonObject want = fixture("submit-v1.quests.json");
    Map<String, Integer> skills = new LinkedHashMap<>();
    skills.put("attack", 5500000);
    Map<String, String> quests = new LinkedHashMap<>();
    quests.put("COOKS_ASSISTANT", "FINISHED");
    quests.put("DRAGON_SLAYER_I", "IN_PROGRESS");
    quests.put("THE_RESTLESS_GHOST", "NOT_STARTED");
    // Build the envelope from the fixture's own field values.
    Map<String, Object> body =
        SubmitEnvelope.body(
            want.get("submissionId").getAsString(),
            want.get("schemaVersion").getAsInt(),
            want.get("pluginVersion").getAsString(),
            want.get("capturedAt").getAsString(),
            Long.parseLong(want.get("accountHash").getAsString()),
            want.get("displayName").getAsString(),
            skills,
            quests);
    JsonObject got = new Gson().toJsonTree(body).getAsJsonObject();
    assertEquals(want, got);
  }

  @Test
  public void javaEnvelopeMatchesTheFrozenDiariesFixture() throws Exception {
    JsonObject want = fixture("submit-v1.diaries.json");
    Map<String, Integer> skills = new LinkedHashMap<>();
    skills.put("attack", 5500000);
    Map<String, String> diaries = new LinkedHashMap<>();
    diaries.put("ARDOUGNE_EASY", "COMPLETE");
    diaries.put("KOUREND_KEBOS_MEDIUM", "INCOMPLETE");
    diaries.put("WESTERN_PROVINCES_ELITE", "INCOMPLETE");
    // Build the envelope from the fixture's own field values.
    Map<String, Object> body =
        SubmitEnvelope.body(
            want.get("submissionId").getAsString(),
            want.get("schemaVersion").getAsInt(),
            want.get("pluginVersion").getAsString(),
            want.get("capturedAt").getAsString(),
            Long.parseLong(want.get("accountHash").getAsString()),
            want.get("displayName").getAsString(),
            skills,
            null,
            diaries);
    JsonObject got = new Gson().toJsonTree(body).getAsJsonObject();
    assertEquals(want, got);
  }

  @Test
  public void javaEnvelopeOmitsFullyEnumeratedOnAnOrdinaryPartialRead() throws Exception {
    JsonObject want = fixture("submit-v1.collectionlog-partial.json");
    Map<String, Integer> skills = new LinkedHashMap<>();
    skills.put("attack", 5500000);
    Map<String, Object> body =
        SubmitEnvelope.body(
            want.get("submissionId").getAsString(),
            want.get("schemaVersion").getAsInt(),
            want.get("pluginVersion").getAsString(),
            want.get("capturedAt").getAsString(),
            Long.parseLong(want.get("accountHash").getAsString()),
            want.get("displayName").getAsString(),
            skills,
            null,
            null,
            java.util.List.of(995, 1000),
            null,
            null,
            null,
            false);
    JsonObject got = new Gson().toJsonTree(body).getAsJsonObject();
    assertEquals(want, got);
  }

  @Test
  public void javaEnvelopeMarksAFullEnumerationOnlyWhenFlagged() throws Exception {
    JsonObject want = fixture("submit-v1.collectionlog-complete.json");
    Map<String, Integer> skills = new LinkedHashMap<>();
    skills.put("attack", 5500000);
    // Built via the fullest overload (#466): the guided Search read finished, so the
    // envelope carries collectionLogFullyEnumerated alongside the items it always sent.
    Map<String, Object> body =
        SubmitEnvelope.body(
            want.get("submissionId").getAsString(),
            want.get("schemaVersion").getAsInt(),
            want.get("pluginVersion").getAsString(),
            want.get("capturedAt").getAsString(),
            Long.parseLong(want.get("accountHash").getAsString()),
            want.get("displayName").getAsString(),
            skills,
            null,
            null,
            java.util.List.of(995, 1000, 6199),
            null,
            null,
            null,
            true);
    JsonObject got = new Gson().toJsonTree(body).getAsJsonObject();
    assertEquals(want, got);
  }
}
