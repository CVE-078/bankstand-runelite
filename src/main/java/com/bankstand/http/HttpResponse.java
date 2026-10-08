package com.bankstand.http;

/**
 * An immutable HTTP response: the status code, the raw body string and, when the server
 * sent one, its {@code Retry-After} header.
 */
public final class HttpResponse {
  private final int status;
  private final String body;
  private final String retryAfter;

  public HttpResponse(int status, String body) {
    this(status, body, null);
  }

  public HttpResponse(int status, String body, String retryAfter) {
    this.status = status;
    this.body = body;
    this.retryAfter = retryAfter;
  }

  public int getStatus() {
    return status;
  }

  public String getBody() {
    return body;
  }

  /** The raw {@code Retry-After} header, or null when the response carried none. */
  public String getRetryAfter() {
    return retryAfter;
  }
}
