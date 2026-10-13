package com.bankstand;

import com.bankstand.dto.PairResponse;
import com.bankstand.dto.PresenceResponse;
import com.bankstand.dto.SubmitLootResponse;
import com.bankstand.dto.SubmitEventsResponse;
import com.bankstand.dto.SubmitResponse;
import com.bankstand.dto.SubmitSnapshotResponse;
import com.bankstand.http.HttpResponse;
import com.bankstand.http.HttpTransport;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * HTTP client for the Bankstand plugin API. Pairing failures collapse into one generic
 * {@link PairingException} and are never retried. Never log the device token or account hash.
 */
public class BankstandClient {

  private static final String PAIR_PATH = "/api/plugin/v1/pair";
  private static final String SUBMIT_PATH = "/api/plugin/v1/submit";
  private static final String EVENTS_PATH = "/api/plugin/v1/events";
  private static final String MANIFEST_PATH = "/api/plugin/v1/manifest";
  private static final String PRESENCE_PATH = "/api/plugin/v1/presence";
  private static final String LOOT_PATH = "/api/plugin/v1/loot";
  private static final String USER_AGENT = "Bankstand-RuneLite";
  private static final String GENERIC_FAILURE = "Pairing failed. Check the code and try again.";

  // The pause for a 429 without a Retry-After.
  static final long DEFAULT_RETRY_AFTER_MILLIS = 60_000L;

  private final HttpTransport transport;
  private final Gson gson;
  // Keeps explicit nulls: the server rejects a body missing a nullable field.
  private final Gson wire;
  private final ScheduledExecutorService executor;
  private final Supplier<Instant> clock;

  /** @param executor schedules retries; never blocked on, so it may be the caller's own. */
  public BankstandClient(HttpTransport transport, Gson gson, ScheduledExecutorService executor) {
    this(transport, gson, executor, Instant::now);
  }

  /** @param clock stamps {@code sentAt} on the requests that carry one; a test fixes it. */
  BankstandClient(
      HttpTransport transport,
      Gson gson,
      ScheduledExecutorService executor,
      Supplier<Instant> clock) {
    this.transport = transport;
    this.gson = gson;
    this.wire = gson.newBuilder().serializeNulls().create();
    this.executor = executor;
    this.clock = clock;
  }

  public PairResponse exchangePairingCode(String baseUrl, String rawCode) throws PairingException {
    String code = PairingCodes.normalize(rawCode);
    if (!PairingCodes.isValid(code)) {
      // Fail locally so a malformed code never burns a rate-limit slot.
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
      response = transport.post(url, wire.toJson(body), headers);
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
      response = transport.post(url, wire.toJson(body), headers);
    } catch (IOException e) {
      throw new SubmitException("Could not reach Bankstand.", true);
    }
    int status = response.getStatus();
    if (status != 200) {
      if (status == 401 || status == 403) {
        // Token invalid or revoked: terminal, point the user at re-pairing.
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

  /** Retries network, 429 and 5xx failures with backoff; other failures fail fast. */
  public CompletableFuture<SubmitResponse> submitIdentityWithRetry(
      String baseUrl,
      String deviceToken,
      long accountHash,
      String displayName,
      int maxAttempts,
      long baseDelayMillis) {
    return withRetry(
        () -> submitIdentity(baseUrl, deviceToken, accountHash, displayName),
        maxAttempts,
        baseDelayMillis);
  }

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
      response = transport.post(url, wire.toJson(envelopeBody), headers);
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

  public CompletableFuture<SubmitSnapshotResponse> submitSnapshotWithRetry(
      String baseUrl,
      String deviceToken,
      Map<String, Object> envelopeBody,
      int maxAttempts,
      long baseDelayMillis) {
    return withRetry(
        () -> submitSnapshot(baseUrl, deviceToken, envelopeBody), maxAttempts, baseDelayMillis);
  }

  /** Wire field names are a server contract; do not rename them. */
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
    // Stamped per attempt so the server can correct for this machine's clock.
    body.put("sentAt", clock.get().toString());
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    headers.put("User-Agent", USER_AGENT);
    headers.put("Authorization", "Bearer " + deviceToken);

    HttpResponse response;
    try {
      response = transport.post(url, wire.toJson(body), headers);
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

  public CompletableFuture<SubmitEventsResponse> submitEventsWithRetry(
      String baseUrl,
      String deviceToken,
      long accountHash,
      List<TransientEvent> events,
      int maxAttempts,
      long baseDelayMillis) {
    return withRetry(
        () -> submitEvents(baseUrl, deviceToken, accountHash, events), maxAttempts, baseDelayMillis);
  }

  /**
   * Sends one presence signal, single attempt. {@code elapsedMs} is how long it waited in
   * memory. {@code loot} says whether full loot is on; null leaves it out.
   */
  public PresenceResponse submitPresence(
      String baseUrl,
      String deviceToken,
      long accountHash,
      String state,
      long elapsedMs,
      Boolean loot)
      throws SubmitException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("accountHash", Long.toString(accountHash));
    body.put("state", state);
    body.put("elapsedMs", elapsedMs);
    if (loot != null) {
      body.put("loot", loot);
    }
    HttpResponse response = authorizedPost(baseUrl, PRESENCE_PATH, deviceToken, body);
    return parse(response, PresenceResponse.class);
  }

  /** Sends one account's loot records, single attempt; a 429 carries its Retry-After. */
  public SubmitLootResponse submitLoot(
      String baseUrl, String deviceToken, long accountHash, List<LootEvent> events)
      throws SubmitException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("accountHash", Long.toString(accountHash));
    body.put("sentAt", clock.get().toString());
    body.put("events", new ArrayList<>(events));
    HttpResponse response = authorizedPost(baseUrl, LOOT_PATH, deviceToken, body);
    return parse(response, SubmitLootResponse.class);
  }

  private HttpResponse authorizedPost(
      String baseUrl, String path, String deviceToken, Map<String, Object> body)
      throws SubmitException {
    if (isBlank(deviceToken)) {
      throw new SubmitException("Not paired.");
    }
    String url = trimTrailingSlash(baseUrl) + path;
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    headers.put("User-Agent", USER_AGENT);
    headers.put("Authorization", "Bearer " + deviceToken);

    HttpResponse response;
    try {
      response = transport.post(url, wire.toJson(body), headers);
    } catch (IOException e) {
      throw new SubmitException("Could not reach Bankstand.", true);
    }
    int status = response.getStatus();
    if (status == 200) {
      return response;
    }
    if (status == 401 || status == 403) {
      throw new SubmitException(
          "Bankstand rejected the device token. Re-pair in Account > Connect RuneLite.", false, true);
    }
    if (status == 429) {
      throw new SubmitException(
          "Bankstand is busy.", true, false, retryAfterMillis(response.getRetryAfter()));
    }
    if (status >= 500) {
      throw new SubmitException("Bankstand is busy.", true);
    }
    throw new SubmitException("Bankstand rejected the update.", false);
  }

  private <T> T parse(HttpResponse response, Class<T> type) throws SubmitException {
    try {
      T parsed = gson.fromJson(response.getBody(), type);
      if (parsed == null) {
        throw new SubmitException("Unexpected response from Bankstand.");
      }
      return parsed;
    } catch (JsonSyntaxException e) {
      throw new SubmitException("Unexpected response from Bankstand.");
    }
  }

  /** A {@code Retry-After} in seconds as millis, capped at an hour, else the default. */
  static long retryAfterMillis(String header) {
    if (header == null) {
      return DEFAULT_RETRY_AFTER_MILLIS;
    }
    try {
      long seconds = Long.parseLong(header.trim());
      if (seconds <= 0) {
        return DEFAULT_RETRY_AFTER_MILLIS;
      }
      return Math.min(seconds, 3600L) * 1000L;
    } catch (NumberFormatException e) {
      return DEFAULT_RETRY_AFTER_MILLIS;
    }
  }

  private interface SubmitCall<T> {
    T call() throws SubmitException;
  }

  static final long MAX_BACKOFF_MILLIS = 8_000L;

  /**
   * Full jitter over a doubling window, so clients that failed together do not retry
   * together. The randomness is a parameter so the backoff is testable.
   *
   * @param randomFraction a value in {@code [0, 1)}
   */
  static long backoffMillis(int attempt, long baseDelayMillis, double randomFraction) {
    long window = Math.min(MAX_BACKOFF_MILLIS, baseDelayMillis << (attempt - 1));
    return (long) (window * randomFraction);
  }

  /**
   * Retries are scheduled on {@link #executor}, never slept (Plugin Hub rejects
   * {@code Thread.sleep}). The first attempt runs on the calling thread, which must not be
   * the client thread.
   */
  private <T> CompletableFuture<T> withRetry(
      SubmitCall<T> call, int maxAttempts, long baseDelayMillis) {
    CompletableFuture<T> result = new CompletableFuture<>();
    attempt(call, 1, maxAttempts, baseDelayMillis, result);
    return result;
  }

  private <T> void attempt(
      SubmitCall<T> call,
      int attemptNumber,
      int maxAttempts,
      long baseDelayMillis,
      CompletableFuture<T> result) {
    try {
      result.complete(call.call());
    } catch (SubmitException e) {
      if (!e.isRetryable() || attemptNumber == maxAttempts) {
        result.completeExceptionally(e);
        return;
      }
      long delay =
          backoffMillis(attemptNumber, baseDelayMillis, ThreadLocalRandom.current().nextDouble());
      executor.schedule(
          () -> attempt(call, attemptNumber + 1, maxAttempts, baseDelayMillis, result),
          delay,
          TimeUnit.MILLISECONDS);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  /**
   * Returns null on any failure, never throws: a manifest outage must not break a paired
   * client, which falls back to its last good copy. Unauthenticated, since it may be needed
   * before pairing.
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
