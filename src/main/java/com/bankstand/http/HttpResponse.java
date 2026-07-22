package com.bankstand.http;

/** An immutable HTTP response: the status code and the raw body string. */
public final class HttpResponse {
  private final int status;
  private final String body;

  public HttpResponse(int status, String body) {
    this.status = status;
    this.body = body;
  }

  public int getStatus() {
    return status;
  }

  public String getBody() {
    return body;
  }
}
