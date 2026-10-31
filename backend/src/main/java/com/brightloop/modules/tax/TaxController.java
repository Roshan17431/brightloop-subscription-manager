package com.brightloop.modules.tax;

import com.brightloop.config.AccountStatus;
import com.brightloop.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/taxes", "/api/v1/tax-rules"})
public class TaxController {
  private final TaxService service;

  public TaxController(TaxService service) {
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

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable Long id) {
    return ApiResponse.ok(service.get(id));
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody TaxDtos.Input input) {
    return ApiResponse.created(service.create(input));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody TaxDtos.Input input) {
    return ApiResponse.ok(service.update(id, input));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
