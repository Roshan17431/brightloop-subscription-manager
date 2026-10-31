package com.brightloop.modules.payment;

import com.brightloop.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
  private final PaymentService service;

  public PaymentController(PaymentService service) {
    this.service = service;
  }

  @GetMapping("/payments")
  public ResponseEntity<?> list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Long invoiceId,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) PaymentMethod method,
      @RequestParam(required = false) PaymentStatus status,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(
        service.list(q, invoiceId, customerId, method, status, fromDate, toDate, page, size));
  }

  @GetMapping("/payments/{id}")
  public ResponseEntity<?> get(@PathVariable Long id) {
    return ApiResponse.ok(service.get(id));
  }

  @GetMapping("/invoices/{invoiceId}/payments")
  public ResponseEntity<?> listForInvoice(@PathVariable Long invoiceId) {
    return ApiResponse.ok(service.listForInvoice(invoiceId));
  }

  @PostMapping("/payments")
  public ResponseEntity<?> record(
      @Valid @RequestBody PaymentDtos.Input input,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ApiResponse.created(service.record(input, idempotencyKey));
  }

  @PostMapping("/invoices/{invoiceId}/payments")
  public ResponseEntity<?> recordForInvoice(
      @PathVariable Long invoiceId,
      @Valid @RequestBody PaymentDtos.Input input,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    var normalized =
        new PaymentDtos.Input(
            invoiceId,
            input.customerId(),
            input.paymentReference(),
            input.idempotencyKey(),
            input.method(),
            input.amount(),
            input.paymentDate(),
            input.note());
    return ApiResponse.created(service.record(normalized, idempotencyKey));
  }

  @PostMapping("/payments/{id}/reverse")
  public ResponseEntity<?> reverse(
      @PathVariable Long id, @Valid @RequestBody PaymentDtos.ReversalInput input) {
    return ApiResponse.ok(service.reverse(id, input));
  }
}
