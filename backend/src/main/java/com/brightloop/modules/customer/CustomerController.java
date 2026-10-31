package com.brightloop.modules.customer;

import com.brightloop.config.AccountStatus;
import com.brightloop.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<?> list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) AccountStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(service.list(q, status, page, size));
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody CustomerDtos.Input input) {
    return ApiResponse.created(service.create(input));
  }

  @GetMapping("/me")
  public ResponseEntity<?> me() {
    return ApiResponse.ok(service.me());
  }

  @PutMapping("/me")
  public ResponseEntity<?> updateMe(@Valid @RequestBody CustomerDtos.Input input) {
    return ApiResponse.ok(service.updateMe(input));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable Long id) {
    return ApiResponse.ok(service.get(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(
      @PathVariable Long id, @Valid @RequestBody CustomerDtos.Input input) {
    return ApiResponse.ok(service.update(id, input));
  }
}
