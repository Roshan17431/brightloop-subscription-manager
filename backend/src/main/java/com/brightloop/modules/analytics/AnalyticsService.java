package com.brightloop.modules.analytics;

import com.brightloop.config.AccountStatus;
import com.brightloop.exception.ApiException;
import com.brightloop.modules.analytics.AnalyticsDtos.*;
import com.brightloop.modules.customer.Customer;
import com.brightloop.modules.customer.CustomerRepository;
import com.brightloop.modules.discount.Discount;
import com.brightloop.modules.discount.DiscountRepository;
import com.brightloop.modules.discount.DiscountScope;
import com.brightloop.modules.plan.BillingPeriod;
import com.brightloop.modules.subscription.Subscription;
import com.brightloop.modules.subscription.SubscriptionDtos;
import com.brightloop.modules.subscription.SubscriptionRepository;
import com.brightloop.modules.subscription.SubscriptionState;
import com.brightloop.modules.user.Role;
import com.brightloop.security.CurrentUser;
import com.brightloop.validation.Rules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
  private static final BigDecimal FIFTY_TWO = BigDecimal.valueOf(52);
  private static final BigDecimal THIRTY = BigDecimal.valueOf(30);
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final DateTimeFormatter MONTH_LABEL_FMT =
      DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

  private final NamedParameterJdbcTemplate jdbc;
  private final DiscountRepository discounts;
  private final CustomerRepository customers;
  private final SubscriptionRepository subscriptions;
  private final CurrentUser current;

  public AnalyticsService(
      NamedParameterJdbcTemplate jdbc,
      DiscountRepository discounts,
      CustomerRepository customers,
      SubscriptionRepository subscriptions,
      CurrentUser current) {
    this.jdbc = jdbc;
    this.discounts = discounts;
    this.customers = customers;
    this.subscriptions = subscriptions;
    this.current = current;
  }

  /**
   * Normalizes a pre-tax net recurring cycle amount into Monthly Recurring Revenue (MRR).
   *
   * <ul>
   *   <li>MONTHLY: amount * 1
   *   <li>YEARLY: amount / 12 (HALF_UP, scale 2)
   *   <li>WEEKLY: amount * 52 / 12 (HALF_UP, scale 2)
   *   <li>DAILY: amount * 30 (HALF_UP, scale 2)
   * </ul>
   */
  public static BigDecimal normalizeToMrr(BigDecimal netCycleAmount, BillingPeriod period) {
    if (netCycleAmount == null || netCycleAmount.signum() <= 0 || period == null) {
      return ZERO;
    }
    return switch (period) {
      case MONTHLY -> netCycleAmount.setScale(2, RoundingMode.HALF_UP);
      case YEARLY -> netCycleAmount.divide(TWELVE, 2, RoundingMode.HALF_UP);
      case WEEKLY -> netCycleAmount.multiply(FIFTY_TWO).divide(TWELVE, 2, RoundingMode.HALF_UP);
      case DAILY -> netCycleAmount.multiply(THIRTY).setScale(2, RoundingMode.HALF_UP);
    };
  }

  public static BigDecimal mrrToArr(BigDecimal mrr) {
    if (mrr == null || mrr.signum() <= 0) return ZERO;
    return mrr.multiply(TWELVE).setScale(2, RoundingMode.HALF_UP);
  }

  private void validateFilters(FilterParams f) {
    if (f.fromDate() != null && f.toDate() != null && f.toDate().isBefore(f.fromDate())) {
      throw new IllegalArgumentException("toDate cannot be before fromDate");
    }
  }

  @Transactional(readOnly = true)
  public OverviewResponse getOverview(FilterParams f) {
    current.requireManager();
    validateFilters(f);
    LocalDate asOf = f.effectiveAsOfDate();
    int windowDays = f.effectiveWindowDays();

    SubscriptionComputation subComp = computeSubscriptionMetrics(f, asOf);
    InvoiceSummaryTotals invTotals = queryInvoiceSummaryTotals(f);
    BigDecimal collected = queryCollectedPaymentsTotal(f);
    ReceivablesSummaryTotals recTotals = queryReceivablesTotals(f, asOf);

    List<RecentInvoiceItem> recentInvoices = queryRecentInvoices(f, asOf, 5);
    List<RecentPaymentItem> recentPayments = queryRecentPayments(f, 5);
    RenewalsResponse renewals = computeRenewals(f, asOf, windowDays, 5, subComp.mrrBySubId, subComp.cycleBySubId);

    return new OverviewResponse(
        asOf,
        f.fromDate(),
        f.toDate(),
        subComp.totalCount,
        subComp.countByState.getOrDefault(SubscriptionState.ACTIVE, 0L),
        subComp.countByState.getOrDefault(SubscriptionState.PAUSED, 0L),
        subComp.countByState.getOrDefault(SubscriptionState.CLOSED, 0L),
        subComp.countByState.getOrDefault(SubscriptionState.DRAFT, 0L),
        subComp.countByState.getOrDefault(SubscriptionState.QUOTATION, 0L),
        subComp.countByState.getOrDefault(SubscriptionState.CONFIRMED, 0L),
        subComp.newSubscriptionsCount,
        subComp.activeMrr,
        mrrToArr(subComp.activeMrr),
        subComp.pausedMrr,
        mrrToArr(subComp.pausedMrr),
        invTotals.subtotal,
        invTotals.discount,
        invTotals.tax,
        invTotals.total,
        collected,
        recTotals.totalOutstanding,
        recTotals.overdueCount,
        recTotals.overdueAmount,
        renewals.upcomingRenewalsCount(),
        renewals.expiringSubscriptionsCount(),
        recentInvoices,
        recentPayments,
        renewals.upcomingRenewals(),
        renewals.expiringSubscriptions());
  }

  @Transactional(readOnly = true)
  public RevenueResponse getRevenue(FilterParams f) {
    current.requireManager();
    validateFilters(f);
    Granularity gran = f.effectiveGranularity();
    String bucketExpr = sqlBucketExpression("i.issue_date", gran);

    var params = new MapSqlParameterSource();
    String invWhere = buildInvoiceWhere(f, params, true);

    String sql =
        "SELECT "
            + bucketExpr
            + " AS bucket, "
            + "COUNT(i.id) AS inv_count, "
            + "COALESCE(SUM(i.subtotal), 0.00) AS subtotal, "
            + "COALESCE(SUM(i.discount_amount), 0.00) AS discount_amount, "
            + "COALESCE(SUM(i.tax_amount), 0.00) AS tax_amount, "
            + "COALESCE(SUM(i.total), 0.00) AS invoiced_revenue, "
            + "COALESCE(SUM(CASE WHEN i.state = 'CONFIRMED' THEN (i.total - i.amount_paid) ELSE 0.00 END), 0.00) AS outstanding_amount "
            + "FROM invoices i "
            + invWhere
            + " GROUP BY bucket ORDER BY bucket ASC";

    Map<String, RevenueTrendAccumulator> map = new TreeMap<>();
    jdbc.query(
        sql,
        params,
        rs -> {
          String b = rs.getString("bucket");
          var acc = map.computeIfAbsent(b, k -> new RevenueTrendAccumulator());
          acc.invoiceCount = rs.getLong("inv_count");
          acc.subtotal = scale2(rs.getBigDecimal("subtotal"));
          acc.discountAmount = scale2(rs.getBigDecimal("discount_amount"));
          acc.taxAmount = scale2(rs.getBigDecimal("tax_amount"));
          acc.invoicedRevenue = scale2(rs.getBigDecimal("invoiced_revenue"));
          acc.outstandingAmount = scale2(rs.getBigDecimal("outstanding_amount"));
        });

    // Also aggregate completed payments by bucket
    var payParams = new MapSqlParameterSource();
    String payWhere = buildPaymentWhere(f, payParams, true);
    String payBucketExpr = sqlBucketExpression("p.payment_date", gran);
    String paySql =
        "SELECT "
            + payBucketExpr
            + " AS bucket, "
            + "COALESCE(SUM(p.amount), 0.00) AS collected "
            + "FROM payments p "
            + payWhere
            + " GROUP BY bucket ORDER BY bucket ASC";

    jdbc.query(
        paySql,
        payParams,
        rs -> {
          String b = rs.getString("bucket");
          var acc = map.computeIfAbsent(b, k -> new RevenueTrendAccumulator());
          acc.collectedPayments = scale2(rs.getBigDecimal("collected"));
        });

    BigDecimal totSub = ZERO;
    BigDecimal totDisc = ZERO;
    BigDecimal totTax = ZERO;
    BigDecimal totRev = ZERO;
    BigDecimal totCol = ZERO;
    BigDecimal totOut = ZERO;

    List<RevenueTrendPoint> series = new ArrayList<>(map.size());
    for (var entry : map.entrySet()) {
      String period = entry.getKey();
      var acc = entry.getValue();
      totSub = totSub.add(acc.subtotal);
      totDisc = totDisc.add(acc.discountAmount);
      totTax = totTax.add(acc.taxAmount);
      totRev = totRev.add(acc.invoicedRevenue);
      totCol = totCol.add(acc.collectedPayments);
      totOut = totOut.add(acc.outstandingAmount);
      series.add(
          new RevenueTrendPoint(
              period,
              formatBucketLabel(period, gran),
              acc.invoiceCount,
              acc.subtotal,
              acc.discountAmount,
              acc.taxAmount,
              acc.invoicedRevenue,
              acc.collectedPayments,
              acc.outstandingAmount));
    }

    BigDecimal collectionRate =
        totRev.signum() > 0
            ? totCol.multiply(HUNDRED).divide(totRev, 2, RoundingMode.HALF_UP)
            : ZERO;

    return new RevenueResponse(
        gran.name(),
        f.fromDate(),
        f.toDate(),
        totSub,
        totDisc,
        totTax,
        totRev,
        totCol,
        totOut,
        collectionRate,
        series);
  }

  @Transactional(readOnly = true)
  public SubscriptionAnalyticsResponse getSubscriptionAnalytics(FilterParams f) {
    current.requireManager();
    validateFilters(f);
    LocalDate asOf = f.effectiveAsOfDate();
    SubscriptionComputation comp = computeSubscriptionMetrics(f, asOf);

    List<StatusCountItem> byStatus = new ArrayList<>();
    for (SubscriptionState st : SubscriptionState.values()) {
      long count = comp.countByState.getOrDefault(st, 0L);
      BigDecimal stMrr =
          st == SubscriptionState.ACTIVE
              ? comp.activeMrr
              : (st == SubscriptionState.PAUSED ? comp.pausedMrr : ZERO);
      byStatus.add(new StatusCountItem(st.name(), count, stMrr, mrrToArr(stMrr)));
    }

    List<PlanBreakdownItem> byPlan =
        comp.planMap.values().stream()
            .sorted(
                Comparator.comparing((PlanAcc p) -> p.activeMrr, Comparator.reverseOrder())
                    .thenComparing(p -> p.planName))
            .map(
                p ->
                    new PlanBreakdownItem(
                        p.planId,
                        p.planName,
                        p.billingPeriod.name(),
                        p.activeCount,
                        p.pausedCount,
                        p.closedCount,
                        p.totalCount,
                        p.activeMrr,
                        mrrToArr(p.activeMrr)))
            .toList();

    List<FrequencyBreakdownItem> byFrequency = new ArrayList<>();
    for (BillingPeriod bp : BillingPeriod.values()) {
      FreqAcc fa = comp.freqMap.getOrDefault(bp, new FreqAcc(bp));
      byFrequency.add(
          new FrequencyBreakdownItem(
              bp.name(),
              fa.activeCount,
              fa.pausedCount,
              fa.totalCount,
              fa.activeMrr,
              mrrToArr(fa.activeMrr)));
    }

    return new SubscriptionAnalyticsResponse(
        comp.totalCount,
        comp.countByState.getOrDefault(SubscriptionState.ACTIVE, 0L),
        comp.countByState.getOrDefault(SubscriptionState.PAUSED, 0L),
        comp.countByState.getOrDefault(SubscriptionState.CLOSED, 0L),
        comp.newSubscriptionsCount,
        comp.activeMrr,
        mrrToArr(comp.activeMrr),
        comp.pausedMrr,
        mrrToArr(comp.pausedMrr),
        byStatus,
        byPlan,
        byFrequency);
  }

  @Transactional(readOnly = true)
  public PaymentAnalyticsResponse getPaymentAnalytics(FilterParams f) {
    current.requireManager();
    validateFilters(f);
    Granularity gran = f.effectiveGranularity();

    var params = new MapSqlParameterSource();
    String where = buildPaymentWhere(f, params, false);

    // 1. Totals & method breakdown (for COMPLETED payments)
    String summarySql =
        "SELECT p.status, p.method, COUNT(p.id) AS cnt, COALESCE(SUM(p.amount), 0.00) AS amt "
            + "FROM payments p "
            + where
            + " GROUP BY p.status, p.method";

    long completedCount = 0;
    long reversedCount = 0;
    BigDecimal completedTotal = ZERO;
    BigDecimal reversedTotal = ZERO;
    Map<String, MethodAcc> methodMap = new LinkedHashMap<>();
    for (var m : com.brightloop.modules.payment.PaymentMethod.values()) {
      methodMap.put(m.name(), new MethodAcc(m.name()));
    }

    var rows = jdbc.queryForList(summarySql, params);
    for (var row : rows) {
      String status = (String) row.get("status");
      String method = (String) row.get("method");
      long cnt = ((Number) row.get("cnt")).longValue();
      BigDecimal amt = scale2((BigDecimal) row.get("amt"));
      if ("COMPLETED".equals(status)) {
        completedCount += cnt;
        completedTotal = completedTotal.add(amt);
        var ma = methodMap.computeIfAbsent(method, MethodAcc::new);
        ma.count += cnt;
        ma.amount = ma.amount.add(amt);
      } else if ("REVERSED".equals(status)) {
        reversedCount += cnt;
        reversedTotal = reversedTotal.add(amt);
      }
    }

    final BigDecimal finalCompletedTotal = completedTotal;
    List<PaymentMethodBreakdownItem> byMethod =
        methodMap.values().stream()
            .map(
                ma -> {
                  BigDecimal share =
                      finalCompletedTotal.signum() > 0
                          ? ma.amount
                              .multiply(HUNDRED)
                              .divide(finalCompletedTotal, 2, RoundingMode.HALF_UP)
                          : ZERO;
                  return new PaymentMethodBreakdownItem(ma.method, ma.count, ma.amount, share);
                })
            .toList();

    // 2. Time-series
    String bucketExpr = sqlBucketExpression("p.payment_date", gran);
    String trendSql =
        "SELECT "
            + bucketExpr
            + " AS bucket, "
            + "COUNT(CASE WHEN p.status = 'COMPLETED' THEN 1 END) AS comp_cnt, "
            + "COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN p.amount ELSE 0.00 END), 0.00) AS comp_amt, "
            + "COALESCE(SUM(CASE WHEN p.status = 'REVERSED' THEN p.amount ELSE 0.00 END), 0.00) AS rev_amt "
            + "FROM payments p "
            + where
            + " GROUP BY bucket ORDER BY bucket ASC";

    List<PaymentTrendPoint> series =
        jdbc.query(
            trendSql,
            params,
            (rs, rowNum) -> {
              String period = rs.getString("bucket");
              return new PaymentTrendPoint(
                  period,
                  formatBucketLabel(period, gran),
                  rs.getLong("comp_cnt"),
                  scale2(rs.getBigDecimal("comp_amt")),
                  scale2(rs.getBigDecimal("rev_amt")));
            });

    return new PaymentAnalyticsResponse(
        gran.name(),
        f.fromDate(),
        f.toDate(),
        completedCount,
        reversedCount,
        completedTotal,
        reversedTotal,
        byMethod,
        series);
  }

  @Transactional(readOnly = true)
  public ReceivablesResponse getReceivables(FilterParams f) {
    current.requireManager();
    validateFilters(f);
    LocalDate asOf = f.effectiveAsOfDate();
    ReceivablesSummaryTotals totals = queryReceivablesTotals(f, asOf);

    List<AgingBucketItem> buckets =
        List.of(
            new AgingBucketItem("1-30", "1–30 days overdue", totals.cnt1_30, totals.amt1_30),
            new AgingBucketItem("31-60", "31–60 days overdue", totals.cnt31_60, totals.amt31_60),
            new AgingBucketItem("61-90", "61–90 days overdue", totals.cnt61_90, totals.amt61_90),
            new AgingBucketItem("90+", "90+ days overdue", totals.cnt90Plus, totals.amt90Plus));

    var params = new MapSqlParameterSource();
    params.addValue("asOfDate", asOf);
    String extraFilters = buildReceivablesFilterClause(f, params);

    String topSql =
        "SELECT i.id, i.invoice_number, i.subscription_id, i.subscription_number, "
            + "i.customer_id, i.customer_name, i.customer_email, i.plan_name, "
            + "i.issue_date, i.due_date, i.total, i.amount_paid, "
            + "(i.total - i.amount_paid) AS balance_due, "
            + "(CAST(:asOfDate AS DATE) - i.due_date) AS days_overdue "
            + "FROM invoices i "
            + "WHERE i.state = 'CONFIRMED' AND i.amount_paid < i.total AND i.due_date < :asOfDate "
            + extraFilters
            + " ORDER BY days_overdue DESC, balance_due DESC, i.id DESC LIMIT 20";

    List<OverdueInvoiceItem> topOverdue =
        jdbc.query(
            topSql,
            params,
            (rs, rowNum) -> {
              long days = rs.getLong("days_overdue");
              String bucket =
                  days <= 30 ? "1-30" : (days <= 60 ? "31-60" : (days <= 90 ? "61-90" : "90+"));
              return new OverdueInvoiceItem(
                  rs.getLong("id"),
                  rs.getString("invoice_number"),
                  rs.getLong("subscription_id"),
                  rs.getString("subscription_number"),
                  rs.getLong("customer_id"),
                  rs.getString("customer_name"),
                  rs.getString("customer_email"),
                  rs.getString("plan_name"),
                  rs.getObject("issue_date", LocalDate.class),
                  rs.getObject("due_date", LocalDate.class),
                  days,
                  bucket,
                  scale2(rs.getBigDecimal("total")),
                  scale2(rs.getBigDecimal("amount_paid")),
                  scale2(rs.getBigDecimal("balance_due")));
            });

    return new ReceivablesResponse(
        asOf,
        totals.openCount,
        totals.totalOutstanding,
        totals.overdueCount,
        totals.overdueAmount,
        totals.currentNotDueCount,
        totals.currentNotDueAmount,
        buckets,
        topOverdue);
  }

  @Transactional(readOnly = true)
  public RenewalsResponse getRenewals(FilterParams f) {
    current.requireManager();
    validateFilters(f);
    LocalDate asOf = f.effectiveAsOfDate();
    int windowDays = f.effectiveWindowDays();
    SubscriptionComputation subComp = computeSubscriptionMetrics(f, asOf);
    return computeRenewals(f, asOf, windowDays, 50, subComp.mrrBySubId, subComp.cycleBySubId);
  }

  @Transactional(readOnly = true)
  public CustomerPortalOverviewResponse getCustomerPortalOverview() {
    var user = current.get();
    Customer customer;
    if (user.role == Role.CUSTOMER) {
      customer =
          customers
              .findByUserId(user.id)
              .map(v -> org.hibernate.Hibernate.unproxy(v, Customer.class))
              .orElseThrow(() -> ApiException.notFound("Customer profile"));
    } else {
      throw ApiException.forbidden();
    }

    LocalDate today = LocalDate.now();
    FilterParams customerFilter =
        new FilterParams(
            null, null, today, Granularity.MONTHLY, customer.id, null, null, null, null, null, 30);

    SubscriptionComputation subComp = computeSubscriptionMetrics(customerFilter, today);
    InvoiceSummaryTotals invTotals = queryInvoiceSummaryTotals(customerFilter);
    BigDecimal totalPaid = queryCollectedPaymentsTotal(customerFilter);
    ReceivablesSummaryTotals recTotals = queryReceivablesTotals(customerFilter, today);

    List<SubscriptionDtos.View> subViews =
        subscriptions
            .search(null, null, customer.id, org.springframework.data.domain.PageRequest.of(0, 50))
            .getContent()
            .stream()
            .map(SubscriptionDtos::view)
            .toList();

    LocalDate earliestNextBilling =
        subViews.stream()
            .filter(s -> s.state() == SubscriptionState.ACTIVE && s.nextBillingDate() != null)
            .map(SubscriptionDtos.View::nextBillingDate)
            .min(LocalDate::compareTo)
            .orElse(null);

    List<RecentInvoiceItem> recentInvoices = queryRecentInvoices(customerFilter, today, 10);
    List<RecentPaymentItem> recentPayments = queryRecentPayments(customerFilter, 10);

    return new CustomerPortalOverviewResponse(
        customer.id,
        customer.name,
        customer.email,
        customer.companyName,
        subComp.countByState.getOrDefault(SubscriptionState.ACTIVE, 0L),
        subComp.countByState.getOrDefault(SubscriptionState.PAUSED, 0L),
        subComp.countByState.getOrDefault(SubscriptionState.CLOSED, 0L),
        subComp.totalCount,
        subComp.activeMrr,
        invTotals.total,
        totalPaid,
        recTotals.totalOutstanding,
        recTotals.openCount,
        recTotals.overdueCount,
        recTotals.overdueAmount,
        earliestNextBilling,
        subViews,
        recentInvoices,
        recentPayments);
  }

  @Transactional(readOnly = true)
  public String exportCsv(String section, FilterParams f) {
    current.requireManager();
    validateFilters(f);
    String sec = section == null ? "revenue" : section.trim().toLowerCase(Locale.ROOT);
    StringBuilder sb = new StringBuilder();
    switch (sec) {
      case "subscriptions" -> {
        var data = getSubscriptionAnalytics(f);
        sb.append("Plan ID,Plan Name,Billing Period,Active,Paused,Closed,Total,MRR,ARR\n");
        for (var p : data.byPlan()) {
          sb.append(p.planId())
              .append(',')
              .append(csvEscape(p.planName()))
              .append(',')
              .append(p.billingPeriod())
              .append(',')
              .append(p.activeCount())
              .append(',')
              .append(p.pausedCount())
              .append(',')
              .append(p.closedCount())
              .append(',')
              .append(p.totalCount())
              .append(',')
              .append(p.mrr())
              .append(',')
              .append(p.arr())
              .append('\n');
        }
      }
      case "payments" -> {
        var data = getPaymentAnalytics(f);
        sb.append("Period,Label,Completed Count,Collected Amount,Reversed Amount\n");
        for (var pt : data.series()) {
          sb.append(csvEscape(pt.period()))
              .append(',')
              .append(csvEscape(pt.label()))
              .append(',')
              .append(pt.paymentCount())
              .append(',')
              .append(pt.collectedAmount())
              .append(',')
              .append(pt.reversedAmount())
              .append('\n');
        }
      }
      case "receivables" -> {
        var data = getReceivables(f);
        sb.append(
            "Invoice Number,Subscription Number,Customer,Email,Plan,Issue Date,Due Date,Days Overdue,Aging Bucket,Total,Amount Paid,Balance Due\n");
        for (var item : data.topOverdueInvoices()) {
          sb.append(csvEscape(item.invoiceNumber()))
              .append(',')
              .append(csvEscape(item.subscriptionNumber()))
              .append(',')
              .append(csvEscape(item.customerName()))
              .append(',')
              .append(csvEscape(item.customerEmail()))
              .append(',')
              .append(csvEscape(item.planName()))
              .append(',')
              .append(item.issueDate())
              .append(',')
              .append(item.dueDate())
              .append(',')
              .append(item.daysOverdue())
              .append(',')
              .append(csvEscape(item.agingBucket()))
              .append(',')
              .append(item.total())
              .append(',')
              .append(item.amountPaid())
              .append(',')
              .append(item.balanceDue())
              .append('\n');
        }
      }
      default -> {
        var data = getRevenue(f);
        sb.append(
            "Period,Label,Invoices,Pre-Tax Subtotal,Discounts,Taxes,Invoiced Revenue,Collected Payments,Outstanding Balance\n");
        for (var row : data.series()) {
          sb.append(csvEscape(row.period()))
              .append(',')
              .append(csvEscape(row.label()))
              .append(',')
              .append(row.invoiceCount())
              .append(',')
              .append(row.subtotal())
              .append(',')
              .append(row.discountAmount())
              .append(',')
              .append(row.taxAmount())
              .append(',')
              .append(row.invoicedRevenue())
              .append(',')
              .append(row.collectedPayments())
              .append(',')
              .append(row.outstandingAmount())
              .append('\n');
        }
      }
    }
    return sb.toString();
  }

  private static String csvEscape(String value) {
    if (value == null) return "";
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  // --- Internal SQL & Aggregation Helpers ---

  private SubscriptionComputation computeSubscriptionMetrics(FilterParams f, LocalDate asOfDate) {
    var comp = new SubscriptionComputation();
    List<Discount> activeDiscounts =
        discounts.findAllActiveWithCapacity().stream()
            .filter(
                d ->
                    d.status == AccountStatus.ACTIVE
                        && (d.validFrom == null || !asOfDate.isBefore(d.validFrom))
                        && (d.validUntil == null || !asOfDate.isAfter(d.validUntil))
                        && (d.usageLimit == null || d.usedCount < d.usageLimit))
            .toList();

    var params = new MapSqlParameterSource();
    String where = buildSubscriptionWhere(f, params);

    LocalDate newSince = f.fromDate() != null ? f.fromDate() : asOfDate.minusDays(30);
    LocalDate newUntil = f.toDate() != null ? f.toDate() : asOfDate;

    if (activeDiscounts.isEmpty()) {
      // Fast single-query pre-tax subtotal aggregation per subscription
      String sql =
          "SELECT s.id, s.state, s.start_date, s.plan_id, p.name AS plan_name, p.billing_period, "
              + "COALESCE(SUM(sl.unit_price * sl.quantity), 0.00) AS pre_tax_subtotal "
              + "FROM subscriptions s "
              + "JOIN plans p ON p.id = s.plan_id "
              + "LEFT JOIN subscription_lines sl ON sl.subscription_id = s.id "
              + where
              + " GROUP BY s.id, s.state, s.start_date, s.plan_id, p.name, p.billing_period";

      jdbc.query(
          sql,
          params,
          rs -> {
            long subId = rs.getLong("id");
            SubscriptionState st = SubscriptionState.valueOf(rs.getString("state"));
            LocalDate startDate = rs.getObject("start_date", LocalDate.class);
            long planId = rs.getLong("plan_id");
            String planName = rs.getString("plan_name");
            BillingPeriod bp = BillingPeriod.valueOf(rs.getString("billing_period"));
            BigDecimal netCycle = scale2(rs.getBigDecimal("pre_tax_subtotal"));
            accumulateSubscription(
                comp, subId, st, startDate, planId, planName, bp, netCycle, newSince, newUntil);
          });
      return comp;
    }

    // When active discounts exist, fetch subscription + line pre-tax subtotals in one query
    String sql =
        "SELECT s.id, s.state, s.start_date, s.plan_id, p.name AS plan_name, p.billing_period, "
            + "sl.product_id, sl.quantity, sl.unit_price "
            + "FROM subscriptions s "
            + "JOIN plans p ON p.id = s.plan_id "
            + "LEFT JOIN subscription_lines sl ON sl.subscription_id = s.id "
            + where
            + " ORDER BY s.id ASC";

    Map<Long, SubHeader> subs = new LinkedHashMap<>();
    jdbc.query(
        sql,
        params,
        rs -> {
          long subId = rs.getLong("id");
          SubHeader hdr =
              subs.computeIfAbsent(
                  subId,
                  id -> {
                    try {
                      return new SubHeader(
                          id,
                          SubscriptionState.valueOf(rs.getString("state")),
                          rs.getObject("start_date", LocalDate.class),
                          rs.getLong("plan_id"),
                          rs.getString("plan_name"),
                          BillingPeriod.valueOf(rs.getString("billing_period")));
                    } catch (java.sql.SQLException e) {
                      throw new RuntimeException(e);
                    }
                  });
          Long prodId = (Long) rs.getObject("product_id");
          if (prodId != null) {
            int qty = rs.getInt("quantity");
            BigDecimal price = rs.getBigDecimal("unit_price");
            hdr.lines.add(new SubLineData(prodId, qty, Rules.lineSubtotal(price, qty)));
          }
        });

    for (SubHeader hdr : subs.values()) {
      BigDecimal subSubtotal =
          hdr.lines.stream().map(SubLineData::subtotal).reduce(ZERO, BigDecimal::add);
      int totalQty = hdr.lines.stream().mapToInt(SubLineData::quantity).sum();
      BigDecimal discountAmt =
          evaluateRecurringDiscount(hdr.id, hdr.lines, totalQty, subSubtotal, activeDiscounts);
      BigDecimal netCycle = subSubtotal.subtract(discountAmt).max(ZERO).setScale(2, RoundingMode.HALF_UP);
      accumulateSubscription(
          comp,
          hdr.id,
          hdr.state,
          hdr.startDate,
          hdr.planId,
          hdr.planName,
          hdr.billingPeriod,
          netCycle,
          newSince,
          newUntil);
    }

    return comp;
  }

  private static void accumulateSubscription(
      SubscriptionComputation comp,
      long subId,
      SubscriptionState st,
      LocalDate startDate,
      long planId,
      String planName,
      BillingPeriod bp,
      BigDecimal netCycle,
      LocalDate newSince,
      LocalDate newUntil) {
    comp.totalCount++;
    comp.countByState.merge(st, 1L, Long::sum);
    if (startDate != null && !startDate.isBefore(newSince) && !startDate.isAfter(newUntil)) {
      comp.newSubscriptionsCount++;
    }

    BigDecimal subMrr = normalizeToMrr(netCycle, bp);
    comp.cycleBySubId.put(subId, netCycle);
    comp.mrrBySubId.put(subId, subMrr);

    PlanAcc planAcc =
        comp.planMap.computeIfAbsent(planId, id -> new PlanAcc(id, planName, bp));
    planAcc.totalCount++;

    FreqAcc freqAcc = comp.freqMap.computeIfAbsent(bp, FreqAcc::new);
    freqAcc.totalCount++;

    if (st == SubscriptionState.ACTIVE) {
      comp.activeMrr = comp.activeMrr.add(subMrr);
      planAcc.activeCount++;
      planAcc.activeMrr = planAcc.activeMrr.add(subMrr);
      freqAcc.activeCount++;
      freqAcc.activeMrr = freqAcc.activeMrr.add(subMrr);
    } else if (st == SubscriptionState.PAUSED) {
      comp.pausedMrr = comp.pausedMrr.add(subMrr);
      planAcc.pausedCount++;
      freqAcc.pausedCount++;
    } else if (st == SubscriptionState.CLOSED) {
      planAcc.closedCount++;
    }
  }

  private static BigDecimal evaluateRecurringDiscount(
      long subId,
      List<SubLineData> lines,
      int totalQty,
      BigDecimal subSubtotal,
      List<Discount> activeDiscounts) {
    if (lines.isEmpty() || subSubtotal.signum() <= 0 || activeDiscounts.isEmpty()) {
      return ZERO;
    }
    BigDecimal bestSavings = ZERO;
    int bestSpecificity = -1;
    long bestId = -1;

    for (Discount d : activeDiscounts) {
      Long dSubId = d.subscription != null ? d.subscription.getId() : null;
      Long dProdId = d.product != null ? d.product.getId() : null;
      if (dSubId != null && !dSubId.equals(subId)) continue;

      BigDecimal savings;
      if (d.scope == DiscountScope.PRODUCT) {
        if (dProdId == null) continue;
        var matching = lines.stream().filter(l -> l.productId.equals(dProdId)).toList();
        if (matching.isEmpty()) continue;
        int prodQty = matching.stream().mapToInt(SubLineData::quantity).sum();
        BigDecimal prodSub =
            matching.stream().map(SubLineData::subtotal).reduce(ZERO, BigDecimal::add);
        if (d.minimumQuantity != null && prodQty < d.minimumQuantity) continue;
        if (d.minimumPurchase != null && prodSub.compareTo(d.minimumPurchase) < 0) continue;
        savings = Rules.discountAmount(prodSub, d.type.name(), d.value);
      } else {
        if (dProdId != null && lines.stream().noneMatch(l -> l.productId.equals(dProdId))) {
          continue;
        }
        if (d.minimumQuantity != null && totalQty < d.minimumQuantity) continue;
        if (d.minimumPurchase != null && subSubtotal.compareTo(d.minimumPurchase) < 0) continue;
        savings = Rules.discountAmount(subSubtotal, d.type.name(), d.value);
      }

      if (savings.signum() <= 0) continue;
      int specificity = (dSubId != null || dProdId != null) ? 1 : 0;
      if (savings.compareTo(bestSavings) > 0
          || (savings.compareTo(bestSavings) == 0 && specificity > bestSpecificity)
          || (savings.compareTo(bestSavings) == 0
              && specificity == bestSpecificity
              && d.id > bestId)) {
        bestSavings = savings;
        bestSpecificity = specificity;
        bestId = d.id;
      }
    }
    return bestSavings;
  }

  private InvoiceSummaryTotals queryInvoiceSummaryTotals(FilterParams f) {
    var params = new MapSqlParameterSource();
    String where = buildInvoiceWhere(f, params, true);
    String sql =
        "SELECT COALESCE(SUM(i.subtotal), 0.00) AS sub, "
            + "COALESCE(SUM(i.discount_amount), 0.00) AS disc, "
            + "COALESCE(SUM(i.tax_amount), 0.00) AS tax, "
            + "COALESCE(SUM(i.total), 0.00) AS tot "
            + "FROM invoices i "
            + where;
    return jdbc.queryForObject(
        sql,
        params,
        (rs, rowNum) ->
            new InvoiceSummaryTotals(
                scale2(rs.getBigDecimal("sub")),
                scale2(rs.getBigDecimal("disc")),
                scale2(rs.getBigDecimal("tax")),
                scale2(rs.getBigDecimal("tot"))));
  }

  private BigDecimal queryCollectedPaymentsTotal(FilterParams f) {
    var params = new MapSqlParameterSource();
    String where = buildPaymentWhere(f, params, true);
    String sql = "SELECT COALESCE(SUM(p.amount), 0.00) FROM payments p " + where;
    BigDecimal val = jdbc.queryForObject(sql, params, BigDecimal.class);
    return scale2(val);
  }

  private ReceivablesSummaryTotals queryReceivablesTotals(FilterParams f, LocalDate asOfDate) {
    var params = new MapSqlParameterSource();
    params.addValue("asOfDate", asOfDate);
    String extra = buildReceivablesFilterClause(f, params);

    String sql =
        "SELECT "
            + "COUNT(i.id) AS open_cnt, "
            + "COALESCE(SUM(i.total - i.amount_paid), 0.00) AS total_out, "
            + "COUNT(CASE WHEN i.due_date < :asOfDate THEN 1 END) AS overdue_cnt, "
            + "COALESCE(SUM(CASE WHEN i.due_date < :asOfDate THEN (i.total - i.amount_paid) ELSE 0.00 END), 0.00) AS overdue_amt, "
            + "COUNT(CASE WHEN i.due_date >= :asOfDate THEN 1 END) AS current_cnt, "
            + "COALESCE(SUM(CASE WHEN i.due_date >= :asOfDate THEN (i.total - i.amount_paid) ELSE 0.00 END), 0.00) AS current_amt, "
            + "COUNT(CASE WHEN (CAST(:asOfDate AS DATE) - i.due_date) BETWEEN 1 AND 30 THEN 1 END) AS cnt_1_30, "
            + "COALESCE(SUM(CASE WHEN (CAST(:asOfDate AS DATE) - i.due_date) BETWEEN 1 AND 30 THEN (i.total - i.amount_paid) ELSE 0.00 END), 0.00) AS amt_1_30, "
            + "COUNT(CASE WHEN (CAST(:asOfDate AS DATE) - i.due_date) BETWEEN 31 AND 60 THEN 1 END) AS cnt_31_60, "
            + "COALESCE(SUM(CASE WHEN (CAST(:asOfDate AS DATE) - i.due_date) BETWEEN 31 AND 60 THEN (i.total - i.amount_paid) ELSE 0.00 END), 0.00) AS amt_31_60, "
            + "COUNT(CASE WHEN (CAST(:asOfDate AS DATE) - i.due_date) BETWEEN 61 AND 90 THEN 1 END) AS cnt_61_90, "
            + "COALESCE(SUM(CASE WHEN (CAST(:asOfDate AS DATE) - i.due_date) BETWEEN 61 AND 90 THEN (i.total - i.amount_paid) ELSE 0.00 END), 0.00) AS amt_61_90, "
            + "COUNT(CASE WHEN (CAST(:asOfDate AS DATE) - i.due_date) > 90 THEN 1 END) AS cnt_90_plus, "
            + "COALESCE(SUM(CASE WHEN (CAST(:asOfDate AS DATE) - i.due_date) > 90 THEN (i.total - i.amount_paid) ELSE 0.00 END), 0.00) AS amt_90_plus "
            + "FROM invoices i "
            + "WHERE i.state = 'CONFIRMED' AND i.amount_paid < i.total "
            + extra;

    return jdbc.queryForObject(
        sql,
        params,
        (rs, rowNum) ->
            new ReceivablesSummaryTotals(
                rs.getLong("open_cnt"),
                scale2(rs.getBigDecimal("total_out")),
                rs.getLong("overdue_cnt"),
                scale2(rs.getBigDecimal("overdue_amt")),
                rs.getLong("current_cnt"),
                scale2(rs.getBigDecimal("current_amt")),
                rs.getLong("cnt_1_30"),
                scale2(rs.getBigDecimal("amt_1_30")),
                rs.getLong("cnt_31_60"),
                scale2(rs.getBigDecimal("amt_31_60")),
                rs.getLong("cnt_61_90"),
                scale2(rs.getBigDecimal("amt_61_90")),
                rs.getLong("cnt_90_plus"),
                scale2(rs.getBigDecimal("amt_90_plus"))));
  }

  private List<RecentInvoiceItem> queryRecentInvoices(
      FilterParams f, LocalDate asOfDate, int limit) {
    var params = new MapSqlParameterSource();
    params.addValue("limit", limit);
    String where = buildInvoiceWhere(f, params, false);
    String sql =
        "SELECT i.id, i.invoice_number, i.subscription_id, i.subscription_number, "
            + "i.customer_id, i.customer_name, i.plan_name, i.issue_date, i.due_date, i.state, "
            + "i.subtotal, i.discount_amount, i.tax_amount, i.total, i.amount_paid, "
            + "(i.total - i.amount_paid) AS balance_due "
            + "FROM invoices i "
            + where
            + " ORDER BY i.issue_date DESC, i.id DESC LIMIT :limit";
    return jdbc.query(
        sql,
        params,
        (rs, rowNum) -> {
          String state = rs.getString("state");
          LocalDate dueDate = rs.getObject("due_date", LocalDate.class);
          BigDecimal balanceDue =
              "CANCELLED".equals(state) ? ZERO : scale2(rs.getBigDecimal("balance_due"));
          boolean overdue =
              "CONFIRMED".equals(state)
                  && balanceDue.signum() > 0
                  && dueDate != null
                  && dueDate.isBefore(asOfDate);
          return new RecentInvoiceItem(
              rs.getLong("id"),
              rs.getString("invoice_number"),
              rs.getLong("subscription_id"),
              rs.getString("subscription_number"),
              rs.getLong("customer_id"),
              rs.getString("customer_name"),
              rs.getString("plan_name"),
              rs.getObject("issue_date", LocalDate.class),
              dueDate,
              state,
              scale2(rs.getBigDecimal("subtotal")),
              scale2(rs.getBigDecimal("discount_amount")),
              scale2(rs.getBigDecimal("tax_amount")),
              scale2(rs.getBigDecimal("total")),
              scale2(rs.getBigDecimal("amount_paid")),
              balanceDue,
              overdue);
        });
  }

  private List<RecentPaymentItem> queryRecentPayments(FilterParams f, int limit) {
    var params = new MapSqlParameterSource();
    params.addValue("limit", limit);
    String where = buildPaymentWhere(f, params, false);
    String sql =
        "SELECT p.id, p.payment_reference, p.invoice_id, p.invoice_number, "
            + "p.customer_id, p.customer_name, p.method, p.amount, p.payment_date, p.status, p.recorded_by "
            + "FROM payments p "
            + where
            + " ORDER BY p.payment_date DESC, p.id DESC LIMIT :limit";
    return jdbc.query(
        sql,
        params,
        (rs, rowNum) ->
            new RecentPaymentItem(
                rs.getLong("id"),
                rs.getString("payment_reference"),
                rs.getLong("invoice_id"),
                rs.getString("invoice_number"),
                rs.getLong("customer_id"),
                rs.getString("customer_name"),
                rs.getString("method"),
                scale2(rs.getBigDecimal("amount")),
                rs.getObject("payment_date", LocalDate.class),
                rs.getString("status"),
                rs.getString("recorded_by")));
  }

  private RenewalsResponse computeRenewals(
      FilterParams f,
      LocalDate asOfDate,
      int windowDays,
      int limit,
      Map<Long, BigDecimal> mrrBySubId,
      Map<Long, BigDecimal> cycleBySubId) {
    LocalDate horizon = asOfDate.plusDays(windowDays);
    var params = new MapSqlParameterSource();
    params.addValue("asOfDate", asOfDate);
    params.addValue("horizon", horizon);
    params.addValue("limit", limit);

    String extra = buildSubscriptionFilterTail(f, params);

    String countUpcomingSql =
        "SELECT COUNT(s.id) FROM subscriptions s JOIN plans p ON p.id = s.plan_id "
            + "WHERE s.state = 'ACTIVE' AND s.next_billing_date BETWEEN :asOfDate AND :horizon AND s.next_billing_date <= s.expiry_date"
            + extra;
    long upcomingCount =
        Optional.ofNullable(jdbc.queryForObject(countUpcomingSql, params, Long.class)).orElse(0L);

    String upcomingSql =
        "SELECT s.id, s.number, s.customer_id, c.name AS customer_name, s.plan_id, s.plan_name, "
            + "p.billing_period, p.renewal_allowed, s.state, s.next_billing_date, s.expiry_date, s.total "
            + "FROM subscriptions s "
            + "JOIN plans p ON p.id = s.plan_id "
            + "JOIN customers c ON c.id = s.customer_id "
            + "WHERE s.state = 'ACTIVE' AND s.next_billing_date BETWEEN :asOfDate AND :horizon AND s.next_billing_date <= s.expiry_date"
            + extra
            + " ORDER BY s.next_billing_date ASC, s.id ASC LIMIT :limit";

    List<UpcomingRenewalItem> upcoming =
        jdbc.query(
            upcomingSql,
            params,
            (rs, rowNum) -> {
              long id = rs.getLong("id");
              LocalDate nextBill = rs.getObject("next_billing_date", LocalDate.class);
              LocalDate expiry = rs.getObject("expiry_date", LocalDate.class);
              BillingPeriod bp = BillingPeriod.valueOf(rs.getString("billing_period"));
              BigDecimal cycle =
                  cycleBySubId.getOrDefault(id, scale2(rs.getBigDecimal("total")));
              BigDecimal mrr = mrrBySubId.getOrDefault(id, normalizeToMrr(cycle, bp));
              return new UpcomingRenewalItem(
                  id,
                  rs.getString("number"),
                  rs.getLong("customer_id"),
                  rs.getString("customer_name"),
                  rs.getLong("plan_id"),
                  rs.getString("plan_name"),
                  bp.name(),
                  rs.getString("state"),
                  nextBill,
                  expiry,
                  ChronoUnit.DAYS.between(asOfDate, nextBill),
                  rs.getBoolean("renewal_allowed"),
                  cycle,
                  mrr);
            });

    String countExpiringSql =
        "SELECT COUNT(s.id) FROM subscriptions s JOIN plans p ON p.id = s.plan_id "
            + "WHERE s.state IN ('ACTIVE', 'PAUSED') AND s.expiry_date BETWEEN :asOfDate AND :horizon"
            + extra;
    long expiringCount =
        Optional.ofNullable(jdbc.queryForObject(countExpiringSql, params, Long.class)).orElse(0L);

    String expiringSql =
        "SELECT s.id, s.number, s.customer_id, c.name AS customer_name, s.plan_id, s.plan_name, "
            + "p.billing_period, p.renewal_allowed, s.state, s.expiry_date, s.total "
            + "FROM subscriptions s "
            + "JOIN plans p ON p.id = s.plan_id "
            + "JOIN customers c ON c.id = s.customer_id "
            + "WHERE s.state IN ('ACTIVE', 'PAUSED') AND s.expiry_date BETWEEN :asOfDate AND :horizon"
            + extra
            + " ORDER BY s.expiry_date ASC, s.id ASC LIMIT :limit";

    List<ExpiringSubscriptionItem> expiring =
        jdbc.query(
            expiringSql,
            params,
            (rs, rowNum) -> {
              long id = rs.getLong("id");
              LocalDate expiry = rs.getObject("expiry_date", LocalDate.class);
              BillingPeriod bp = BillingPeriod.valueOf(rs.getString("billing_period"));
              BigDecimal cycle =
                  cycleBySubId.getOrDefault(id, scale2(rs.getBigDecimal("total")));
              BigDecimal mrr = mrrBySubId.getOrDefault(id, normalizeToMrr(cycle, bp));
              return new ExpiringSubscriptionItem(
                  id,
                  rs.getString("number"),
                  rs.getLong("customer_id"),
                  rs.getString("customer_name"),
                  rs.getLong("plan_id"),
                  rs.getString("plan_name"),
                  bp.name(),
                  rs.getString("state"),
                  expiry,
                  ChronoUnit.DAYS.between(asOfDate, expiry),
                  rs.getBoolean("renewal_allowed"),
                  cycle,
                  mrr);
            });

    return new RenewalsResponse(asOfDate, windowDays, upcomingCount, expiringCount, upcoming, expiring);
  }

  private static String buildSubscriptionWhere(FilterParams f, MapSqlParameterSource params) {
    List<String> clauses = new ArrayList<>();
    if (f.customerId() != null) {
      clauses.add("s.customer_id = :customerId");
      params.addValue("customerId", f.customerId());
    }
    if (f.planId() != null) {
      clauses.add("s.plan_id = :planId");
      params.addValue("planId", f.planId());
    }
    if (f.billingPeriod() != null) {
      clauses.add("p.billing_period = :billingPeriod");
      params.addValue("billingPeriod", f.billingPeriod().name());
    }
    if (f.subscriptionState() != null) {
      clauses.add("s.state = :subscriptionState");
      params.addValue("subscriptionState", f.subscriptionState().name());
    }
    if (f.fromDate() != null) {
      clauses.add("s.start_date >= :fromDate");
      params.addValue("fromDate", f.fromDate());
    }
    if (f.toDate() != null) {
      clauses.add("s.start_date <= :toDate");
      params.addValue("toDate", f.toDate());
    }
    return clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
  }

  private static String buildSubscriptionFilterTail(FilterParams f, MapSqlParameterSource params) {
    StringBuilder sb = new StringBuilder();
    if (f.customerId() != null) {
      sb.append(" AND s.customer_id = :customerId");
      params.addValue("customerId", f.customerId());
    }
    if (f.planId() != null) {
      sb.append(" AND s.plan_id = :planId");
      params.addValue("planId", f.planId());
    }
    if (f.billingPeriod() != null) {
      sb.append(" AND p.billing_period = :billingPeriod");
      params.addValue("billingPeriod", f.billingPeriod().name());
    }
    return sb.toString();
  }

  private static String buildInvoiceWhere(
      FilterParams f, MapSqlParameterSource params, boolean revenueRecognizedOnly) {
    List<String> clauses = new ArrayList<>();
    if (f.invoiceState() != null) {
      clauses.add("i.state = :invoiceState");
      params.addValue("invoiceState", f.invoiceState().name());
    } else if (revenueRecognizedOnly) {
      clauses.add("i.state IN ('CONFIRMED', 'PAID')");
    }
    if (f.customerId() != null) {
      clauses.add("i.customer_id = :customerId");
      params.addValue("customerId", f.customerId());
    }
    if (f.planId() != null) {
      clauses.add("i.plan_id = :planId");
      params.addValue("planId", f.planId());
    }
    if (f.billingPeriod() != null) {
      clauses.add("i.billing_period = :billingPeriod");
      params.addValue("billingPeriod", f.billingPeriod().name());
    }
    if (f.fromDate() != null) {
      clauses.add("i.issue_date >= :fromDate");
      params.addValue("fromDate", f.fromDate());
    }
    if (f.toDate() != null) {
      clauses.add("i.issue_date <= :toDate");
      params.addValue("toDate", f.toDate());
    }
    return clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
  }

  private static String buildReceivablesFilterClause(FilterParams f, MapSqlParameterSource params) {
    StringBuilder sb = new StringBuilder();
    if (f.customerId() != null) {
      sb.append(" AND i.customer_id = :customerId");
      params.addValue("customerId", f.customerId());
    }
    if (f.planId() != null) {
      sb.append(" AND i.plan_id = :planId");
      params.addValue("planId", f.planId());
    }
    if (f.billingPeriod() != null) {
      sb.append(" AND i.billing_period = :billingPeriod");
      params.addValue("billingPeriod", f.billingPeriod().name());
    }
    if (f.fromDate() != null) {
      sb.append(" AND i.issue_date >= :fromDate");
      params.addValue("fromDate", f.fromDate());
    }
    if (f.toDate() != null) {
      sb.append(" AND i.issue_date <= :toDate");
      params.addValue("toDate", f.toDate());
    }
    return sb.toString();
  }

  private static String buildPaymentWhere(
      FilterParams f, MapSqlParameterSource params, boolean completedOnly) {
    List<String> clauses = new ArrayList<>();
    if (completedOnly) {
      clauses.add("p.status = 'COMPLETED'");
    }
    if (f.customerId() != null) {
      clauses.add("p.customer_id = :customerId");
      params.addValue("customerId", f.customerId());
    }
    if (f.paymentMethod() != null) {
      clauses.add("p.method = :paymentMethod");
      params.addValue("paymentMethod", f.paymentMethod().name());
    }
    if (f.fromDate() != null) {
      clauses.add("p.payment_date >= :fromDate");
      params.addValue("fromDate", f.fromDate());
    }
    if (f.toDate() != null) {
      clauses.add("p.payment_date <= :toDate");
      params.addValue("toDate", f.toDate());
    }
    if (f.planId() != null || f.billingPeriod() != null) {
      clauses.add(
          "EXISTS (SELECT 1 FROM invoices inv WHERE inv.id = p.invoice_id"
              + (f.planId() != null ? " AND inv.plan_id = :planId" : "")
              + (f.billingPeriod() != null ? " AND inv.billing_period = :billingPeriod" : "")
              + ")");
      if (f.planId() != null) params.addValue("planId", f.planId());
      if (f.billingPeriod() != null) params.addValue("billingPeriod", f.billingPeriod().name());
    }
    return clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
  }

  private static String sqlBucketExpression(String column, Granularity granularity) {
    return switch (granularity) {
      case DAILY -> "to_char(" + column + ", 'YYYY-MM-DD')";
      case MONTHLY -> "to_char(" + column + ", 'YYYY-MM')";
      case YEARLY -> "to_char(" + column + ", 'YYYY')";
    };
  }

  private static String formatBucketLabel(String period, Granularity granularity) {
    try {
      if (granularity == Granularity.MONTHLY && period.length() == 7) {
        return YearMonth.parse(period).format(MONTH_LABEL_FMT);
      }
    } catch (Exception ignored) {
    }
    return period;
  }

  private static BigDecimal scale2(BigDecimal val) {
    return val == null ? ZERO : val.setScale(2, RoundingMode.HALF_UP);
  }

  private record SubLineData(Long productId, int quantity, BigDecimal subtotal) {}

  private static final class SubHeader {
    final long id;
    final SubscriptionState state;
    final LocalDate startDate;
    final long planId;
    final String planName;
    final BillingPeriod billingPeriod;
    final List<SubLineData> lines = new ArrayList<>();

    SubHeader(
        long id,
        SubscriptionState state,
        LocalDate startDate,
        long planId,
        String planName,
        BillingPeriod billingPeriod) {
      this.id = id;
      this.state = state;
      this.startDate = startDate;
      this.planId = planId;
      this.planName = planName;
      this.billingPeriod = billingPeriod;
    }
  }

  private static final class PlanAcc {
    final long planId;
    final String planName;
    final BillingPeriod billingPeriod;
    long activeCount = 0;
    long pausedCount = 0;
    long closedCount = 0;
    long totalCount = 0;
    BigDecimal activeMrr = ZERO;

    PlanAcc(long planId, String planName, BillingPeriod billingPeriod) {
      this.planId = planId;
      this.planName = planName;
      this.billingPeriod = billingPeriod;
    }
  }

  private static final class FreqAcc {
    final BillingPeriod billingPeriod;
    long activeCount = 0;
    long pausedCount = 0;
    long totalCount = 0;
    BigDecimal activeMrr = ZERO;

    FreqAcc(BillingPeriod billingPeriod) {
      this.billingPeriod = billingPeriod;
    }
  }

  private static final class SubscriptionComputation {
    long totalCount = 0;
    long newSubscriptionsCount = 0;
    BigDecimal activeMrr = ZERO;
    BigDecimal pausedMrr = ZERO;
    final Map<SubscriptionState, Long> countByState = new EnumMap<>(SubscriptionState.class);
    final Map<Long, PlanAcc> planMap = new LinkedHashMap<>();
    final Map<BillingPeriod, FreqAcc> freqMap = new EnumMap<>(BillingPeriod.class);
    final Map<Long, BigDecimal> mrrBySubId = new HashMap<>();
    final Map<Long, BigDecimal> cycleBySubId = new HashMap<>();
  }

  private static final class RevenueTrendAccumulator {
    long invoiceCount = 0;
    BigDecimal subtotal = ZERO;
    BigDecimal discountAmount = ZERO;
    BigDecimal taxAmount = ZERO;
    BigDecimal invoicedRevenue = ZERO;
    BigDecimal collectedPayments = ZERO;
    BigDecimal outstandingAmount = ZERO;
  }

  private static final class MethodAcc {
    final String method;
    long count = 0;
    BigDecimal amount = ZERO;

    MethodAcc(String method) {
      this.method = method;
    }
  }

  private record InvoiceSummaryTotals(
      BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {}

  private record ReceivablesSummaryTotals(
      long openCount,
      BigDecimal totalOutstanding,
      long overdueCount,
      BigDecimal overdueAmount,
      long currentNotDueCount,
      BigDecimal currentNotDueAmount,
      long cnt1_30,
      BigDecimal amt1_30,
      long cnt31_60,
      BigDecimal amt31_60,
      long cnt61_90,
      BigDecimal amt61_90,
      long cnt90Plus,
      BigDecimal amt90Plus) {}
}
