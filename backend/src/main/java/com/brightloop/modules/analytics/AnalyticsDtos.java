package com.brightloop.modules.analytics;

import com.brightloop.modules.invoice.InvoiceState;
import com.brightloop.modules.payment.PaymentMethod;
import com.brightloop.modules.plan.BillingPeriod;
import com.brightloop.modules.subscription.SubscriptionDtos;
import com.brightloop.modules.subscription.SubscriptionState;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {
  private AnalyticsDtos() {}

  public enum Granularity {
    DAILY,
    MONTHLY,
    YEARLY
  }

  public record FilterParams(
      LocalDate fromDate,
      LocalDate toDate,
      LocalDate asOfDate,
      Granularity granularity,
      Long customerId,
      Long planId,
      BillingPeriod billingPeriod,
      SubscriptionState subscriptionState,
      InvoiceState invoiceState,
      PaymentMethod paymentMethod,
      Integer windowDays) {
    public LocalDate effectiveAsOfDate() {
      if (asOfDate != null) return asOfDate;
      if (toDate != null) return toDate;
      return LocalDate.now();
    }

    public Granularity effectiveGranularity() {
      return granularity != null ? granularity : Granularity.MONTHLY;
    }

    public int effectiveWindowDays() {
      if (windowDays == null) return 30;
      return Math.max(1, Math.min(365, windowDays));
    }
  }

  public record RecentInvoiceItem(
      Long id,
      String invoiceNumber,
      Long subscriptionId,
      String subscriptionNumber,
      Long customerId,
      String customerName,
      String planName,
      LocalDate issueDate,
      LocalDate dueDate,
      String state,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discountAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amountPaid,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceDue,
      boolean overdue) {}

  public record RecentPaymentItem(
      Long id,
      String paymentReference,
      Long invoiceId,
      String invoiceNumber,
      Long customerId,
      String customerName,
      String method,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
      LocalDate paymentDate,
      String status,
      String recordedBy) {}

  public record UpcomingRenewalItem(
      Long subscriptionId,
      String subscriptionNumber,
      Long customerId,
      String customerName,
      Long planId,
      String planName,
      String billingPeriod,
      String state,
      LocalDate nextBillingDate,
      LocalDate expiryDate,
      long daysUntilBilling,
      boolean renewalAllowed,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cycleAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal mrrContribution) {}

  public record ExpiringSubscriptionItem(
      Long subscriptionId,
      String subscriptionNumber,
      Long customerId,
      String customerName,
      Long planId,
      String planName,
      String billingPeriod,
      String state,
      LocalDate expiryDate,
      long daysUntilExpiry,
      boolean renewalAllowed,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cycleAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal mrrContribution) {}

  public record OverviewResponse(
      LocalDate asOfDate,
      LocalDate fromDate,
      LocalDate toDate,
      long totalSubscriptions,
      long activeSubscriptions,
      long pausedSubscriptions,
      long closedSubscriptions,
      long draftSubscriptions,
      long quotationSubscriptions,
      long confirmedSubscriptions,
      long newSubscriptions,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal mrr,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal arr,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pausedMrr,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pausedArr,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal invoicedSubtotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal invoicedDiscount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal invoicedTax,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal invoicedRevenue,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal collectedPayments,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstandingReceivables,
      long overdueInvoiceCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal overdueAmount,
      long upcomingRenewalsCount,
      long expiringSubscriptionsCount,
      List<RecentInvoiceItem> recentInvoices,
      List<RecentPaymentItem> recentPayments,
      List<UpcomingRenewalItem> upcomingRenewals,
      List<ExpiringSubscriptionItem> expiringSubscriptions) {}

  public record RevenueTrendPoint(
      String period,
      String label,
      long invoiceCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discountAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal invoicedRevenue,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal collectedPayments,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstandingAmount) {}

  public record RevenueResponse(
      String granularity,
      LocalDate fromDate,
      LocalDate toDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalInvoicedSubtotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalInvoicedDiscount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalInvoicedTax,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalInvoicedRevenue,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCollectedPayments,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalOutstandingReceivables,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal collectionRatePercent,
      List<RevenueTrendPoint> series) {}

  public record StatusCountItem(
      String state,
      long count,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal mrr,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal arr) {}

  public record PlanBreakdownItem(
      Long planId,
      String planName,
      String billingPeriod,
      long activeCount,
      long pausedCount,
      long closedCount,
      long totalCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal mrr,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal arr) {}

  public record FrequencyBreakdownItem(
      String billingPeriod,
      long activeCount,
      long pausedCount,
      long totalCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal mrr,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal arr) {}

  public record SubscriptionAnalyticsResponse(
      long totalSubscriptions,
      long activeSubscriptions,
      long pausedSubscriptions,
      long closedSubscriptions,
      long newSubscriptions,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal mrr,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal arr,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pausedMrr,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pausedArr,
      List<StatusCountItem> byStatus,
      List<PlanBreakdownItem> byPlan,
      List<FrequencyBreakdownItem> byBillingFrequency) {}

  public record PaymentMethodBreakdownItem(
      String method,
      long count,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal sharePercent) {}

  public record PaymentTrendPoint(
      String period,
      String label,
      long paymentCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal collectedAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal reversedAmount) {}

  public record PaymentAnalyticsResponse(
      String granularity,
      LocalDate fromDate,
      LocalDate toDate,
      long completedPaymentCount,
      long reversedPaymentCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCollectedAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalReversedAmount,
      List<PaymentMethodBreakdownItem> byMethod,
      List<PaymentTrendPoint> series) {}

  public record AgingBucketItem(
      String bucket,
      String label,
      long invoiceCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {}

  public record OverdueInvoiceItem(
      Long invoiceId,
      String invoiceNumber,
      Long subscriptionId,
      String subscriptionNumber,
      Long customerId,
      String customerName,
      String customerEmail,
      String planName,
      LocalDate issueDate,
      LocalDate dueDate,
      long daysOverdue,
      String agingBucket,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amountPaid,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceDue) {}

  public record ReceivablesResponse(
      LocalDate asOfDate,
      long openInvoiceCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalOutstandingAmount,
      long overdueInvoiceCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalOverdueAmount,
      long currentNotDueCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal currentNotDueAmount,
      List<AgingBucketItem> agingBuckets,
      List<OverdueInvoiceItem> topOverdueInvoices) {}

  public record RenewalsResponse(
      LocalDate asOfDate,
      int windowDays,
      long upcomingRenewalsCount,
      long expiringSubscriptionsCount,
      List<UpcomingRenewalItem> upcomingRenewals,
      List<ExpiringSubscriptionItem> expiringSubscriptions) {}

  public record CustomerPortalOverviewResponse(
      Long customerId,
      String customerName,
      String customerEmail,
      String companyName,
      long activeSubscriptionCount,
      long pausedSubscriptionCount,
      long closedSubscriptionCount,
      long totalSubscriptionCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monthlyRecurringSpend,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalBilledAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalPaidAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstandingBalance,
      long openInvoiceCount,
      long overdueInvoiceCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal overdueBalance,
      LocalDate nextBillingDate,
      List<SubscriptionDtos.View> subscriptions,
      List<RecentInvoiceItem> recentInvoices,
      List<RecentPaymentItem> recentPayments) {}
}
