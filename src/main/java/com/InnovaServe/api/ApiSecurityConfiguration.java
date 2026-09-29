package com.InnovaServe.api;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.repository.StaffRoleRepository;
import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.core.security.Permission;
import com.InnovaServe.core.security.RoleType;
import com.InnovaServe.core.service.TokenService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class ApiSecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain apiFilterChain(HttpSecurity http, BearerTokenFilter filter) throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/v1/auth/login").permitAll()
                    .requestMatchers("/api/v1/auth/password-reset/**")
                    .permitAll()
                    .requestMatchers("/api/v1/guest/**")
                    .permitAll()
                    .requestMatchers("/api/v1/tenants/lookup")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/tenants")
                    .permitAll()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/tenants/*/modules")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, exception) -> {
                          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                          response.setContentType("application/json");
                          response
                              .getWriter()
                              .write(
                                  "{\"error\":\"Unauthorized\",\"message\":\"Authentication is"
                                      + " required\",\"details\":{}}");
                        })
                    .accessDeniedHandler(
                        (request, response, exception) -> {
                          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                          response.setContentType("application/json");
                          response
                              .getWriter()
                              .write(
                                  "{\"error\":\"Forbidden\",\"message\":\"You do not have"
                                      + " permission for this action\",\"details\":{}}");
                        }))
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}

@Component
class BearerTokenFilter extends OncePerRequestFilter {
  private final TokenService tokens;
  private final StaffUserRepository users;
  private final StaffRoleRepository roles;

  BearerTokenFilter(TokenService tokens, StaffUserRepository users, StaffRoleRepository roles) {
    this.tokens = tokens;
    this.users = users;
    this.roles = roles;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      Map<?, ?> claims = tokens.verify(header.substring(7));
      if (claims != null) {
        UUID tenantId = UUID.fromString(claims.get("tenant_id").toString());
        UUID userId = UUID.fromString(claims.get("user_id").toString());
        StaffUser user = users.findByTenantIdAndId(tenantId, userId).orElse(null);
        if (user == null || !user.isActive()) {
          chain.doFilter(request, response);
          return;
        }
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        StaffRole role =
            user.getRoleId() == null
                ? null
                : roles.findByTenantIdAndId(tenantId, user.getRoleId()).orElse(null);
        if (role != null && role.getPermissions() != null) {
          Collection<?> permissions = role.getPermissions();
          for (Object permission : permissions) {
            try {
              authorities.add(
                  new SimpleGrantedAuthority(
                      "PERM_" + Permission.valueOf(permission.toString()).name()));
            } catch (IllegalArgumentException ignored) {
              // Unknown permissions in an older token are ignored.
            }
          }
          if ("OWNER".equals(role.getName()) || "HOTEL_ADMIN".equals(role.getName())) {
            RoleType.valueOf(role.getName()).defaultPermissions().stream()
                .map(permission -> new SimpleGrantedAuthority("PERM_" + permission.name()))
                .forEach(authorities::add);
          }
        }
        var auth = new UsernamePasswordAuthenticationToken(claims, null, authorities);
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(auth);
      }
    }
    chain.doFilter(request, response);
  }
}
