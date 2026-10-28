package com.bankstand.http;

import java.io.IOException;
import java.util.Map;

/** A minimal HTTP seam, so the network logic can be tested with a fake. */
public interface HttpTransport {
  HttpResponse post(String url, String jsonBody, Map<String, String> headers) throws IOException;

  /** A post that gives up after {@code timeoutMillis} in total. */
  default HttpResponse post(
      String url, String jsonBody, Map<String, String> headers, long timeoutMillis)
      throws IOException {
    return post(url, jsonBody, headers);
  }

  HttpResponse get(String url, Map<String, String> headers) throws IOException;
}
