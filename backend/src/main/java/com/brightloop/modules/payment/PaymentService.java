package com.brightloop.modules.payment;

import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.customer.Customer;
import com.brightloop.modules.customer.CustomerRepository;
import com.brightloop.modules.invoice.Invoice;
import com.brightloop.modules.invoice.InvoiceRepository;
import com.brightloop.modules.invoice.InvoiceState;
import com.brightloop.modules.user.Role;
import com.brightloop.response.PageResponse;
import com.brightloop.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private final PaymentRepository payments;
  private final InvoiceRepository invoices;
  private final CustomerRepository customers;
  private final CurrentUser current;
  private final AuditService audit;

  public PaymentService(
      PaymentRepository payments,
      InvoiceRepository invoices,
      CustomerRepository customers,
      CurrentUser current,
      AuditService audit) {
    this.payments = payments;
    this.invoices = invoices;
    this.customers = customers;
    this.current = current;
    this.audit = audit;
  }

  private Payment owned(Payment p) {
    var u = current.get();
    if (u.role == Role.CUSTOMER
        && (p.customer.getUser() == null || !p.customer.getUser().getId().equals(u.id))) {
      throw ApiException.forbidden();
    }
    return p;
  }

  @Transactional(readOnly = true)
  public PageResponse<PaymentDtos.View> list(
      String q,
      Long invoiceId,
      Long customerId,
      PaymentMethod method,
      PaymentStatus status,
      LocalDate fromDate,
      LocalDate toDate,
      int page,
      int size) {
    if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
      throw new IllegalArgumentException("End date must be on or after start date");
    }
    var user = current.get();
    Long effectiveCustomerId = customerId;
    if (user.role == Role.CUSTOMER) {
      Long ownCustomerId =
          customers
              .findByUserId(user.id)
              .map(v -> org.hibernate.Hibernate.unproxy(v, Customer.class))
              .orElseThrow(() -> ApiException.notFound("Customer profile"))
              .id;
      if (customerId != null && !customerId.equals(ownCustomerId)) {
        throw ApiException.forbidden();
      }
      if (invoiceId != null) {
        var inv =
            invoices
                .findById(invoiceId)
                .map(v -> org.hibernate.Hibernate.unproxy(v, Invoice.class))
                .orElseThrow(() -> ApiException.notFound("Invoice"));
        if (!inv.customer.getId().equals(ownCustomerId)) {
          throw ApiException.forbidden();
        }
      }
      effectiveCustomerId = ownCustomerId;
    }
    return PageResponse.from(
        payments.search(
            PageResponse.search(q),
            invoiceId,
            effectiveCustomerId,
            method,
            status,
            fromDate,
            toDate,
            PageResponse.request(page, size)),
        PaymentDtos::view);
  }

  @Transactional(readOnly = true)
  public List<PaymentDtos.View> listForInvoice(Long invoiceId) {
    var inv =
        invoices
            .findById(invoiceId)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Invoice.class))
            .orElseThrow(() -> ApiException.notFound("Invoice"));
    var u = current.get();
    if (u.role == Role.CUSTOMER
        && (inv.customer.getUser() == null || !inv.customer.getUser().getId().equals(u.id))) {
      throw ApiException.forbidden();
    }
    return payments.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId).stream()
        .map(PaymentDtos::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public PaymentDtos.View get(Long id) {
    return PaymentDtos.view(
        owned(
            payments
                .findById(id)
                .map(v -> org.hibernate.Hibernate.unproxy(v, Payment.class))
                .orElseThrow(() -> ApiException.notFound("Payment"))));
  }

  @Transactional
  public PaymentDtos.View record(PaymentDtos.Input input, String headerIdempotencyKey) {
    current.requireManager();
    var user = current.get();

    String explicitKey =
        (headerIdempotencyKey != null && !headerIdempotencyKey.isBlank())
            ? headerIdempotencyKey.trim()
            : ((input.idempotencyKey() != null && !input.idempotencyKey().isBlank())
                ? input.idempotencyKey().trim()
                : null);

    BigDecimal requestedAmount = input.amount().setScale(2, RoundingMode.HALF_UP);
    if (requestedAmount.signum() <= 0) {
      throw new IllegalArgumentException("Payment amount must be greater than zero");
    }

    var inv =
        invoices
            .lockById(input.invoiceId())
            .map(v -> org.hibernate.Hibernate.unproxy(v, Invoice.class))
            .orElseThrow(() -> ApiException.notFound("Invoice"));

    if (explicitKey != null) {
      var existingByKey = payments.findByIdempotencyKey(explicitKey);
      if (existingByKey.isPresent()) {
        var existing = org.hibernate.Hibernate.unproxy(existingByKey.get(), Payment.class);
        if (!existing.invoice.getId().equals(input.invoiceId())
            || existing.amount.compareTo(requestedAmount) != 0) {
          throw ApiException.conflict(
              "Idempotency key already used for a different payment request");
        }
        return PaymentDtos.view(existing);
      }
    }

    LocalDate paymentDate = input.paymentDate() != null ? input.paymentDate() : LocalDate.now();
    String reference;
    if (input.paymentReference() != null && !input.paymentReference().isBlank()) {
      reference = input.paymentReference().trim();
      if (payments.findByPaymentReferenceIgnoreCase(reference).isPresent()) {
        throw ApiException.conflict("Duplicate payment reference: " + reference);
      }
    } else {
      reference =
          "PAY-"
              + paymentDate.toString().replace("-", "")
              + "-"
              + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    if (inv.state == InvoiceState.CANCELLED) {
      throw ApiException.conflict("Cannot record payment against a cancelled invoice");
    }
    if (inv.state == InvoiceState.DRAFT) {
      throw ApiException.conflict("Invoice must be confirmed before recording payments");
    }
    if (inv.state == InvoiceState.PAID) {
      throw ApiException.conflict("Invoice is already fully paid");
    }

    var customer = org.hibernate.Hibernate.unproxy(inv.customer, Customer.class);
    if (input.customerId() != null && !input.customerId().equals(customer.id)) {
      throw ApiException.conflict("Customer ID does not match invoice customer");
    }

    BigDecimal currentPaid =
        inv.amountPaid != null ? inv.amountPaid : BigDecimal.ZERO.setScale(2);
    BigDecimal remaining = inv.total.subtract(currentPaid);
    if (requestedAmount.compareTo(remaining) > 0) {
      throw ApiException.conflict(
          "Payment amount ("
              + requestedAmount.toPlainString()
              + ") exceeds invoice outstanding balance ("
              + remaining.toPlainString()
              + ")");
    }

    var p = new Payment();
    p.paymentReference = reference;
    p.idempotencyKey =
        explicitKey != null ? explicitKey : "AUTO-" + reference + "-" + UUID.randomUUID();
    p.invoice = inv;
    p.invoiceNumber = inv.invoiceNumber;
    p.customer = customer;
    p.customerName = customer.name;
    p.method = input.method();
    p.amount = requestedAmount;
    p.paymentDate = paymentDate;
    p.status = PaymentStatus.COMPLETED;
    p.note = input.note() == null || input.note().isBlank() ? null : input.note().trim();
    p.recordedBy = user.email;

    payments.saveAndFlush(p);

    BigDecimal reconciledTotal =
        payments.sumCompletedAmountByInvoiceId(inv.id).setScale(2, RoundingMode.HALF_UP);
    inv.amountPaid = reconciledTotal;
    inv.paymentReference = p.paymentReference;

    boolean becamePaid = false;
    if (reconciledTotal.compareTo(inv.total) == 0) {
      inv.state = InvoiceState.PAID;
      inv.paidAt = Instant.now();
      becamePaid = true;
    }
    invoices.saveAndFlush(inv);

    audit.record(
        "PAYMENT_RECORDED",
        "PAYMENT",
        p.id,
        "ref="
            + p.paymentReference
            + ",invoice="
            + inv.invoiceNumber
            + ",amount="
            + p.amount
            + ",method="
            + p.method);
    if (becamePaid) {
      audit.record(
          "INVOICE_PAID_RECONCILED",
          "INVOICE",
          inv.id,
          "number="
              + inv.invoiceNumber
              + ",amountPaid="
              + inv.amountPaid
              + ",lastPaymentRef="
              + p.paymentReference);
    }

    return PaymentDtos.view(p);
  }

  @Transactional
  public PaymentDtos.View reverse(Long id, PaymentDtos.ReversalInput input) {
    current.requireManager();
    var user = current.get();

    var p =
        payments
            .lockById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Payment.class))
            .orElseThrow(() -> ApiException.notFound("Payment"));
    if (p.status == PaymentStatus.REVERSED) {
      throw ApiException.conflict("Payment has already been reversed");
    }

    var inv =
        invoices
            .lockById(p.invoice.getId())
            .map(v -> org.hibernate.Hibernate.unproxy(v, Invoice.class))
            .orElseThrow(() -> ApiException.notFound("Invoice"));

    String revRef =
        (input.reversalReference() != null && !input.reversalReference().isBlank())
            ? input.reversalReference().trim()
            : "REV-" + p.paymentReference;

    p.status = PaymentStatus.REVERSED;
    p.reversedAt = Instant.now();
    p.reversedBy = user.email;
    p.reversalReason = input.reason().trim();
    p.reversalReference = revRef;
    payments.saveAndFlush(p);

    BigDecimal reconciledTotal =
        payments.sumCompletedAmountByInvoiceId(inv.id).setScale(2, RoundingMode.HALF_UP);
    inv.amountPaid = reconciledTotal;
    if (inv.state == InvoiceState.PAID && reconciledTotal.compareTo(inv.total) < 0) {
      inv.state = InvoiceState.CONFIRMED;
      inv.paidAt = null;
    }
    if (reconciledTotal.signum() == 0) {
      inv.paymentReference = null;
    }
    invoices.saveAndFlush(inv);

    audit.record(
        "PAYMENT_REVERSED",
        "PAYMENT",
        p.id,
        "ref="
            + p.paymentReference
            + ",reversalRef="
            + revRef
            + ",invoice="
            + inv.invoiceNumber
            + ",newAmountPaid="
            + inv.amountPaid);

    return PaymentDtos.view(p);
  }
}
