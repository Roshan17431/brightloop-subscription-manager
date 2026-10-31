package com.brightloop.modules.billing;

import com.brightloop.config.AccountStatus;
import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.discount.Discount;
import com.brightloop.modules.discount.DiscountService;
import com.brightloop.modules.invoice.*;
import com.brightloop.modules.subscription.*;
import com.brightloop.modules.tax.TaxRule;
import com.brightloop.modules.tax.TaxService;
import com.brightloop.response.PageResponse;
import com.brightloop.security.CurrentUser;
import com.brightloop.validation.Rules;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BillingService {
  private static final Pattern NET_DAYS = Pattern.compile("(?i)net\\s+(\\d+)");

  private final SubscriptionRepository subscriptions;
  private final InvoiceRepository invoices;
  private final BillingRunRepository runs;
  private final BillingRunItemRepository items;
  private final TaxService taxes;
  private final DiscountService discounts;
  private final CurrentUser current;
  private final AuditService audit;
  private final TransactionTemplate requiresNewTx;

  public BillingService(
      SubscriptionRepository subscriptions,
      InvoiceRepository invoices,
      BillingRunRepository runs,
      BillingRunItemRepository items,
      TaxService taxes,
      DiscountService discounts,
      CurrentUser current,
      AuditService audit,
      PlatformTransactionManager txManager) {
    this.subscriptions = subscriptions;
    this.invoices = invoices;
    this.runs = runs;
    this.items = items;
    this.taxes = taxes;
    this.discounts = discounts;
    this.current = current;
    this.audit = audit;
    var template = new TransactionTemplate(txManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.requiresNewTx = template;
  }

  @Transactional(readOnly = true)
  public PageResponse<SubscriptionDtos.View> listDue(LocalDate billingDate, int page, int size) {
    current.requireManager();
    LocalDate effectiveDate = billingDate != null ? billingDate : LocalDate.now();
    return PageResponse.from(
        subscriptions.findDueSubscriptions(effectiveDate, PageResponse.request(page, size)),
        SubscriptionDtos::view);
  }

  @Transactional(readOnly = true)
  public PageResponse<BillingDtos.RunView> listRuns(String q, String status, int page, int size) {
    current.requireManager();
    String filterStatus = (status == null || status.isBlank()) ? null : status.trim();
    return PageResponse.from(
        runs.search(PageResponse.search(q), filterStatus, PageResponse.request(page, size)),
        r -> BillingDtos.view(r, items.findByBillingRunIdOrderByIdAsc(r.id)));
  }

  @Transactional(readOnly = true)
  public BillingDtos.RunView getRun(Long id) {
    current.requireManager();
    var run =
        runs.findById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, BillingRun.class))
            .orElseThrow(() -> ApiException.notFound("Billing run"));
    return BillingDtos.view(run, items.findByBillingRunIdOrderByIdAsc(run.id));
  }

  public BillingDtos.RunView triggerAdminBilling(BillingDtos.RunRequest request) {
    current.requireAdmin();
    LocalDate billingDate =
        request != null && request.billingDate() != null ? request.billingDate() : LocalDate.now();
    String key =
        request != null
                && request.idempotencyKey() != null
                && !request.idempotencyKey().isBlank()
            ? request.idempotencyKey().trim()
            : "MANUAL-" + billingDate + "-" + UUID.randomUUID();
    Long targetSubId = request != null ? request.subscriptionId() : null;
    return executeBillingRun(billingDate, key, "MANUAL", targetSubId, null);
  }

  public BillingDtos.RunView runScheduledBilling(LocalDate billingDate) {
    LocalDate effectiveDate = billingDate != null ? billingDate : LocalDate.now();
    String key = "SCHEDULED-" + effectiveDate + "-" + UUID.randomUUID();
    return executeBillingRun(effectiveDate, key, "SCHEDULED", null, null);
  }

  public BillingDtos.RunView retryRun(Long runId) {
    current.requireAdmin();
    var previousRun =
        runs.findById(runId)
            .map(v -> org.hibernate.Hibernate.unproxy(v, BillingRun.class))
            .orElseThrow(() -> ApiException.notFound("Billing run"));
    var previousItems = items.findByBillingRunIdOrderByIdAsc(previousRun.id);
    List<Long> failedSubIds =
        previousItems.stream()
            .filter(i -> "FAILED".equals(i.status))
            .map(i -> i.subscriptionId)
            .distinct()
            .toList();
    Map<Long, Integer> previousAttempts = new HashMap<>();
    for (var item : previousItems) {
      previousAttempts.put(item.subscriptionId, item.attemptCount + 1);
    }
    String key = "RETRY-" + previousRun.id + "-" + UUID.randomUUID();
    return executeBillingRunForCandidates(
        previousRun.billingDate,
        key,
        "RETRY",
        failedSubIds.isEmpty()
            ? subscriptions.findCandidateIdsForBilling(previousRun.billingDate)
            : failedSubIds,
        previousAttempts,
        null);
  }

  public BillingDtos.RunView executeBillingRun(
      LocalDate billingDate,
      String idempotencyKey,
      String triggerType,
      Long specificSubscriptionId,
      Consumer<Subscription> faultInjector) {
    var existing = runs.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      var run = existing.get();
      return BillingDtos.view(run, items.findByBillingRunIdOrderByIdAsc(run.id));
    }
    List<Long> candidateIds =
        specificSubscriptionId != null
            ? List.of(specificSubscriptionId)
            : subscriptions.findCandidateIdsForBilling(billingDate);
    return executeBillingRunForCandidates(
        billingDate, idempotencyKey, triggerType, candidateIds, Map.of(), faultInjector);
  }

  private BillingDtos.RunView executeBillingRunForCandidates(
      LocalDate billingDate,
      String idempotencyKey,
      String triggerType,
      List<Long> candidateIds,
      Map<Long, Integer> attemptMap,
      Consumer<Subscription> faultInjector) {
    var existing = runs.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      var run = existing.get();
      return BillingDtos.view(run, items.findByBillingRunIdOrderByIdAsc(run.id));
    }

    var auth = SecurityContextHolder.getContext().getAuthentication();
    String actor = auth == null ? "SYSTEM" : auth.getName();

    BillingRun run;
    try {
      run =
          requiresNewTx.execute(
              status -> {
                var r = new BillingRun();
                r.idempotencyKey = idempotencyKey;
                r.billingDate = billingDate;
                r.triggerType = triggerType;
                r.triggeredBy = actor;
                r.status = "RUNNING";
                r.startedAt = Instant.now();
                return runs.saveAndFlush(r);
              });
    } catch (DataIntegrityViolationException dup) {
      var concurrent = runs.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> dup);
      return BillingDtos.view(concurrent, items.findByBillingRunIdOrderByIdAsc(concurrent.id));
    }

    int generated = 0;
    int skipped = 0;
    int failed = 0;
    int expiredClosed = 0;
    List<String> errors = new ArrayList<>();

    for (Long subId : candidateIds) {
      int attempt = attemptMap.getOrDefault(subId, 1);
      try {
        BillingRunItem item =
            requiresNewTx.execute(
                status -> processSingleSubscription(run.id, subId, billingDate, attempt, faultInjector));
        if (item != null) {
          switch (item.status) {
            case "INVOICED" -> generated++;
            case "SKIPPED" -> skipped++;
            case "EXPIRED_CLOSED" -> expiredClosed++;
            case "FAILED" -> {
              failed++;
              if (item.message != null) errors.add(item.message);
            }
          }
        }
      } catch (DataIntegrityViolationException | OptimisticLockingFailureException race) {
        skipped++;
        recordStandaloneItem(
            run.id,
            subId,
            billingDate,
            "SKIPPED",
            attempt,
            "Already billed concurrently for this billing cycle");
      } catch (Exception ex) {
        failed++;
        String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        errors.add("Sub #" + subId + ": " + msg);
        recordStandaloneItem(run.id, subId, billingDate, "FAILED", attempt, msg);
      }
    }

    final int fTotal = candidateIds.size();
    final int fGen = generated;
    final int fSkip = skipped;
    final int fFail = failed;
    final int fExp = expiredClosed;
    final String fErr =
        errors.isEmpty()
            ? null
            : String.join("; ", errors)
                .substring(0, Math.min(2000, String.join("; ", errors).length()));

    BillingRun completed =
        requiresNewTx.execute(
            status -> {
              var r = runs.findById(run.id).orElseThrow();
              r.totalEvaluated = fTotal;
              r.invoicesGenerated = fGen;
              r.skippedCount = fSkip;
              r.failedCount = fFail;
              r.expiredClosedCount = fExp;
              r.completedAt = Instant.now();
              r.errorSummary = fErr;
              r.status =
                  fFail == 0
                      ? "COMPLETED"
                      : (fGen > 0 || fSkip > 0 || fExp > 0 ? "COMPLETED_WITH_ERRORS" : "FAILED");
              runs.saveAndFlush(r);
              audit.record(
                  "BILLING_RUN_COMPLETED",
                  "BILLING_RUN",
                  r.id,
                  "date="
                      + billingDate
                      + ",generated="
                      + fGen
                      + ",skipped="
                      + fSkip
                      + ",failed="
                      + fFail
                      + ",expiredClosed="
                      + fExp);
              return r;
            });

    return BillingDtos.view(completed, items.findByBillingRunIdOrderByIdAsc(completed.id));
  }

  private void recordStandaloneItem(
      Long runId,
      Long subId,
      LocalDate fallbackDate,
      String itemStatus,
      int attempt,
      String message) {
    requiresNewTx.executeWithoutResult(
        tx -> {
          var subOpt = subscriptions.findById(subId);
          var item = new BillingRunItem();
          item.billingRun = runs.getReferenceById(runId);
          item.subscriptionId = subId;
          item.subscriptionNumber = subOpt.map(s -> s.number).orElse("SUB-" + subId);
          item.billingPeriodStart = subOpt.map(s -> s.nextBillingDate).orElse(fallbackDate);
          item.status = itemStatus;
          item.attemptCount = attempt;
          item.message =
              message != null && message.length() > 1000 ? message.substring(0, 1000) : message;
          items.saveAndFlush(item);
        });
  }

  protected BillingRunItem processSingleSubscription(
      Long runId,
      Long subId,
      LocalDate billingDate,
      int attempt,
      Consumer<Subscription> faultInjector) {
    var s =
        subscriptions
            .lockById(subId)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Subscription.class))
            .orElseThrow(() -> ApiException.notFound("Subscription"));
    var plan =
        org.hibernate.Hibernate.unproxy(s.plan, com.brightloop.modules.plan.Plan.class);
    var customer =
        org.hibernate.Hibernate.unproxy(
            s.customer, com.brightloop.modules.customer.Customer.class);

    LocalDate periodStart = s.nextBillingDate;

    if (s.state != SubscriptionState.ACTIVE) {
      return saveItem(
          runId,
          s,
          periodStart,
          "SKIPPED",
          null,
          attempt,
          "Subscription is not in ACTIVE state (" + s.state + ")");
    }

    // Check if subscription has already expired before billingDate or has no remaining period before expiry
    if (s.expiryDate.isBefore(billingDate) || periodStart.isAfter(s.expiryDate)) {
      var prev = s.state;
      s.state = SubscriptionState.CLOSED;
      subscriptions.saveAndFlush(s);
      audit.record(
          "SUBSCRIPTION_EXPIRED_CLOSED",
          "SUBSCRIPTION",
          s.id,
          "from=" + prev + ",to=CLOSED,expiryDate=" + s.expiryDate);
      return saveItem(
          runId,
          s,
          periodStart,
          "EXPIRED_CLOSED",
          null,
          attempt,
          "Subscription expired on " + s.expiryDate + " and was closed");
    }

    if (periodStart.isAfter(billingDate)) {
      return saveItem(
          runId,
          s,
          periodStart,
          "SKIPPED",
          null,
          attempt,
          "Next billing date " + periodStart + " is after " + billingDate);
    }

    // Check if an invoice already exists for (subscriptionId, periodStart) (idempotency)
    var existingInvoice =
        invoices.findBySubscriptionIdAndBillingPeriodStart(s.id, periodStart);
    if (existingInvoice.isPresent()) {
      // Ensure schedule is advanced if somehow not yet advanced
      LocalDate anchorDate = s.startDate.plusDays(s.totalPausedDays);
      s.lastBilledDate = periodStart;
      s.nextBillingDate =
          Rules.advanceBillingDate(periodStart, plan.billingPeriod, anchorDate);
      if (s.nextBillingDate.isAfter(s.expiryDate) && plan.automaticClosing) {
        s.state = SubscriptionState.CLOSED;
      }
      subscriptions.saveAndFlush(s);
      return saveItem(
          runId,
          s,
          periodStart,
          "SKIPPED",
          existingInvoice.get(),
          attempt,
          "Invoice already exists for period starting " + periodStart);
    }

    if (customer.status != AccountStatus.ACTIVE) {
      throw new IllegalStateException("Customer account is inactive");
    }

    var subLines = new ArrayList<>(s.lines);
    if (subLines.isEmpty()) {
      throw new IllegalStateException("Subscription has no billable lines");
    }

    // Evaluate and consume applicable discount with pessimistic lock
    var appliedDiscountOpt = discounts.evaluateAndConsume(s, subLines, billingDate);
    Discount appliedDiscount =
        appliedDiscountOpt.map(DiscountService.AppliedDiscount::discount).orElse(null);
    Map<Long, BigDecimal> lineDiscountMap =
        appliedDiscountOpt
            .map(DiscountService.AppliedDiscount::lineDiscountsBySubLineId)
            .orElse(Map.of());

    LocalDate anchorDate = s.startDate.plusDays(s.totalPausedDays);
    LocalDate periodEnd =
        Rules.billingPeriodEnd(periodStart, plan.billingPeriod, anchorDate, s.expiryDate);

    var invoice = new Invoice();
    invoice.invoiceNumber =
        "INV-"
            + billingDate.toString().replace("-", "")
            + "-"
            + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    invoice.subscription = s;
    invoice.subscriptionNumber = s.number;
    invoice.customer = customer;
    invoice.customerName = customer.name;
    invoice.customerEmail = customer.email;
    invoice.customerPhone = customer.phone;
    invoice.customerCompany = customer.companyName;
    invoice.billingAddress = customer.billingAddress;
    invoice.plan = plan;
    invoice.planName = s.planName;
    invoice.billingPeriod = plan.billingPeriod;
    invoice.billingPeriodStart = periodStart;
    invoice.billingPeriodEnd = periodEnd;
    invoice.issueDate = billingDate;
    invoice.dueDate = computeDueDate(billingDate, s.paymentTerms);
    invoice.paymentTerms = s.paymentTerms;
    invoice.state = InvoiceState.DRAFT;
    invoice.idempotencyKey = "SUB-" + s.id + "-PERIOD-" + periodStart;
    invoice.billingRunId = runId;
    invoice.discount = appliedDiscount;
    invoice.discountName = appliedDiscount == null ? null : appliedDiscount.name;

    BigDecimal invSubtotal = BigDecimal.ZERO.setScale(2);
    BigDecimal invDiscount = BigDecimal.ZERO.setScale(2);
    BigDecimal invTax = BigDecimal.ZERO.setScale(2);
    BigDecimal invTotal = BigDecimal.ZERO.setScale(2);

    for (int idx = 0; idx < subLines.size(); idx++) {
      var sl = subLines.get(idx);
      var prod =
          org.hibernate.Hibernate.unproxy(
              sl.product, com.brightloop.modules.product.Product.class);
      var il = new InvoiceLine();
      il.invoice = invoice;
      il.product = prod;
      il.variant = sl.variant;
      il.productName = sl.productName;
      il.variantName = sl.variantName;
      il.quantity = sl.quantity;
      il.unitPrice = sl.unitPrice;
      il.subtotal = Rules.lineSubtotal(sl.unitPrice, sl.quantity);

      BigDecimal lineDisc =
          lineDiscountMap.getOrDefault(
              sl.id != null ? sl.id : (long) idx, BigDecimal.ZERO.setScale(2));
      il.discountAmount = lineDisc;
      if (lineDisc.signum() > 0 && appliedDiscount != null) {
        il.discount = appliedDiscount;
        il.discountName = appliedDiscount.name;
      }

      BigDecimal taxable = il.subtotal.subtract(il.discountAmount).max(BigDecimal.ZERO);

      Optional<TaxRule> ruleOpt = taxes.resolveForProduct(prod, billingDate);
      if (ruleOpt.isPresent()) {
        TaxRule rule = ruleOpt.get();
        il.taxRate = rule.rate;
        il.taxRule = rule;
        il.taxRuleName = rule.name;
      } else {
        il.taxRate = sl.taxRate;
      }

      il.taxAmount = Rules.taxAmount(taxable, il.taxRate);
      il.amount = taxable.add(il.taxAmount);

      invSubtotal = invSubtotal.add(il.subtotal);
      invDiscount = invDiscount.add(il.discountAmount);
      invTax = invTax.add(il.taxAmount);
      invTotal = invTotal.add(il.amount);

      invoice.lines.add(il);
    }

    invoice.subtotal = invSubtotal;
    invoice.discountAmount = invDiscount;
    invoice.taxAmount = invTax;
    invoice.total = invTotal;

    invoices.saveAndFlush(invoice);

    if (faultInjector != null) {
      faultInjector.accept(s);
    }

    s.lastBilledDate = periodStart;
    s.nextBillingDate =
        Rules.advanceBillingDate(periodStart, plan.billingPeriod, anchorDate);
    if (s.nextBillingDate.isAfter(s.expiryDate) && plan.automaticClosing) {
      var prev = s.state;
      s.state = SubscriptionState.CLOSED;
      audit.record(
          "SUBSCRIPTION_EXPIRED_CLOSED",
          "SUBSCRIPTION",
          s.id,
          "from=" + prev + ",to=CLOSED,finalPeriod=" + periodStart);
    }
    subscriptions.saveAndFlush(s);

    audit.record(
        "INVOICE_GENERATED",
        "INVOICE",
        invoice.id,
        "invoiceNumber="
            + invoice.invoiceNumber
            + ",subscription="
            + s.number
            + ",periodStart="
            + periodStart
            + ",total="
            + invoice.total);

    return saveItem(
        runId,
        s,
        periodStart,
        "INVOICED",
        invoice,
        attempt,
        "Generated invoice " + invoice.invoiceNumber);
  }

  private BillingRunItem saveItem(
      Long runId,
      Subscription s,
      LocalDate periodStart,
      String itemStatus,
      Invoice invoice,
      int attempt,
      String message) {
    var item = new BillingRunItem();
    item.billingRun = runs.getReferenceById(runId);
    item.subscriptionId = s.id;
    item.subscriptionNumber = s.number;
    item.billingPeriodStart = periodStart;
    item.status = itemStatus;
    if (invoice != null) {
      item.invoiceId = invoice.id;
      item.invoiceNumber = invoice.invoiceNumber;
    }
    item.attemptCount = attempt;
    item.message = message;
    return items.saveAndFlush(item);
  }

  public static LocalDate computeDueDate(LocalDate issueDate, String paymentTerms) {
    if (paymentTerms != null) {
      var matcher = NET_DAYS.matcher(paymentTerms);
      if (matcher.find()) {
        int days = Integer.parseInt(matcher.group(1));
        if (days >= 0 && days <= 3650) {
          return issueDate.plusDays(days);
        }
      }
    }
    return issueDate;
  }
}
