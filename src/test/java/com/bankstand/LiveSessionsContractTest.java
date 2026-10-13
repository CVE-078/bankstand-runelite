package com.bankstand;

import static org.junit.Assert.assertEquals;

import com.bankstand.http.HttpResponse;
import com.bankstand.http.HttpTransport;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.Test;

/** Request bodies captured off the real client and compared with the server's fixtures. */
public class LiveSessionsContractTest {

  private static final String OK = "{\"applied\":true,\"acks\":[],\"routed\":true}";
  private static final Instant SENT_AT = Instant.parse("2026-10-08T12:01:00Z");

  private static final class Capture implements HttpTransport {
    String body;

    @Override
    public HttpResponse post(String url, String jsonBody, Map<String, String> headers) {
      body = jsonBody;
      return new HttpResponse(200, OK);
    }

    @Override
    public HttpResponse get(String url, Map<String, String> headers) {
      return new HttpResponse(200, OK);
    }
  }

  private static JsonObject fixture(String name) throws Exception {
    try (Reader r =
        new InputStreamReader(
            LiveSessionsContractTest.class.getResourceAsStream("/contracts/" + name),
            StandardCharsets.UTF_8)) {
      return new JsonParser().parse(r).getAsJsonObject();
    }
  }

  private static BankstandClient client(Capture capture) {
    return new BankstandClient(
        capture, new Gson(), Executors.newSingleThreadScheduledExecutor(), () -> SENT_AT);
  }

  private static JsonObject sent(Capture capture) {
    return new JsonParser().parse(capture.body).getAsJsonObject();
  }

  @Test
  public void presenceMatchesTheFixture() throws Exception {
    Capture capture = new Capture();
    client(capture).submitPresence("https://x.test", "tok", 123456789012345L, "logout", 180_000L, null);
    assertEquals(fixture("presence-v1.logout.json"), sent(capture));
  }

  @Test
  public void activePresenceCarriesTheLootFlag() throws Exception {
    Capture capture = new Capture();
    client(capture).submitPresence("https://x.test", "tok", 123456789012345L, "active", 0L, true);
    assertEquals(fixture("presence-v1.active.json"), sent(capture));
  }

  @Test
  public void lootMatchesTheFixture() throws Exception {
    Capture capture = new Capture();
    LootEvent event =
        new LootEvent(
            "018f9c8e-7b7a-7c00-8000-000000000101",
            1047,
            "Cave horror",
            3,
            "2026-10-08T12:00:00Z",
            "2026-10-08T12:00:48Z",
            Arrays.asList(new LootEvent.Item(995, 1200), new LootEvent.Item(5304, 3)));
    client(capture)
        .submitLoot("https://x.test", "tok", 123456789012345L, Collections.singletonList(event));
    assertEquals(fixture("loot-v1.valid.json"), sent(capture));
  }

  @Test
  public void slayerCompletionMatchesTheFixtureNullsIncluded() throws Exception {
    Capture capture = new Capture();
    TransientEvent event =
        new TransientEvent(
            "018f9c8e-7b7a-7c00-8000-000000000201",
            TransientEvent.TYPE_SLAYER_TASK_COMPLETED,
            "2026-10-08T12:00:30Z",
            SlayerTaskCompletionCapture.payload("Cave horrors", 120, 15, 1234, 56));
    client(capture)
        .submitEvents("https://x.test", "tok", 123456789012345L, Collections.singletonList(event));
    assertEquals(fixture("events-v1.slayer-task-completed.json"), sent(capture));
  }

  @Test
  public void anActiveSlayerTaskBlockMatchesTheFixture() throws Exception {
    assertSlayerFixture(
        "submit-v1.slayer-task.json",
        "018f9c8e-7b7a-7c00-8000-000000000301",
        new SlayerTask(true, "Cave horrors", 40, 120, null, 1234, 56));
  }

  @Test
  public void noSlayerTaskMatchesTheFixture() throws Exception {
    assertSlayerFixture(
        "submit-v1.slayer-task-none.json", "018f9c8e-7b7a-7c00-8000-000000000302", SlayerTask.NONE);
  }

  private static void assertSlayerFixture(String name, String id, SlayerTask task)
      throws Exception {
    Capture capture = new Capture();
    Map<String, Integer> skills = new LinkedHashMap<>();
    skills.put("slayer", 101333);
    Map<String, Object> body =
        SubmitEnvelope.body(
            id, 1, "1.0.0", "2026-10-08T12:00:00.000Z", 123456789012345L, null, skills, null,
            null, null, null, null, null, false, null, task.toWire());
    client(capture).submitSnapshot("https://x.test", "tok", body);
    assertEquals(fixture(name), sent(capture));
  }
}
