package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.bankstand.dto.PairResponse;
import com.bankstand.dto.SubmitResponse;
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
