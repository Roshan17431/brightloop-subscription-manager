package com.brightloop.modules.auth;

import com.brightloop.modules.user.*;
import com.brightloop.response.ApiResponse;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final UserService users;
  private final AuthService auth;
  private final AuthenticationManager manager;
  private final HttpSessionSecurityContextRepository contexts;
  private final HttpSessionCsrfTokenRepository csrf;

  public AuthController(
      UserService users,
      AuthService auth,
      AuthenticationManager manager,
      HttpSessionSecurityContextRepository contexts,
      HttpSessionCsrfTokenRepository csrf) {
    this.users = users;
    this.auth = auth;
    this.manager = manager;
    this.contexts = contexts;
    this.csrf = csrf;
  }

  @GetMapping("/csrf")
  public ResponseEntity<?> csrf(CsrfToken token) {
    return ApiResponse.ok(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody AuthDtos.Register input) {
    return ApiResponse.created(users.register(input));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody AuthDtos.Login input,
      HttpServletRequest request,
      HttpServletResponse response) {
    var authenticated =
        manager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                UserService.email(input.email()), input.password()));
    if (request.getSession(false) != null) request.changeSessionId();
    new CsrfAuthenticationStrategy(csrf).onAuthentication(authenticated, request, response);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authenticated);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    return ApiResponse.ok(users.me());
  }

  @GetMapping("/me")
  public ResponseEntity<?> me() {
    return ApiResponse.ok(users.me());
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<?> forgot(@Valid @RequestBody AuthDtos.Forgot input) {
    auth.forgot(input.email());
    return ApiResponse.ok(
        Map.of("message", "If the account exists, a password reset link has been sent"));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<?> reset(@Valid @RequestBody AuthDtos.Reset input) {
    auth.reset(input.token(), input.password());
    return ApiResponse.ok(Map.of("message", "Password reset. Please sign in"));
  }
}
