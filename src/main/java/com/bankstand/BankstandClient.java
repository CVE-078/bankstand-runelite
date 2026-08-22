package com.bankstand;

import com.bankstand.dto.PairResponse;
import com.bankstand.dto.SubmitEventsResponse;
import com.bankstand.dto.SubmitResponse;
import com.bankstand.dto.SubmitSnapshotResponse;
import com.bankstand.http.HttpResponse;
import com.bankstand.http.HttpTransport;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Talks to the Bankstand pairing endpoint. Given a raw pairing code and the
 * configured server base URL, it exchanges the code for a device token. All
 * failures collapse into a single generic {@link PairingException}: the server
 * returns one indistinguishable error for a wrong, expired, consumed or
 * rate-limited code, so the plugin never distinguishes them and never retry-loops.
 * The raw device token is returned to the caller once and must never be logged.
 */
public class BankstandClient {

  private static final String PAIR_PATH = "/api/plugin/v1/pair";
  private static final String SUBMIT_PATH = "/api/plugin/v1/submit";
  private static final String EVENTS_PATH = "/api/plugin/v1/events";
  private static final String MANIFEST_PATH = "/api/plugin/v1/manifest";
  private static final String USER_AGENT = "Bankstand-RuneLite";
  private static final String GENERIC_FAILURE = "Pairing failed. Check the code and try again.";

  private final HttpTransport transport;
  private final Gson gson;

  public BankstandClient(HttpTransport transport, Gson gson) {
    this.transport = transport;
    this.gson = gson;
  }

  public PairResponse exchangePairingCode(String baseUrl, String rawCode) throws PairingException {
    String code = PairingCodes.normalize(rawCode);
    if (!PairingCodes.isValid(code)) {
      // Fail before any request so an obviously malformed code never burns a rate-limit slot.
      throw new PairingException("Enter the 8-character code shown in Bankstand.");
    }

    String url = trimTrailingSlash(baseUrl) + PAIR_PATH;
    Map<String, String> body = new LinkedHashMap<>();
    body.put("code", code);
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    headers.put("User-Agent", USER_AGENT);

    HttpResponse response;
    try {
      response = transport.post(url, gson.toJson(body), headers);
    } catch (IOException e) {
      throw new PairingException("Could not reach Bankstand. Check your connection and try again.");
    }

    if (response.getStatus() != 200) {
      throw new PairingException(GENERIC_FAILURE);
    }

    PairResponse parsed;
    try {
      parsed = gson.fromJson(response.getBody(), PairResponse.class);
    } catch (JsonSyntaxException e) {
      throw new PairingException(GENERIC_FAILURE);
    }
    if (parsed == null || isBlank(parsed.getDeviceToken())) {
      throw new PairingException(GENERIC_FAILURE);
    }
    return parsed;
  }

  /**
   * Submits the logged-in character's account hash (and display name) to the
   * server, authenticated by the device token. The server records the client's
   * last-seen and, if the display name matches one of the user's tracked accounts,
   * links it and reports {@code verified}. Any failure is a generic
   * {@link SubmitException}; the token and account hash are never logged.
   */
  public SubmitResponse submitIdentity(
      String baseUrl, String deviceToken, long accountHash, String displayName)
      throws SubmitException {
    if (isBlank(deviceToken)) {
      throw new SubmitException("Not paired.");
    }
    String url = trimTrailingSlash(baseUrl) + SUBMIT_PATH;
    Map<String, String> body = new LinkedHashMap<>();
    // Sent as a decimal string: a 64-bit account hash does not fit a JSON number safely.
    body.put("accountHash", Long.toString(accountHash));
    if (!isBlank(displayName)) {
      body.put("displayName", displayName);
    }
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    headers.put("User-Agent", USER_AGENT);
    headers.put("Authorization", "Bearer " + deviceToken);

    HttpResponse response;
    try {
      response = transport.post(url, gson.toJson(body), headers);
    } catch (IOException e) {
      throw new SubmitException("Could not reach Bankstand.", true);
    }
    int status = response.getStatus();
    if (status != 200) {
      if (status == 401 || status == 403) {
        // The device token is invalid or revoked; retrying will not help. Point the
        // user at the one action that fixes it rather than looping.
        throw new SubmitException(
            "Bankstand rejected the device token. Re-pair in Account > Connect RuneLite.", false, true);
      }
      if (status == 429 || status >= 500) {
        throw new SubmitException("Bankstand is busy.", true);
      }
      throw new SubmitException("Bankstand rejected the update.", false);
    }
    try {
      SubmitResponse parsed = gson.fromJson(response.getBody(), SubmitResponse.class);
      if (parsed == null) {
        throw new SubmitException("Unexpected response from Bankstand.");
      }
      return parsed;
    } catch (JsonSyntaxException e) {
      throw new SubmitException("Unexpected response from Bankstand.");
    }
  }

  /**
   * Submits identity with a bounded retry. Retryable failures (network, 429, 5xx)
   * are retried up to {@code maxAttempts} with a linear backoff of
   * {@code baseDelayMillis * attempt}; terminal failures fail fast. Blocks the
   * calling thread during backoff, so it runs on the plugin's background executor.
   */
  public SubmitResponse submitIdentityWithRetry(
      String baseUrl,
      String deviceToken,
      long accountHash,
      String displayName,
      int maxAttempts,
      long baseDelayMillis)
      throws SubmitException {
    return withRetry(
        () -> submitIdentity(baseUrl, deviceToken, accountHash, displayName),
        maxAttempts,
        baseDelayMillis);
  }

  /**
   * Submits a v1 skills envelope (already built by SubmitEnvelope), authenticated by
   * the device token. Status handling mirrors submitIdentity: 401/403 terminal, 429/5xx
   * retryable, other non-200 terminal, IOException retryable. The token is never logged.
   */
  public SubmitSnapshotResponse submitSnapshot(
      String baseUrl, String deviceToken, Map<String, Object> envelopeBody)
      throws SubmitException {
    if (isBlank(deviceToken)) {
      throw new SubmitException("Not paired.");
    }
    String url = trimTrailingSlash(baseUrl) + SUBMIT_PATH;
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    headers.put("User-Agent", USER_AGENT);
    headers.put("Authorization", "Bearer " + deviceToken);

    HttpResponse response;
    try {
      response = transport.post(url, gson.toJson(envelopeBody), headers);
    } catch (IOException e) {
      throw new SubmitException("Could not reach Bankstand.", true);
    }
    int status = response.getStatus();
    if (status != 200) {
      if (status == 401 || status == 403) {
        throw new SubmitException(
            "Bankstand rejected the device token. Re-pair in Account > Connect RuneLite.", false, true);
      }
      if (status == 429 || status >= 500) {
        throw new SubmitException("Bankstand is busy.", true);
      }
      throw new SubmitException("Bankstand rejected the update.", false);
    }
    try {
      SubmitSnapshotResponse parsed =
          gson.fromJson(response.getBody(), SubmitSnapshotResponse.class);
      if (parsed == null) {
        throw new SubmitException("Unexpected response from Bankstand.");
      }
      return parsed;
    } catch (JsonSyntaxException e) {
      throw new SubmitException("Unexpected response from Bankstand.");
    }
  }

  /**
   * Submits a snapshot with the same bounded retry policy as
   * {@link #submitIdentityWithRetry}.
   */
  public SubmitSnapshotResponse submitSnapshotWithRetry(
      String baseUrl,
      String deviceToken,
      Map<String, Object> envelopeBody,
      int maxAttempts,
      long baseDelayMillis)
      throws SubmitException {
    return withRetry(
        () -> submitSnapshot(baseUrl, deviceToken, envelopeBody), maxAttempts, baseDelayMillis);
  }

  /**
   * Submits a batch of {@link TransientEvent}s for one account hash.
   * Status handling mirrors {@link #submitSnapshot}: 401/403 terminal, 429/5xx
   * retryable, other non-200 terminal, IOException retryable. The events field
   * names are exactly what {@code lib/plugin/events-envelope.ts} validates.
   */
  public SubmitEventsResponse submitEvents(
      String baseUrl, String deviceToken, long accountHash, List<TransientEvent> events)
      throws SubmitException {
    if (isBlank(deviceToken)) {
      throw new SubmitException("Not paired.");
    }
    String url = trimTrailingSlash(baseUrl) + EVENTS_PATH;
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("accountHash", Long.toString(accountHash));
    List<Map<String, Object>> wireEvents = new ArrayList<>(events.size());
    for (TransientEvent event : events) {
      Map<String, Object> wire = new LinkedHashMap<>();
      wire.put("id", event.getId());
      wire.put("type", event.getType());
      wire.put("occurredAt", event.getOccurredAt());
      wire.put("payload", event.getPayload());
      wireEvents.add(wire);
    }
    body.put("events", wireEvents);
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    headers.put("User-Agent", USER_AGENT);
    headers.put("Authorization", "Bearer " + deviceToken);

    HttpResponse response;
    try {
      response = transport.post(url, gson.toJson(body), headers);
    } catch (IOException e) {
      throw new SubmitException("Could not reach Bankstand.", true);
    }
    int status = response.getStatus();
    if (status != 200) {
      if (status == 401 || status == 403) {
        throw new SubmitException(
            "Bankstand rejected the device token. Re-pair in Account > Connect RuneLite.", false, true);
      }
      if (status == 429 || status >= 500) {
        throw new SubmitException("Bankstand is busy.", true);
      }
      throw new SubmitException("Bankstand rejected the update.", false);
    }
    try {
      SubmitEventsResponse parsed = gson.fromJson(response.getBody(), SubmitEventsResponse.class);
      if (parsed == null) {
        throw new SubmitException("Unexpected response from Bankstand.");
      }
      return parsed;
    } catch (JsonSyntaxException e) {
      throw new SubmitException("Unexpected response from Bankstand.");
    }
  }

  /** Submits an events batch with the same bounded retry policy as
   *  {@link #submitIdentityWithRetry}. */
  public SubmitEventsResponse submitEventsWithRetry(
      String baseUrl,
      String deviceToken,
      long accountHash,
      List<TransientEvent> events,
      int maxAttempts,
      long baseDelayMillis)
      throws SubmitException {
    return withRetry(
        () -> submitEvents(baseUrl, deviceToken, accountHash, events), maxAttempts, baseDelayMillis);
  }

  /** A retryable unit of work that produces a {@code T} or throws {@link SubmitException}. */
  private interface SubmitCall<T> {
    T call() throws SubmitException;
  }

  /** Longest a single backoff waits, whatever the attempt number. */
  static final long MAX_BACKOFF_MILLIS = 8_000L;

  /**
   * Full jitter over a doubling window: a uniform pick from {@code [0, window)} rather
   * than the window itself, so clients that failed together do not return together.
   *
   * <p>Takes the randomness rather than drawing it, because a backoff only ever observed
   * as a sleep is a backoff nobody can test.
   *
   * @param randomFraction a value in {@code [0, 1)}
   */
  static long backoffMillis(int attempt, long baseDelayMillis, double randomFraction) {
    long window = Math.min(MAX_BACKOFF_MILLIS, baseDelayMillis << (attempt - 1));
    return (long) (window * randomFraction);
  }

  /**
   * Generic bounded retry shared by every submit endpoint: retryable failures are
   * retried up to {@code maxAttempts}, terminal ones fail fast. Blocks the calling
   * thread during backoff, so it runs on the plugin's background executor.
   */
  private static <T> T withRetry(SubmitCall<T> call, int maxAttempts, long baseDelayMillis)
      throws SubmitException {
    SubmitException last = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return call.call();
      } catch (SubmitException e) {
        last = e;
        if (!e.isRetryable() || attempt == maxAttempts) {
          throw e;
        }
        sleep(backoffMillis(attempt, baseDelayMillis, ThreadLocalRandom.current().nextDouble()));
      }
    }
    // Unreachable for maxAttempts >= 1: the final attempt always returns or throws.
    throw last;
  }

  private static void sleep(long millis) {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      // A shutdown interrupt: stop backing off but let the bounded loop finish.
      Thread.currentThread().interrupt();
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  /**
   * Fetches the capability manifest, or returns null if anything at all goes wrong.
   *
   * <p>Null, never an exception. The manifest is an optimisation: it tells the client
   * which capabilities are worth uploading. A client that cannot fetch one keeps its last
   * known good copy, or the compiled-in bundle, and carries on working. A manifest outage
   * must never take a paired client down with it.
   *
   * <p>Unauthenticated, because the document is public and inert and a client may need it
   * before it has a device token.
   */
  public CapabilityManifest.RawManifest fetchManifest(String baseUrl) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Accept", "application/json");
    headers.put("User-Agent", USER_AGENT);
    try {
      HttpResponse response = transport.get(trimTrailingSlash(baseUrl) + MANIFEST_PATH, headers);
      if (response.getStatus() != 200) {
        return null;
      }
      return gson.fromJson(response.getBody(), CapabilityManifest.RawManifest.class);
    } catch (IOException | JsonSyntaxException e) {
      return null;
    }
  }

  private static String trimTrailingSlash(String url) {
    String trimmed = url == null ? "" : url.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
