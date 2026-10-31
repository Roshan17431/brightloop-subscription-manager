package com.brightloop.modules.quotation;

import com.brightloop.config.AccountStatus;
import com.brightloop.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class QuotationController {
  private final QuotationService service;

  public QuotationController(QuotationService service) {
    this.service = service;
  }

  @GetMapping("/quotation-templates")
  public ResponseEntity<?> listTemplates(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) AccountStatus status,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(service.listTemplates(q, status, planId, page, size));
  }

  @GetMapping("/quotation-templates/{id}")
  public ResponseEntity<?> getTemplate(@PathVariable Long id) {
    return ApiResponse.ok(service.getTemplate(id));
  }

  @PostMapping("/quotation-templates")
  public ResponseEntity<?> createTemplate(
      @Valid @RequestBody QuotationDtos.TemplateInput input) {
    return ApiResponse.created(service.createTemplate(input));
  }

  @PutMapping("/quotation-templates/{id}")
  public ResponseEntity<?> updateTemplate(
      @PathVariable Long id, @Valid @RequestBody QuotationDtos.TemplateInput input) {
    return ApiResponse.ok(service.updateTemplate(id, input));
  }

  @PostMapping("/quotation-templates/{id}/generate")
  public ResponseEntity<?> generateFromTemplate(
      @PathVariable Long id, @Valid @RequestBody QuotationDtos.GenerateInput input) {
    return ApiResponse.created(service.generateFromTemplate(id, input));
  }

  @GetMapping("/quotations")
  public ResponseEntity<?> listQuotations(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) QuotationState state,
      @RequestParam(required = false) QuotationState status,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long templateId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(
        service.listQuotations(
            q, state != null ? state : status, customerId, templateId, page, size));
  }

  @GetMapping("/quotations/{id}")
  public ResponseEntity<?> getQuotation(@PathVariable Long id) {
    return ApiResponse.ok(service.getQuotation(id));
  }

  @PostMapping("/quotations")
  public ResponseEntity<?> createQuotation(
      @Valid @RequestBody QuotationDtos.GenerateInput input) {
    return ApiResponse.created(service.generateFromTemplate(input.templateId(), input));
  }

  @PostMapping("/quotations/{id}/confirm")
  public ResponseEntity<?> confirmQuotation(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) QuotationDtos.ConfirmInput input) {
    return ApiResponse.ok(service.confirm(id, input));
  }

  @PostMapping("/quotations/{id}/cancel")
  public ResponseEntity<?> cancelQuotation(@PathVariable Long id) {
    return ApiResponse.ok(service.cancel(id));
  }

  @PostMapping("/quotations/{id}/convert")
  public ResponseEntity<?> convertQuotation(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) QuotationDtos.ConvertInput input) {
    return ApiResponse.created(service.convertToSubscription(id, input));
  }
}
