package com.bankstand.http;

import static org.junit.Assert.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OkHttpTransportTest {

  private HttpServer server;
  private final AtomicInteger followed = new AtomicInteger();
  private String base;

  @Before
  public void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/moved",
        exchange -> {
          exchange.getResponseHeaders().add("Location", base + "/target");
          exchange.sendResponseHeaders(307, -1);
          exchange.close();
        });
    server.createContext(
        "/target",
        exchange -> {
          followed.incrementAndGet();
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @After
  public void stop() {
    server.stop(0);
  }

  @Test
  public void returnsARedirectInsteadOfFollowingIt() throws Exception {
    OkHttpTransport transport = new OkHttpTransport(new OkHttpClient());

    assertEquals(307, transport.post(base + "/moved", "{}", Collections.emptyMap()).getStatus());
    assertEquals(307, transport.post(base + "/moved", "", Collections.emptyMap(), 5_000L).getStatus());
    assertEquals(307, transport.get(base + "/moved", Collections.emptyMap()).getStatus());
    assertEquals("the body and headers never reach the redirect target", 0, followed.get());
  }
}
