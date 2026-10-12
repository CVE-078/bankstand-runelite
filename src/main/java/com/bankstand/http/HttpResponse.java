package com.bankstand.http;

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
