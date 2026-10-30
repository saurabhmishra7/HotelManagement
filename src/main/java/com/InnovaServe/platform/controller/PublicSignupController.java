package com.InnovaServe.platform.controller;

import com.InnovaServe.platform.service.PublicSignupService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicSignupController {
  private final PublicSignupService signups;

  public PublicSignupController(PublicSignupService signups) {
    this.signups = signups;
  }

  @GetMapping("/signup-mode")
  public Map<String, Object> signupMode() {
    return signups.signupMode();
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> signup(
      @RequestBody PublicSignupService.SignupRequest request, HttpServletRequest servletRequest) {
    return signups.signup(request, servletRequest.getRemoteAddr());
  }

  @PostMapping("/signup/status")
  public Map<String, Object> signupProgress(@RequestBody SignupId request) {
    return signups.signupProgress(request.signupIntentId());
  }

  @PostMapping("/signup/email/resend")
  public Map<String, Object> resendEmail(@RequestBody SignupId request, HttpServletRequest servletRequest) {
    return signups.resendVerification(request.signupIntentId(), servletRequest.getRemoteAddr());
  }

  @PostMapping("/signup/email/confirm")
  public Map<String, Object> confirmEmail(@RequestBody EmailToken request) {
    return signups.confirmSignupEmail(request.token());
  }

  @PostMapping("/signup/continue")
  public Map<String, Object> continueSignup(@RequestBody Continuation request) {
    return signups.continueSignup(request.signupIntentId(), request.continuationToken());
  }

  @PostMapping("/signup/phone/confirm")
  public Map<String, Object> confirmPhone(@RequestBody PhoneProof request) {
    return signups.verifySignupPhone(request.signupIntentId(), request.continuationToken(), request.providerProof());
  }

  @PostMapping("/signup/complete")
  public Map<String, Object> complete(@RequestBody PublicSignupService.PaymentCompletion request) {
    return signups.completePayment(request);
  }

  @PostMapping("/signup/demo-complete")
  public Map<String, Object> completeDemo(@RequestBody DemoCompletionRequest request) {
    return signups.completeDemoPayment(request.signupIntentId());
  }

  public record DemoCompletionRequest(
      @com.fasterxml.jackson.annotation.JsonProperty("signup_intent_id") UUID signupIntentId) {}

  public record SignupId(
      @com.fasterxml.jackson.annotation.JsonProperty("signup_intent_id") UUID signupIntentId) {}

  public record EmailToken(String token) {}

  public record Continuation(
      @com.fasterxml.jackson.annotation.JsonProperty("signup_intent_id") UUID signupIntentId,
      @com.fasterxml.jackson.annotation.JsonProperty("continuation_token") String continuationToken) {}

  public record PhoneProof(
      @com.fasterxml.jackson.annotation.JsonProperty("signup_intent_id") UUID signupIntentId,
      @com.fasterxml.jackson.annotation.JsonProperty("continuation_token") String continuationToken,
      @com.fasterxml.jackson.annotation.JsonProperty("provider_proof") String providerProof) {}
}
