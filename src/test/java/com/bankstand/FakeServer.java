package com.bankstand;

import com.bankstand.http.HttpResponse;
import com.bankstand.http.HttpTransport;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The server's capture rule, as a transport: a stamped upload is taken only under the current
 * generation, an unstamped one only before the first removal. Records what it accepted.
 */
final class FakeServer implements HttpTransport {
  final Map<Long, Integer> generation = new HashMap<>();
  final List<String> accepted = new ArrayList<>();
  final List<String> refused = new ArrayList<>();
  // Runs inside a request before the server reads anything: a removal "while in flight".
  Runnable beforeRead = () -> {};
  // Runs after the server read its answer and before it returns: a removal "after the read".
  Runnable afterRead = () -> {};
  boolean offline;
  // An older server: names no generation.
  boolean older;
  int identityCalls;

  int generationOf(long account) {
    return generation.getOrDefault(account, 1);
  }

  /** What a removal on the website does. */
  void remove(long account) {
    generation.put(account, generationOf(account) + 1);
  }

  @Override
  public HttpResponse post(String url, String jsonBody, Map<String, String> headers)
      throws IOException {
    if (offline) {
      throw new SocketTimeoutException("timeout");
    }
    beforeRead.run();
    JsonObject body = new JsonParser().parse(jsonBody).getAsJsonObject();
    long account = Long.parseLong(body.get("accountHash").getAsString());
    int current = generationOf(account);
    Integer stamp = body.has("captureGeneration") ? body.get("captureGeneration").getAsInt() : null;
    boolean ok = stamp == null ? current == 1 : stamp == current;
    afterRead.run();
    JsonObject res = new JsonObject();
    if (!older) {
      res.addProperty("captureGeneration", current);
    }
    if (url.endsWith("/submit")) {
      identityCalls++;
      res.addProperty("verified", true);
      res.addProperty("outcome", "bound");
    } else if (url.endsWith("/presence")) {
      record(body.get("state").getAsString(), ok);
      res.addProperty("applied", ok);
      if (!ok) {
        res.addProperty("reason", "stale_generation");
      }
    } else {
      JsonArray acks = new JsonArray();
      for (JsonElement e : body.getAsJsonArray("events")) {
        String id = e.getAsJsonObject().get("id").getAsString();
        record(id, ok);
        JsonObject ack = new JsonObject();
        ack.addProperty("id", id);
        ack.addProperty("outcome", ok ? "stored" : "rejected");
        if (!ok) {
          ack.addProperty("reason", "stale_generation");
        }
        acks.add(ack);
      }
      res.addProperty("routed", true);
      res.add("acks", acks);
    }
    return new HttpResponse(200, res.toString());
  }

  private void record(String id, boolean ok) {
    (ok ? accepted : refused).add(id);
  }

  @Override
  public HttpResponse get(String url, Map<String, String> headers) {
    return new HttpResponse(404, "");
  }
}
