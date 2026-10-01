package com.InnovaServe.platform.controller;

import com.InnovaServe.platform.service.PlatformAuthService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/auth")
public class PlatformAuthController {
  private final PlatformAuthService auth;

  public PlatformAuthController(PlatformAuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    return auth
        .login(request.email(), request.password())
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(
            () ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(
                        Map.of(
                            "error", "InvalidCredentials",
                            "message", "Email or password is incorrect",
                            "details", Map.of())));
  }

  public record LoginRequest(String email, String password) {}
}
