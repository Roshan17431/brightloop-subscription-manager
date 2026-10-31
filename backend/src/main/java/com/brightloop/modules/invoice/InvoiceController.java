package com.brightloop.modules.invoice;

import com.brightloop.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {
  private final InvoiceService service;

  public InvoiceController(InvoiceService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<?> list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) InvoiceState state,
      @RequestParam(required = false) InvoiceState status,
      @RequestParam(required = false) Long subscriptionId,
      @RequestParam(required = false) Long customerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(
        service.list(q, state != null ? state : status, subscriptionId, customerId, page, size));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable Long id) {
    return ApiResponse.ok(service.get(id));
  }

  @PostMapping("/{id}/transition")
  public ResponseEntity<?> transition(
      @PathVariable Long id, @Valid @RequestBody InvoiceDtos.Transition input) {
    return ApiResponse.ok(service.transition(id, input.state()));
  }

  @PostMapping("/{id}/confirm")
  public ResponseEntity<?> confirm(@PathVariable Long id) {
    return ApiResponse.ok(service.transition(id, InvoiceState.CONFIRMED));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<?> cancel(@PathVariable Long id) {
    return ApiResponse.ok(service.transition(id, InvoiceState.CANCELLED));
  }

  @PostMapping("/{id}/send")
  public ResponseEntity<?> send(@PathVariable Long id) {
    return ApiResponse.ok(service.sendEmail(id));
  }

  @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
    var invoice = service.getOwnedEntity(id);
    byte[] pdfBytes = InvoicePdfGenerator.generate(invoice);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + invoice.invoiceNumber + ".pdf\"")
        .body(pdfBytes);
  }
}
