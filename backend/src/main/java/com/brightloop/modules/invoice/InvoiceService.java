package com.brightloop.modules.invoice;

import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.customer.Customer;
import com.brightloop.modules.customer.CustomerRepository;
import com.brightloop.modules.user.Role;
import com.brightloop.response.PageResponse;
import com.brightloop.security.CurrentUser;
import com.brightloop.validation.Rules;
import java.time.Instant;
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
    return PageResponse.from(
        invoices.search(
            PageResponse.search(q),
            state,
            subscriptionId,
            effectiveCustomerId,
            PageResponse.request(page, size)),
        InvoiceDtos::view);
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
    boolean hasPaymentEvidence =
        inv.paymentReference != null && !inv.paymentReference.isBlank() && inv.paidAt != null;
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
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(inv.customerEmail);
    message.setSubject("Invoice " + inv.invoiceNumber + " from Brightloop");
    StringBuilder body = new StringBuilder();
    body.append("Hello ").append(inv.customerName).append(",\n\n");
    body.append("Here is your subscription invoice from Brightloop:\n\n");
    body.append("Invoice Number: ").append(inv.invoiceNumber).append("\n");
    body.append("Subscription: ").append(inv.subscriptionNumber).append(" (").append(inv.planName).append(")\n");
    body.append("Billing Period: ").append(inv.billingPeriodStart).append(" to ").append(inv.billingPeriodEnd).append("\n");
    body.append("Issue Date: ").append(inv.issueDate).append("\n");
    body.append("Due Date: ").append(inv.dueDate).append("\n");
    body.append("Subtotal: ").append(inv.subtotal.toPlainString()).append("\n");
    body.append("Discount: ").append(inv.discountAmount.toPlainString()).append("\n");
    body.append("Taxes: ").append(inv.taxAmount.toPlainString()).append("\n");
    body.append("Total Due: ").append(inv.total.toPlainString()).append("\n\n");
    body.append("View your invoice online: ").append(frontend).append("/invoices/").append(inv.id).append("\n");
    message.setText(body.toString());
    try {
      mail.send(message);
    } catch (MailException e) {
      log.warn("Invoice email delivery failed for {}; verify SMTP configuration", inv.invoiceNumber);
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
