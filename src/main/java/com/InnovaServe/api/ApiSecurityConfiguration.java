package com.InnovaServe.api;
import com.InnovaServe.core.service.TokenService;import org.springframework.context.annotation.*;import org.springframework.stereotype.Component;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.http.SessionCreationPolicy;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.security.web.*;import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;import org.springframework.web.filter.OncePerRequestFilter;import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.util.*;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
@Configuration public class ApiSecurityConfiguration{
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean SecurityFilterChain apiFilterChain(HttpSecurity http,BearerTokenFilter filter)throws Exception{return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/login").permitAll().anyRequest().authenticated()).addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();}
}
@Component
class BearerTokenFilter extends OncePerRequestFilter{
 private final TokenService tokens;BearerTokenFilter(TokenService tokens){this.tokens=tokens;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{String header=request.getHeader("Authorization");if(header!=null&&header.startsWith("Bearer ")){Map<?,?> claims=tokens.verify(header.substring(7));if(claims!=null){var auth=new UsernamePasswordAuthenticationToken(claims,null,List.of());org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);}}chain.doFilter(request,response);}
}
