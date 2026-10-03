package com.InnovaServe.platform.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class RazorpayPaymentService {
  private final RestClient client;
  private final String keyId;
  private final String keySecret;

  public RazorpayPaymentService(
      @Value("${app.razorpay.key-id:}") String keyId,
      @Value("${app.razorpay.key-secret:}") String keySecret) {
    this.keyId = keyId;
    this.keySecret = keySecret;
    this.client = RestClient.builder()
        .baseUrl("https://api.razorpay.com/v1")
        .defaultHeaders(headers -> headers.setBasicAuth(keyId, keySecret))
        .build();
  }

  public boolean isConfigured() {
    return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
  }

  public String keyId() { return keyId; }

  @SuppressWarnings("unchecked")
  public Order createOrder(BigDecimal amount, String currency, String receipt) {
    requireConfigured();
    long amountMinor = amount.movePointRight(2).longValueExact();
    try {
      Map<String, Object> response = client.post().uri("/orders")
          .body(Map.of("amount", amountMinor, "currency", currency, "receipt", receipt))
          .retrieve().body(Map.class);
      if (response == null || response.get("id") == null)
        throw new IllegalStateException("Payment provider did not return an order ID");
      return new Order(response.get("id").toString(), keyId, amountMinor, currency);
    } catch (RestClientException exception) {
      throw new PaymentProviderUnavailableException();
    }
  }

  @SuppressWarnings("unchecked")
  public void requireCapturedPayment(
      String orderId, String paymentId, String signature, BigDecimal expectedAmount,
      String expectedCurrency) {
    requireConfigured();
    if (orderId == null || paymentId == null || signature == null
        || !validSignature(orderId, paymentId, signature))
      throw new IllegalArgumentException("Payment signature is invalid");
    try {
      Map<String, Object> payment = client.get().uri("/payments/{id}", paymentId)
          .retrieve().body(Map.class);
      if (payment == null
          || !orderId.equals(String.valueOf(payment.get("order_id")))
          || !"captured".equals(payment.get("status"))
          || !expectedCurrency.equals(payment.get("currency"))
          || !(payment.get("amount") instanceof Number amount)
          || amount.longValue() != expectedAmount.movePointRight(2).longValueExact())
        throw new IllegalArgumentException("Payment has not been captured for this signup order");
    } catch (RestClientException exception) {
      throw new PaymentProviderUnavailableException();
    }
  }

  private boolean validSignature(String orderId, String paymentId, String supplied) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
      String expected = java.util.HexFormat.of().formatHex(digest);
      return MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
    } catch (Exception exception) {
      throw new IllegalStateException("Could not verify payment signature", exception);
    }
  }

  private void requireConfigured() {
    if (!isConfigured()) throw new PaymentProviderUnavailableException();
  }

  public record Order(String orderId, String keyId, long amountMinor, String currency) {}

  public static class PaymentProviderUnavailableException extends RuntimeException {
    public PaymentProviderUnavailableException() {
      super("Online payments are not configured. Contact the platform team.");
    }
  }
}
