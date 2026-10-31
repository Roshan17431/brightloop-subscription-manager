package com.brightloop.config;

import com.brightloop.exception.ApiError;
import com.brightloop.modules.user.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService userDetailsService(UserRepository users) {
    return email -> {
      var u =
          users
              .findByEmail(email.trim().toLowerCase(Locale.ROOT))
              .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
      return User.withUsername(u.email).password(u.passwordHash).roles(u.role.name()).build();
    };
  }

  @Bean
  AuthenticationManager authenticationManager(UserDetailsService details, PasswordEncoder encoder) {
    var provider = new DaoAuthenticationProvider(details);
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }

  @Bean
  HttpSessionSecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  HttpSessionCsrfTokenRepository csrfRepository() {
    return new HttpSessionCsrfTokenRepository();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      ObjectMapper mapper,
      HttpSessionCsrfTokenRepository csrf,
      HttpSessionSecurityContextRepository contexts)
      throws Exception {
    http.cors(c -> {})
        .csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .securityContext(c -> c.securityContextRepository(contexts).requireExplicitSave(true))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/api/v1/auth/csrf",
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password")
                    .permitAll()
                    .requestMatchers("/api/v1/staff", "/api/v1/users/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/products", "/api/v1/plans")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/products/**", "/api/v1/plans/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**", "/api/v1/plans/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/customers")
                    .hasAnyRole("ADMIN", "STAFF")
                    .requestMatchers(
                        HttpMethod.POST, "/api/v1/customers", "/api/v1/subscriptions/*/transition")
                    .hasAnyRole("ADMIN", "STAFF")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          mapper.writeValue(
                              res.getOutputStream(),
                              ApiError.of(
                                  HttpStatus.UNAUTHORIZED,
                                  "UNAUTHENTICATED",
                                  "Authentication required",
                                  req.getRequestURI(),
                                  null));
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json");
                          mapper.writeValue(
                              res.getOutputStream(),
                              ApiError.of(
                                  HttpStatus.FORBIDDEN,
                                  "FORBIDDEN",
                                  "Access denied or invalid CSRF token",
                                  req.getRequestURI(),
                                  null));
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/v1/auth/logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("SESSION")
                    .logoutSuccessHandler(
                        (req, res, auth) -> {
                          res.setStatus(200);
                          res.setContentType("application/json");
                          mapper.writeValue(
                              res.getOutputStream(),
                              com.brightloop.response.ApiResponse.ok(
                                      Map.of("message", "Logged out"))
                                  .getBody());
                        }))
        .requestCache(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable());
    return http.build();
  }

  @Bean
  CorsConfigurationSource cors(@Value("${app.frontend-url}") String frontend) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(frontend));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
