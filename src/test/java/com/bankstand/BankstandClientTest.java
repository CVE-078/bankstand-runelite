package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.bankstand.dto.PairResponse;
import com.bankstand.dto.SubmitResponse;
import com.bankstand.dto.SubmitSnapshotResponse;
import com.bankstand.http.HttpResponse;
import com.bankstand.http.HttpTransport;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.Map;
import org.junit.Test;

public class BankstandClientTest {

  private static final String BASE = "https://example.test";

  /** Records the request and returns a canned response, or throws, without a socket. */
  private static final class FakeTransport implements HttpTransport {
    String url;
    String body;
    Map<String, String> headers;
    boolean called;
    private final HttpResponse response;
    private final IOException toThrow;

    FakeTransport(HttpResponse response) {
      this(response, null);
    }

    FakeTransport(HttpResponse response, IOException toThrow) {
      this.response = response;
      this.toThrow = toThrow;
    }

    @Override
    public HttpResponse post(String url, String jsonBody, Map<String, String> headers)
        throws IOException {
      this.called = true;
      this.url = url;
      this.body = jsonBody;
      this.headers = headers;
      if (toThrow != null) {
        throw toThrow;
      }
      return response;
    }
  }

  /** Returns a scripted sequence of responses/errors and counts calls, for retry tests. */
  private static final class SequencedTransport implements HttpTransport {
    private final java.util.Deque<Object> steps = new java.util.ArrayDeque<>();
    int calls;

    SequencedTransport(Object... steps) {
      java.util.Collections.addAll(this.steps, steps);
    }

    @Override
    public HttpResponse post(String url, String jsonBody, Map<String, String> headers)
        throws IOException {
      calls++;
      Object step = steps.isEmpty() ? null : steps.poll();
      if (step instanceof IOException) {
        throw (IOException) step;
      }
      if (step instanceof HttpResponse) {
        return (HttpResponse) step;
      }
      throw new IllegalStateException("no scripted response for call " + calls);
    }
  }

  private static BankstandClient client(FakeTransport transport) {
    return new BankstandClient(transport, new Gson());
  }

  @Test
  public void pairsOnSuccessAndSendsTheNormalizedCode() throws Exception {
    FakeTransport t =
        new FakeTransport(
            new HttpResponse(
                200,
                "{\"deviceToken\":\"bsd_abc\",\"deviceId\":\"d1\","
                    + "\"expiresAt\":\"2026-10-01T00:00:00.000Z\"}"));

    PairResponse res = client(t).exchangePairingCode(BASE + "/", "abcd-efgh");

    assertEquals("bsd_abc", res.getDeviceToken());
    assertEquals("d1", res.getDeviceId());
    assertEquals("2026-10-01T00:00:00.000Z", res.getExpiresAt());
    assertEquals(BASE + "/api/plugin/v1/pair", t.url); // trailing slash trimmed
    assertTrue("normalized code in body", t.body.contains("\"ABCDEFGH\""));
    assertEquals("application/json", t.headers.get("Content-Type"));
    assertTrue(t.headers.get("User-Agent").startsWith("Bankstand-RuneLite"));
  }

  @Test
  public void rejectsAnInvalidCodeBeforeAnyRequest() {
    FakeTransport t = new FakeTransport(new HttpResponse(200, "{}"));
    try {
      client(t).exchangePairingCode(BASE, "short");
      fail("expected PairingException");
    } catch (PairingException e) {
      assertFalseCalled(t);
    }
  }

  @Test
  public void mapsA400ToAGenericFailure() {
    FakeTransport t = new FakeTransport(new HttpResponse(400, "{\"error\":\"invalid or expired code\"}"));
    assertGenericFailure(t);
  }

  @Test
  public void mapsA500ToTheSameGenericFailure() {
    FakeTransport t = new FakeTransport(new HttpResponse(500, "{\"error\":\"pairing failed\"}"));
    assertGenericFailure(t);
  }

  @Test
  public void mapsMalformedSuccessBodyToAFailure() {
    FakeTransport t = new FakeTransport(new HttpResponse(200, "not json"));
    assertGenericFailure(t);
  }

  @Test
  public void mapsAMissingTokenToAFailure() {
    FakeTransport t = new FakeTransport(new HttpResponse(200, "{\"deviceId\":\"d1\"}"));
    assertGenericFailure(t);
  }

  @Test
  public void mapsANetworkErrorToAFailure() {
    FakeTransport t = new FakeTransport(null, new IOException("boom"));
    try {
      client(t).exchangePairingCode(BASE, "ABCD-EFGH");
      fail("expected PairingException");
    } catch (PairingException e) {
      assertTrue(t.called);
    }
  }

  @Test
  public void submitsIdentityWithBearerTokenAndAccountHash() throws Exception {
    FakeTransport t =
        new FakeTransport(new HttpResponse(200, "{\"verified\":true,\"linkedRsn\":\"Zezima\"}"));

    SubmitResponse res = client(t).submitIdentity(BASE + "/", "bsd_tok", 123456789012345L, "Zezima");

    assertTrue(res.isVerified());
    assertEquals("Zezima", res.getLinkedRsn());
    assertEquals(BASE + "/api/plugin/v1/submit", t.url);
    assertTrue("account hash as a string in body", t.body.contains("\"123456789012345\""));
    assertTrue("display name in body", t.body.contains("\"Zezima\""));
    assertEquals("Bearer bsd_tok", t.headers.get("Authorization"));
  }

  @Test
  public void submitReportsNotVerifiedWhenNothingMatched() throws Exception {
    FakeTransport t =
        new FakeTransport(new HttpResponse(200, "{\"verified\":false,\"linkedRsn\":null}"));
    SubmitResponse res = client(t).submitIdentity(BASE, "bsd_tok", 1L, "Nobody");
    assertFalse(res.isVerified());
    assertNull(res.getLinkedRsn());
  }

  @Test
  public void submitWithoutATokenFailsBeforeAnyRequest() {
    FakeTransport t = new FakeTransport(new HttpResponse(200, "{}"));
    try {
      client(t).submitIdentity(BASE, "", 1L, "Zezima");
      fail("expected SubmitException");
    } catch (SubmitException e) {
      assertFalseCalled(t);
    }
  }

  @Test
  public void submitMapsA401ToAFailure() {
    FakeTransport t = new FakeTransport(new HttpResponse(401, "{\"error\":\"unauthorized\"}"));
    try {
      client(t).submitIdentity(BASE, "bsd_tok", 1L, "Zezima");
      fail("expected SubmitException");
    } catch (SubmitException e) {
      assertTrue(t.called);
    }
  }

  @Test
  public void submitMapsANetworkErrorToAFailure() {
    FakeTransport t = new FakeTransport(null, new IOException("boom"));
    try {
      client(t).submitIdentity(BASE, "bsd_tok", 1L, "Zezima");
      fail("expected SubmitException");
    } catch (SubmitException e) {
      assertTrue(t.called);
    }
  }

  @Test
  public void retriesARetryableFailureThenSucceeds() throws Exception {
    SequencedTransport t =
        new SequencedTransport(
            new IOException("blip"),
            new HttpResponse(200, "{\"verified\":true,\"linkedRsn\":\"Zezima\"}"));

    SubmitResponse res =
        new BankstandClient(t, new Gson())
            .submitIdentityWithRetry(BASE, "bsd_tok", 1L, "Zezima", 3, 0L);

    assertTrue(res.isVerified());
    assertEquals("succeeded on the second attempt", 2, t.calls);
  }

  @Test
  public void aTerminalFailureIsNotRetried() {
    SequencedTransport t =
        new SequencedTransport(new HttpResponse(401, "{\"error\":\"unauthorized\"}"));
    try {
      new BankstandClient(t, new Gson())
          .submitIdentityWithRetry(BASE, "bsd_tok", 1L, "Zezima", 3, 0L);
      fail("expected SubmitException");
    } catch (SubmitException e) {
      assertEquals("terminal failure fails fast without retrying", 1, t.calls);
    }
  }

  @Test
  public void retryableFailuresStopAtTheAttemptCap() {
    SequencedTransport t =
        new SequencedTransport(
            new IOException("a"),
            new IOException("b"),
            new IOException("c"),
            new IOException("d"));
    try {
      new BankstandClient(t, new Gson())
          .submitIdentityWithRetry(BASE, "bsd_tok", 1L, "Zezima", 3, 0L);
      fail("expected SubmitException");
    } catch (SubmitException e) {
      assertEquals("stopped at maxAttempts", 3, t.calls);
      assertTrue("an exhausted retryable failure stays retryable", e.isRetryable());
    }
  }

  @Test
  public void classifiesANetworkErrorAsRetryable() {
    FakeTransport t = new FakeTransport(null, new IOException("boom"));
    try {
      client(t).submitIdentity(BASE, "bsd_tok", 1L, "Zezima");
      fail("expected SubmitException");
    } catch (SubmitException e) {
      assertTrue(e.isRetryable());
    }
  }

  @Test
  public void classifiesAServerErrorAsRetryable() {
    FakeTransport t = new FakeTransport(new HttpResponse(503, "{\"error\":\"busy\"}"));
    try {
      client(t).submitIdentity(BASE, "bsd_tok", 1L, "Zezima");
      fail("expected SubmitException");
    } catch (SubmitException e) {
      assertTrue(e.isRetryable());
    }
  }

  @Test
  public void classifiesARevokedTokenAsTerminal() {
    FakeTransport t = new FakeTransport(new HttpResponse(401, "{\"error\":\"unauthorized\"}"));
    try {
      client(t).submitIdentity(BASE, "bsd_tok", 1L, "Zezima");
      fail("expected SubmitException");
    } catch (SubmitException e) {
      assertFalse(e.isRetryable());
    }
  }

  @Test
  public void submitsASnapshotWithBearerAndEnvelope() throws Exception {
    FakeTransport t =
        new FakeTransport(
            new HttpResponse(
                200,
                "{\"accepted\":true,\"stored\":false,\"reason\":\"not_applied\","
                    + "\"eventsCreated\":0,\"serverTime\":\"2026-07-23T10:00:00.000Z\","
                    + "\"nextSubmitAfter\":\"2026-07-23T10:01:00.000Z\"}"));
    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("submissionId", "018f9c8e-7b7a-7c00-8000-000000000abc");
    body.put("schemaVersion", 1);

    SubmitSnapshotResponse res =
        new BankstandClient(t, new Gson())
            .submitSnapshot(BASE + "/", "bsd_tok", body);

    assertTrue(res.isAccepted());
    assertFalse(res.isStored());
    assertEquals("not_applied", res.getReason());
    assertEquals(BASE + "/api/plugin/v1/submit", t.url);
    assertEquals("Bearer bsd_tok", t.headers.get("Authorization"));
    assertTrue("envelope serialized in body", t.body.contains("\"submissionId\""));
  }

  @Test
  public void submitSnapshotWithoutTokenFailsBeforeAnyRequest() {
    FakeTransport t = new FakeTransport(new HttpResponse(200, "{}"));
    try {
      new BankstandClient(t, new Gson())
          .submitSnapshot(BASE, "", new java.util.LinkedHashMap<>());
      fail("expected SubmitException");
    } catch (SubmitException e) {
      assertFalseCalled(t);
    }
  }

  @Test
  public void submitSnapshotRetriesARetryableFailureThenSucceeds() throws Exception {
    SequencedTransport t =
        new SequencedTransport(
            new IOException("blip"),
            new HttpResponse(200, "{\"accepted\":true,\"stored\":true,\"reason\":\"persisted\"}"));
    SubmitSnapshotResponse res =
        new BankstandClient(t, new Gson())
            .submitSnapshotWithRetry(BASE, "bsd_tok", new java.util.LinkedHashMap<>(), 3, 0L);
    assertTrue(res.isStored());
    assertEquals(2, t.calls);
  }

  @Test
  public void submitSnapshotMapsA401ToATerminalFailure() {
    FakeTransport t = new FakeTransport(new HttpResponse(401, "{\"error\":\"unauthorized\"}"));
    try {
      new BankstandClient(t, new Gson())
          .submitSnapshotWithRetry(BASE, "bsd_tok", new java.util.LinkedHashMap<>(), 3, 0L);
      fail("expected SubmitException");
    } catch (SubmitException e) {
      assertFalse(e.isRetryable());
    }
  }

  private static void assertGenericFailure(FakeTransport t) {
    try {
      client(t).exchangePairingCode(BASE, "ABCD-EFGH");
      fail("expected PairingException");
    } catch (PairingException e) {
      assertTrue(e.getMessage().toLowerCase().contains("pairing failed"));
    }
  }

  private static void assertFalseCalled(FakeTransport t) {
    assertNull(t.url);
    org.junit.Assert.assertFalse(t.called);
  }
}
