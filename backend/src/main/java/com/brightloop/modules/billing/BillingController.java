package com.brightloop.modules.billing;

import com.brightloop.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
  private final BillingService service;

  public BillingController(BillingService service) {
    this.service = service;
  }

  @GetMapping("/due")
  public ResponseEntity<?> due(
      @RequestParam(required = false) LocalDate billingDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(service.listDue(billingDate, page, size));
  }

  @GetMapping("/runs")
  public ResponseEntity<?> runs(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(service.listRuns(q, status, page, size));
  }

  @GetMapping("/runs/{id}")
  public ResponseEntity<?> getRun(@PathVariable Long id) {
    return ApiResponse.ok(service.getRun(id));
  }

  @PostMapping({"/run", "/runs"})
  public ResponseEntity<?> run(
      @RequestHeader(value = "Idempotency-Key", required = false) String headerKey,
      @Valid @RequestBody(required = false) BillingDtos.RunRequest body) {
    var effectiveRequest =
        new BillingDtos.RunRequest(
            body != null ? body.billingDate() : null,
            body != null && body.idempotencyKey() != null && !body.idempotencyKey().isBlank()
                ? body.idempotencyKey()
                : headerKey,
            body != null ? body.subscriptionId() : null);
    return ApiResponse.ok(service.triggerAdminBilling(effectiveRequest));
  }

  @PostMapping("/runs/{id}/retry")
  public ResponseEntity<?> retry(@PathVariable Long id) {
    return ApiResponse.ok(service.retryRun(id));
  }
}
