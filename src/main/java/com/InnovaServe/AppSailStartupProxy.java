package com.InnovaServe;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Opens AppSail's public port before Spring initializes Flyway and JPA. Requests wait for Spring to
 * become ready, then pass through to its loopback-only Tomcat port.
 */
final class AppSailStartupProxy implements AutoCloseable {
  private static final Set<String> HOP_BY_HOP_HEADERS =
      Set.of(
          "connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te",
          "trailer", "transfer-encoding", "upgrade", "host", "content-length", "expect");
  private static final byte[] STARTING_BODY =
      "{\"status\":\"STARTING\"}".getBytes(StandardCharsets.UTF_8);
  private static final byte[] BACKEND_UNAVAILABLE_BODY =
      "{\"status\":\"UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8);
  static final String PEER_HEADER = "X-InnovaServe-Proxy-Peer";

  private final HttpServer server;
  private final HttpClient client;
  private final ExecutorService executor;
  private final CountDownLatch ready = new CountDownLatch(1);
  private final int internalPort;

  private AppSailStartupProxy(int publicPort, int internalPort) throws IOException {
    this.internalPort = internalPort;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", publicPort), 0);
    this.server.createContext("/", this::handle);
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
    this.server.setExecutor(executor);
    this.server.start();
    System.out.println("AppSail listener opened on port " + publicPort + "; starting Spring Boot");
  }

  static AppSailStartupProxy start(int publicPort) throws IOException {
    int internalPort;
    try (ServerSocket socket =
        new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
      internalPort = socket.getLocalPort();
    }
    return new AppSailStartupProxy(publicPort, internalPort);
  }

  int internalPort() {
    return internalPort;
  }

  void markReady() {
    ready.countDown();
    System.out.println("Spring Boot is ready; AppSail listener is forwarding requests");
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      try {
        if (!ready.await(25, TimeUnit.SECONDS)) {
          sendJson(exchange, 503, STARTING_BODY);
          return;
        }
        forward(exchange);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        sendJson(exchange, 503, STARTING_BODY);
      }
    }
  }

  private void forward(HttpExchange exchange) throws IOException, InterruptedException {
    URI target =
        URI.create("http://127.0.0.1:" + internalPort + exchange.getRequestURI().toASCIIString());
    HttpRequest.Builder request = HttpRequest.newBuilder(target);
    for (Map.Entry<String, List<String>> header : exchange.getRequestHeaders().entrySet()) {
      if (!HOP_BY_HOP_HEADERS.contains(header.getKey().toLowerCase(java.util.Locale.ROOT))
          && !header.getKey().equalsIgnoreCase(PEER_HEADER)) {
        for (String value : header.getValue()) {
          request.header(header.getKey(), value);
        }
      }
    }
    request.header(PEER_HEADER, exchange.getRemoteAddress().getAddress().getHostAddress());
    String method = exchange.getRequestMethod();
    boolean hasBody =
        !List.of("GET", "HEAD", "OPTIONS").contains(method)
            || exchange.getRequestHeaders().containsKey("Transfer-Encoding")
            || exchange.getRequestHeaders().getFirst("Content-Length") != null;
    request.method(
        method,
        hasBody
            ? HttpRequest.BodyPublishers.ofInputStream(exchange::getRequestBody)
            : HttpRequest.BodyPublishers.noBody());

    HttpResponse<InputStream> response;
    try {
      response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException exception) {
      sendJson(exchange, 502, BACKEND_UNAVAILABLE_BODY);
      return;
    }
    Headers responseHeaders = exchange.getResponseHeaders();
    response.headers().map().forEach((name, values) -> {
      if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
        responseHeaders.put(name, values);
      }
    });
    int status = response.statusCode();
    try (InputStream body = response.body()) {
      if (method.equals("HEAD") || status == 204 || status == 304) {
        exchange.sendResponseHeaders(status, -1);
      } else {
        exchange.sendResponseHeaders(status, 0);
        try (OutputStream output = exchange.getResponseBody()) {
          body.transferTo(output);
        }
      }
    }
  }

  private static void sendJson(HttpExchange exchange, int status, byte[] body) throws IOException {
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.getResponseHeaders().set("Retry-After", "2");
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(body);
    }
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }
}
