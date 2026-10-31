package com.brightloop.modules.invoice;

import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.customer.Customer;
import com.brightloop.modules.customer.CustomerRepository;
import com.brightloop.modules.user.Role;
import com.brightloop.response.PageResponse;
import com.brightloop.security.CurrentUser;
import com.brightloop.validation.Rules;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {
  private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
  private final InvoiceRepository invoices;
  private final CustomerRepository customers;
  private final CurrentUser current;
  private final AuditService audit;
  private final JavaMailSender mail;
  private final String frontend;
  private final String from;

  public InvoiceService(
      InvoiceRepository invoices,
      CustomerRepository customers,
      CurrentUser current,
      AuditService audit,
      JavaMailSender mail,
      @Value("${app.frontend-url}") String frontend,
      @Value("${app.mail-from}") String from) {
    this.invoices = invoices;
    this.customers = customers;
    this.current = current;
    this.audit = audit;
    this.mail = mail;
    this.frontend = frontend;
    this.from = from;
  }

  @Transactional(readOnly = true)
  public PageResponse<InvoiceDtos.View> list(
      String q,
      InvoiceState state,
      Long subscriptionId,
      Long customerId,
      Boolean outstanding,
      Boolean overdue,
      Boolean partiallyPaid,
      LocalDate asOfDate,
      int page,
      int size) {
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
      effectiveCustomerId = ownCustomerId;
    }
    LocalDate evalDate = asOfDate != null ? asOfDate : LocalDate.now();
    return PageResponse.from(
        invoices.search(
            PageResponse.search(q),
            state,
            subscriptionId,
            effectiveCustomerId,
            Boolean.TRUE.equals(outstanding),
            Boolean.TRUE.equals(overdue),
            Boolean.TRUE.equals(partiallyPaid),
            evalDate,
            PageResponse.request(page, size)),
        inv -> InvoiceDtos.view(inv, evalDate));
  }

  private Invoice owned(Invoice inv) {
    var u = current.get();
    if (u.role == Role.CUSTOMER
        && (inv.customer.getUser() == null || !inv.customer.getUser().getId().equals(u.id))) {
      throw ApiException.forbidden();
    }
    return inv;
  }

  @Transactional(readOnly = true)
  public InvoiceDtos.View get(Long id) {
    return InvoiceDtos.view(
        owned(
            invoices
                .findById(id)
                .map(v -> org.hibernate.Hibernate.unproxy(v, Invoice.class))
                .orElseThrow(() -> ApiException.notFound("Invoice"))));
  }

  @Transactional(readOnly = true)
  public Invoice getOwnedEntity(Long id) {
    var inv =
        owned(
            invoices
                .findById(id)
                .map(v -> org.hibernate.Hibernate.unproxy(v, Invoice.class))
                .orElseThrow(() -> ApiException.notFound("Invoice")));
    inv.lines.size();
    return inv;
  }

  @Transactional
  public InvoiceDtos.View transition(Long id, InvoiceState next) {
    current.requireManager();
    var inv =
        invoices
            .lockById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Invoice.class))
            .orElseThrow(() -> ApiException.notFound("Invoice"));
    if (next == InvoiceState.CANCELLED
        && inv.amountPaid != null
        && inv.amountPaid.signum() > 0) {
      throw ApiException.conflict(
          "Cannot cancel an invoice with recorded payments; reverse payments first");
    }
    boolean hasPaymentEvidence =
        inv.paymentReference != null
            && !inv.paymentReference.isBlank()
            && inv.paidAt != null
            && inv.amountPaid != null
            && inv.amountPaid.compareTo(inv.total) == 0;
    try {
      Rules.invoiceTransition(inv.state.name(), next.name(), hasPaymentEvidence);
    } catch (IllegalArgumentException e) {
      throw ApiException.conflict(e.getMessage());
    }
    var previous = inv.state;
    inv.state = next;
    Instant now = Instant.now();
    if (next == InvoiceState.CONFIRMED) inv.confirmedAt = now;
    if (next == InvoiceState.CANCELLED) inv.cancelledAt = now;
    if (next == InvoiceState.PAID) inv.paidAt = now;
    invoices.saveAndFlush(inv);
    audit.record(
        "INVOICE_STATE_CHANGED",
        "INVOICE",
        inv.id,
        "number=" + inv.invoiceNumber + ",from=" + previous + ",to=" + next);
    return InvoiceDtos.view(inv);
  }

  @Transactional
  public InvoiceDtos.View settleZeroInvoice(Long id) {
    current.requireManager();
    var inv =
        invoices
            .lockById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Invoice.class))
            .orElseThrow(() -> ApiException.notFound("Invoice"));
    if (inv.total.signum() != 0) {
      throw ApiException.conflict(
          "Only zero-value invoices (total = 0.00) can be settled without a recorded payment");
    }
    if (inv.state == InvoiceState.CANCELLED) {
      throw ApiException.conflict("Cannot settle a cancelled invoice");
    }
    if (inv.state == InvoiceState.PAID) {
      return InvoiceDtos.view(inv);
    }
    var previous = inv.state;
    Instant now = Instant.now();
    if (inv.confirmedAt == null) inv.confirmedAt = now;
    inv.amountPaid = BigDecimal.ZERO.setScale(2);
    inv.paymentReference = "ZERO-SETTLEMENT-" + inv.invoiceNumber;
    inv.paidAt = now;
    inv.state = InvoiceState.PAID;
    invoices.saveAndFlush(inv);
    audit.record(
        "INVOICE_ZERO_SETTLED",
        "INVOICE",
        inv.id,
        "number=" + inv.invoiceNumber + ",from=" + previous + ",to=PAID");
    return InvoiceDtos.view(inv);
  }

  @Transactional
  public InvoiceDtos.View recordFollowUp(Long id, InvoiceDtos.FollowUpInput input) {
    current.requireManager();
    var user = current.get();
    var inv =
        invoices
            .lockById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Invoice.class))
            .orElseThrow(() -> ApiException.notFound("Invoice"));
    if (inv.state == InvoiceState.PAID || inv.state == InvoiceState.CANCELLED) {
      throw ApiException.conflict("Cannot record follow-up on a " + inv.state + " invoice");
    }
    inv.lastFollowupAt = Instant.now();
    inv.followupNotes = input.notes().trim();
    inv.followupBy = user.email;
    invoices.saveAndFlush(inv);
    audit.record(
        "INVOICE_FOLLOWUP_RECORDED",
        "INVOICE",
        inv.id,
        "number=" + inv.invoiceNumber + ",by=" + user.email);
    return InvoiceDtos.view(inv);
  }

  @Transactional
  public InvoiceDtos.View sendEmail(Long id) {
    current.requireManager();
    var inv =
        invoices
            .lockById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Invoice.class))
            .orElseThrow(() -> ApiException.notFound("Invoice"));
    if (inv.state == InvoiceState.CANCELLED) {
      throw ApiException.conflict("Cannot email a cancelled invoice");
    }
    BigDecimal paid = inv.amountPaid != null ? inv.amountPaid : BigDecimal.ZERO.setScale(2);
    BigDecimal balance = inv.total.subtract(paid).max(BigDecimal.ZERO.setScale(2));
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(inv.customerEmail);
    message.setSubject("Invoice " + inv.invoiceNumber + " from Brightloop");
    StringBuilder body = new StringBuilder();
    body.append("Hello ").append(inv.customerName).append(",\n\n");
    body.append("Here is your subscription invoice from Brightloop:\n\n");
    body.append("Invoice Number: ").append(inv.invoiceNumber).append("\n");
    body.append("Subscription: ")
        .append(inv.subscriptionNumber)
        .append(" (")
        .append(inv.planName)
        .append(")\n");
    body.append("Billing Period: ")
        .append(inv.billingPeriodStart)
        .append(" to ")
        .append(inv.billingPeriodEnd)
        .append("\n");
    body.append("Issue Date: ").append(inv.issueDate).append("\n");
    body.append("Due Date: ").append(inv.dueDate).append("\n");
    body.append("Subtotal: ").append(inv.subtotal.toPlainString()).append("\n");
    body.append("Discount: ").append(inv.discountAmount.toPlainString()).append("\n");
    body.append("Taxes: ").append(inv.taxAmount.toPlainString()).append("\n");
    body.append("Total: ").append(inv.total.toPlainString()).append("\n");
    body.append("Amount Paid: ").append(paid.toPlainString()).append("\n");
    body.append("Balance Due: ").append(balance.toPlainString()).append("\n\n");
    body.append("View your invoice online: ")
        .append(frontend)
        .append("/invoices/")
        .append(inv.id)
        .append("\n");
    message.setText(body.toString());
    try {
      mail.send(message);
    } catch (MailException e) {
      log.warn(
          "Invoice email delivery failed for {}; verify SMTP configuration", inv.invoiceNumber);
      throw ApiException.conflict("Email delivery failed; verify SMTP configuration");
    }
    inv.sentAt = Instant.now();
    invoices.saveAndFlush(inv);
    audit.record(
        "INVOICE_EMAIL_SENT",
        "INVOICE",
        inv.id,
        "number=" + inv.invoiceNumber + ",recipient=" + inv.customerEmail);
    return InvoiceDtos.view(inv);
  }
}
