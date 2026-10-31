package com.InnovaServe.api;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityIT {
  @Autowired MockMvc mvc;
  @Test void healthIsPublicAndMinimal() throws Exception {
    mvc.perform(get("/health").secure(true)).andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("UP"))
      .andExpect(header().string("X-Content-Type-Options", "nosniff"))
      .andExpect(header().string("X-Frame-Options", "DENY"))
      .andExpect(header().exists("Strict-Transport-Security"))
      .andExpect(header().exists("Content-Security-Policy"));
  }
  @Test void unauthenticatedBillingIs401() throws Exception {
    mvc.perform(get("/api/v1/invoices/00000000-0000-0000-0000-000000000001")).andExpect(status().isUnauthorized());
  }
  @WithMockUser @Test void insufficientPermissionIs403() throws Exception {
    mvc.perform(get("/api/v1/invoices/00000000-0000-0000-0000-000000000001")).andExpect(status().isForbidden());
  }
  @WithMockUser(authorities="PERM_PAYMENT_RECORD") @Test void invalidPaymentIs400() throws Exception {
    mvc.perform(post("/api/v1/payments").contentType("application/json").content("{\"mode\":\"cash\",\"amount\":-1}"))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("InvalidRequest"));
  }
  @WithMockUser(authorities="PERM_PAYMENT_RECORD") @Test void malformedJsonIsSanitized() throws Exception {
    mvc.perform(post("/api/v1/payments").contentType("application/json").content("{"))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Check the required fields, values and request format"));
  }
  @Test void disallowedOriginIsRejected() throws Exception {
    mvc.perform(options("/api/v1/payments").header("Origin", "https://evil.example")
      .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
  }
  @Test void allowedOriginCanPreflight() throws Exception {
    mvc.perform(options("/api/v1/payments").header("Origin", "https://staff.example.com")
      .header("Access-Control-Request-Method", "POST")).andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "https://staff.example.com"));
  }
}
