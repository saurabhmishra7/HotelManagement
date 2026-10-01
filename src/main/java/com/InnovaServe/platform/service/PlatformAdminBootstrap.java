package com.InnovaServe.platform.service;

import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.repository.PlatformAdminRepository;
import com.InnovaServe.platform.security.PlatformRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformAdminBootstrap implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);
  private final PlatformAdminRepository admins;
  private final PasswordEncoder passwords;
  private final boolean enabled;
  private final String name;
  private final String email;
  private final String password;

  public PlatformAdminBootstrap(
      PlatformAdminRepository admins,
      PasswordEncoder passwords,
      @Value("${app.platform.bootstrap.enabled:false}") boolean enabled,
      @Value("${app.platform.bootstrap.name:}") String name,
      @Value("${app.platform.bootstrap.email:}") String email,
      @Value("${app.platform.bootstrap.password:}") String password) {
    this.admins = admins;
    this.passwords = passwords;
    this.enabled = enabled;
    this.name = name;
    this.email = email;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled) return;
    if (admins.count() > 0) {
      log.info("Platform admin bootstrap skipped because an admin already exists");
      return;
    }
    if (name == null || name.isBlank() || email == null || !email.contains("@")) {
      throw new IllegalStateException("Platform bootstrap name and valid email are required");
    }
    if (password == null || password.length() < 16) {
      throw new IllegalStateException("Platform bootstrap password must be at least 16 characters");
    }
    admins.save(new PlatformAdmin(name, email, passwords.encode(password), PlatformRole.OWNER));
    log.info("Bootstrapped the initial platform owner account for {}", email);
  }
}
