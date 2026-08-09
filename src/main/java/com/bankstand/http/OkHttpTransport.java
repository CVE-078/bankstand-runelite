package com.bankstand.http;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The real {@link HttpTransport}, a thin wrapper over RuneLite's shared
 * OkHttpClient. Pure I/O glue with no branching logic, so the pairing behaviour is
 * covered by {@code BankstandClientTest} against a fake transport instead.
 */
public class OkHttpTransport implements HttpTransport {

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private final OkHttpClient client;

  @Inject
  public OkHttpTransport(OkHttpClient client) {
    this.client = client;
  }

  @Override
  public HttpResponse post(String url, String jsonBody, Map<String, String> headers)
      throws IOException {
    Request.Builder builder =
        new Request.Builder().url(url).post(RequestBody.create(JSON, jsonBody));
    for (Map.Entry<String, String> header : headers.entrySet()) {
      builder.header(header.getKey(), header.getValue());
    }
    try (Response response = client.newCall(builder.build()).execute()) {
      ResponseBody body = response.body();
      return new HttpResponse(response.code(), body != null ? body.string() : "");
    }
  }

  @Override
  public HttpResponse get(String url, Map<String, String> headers) throws IOException {
    Request.Builder builder = new Request.Builder().url(url).get();
    for (Map.Entry<String, String> header : headers.entrySet()) {
      builder.header(header.getKey(), header.getValue());
    }
    try (Response response = client.newCall(builder.build()).execute()) {
      ResponseBody body = response.body();
      return new HttpResponse(response.code(), body != null ? body.string() : "");
    }
  }
}
