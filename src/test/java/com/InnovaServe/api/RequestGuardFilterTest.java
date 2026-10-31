package com.InnovaServe.api;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
class RequestGuardFilterTest {
  @Test void boundsAttemptsAndSeparatesAddresses() {
    var filter = new RequestGuardFilter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),2);
    assertTrue(filter.permit("a")); assertTrue(filter.permit("a")); assertFalse(filter.permit("a")); assertTrue(filter.permit("b"));
  }
  @Test void rejectsChunkedOversizedJson() throws Exception {
    var filter = new RequestGuardFilter(Clock.systemUTC(),30);
    var req = new MockHttpServletRequest("POST", "/api/v1/public/signup/status");
    req.setContentType("application/json"); req.setContent(new byte[1024*1024+1]);
    var res = new MockHttpServletResponse();
    filter.doFilter(req,res,(a,b)->fail("Oversize body reached controller"));
    assertEquals(413,res.getStatus());
  }
  @Test void bodyRemainsReadable() throws Exception {
    var filter = new RequestGuardFilter(Clock.systemUTC(),30);
    var req = new MockHttpServletRequest("POST", "/api/v1/payments"); req.setContent("{}".getBytes());
    filter.doFilter(req,new MockHttpServletResponse(),(a,b)-> assertEquals("{}",new String(a.getInputStream().readAllBytes())));
  }
}
