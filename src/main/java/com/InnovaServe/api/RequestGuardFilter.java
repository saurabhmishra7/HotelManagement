package com.InnovaServe.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded request buffers and per-instance abuse protection. Edge limits are still required. */
@Component
@Order(-110)
public class RequestGuardFilter extends OncePerRequestFilter {
  private final Clock clock;
  private final int authLimit;
  private final Map<String, Counter> counters = new HashMap<>();
  public RequestGuardFilter(Clock clock, @Value("${app.security.auth-requests-per-minute:30}") int authLimit) {
    this.clock = clock; this.authLimit = authLimit;
  }
  private record Counter(long minute, int count) {}
  synchronized boolean permit(String address) {
    long minute = clock.instant().getEpochSecond() / 60;
    counters.entrySet().removeIf(e -> e.getValue().minute() != minute);
    Counter old = counters.get(address);
    if (old == null && counters.size() >= 10000) return false;
    int count = old == null ? 1 : old.count() + 1;
    counters.put(address, new Counter(minute, count));
    return count <= authLimit;
  }
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    String path = req.getRequestURI();
    boolean sensitive = path.endsWith("/auth/login") || path.contains("/password-reset/")
        || path.equals("/api/v1/public/signup") || path.endsWith("/email/resend");
    String peer = req.getRemoteAddr();
    // The AppSail startup listener is the only local sender and replaces this internal header.
    if ("127.0.0.1".equals(peer) && System.getenv("X_ZOHO_CATALYST_LISTEN_PORT") != null) {
      String originalPeer = req.getHeader("X-InnovaServe-Proxy-Peer");
      if (originalPeer != null && !originalPeer.isBlank()) peer = originalPeer;
    }
    if (sensitive && "POST".equals(req.getMethod()) && !permit(peer)) {
      res.setHeader("Retry-After", "60"); reject(res, 429, "Too many requests. Try again shortly."); return;
    }
    if (!Set.of("POST", "PUT", "PATCH").contains(req.getMethod())) { chain.doFilter(req,res); return; }
    boolean multipart = req.getContentType() != null && req.getContentType().startsWith("multipart/");
    int limit = multipart ? 11 * 1024 * 1024 : 1024 * 1024;
    if (req.getContentLengthLong() > limit) { reject(res,413,"Request body is too large"); return; }
    // Multipart is streamed to disk by the servlet parser with its own configured limits.
    if (multipart) { chain.doFilter(req,res); return; }
    byte[] body = req.getInputStream().readNBytes(limit + 1);
    if (body.length > limit) { reject(res,413,"Request body is too large"); return; }
    chain.doFilter(new HttpServletRequestWrapper(req) {
      @Override public ServletInputStream getInputStream() {
        var input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
          public int read() { return input.read(); }
          public boolean isFinished() { return input.available() == 0; }
          public boolean isReady() { return true; }
          public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous request body"); }
        };
      }
      @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8)); }
    },res);
  }
  private static void reject(HttpServletResponse res, int code, String message) throws IOException {
    res.setStatus(code); res.setContentType("application/json");
    res.getWriter().write("{\"error\":\"RequestRejected\",\"message\":\""+message+"\",\"details\":{}}");
  }
}
