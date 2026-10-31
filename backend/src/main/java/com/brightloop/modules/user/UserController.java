package com.brightloop.modules.user;

import com.brightloop.modules.auth.AuthDtos;
import com.brightloop.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {
  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  public record ChangeRole(@NotNull Role role) {}

  @PostMapping("/staff")
  public ResponseEntity<?> staff(@Valid @RequestBody AuthDtos.Register input) {
    return ApiResponse.created(users.staff(input));
  }

  @GetMapping("/users")
  public ResponseEntity<?> list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(users.list(q, page, size));
  }

  @PatchMapping("/users/{id}/role")
  public ResponseEntity<?> role(@PathVariable Long id, @Valid @RequestBody ChangeRole input) {
    return ApiResponse.ok(users.role(id, input.role()));
  }
}
