package com.InnovaServe.platform.security;

import java.util.Arrays;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class PlatformSecurityConfiguration {
  @Bean
  FilterRegistrationBean<PlatformBearerTokenFilter> disablePlatformFilterServletRegistration(
      PlatformBearerTokenFilter filter) {
    FilterRegistrationBean<PlatformBearerTokenFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  @Order(1)
  SecurityFilterChain platformSecurityFilterChain(
      HttpSecurity http, PlatformBearerTokenFilter filter) throws Exception {
    return http.securityMatcher("/api/v1/platform/**")
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .headers(h -> h.frameOptions(f -> f.deny())
            .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
            .httpStrictTransportSecurity(t -> t.includeSubDomains(true).maxAgeInSeconds(31536000)))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(HttpMethod.POST, "/api/v1/platform/auth/login")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(
                    (request, response, exception) -> {
                      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                      response.setContentType("application/json");
                      response
                          .getWriter()
                          .write(
                              "{\"error\":\"Unauthorized\",\"message\":\"Platform"
                                  + " authentication is required\",\"details\":{}}");
                    }))
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(
      @Value("${app.platform.cors.allowed-origins:}") String origins,
      @Value("${app.cors.allowed-origins:}") String tenantOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(
        Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setExposedHeaders(List.of("Content-Type"));
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v1/platform/**", configuration);
    CorsConfiguration tenantConfiguration = new CorsConfiguration(configuration);
    tenantConfiguration.setAllowedOrigins(Arrays.stream(tenantOrigins.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList());
    tenantConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    tenantConfiguration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "X-Tenant-Id",
            "X-Guest-Session",
            "X-Device-Key",
            "X-Provisioning-Key",
            "Cache-Control",
            "Pragma"));
    source.registerCorsConfiguration("/**", tenantConfiguration);
    return source;
  }
}
