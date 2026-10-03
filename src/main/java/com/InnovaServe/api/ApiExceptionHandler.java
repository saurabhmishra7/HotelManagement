package com.InnovaServe.api;

import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import com.InnovaServe.core.service.ModuleEntitlementService.ModuleNotEntitledException;
import com.InnovaServe.core.service.PasswordResetService.PasswordResetUnavailableException;
import com.InnovaServe.platform.service.PublicSignupRateLimiter.SignupRateLimitException;
import com.InnovaServe.platform.service.RazorpayPaymentService.PaymentProviderUnavailableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ModuleNotEntitledException.class)
  public ResponseEntity<?> moduleNotEntitled(ModuleNotEntitledException ex) {
    return ResponseEntity.status(403)
        .body(
            Map.of(
                "error",
                "MODULE_NOT_ENTITLED",
                "message",
                ex.getMessage(),
                "details",
                Map.of("module", ex.module().key())));
  }

  @ExceptionHandler(PasswordResetUnavailableException.class)
  public ResponseEntity<?> passwordResetUnavailable(PasswordResetUnavailableException ex) {
    return ResponseEntity.status(503)
        .body(
            Map.of(
                "error",
                "PASSWORD_RESET_UNAVAILABLE",
                "message",
                ex.getMessage(),
                "details",
                Map.of()));
  }

  @ExceptionHandler(PaymentProviderUnavailableException.class)
  public ResponseEntity<?> paymentUnavailable(PaymentProviderUnavailableException ex) {
    return ResponseEntity.status(503).body(Map.of("error", "PaymentUnavailable",
        "message", ex.getMessage(), "details", Map.of()));
  }

  @ExceptionHandler(SignupRateLimitException.class)
  public ResponseEntity<?> rateLimited(SignupRateLimitException ex) {
    return ResponseEntity.status(429).body(Map.of("error", "SignupRateLimited",
        "message", ex.getMessage(), "details", Map.of()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<?> uploadTooLarge(MaxUploadSizeExceededException ex) {
    return ResponseEntity.status(413)
        .body(
            Map.of(
                "error",
                "UploadTooLarge",
                "message",
                "Receipt files must be 10 MB or smaller",
                "details",
                Map.of()));
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<?> notFound(NoSuchElementException ex) {
    String message = ex.getMessage() == null ? "Resource not found" : ex.getMessage();
    String code = "RoomNotFound".equals(message) ? "RoomNotFound" : "NotFound";
    return ResponseEntity.status(404)
        .body(Map.of("error", code, "message", message, "details", Map.of()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
    String code = "InvalidRequest";
    int status = 400;
    if ("InvalidCredentials".equals(ex.getMessage())) {
      code = "InvalidCredentials";
      status = 401;
    } else if ("PhoneAlreadyExists".equals(ex.getMessage())) {
      code = "PhoneAlreadyExists";
      status = 409;
    }
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "error",
                code,
                "message",
                ex.getMessage() == null ? "Invalid request" : ex.getMessage(),
                "details",
                Map.of()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<?> conflict(IllegalStateException ex) {
    String message =
        ex.getMessage() == null ? "Request conflicts with current resource state" : ex.getMessage();
    String code =
        switch (message) {
          case "RoomNotAvailable" -> "RoomNotAvailable";
          case "AccountClosed" -> "AccountClosed";
          case "HasUnsettledCharges" -> "HasUnsettledCharges";
          case "AlreadyLocked" -> "AlreadyLocked";
          case "AlreadyApproved" -> "AlreadyApproved";
          default -> "InvalidState";
        };
    return ResponseEntity.status(409)
        .body(Map.of("error", code, "message", message, "details", Map.of()));
  }

  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<?> forbidden(SecurityException ex) {
    return ResponseEntity.status(403)
        .body(
            Map.of(
                "error",
                "Forbidden",
                "message",
                ex.getMessage() == null ? "Permission denied" : ex.getMessage(),
                "details",
                Map.of()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<?> accessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(403)
        .body(Map.of("error", "Forbidden", "message", ex.getMessage(), "details", Map.of()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> constraint(DataIntegrityViolationException ex) {
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "error",
                "Conflict",
                "message",
                "The request conflicts with an existing record or schema constraint",
                "details",
                Map.of()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> internal(Exception ex) {
    log.error("Unhandled API exception", ex);
    return ResponseEntity.internalServerError()
        .body(
            Map.of(
                "error",
                "InternalError",
                "message",
                "Request could not be completed",
                "details",
                Map.of()));
  }
}
