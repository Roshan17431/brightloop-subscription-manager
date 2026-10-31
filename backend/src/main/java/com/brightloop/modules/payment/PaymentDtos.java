package com.brightloop.modules.payment;

import com.brightloop.modules.invoice.Invoice;
import com.brightloop.modules.invoice.InvoiceDtos;
import com.brightloop.modules.invoice.InvoiceState;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class PaymentDtos {
  private PaymentDtos() {}

  public record Input(
      @NotNull Long invoiceId,
      Long customerId,
      @Size(max = 100) String paymentReference,
      @Size(max = 120) String idempotencyKey,
      @NotNull PaymentMethod method,
      @NotNull
          @DecimalMin("0.01")
          @Digits(integer = 17, fraction = 2)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal amount,
      LocalDate paymentDate,
      @Size(max = 1000) String note) {}

  public record ReversalInput(
      @NotBlank @Size(max = 500) String reason,
      @Size(max = 100) String reversalReference) {}

  public record View(
      Long id,
      String paymentReference,
      String idempotencyKey,
      Long invoiceId,
      String invoiceNumber,
      Long customerId,
      String customerName,
      PaymentMethod method,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
      LocalDate paymentDate,
      PaymentStatus status,
      String note,
      String recordedBy,
      Instant reversedAt,
      String reversedBy,
      String reversalReason,
      String reversalReference,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal invoiceTotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal invoiceAmountPaid,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal invoiceOutstandingAmount,
      InvoiceState invoiceState,
      String invoicePaymentStatus,
      Instant createdAt,
      Instant updatedAt) {}

  public static View view(Payment p) {
    var inv =
        p.invoice == null
            ? null
            : org.hibernate.Hibernate.unproxy(p.invoice, Invoice.class);
    var invView = inv == null ? null : InvoiceDtos.view(inv);
    return new View(
        p.id,
        p.paymentReference,
        p.idempotencyKey,
        p.invoice.getId(),
        p.invoiceNumber,
        p.customer.getId(),
        p.customerName,
        p.method,
        p.amount,
        p.paymentDate,
        p.status,
        p.note,
        p.recordedBy,
        p.reversedAt,
        p.reversedBy,
        p.reversalReason,
        p.reversalReference,
        invView == null ? null : invView.total(),
        invView == null ? null : invView.amountPaid(),
        invView == null ? null : invView.outstandingAmount(),
        invView == null ? null : invView.state(),
        invView == null ? null : invView.paymentStatus(),
        p.createdAt,
        p.updatedAt);
  }
}
