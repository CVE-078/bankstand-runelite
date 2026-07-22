package com.bankstand.http;

import java.io.IOException;
import java.util.Map;

/**
 * A minimal HTTP POST seam. Keeping the network behind this interface lets the
 * pairing logic be unit-tested with a fake, so the tests need no real socket and
 * no third-party test dependency. The real implementation ({@code OkHttpTransport})
 * is a thin wrapper over RuneLite's injected OkHttpClient.
 */
public interface HttpTransport {
  HttpResponse post(String url, String jsonBody, Map<String, String> headers) throws IOException;
}
