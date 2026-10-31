package com.brightloop.modules.subscription;

import com.brightloop.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
  private final SubscriptionService service;

  public SubscriptionController(SubscriptionService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<?> list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) SubscriptionState state,
      @RequestParam(required = false) SubscriptionState status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(service.list(q, state != null ? state : status, page, size));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable Long id) {
    return ApiResponse.ok(service.get(id));
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody SubscriptionDtos.Input input) {
    return ApiResponse.created(service.create(input));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(
      @PathVariable Long id, @Valid @RequestBody SubscriptionDtos.Input input) {
    return ApiResponse.ok(service.update(id, input));
  }

  @PostMapping("/{id}/transition")
  public ResponseEntity<?> transition(
      @PathVariable Long id, @Valid @RequestBody SubscriptionDtos.Transition input) {
    return ApiResponse.ok(service.transition(id, input.state()));
  }

  @PostMapping("/{id}/pause")
  public ResponseEntity<?> pause(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) SubscriptionDtos.PauseInput input) {
    return ApiResponse.ok(service.pause(id, input));
  }

  @PostMapping("/{id}/resume")
  public ResponseEntity<?> resume(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) SubscriptionDtos.ResumeInput input) {
    return ApiResponse.ok(service.resume(id, input));
  }

  @PostMapping("/{id}/renew")
  public ResponseEntity<?> renew(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) SubscriptionDtos.RenewInput input,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ApiResponse.ok(service.renew(id, input, idempotencyKey));
  }

  @PostMapping({"/{id}/close", "/{id}/early-close"})
  public ResponseEntity<?> close(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) SubscriptionDtos.CloseInput input) {
    return ApiResponse.ok(service.closeEarly(id, input));
  }
}
