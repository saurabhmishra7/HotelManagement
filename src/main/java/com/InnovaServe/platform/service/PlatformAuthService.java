package com.InnovaServe.platform.service;

import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.repository.PlatformAdminRepository;
import com.InnovaServe.platform.security.PlatformTokenService;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuthService {
  private final PlatformAdminRepository admins;
  private final PasswordEncoder passwordEncoder;
  private final PlatformTokenService tokens;

  public PlatformAuthService(
      PlatformAdminRepository admins,
      PasswordEncoder passwordEncoder,
      PlatformTokenService tokens) {
    this.admins = admins;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
  }

  public Optional<Map<String, Object>> login(String email, String password) {
    if (email == null || password == null) return Optional.empty();
    return admins
        .findByEmailIgnoreCase(email.trim())
        .filter(PlatformAdmin::isActive)
        .filter(admin -> passwordEncoder.matches(password, admin.getPasswordHash()))
        .map(
            admin ->
                Map.of(
                    "token", tokens.issue(admin.getId()),
                    "expires_in", 28800,
                    "admin", Map.of("id", admin.getId(), "name", admin.getName(), "email", admin.getEmail(), "role", admin.getRole())));
  }
}
