package com.InnovaServe.platform.security;

import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.repository.PlatformAdminRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PlatformBearerTokenFilter extends OncePerRequestFilter {
  private final PlatformTokenService tokens;
  private final PlatformAdminRepository admins;

  public PlatformBearerTokenFilter(
      PlatformTokenService tokens, PlatformAdminRepository admins) {
    this.tokens = tokens;
    this.admins = admins;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      var claims = tokens.verify(header.substring(7));
      if (claims != null) {
        try {
          UUID adminId = UUID.fromString(claims.get("admin_id").toString());
          PlatformAdmin admin = admins.findById(adminId).orElse(null);
          if (admin != null && admin.isActive()) {
            var authorities =
                admin.roleType().permissions().stream()
                    .map(permission -> new SimpleGrantedAuthority("PLATFORM_" + permission.name()))
                    .toList();
            var authentication =
                new UsernamePasswordAuthenticationToken(admin, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
          }
        } catch (IllegalArgumentException ignored) {
          SecurityContextHolder.clearContext();
        }
      }
    }
    chain.doFilter(request, response);
  }
}
