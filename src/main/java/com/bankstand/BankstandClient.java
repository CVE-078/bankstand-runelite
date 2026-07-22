package com.bankstand;

import com.bankstand.dto.PairResponse;
import com.bankstand.dto.SubmitResponse;
import com.bankstand.http.HttpResponse;
import com.bankstand.http.HttpTransport;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

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
      throw new SubmitException("Could not reach Bankstand.");
    }
    if (response.getStatus() != 200) {
      throw new SubmitException("Bankstand rejected the update.");
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

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String trimTrailingSlash(String url) {
    String trimmed = url == null ? "" : url.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
