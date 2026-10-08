package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.bankstand.dto.PresenceResponse;
import com.bankstand.dto.SubmitLootResponse;
import com.bankstand.http.HttpResponse;
import com.bankstand.http.HttpTransport;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.Test;

public class LiveSessionsClientTest {

  private static final class Fake implements HttpTransport {
    final HttpResponse response;
    String url;
    String body;
    Map<String, String> headers;

    Fake(HttpResponse response) {
      this.response = response;
    }

    @Override
    public HttpResponse post(String url, String jsonBody, Map<String, String> headers) {
      this.url = url;
      this.body = jsonBody;
      this.headers = headers;
      return response;
    }

    @Override
    public HttpResponse get(String url, Map<String, String> headers) {
      return response;
    }
  }

  private static BankstandClient client(Fake fake) {
    return new BankstandClient(
        fake,
        new Gson(),
        Executors.newSingleThreadScheduledExecutor(),
        () -> Instant.parse("2026-10-08T12:00:00Z"));
  }

  @Test
  public void presenceGoesToItsOwnRouteWithTheDeviceToken() throws Exception {
    Fake fake = new Fake(new HttpResponse(200, "{\"applied\":true}"));
    PresenceResponse res = client(fake).submitPresence("https://x.test/", "tok", 5L, "active", 0L, true);
    assertTrue(res.isApplied());
    assertEquals("https://x.test/api/plugin/v1/presence", fake.url);
    assertEquals("Bearer tok", fake.headers.get("Authorization"));
  }

  @Test
  public void presenceSendsTheLootFlagOnlyWhenGiven() throws Exception {
    Fake fake = new Fake(new HttpResponse(200, "{\"applied\":true}"));
    client(fake).submitPresence("https://x.test", "tok", 5L, "active", 0L, false);
    assertTrue(fake.body.contains("\"loot\":false"));
    client(fake).submitPresence("https://x.test", "tok", 5L, "logout", 0L, null);
    assertFalse(fake.body.contains("loot"));
  }

  @Test
  public void anUnappliedPresenceIsAnAnswerNotAFailure() throws Exception {
    Fake fake = new Fake(new HttpResponse(200, "{\"applied\":false,\"reason\":\"unclaimed\"}"));
    assertFalse(client(fake).submitPresence("https://x.test", "tok", 5L, "login", 0L, false).isApplied());
  }

  @Test
  public void lootGoesToItsOwnRoute() throws Exception {
    Fake fake =
        new Fake(new HttpResponse(200, "{\"acks\":[{\"id\":\"a\",\"outcome\":\"stored\"}]}"));
    SubmitLootResponse res =
        client(fake).submitLoot("https://x.test", "tok", 5L, Collections.emptyList());
    assertEquals("https://x.test/api/plugin/v1/loot", fake.url);
    assertTrue(res.getAcks().get(0).isStored());
  }

  @Test
  public void a429CarriesItsRetryAfter() {
    Fake fake = new Fake(new HttpResponse(429, "", "120"));
    try {
      client(fake).submitLoot("https://x.test", "tok", 5L, Collections.emptyList());
      fail("expected a SubmitException");
    } catch (SubmitException e) {
      assertTrue(e.isRetryable());
      assertEquals(120_000L, e.getRetryAfterMillis());
    }
  }

  @Test
  public void aMissingOrOddRetryAfterFallsBackToTheDefault() {
    assertEquals(
        BankstandClient.DEFAULT_RETRY_AFTER_MILLIS, BankstandClient.retryAfterMillis(null));
    assertEquals(
        BankstandClient.DEFAULT_RETRY_AFTER_MILLIS,
        BankstandClient.retryAfterMillis("Wed, 21 Oct 2026 07:28:00 GMT"));
    assertEquals(
        BankstandClient.DEFAULT_RETRY_AFTER_MILLIS, BankstandClient.retryAfterMillis("0"));
    assertEquals(3_600_000L, BankstandClient.retryAfterMillis("999999"));
  }

  @Test
  public void anOldServerWithoutTheRouteIsATerminalFailure() {
    Fake fake = new Fake(new HttpResponse(404, ""));
    try {
      client(fake).submitPresence("https://x.test", "tok", 5L, "login", 0L, false);
      fail("expected a SubmitException");
    } catch (SubmitException e) {
      assertFalse(e.isRetryable());
    }
  }

  @Test
  public void eventsCarryASentAtForTheServersClockCorrection() throws Exception {
    Fake fake = new Fake(new HttpResponse(200, "{\"routed\":true,\"acks\":[]}"));
    client(fake).submitEvents("https://x.test", "tok", 5L, Collections.emptyList());
    assertTrue(fake.body, fake.body.contains("\"sentAt\":\"2026-10-08T12:00:00Z\""));
  }

  /** A nullable field reaches the server as an explicit null, not omitted. */
  @Test
  public void aNullPayloadFieldIsSentAsNullNotDropped() throws Exception {
    Fake fake = new Fake(new HttpResponse(200, "{\"routed\":true,\"acks\":[]}"));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("petName", "Heron");
    payload.put("source", null);
    TransientEvent pet =
        new TransientEvent("p1", TransientEvent.TYPE_PET_DROP, "2026-10-08T12:00:00Z", payload);
    client(fake).submitEvents("https://x.test", "tok", 5L, Collections.singletonList(pet));
    assertTrue(fake.body, fake.body.contains("\"source\":null"));
  }
}
