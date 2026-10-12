package com.bankstand.http;

import java.io.IOException;
import java.util.Map;

/** A minimal HTTP seam, so the network logic can be tested with a fake. */
public interface HttpTransport {
  HttpResponse post(String url, String jsonBody, Map<String, String> headers) throws IOException;

  HttpResponse get(String url, Map<String, String> headers) throws IOException;
}
