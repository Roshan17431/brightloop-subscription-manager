package com.brightloop.modules.analytics;

import com.brightloop.modules.analytics.AnalyticsDtos.*;
import com.brightloop.modules.invoice.InvoiceState;
import com.brightloop.modules.payment.PaymentMethod;
import com.brightloop.modules.plan.BillingPeriod;
import com.brightloop.modules.subscription.SubscriptionState;
import com.brightloop.response.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {
  private final AnalyticsService analytics;

  public AnalyticsController(AnalyticsService analytics) {
    this.analytics = analytics;
  }

  private FilterParams buildFilters(
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
    return new FilterParams(
        fromDate,
        toDate,
        asOfDate,
        granularity,
        customerId,
        planId,
        billingPeriod,
        subscriptionState,
        invoiceState,
        paymentMethod,
        windowDays);
  }

  @GetMapping("/analytics/overview")
  ResponseEntity<ApiResponse<OverviewResponse>> overview(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOfDate,
      @RequestParam(required = false) Granularity granularity,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) BillingPeriod billingPeriod,
      @RequestParam(required = false) SubscriptionState subscriptionState,
      @RequestParam(required = false) InvoiceState invoiceState,
      @RequestParam(required = false) PaymentMethod paymentMethod,
      @RequestParam(required = false) Integer windowDays) {
    return ApiResponse.ok(
        analytics.getOverview(
            buildFilters(
                fromDate,
                toDate,
                asOfDate,
                granularity,
                customerId,
                planId,
                billingPeriod,
                subscriptionState,
                invoiceState,
                paymentMethod,
                windowDays)));
  }

  @GetMapping("/analytics/revenue")
  ResponseEntity<ApiResponse<RevenueResponse>> revenue(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOfDate,
      @RequestParam(required = false) Granularity granularity,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) BillingPeriod billingPeriod,
      @RequestParam(required = false) SubscriptionState subscriptionState,
      @RequestParam(required = false) InvoiceState invoiceState,
      @RequestParam(required = false) PaymentMethod paymentMethod,
      @RequestParam(required = false) Integer windowDays) {
    return ApiResponse.ok(
        analytics.getRevenue(
            buildFilters(
                fromDate,
                toDate,
                asOfDate,
                granularity,
                customerId,
                planId,
                billingPeriod,
                subscriptionState,
                invoiceState,
                paymentMethod,
                windowDays)));
  }

  @GetMapping("/analytics/subscriptions")
  ResponseEntity<ApiResponse<SubscriptionAnalyticsResponse>> subscriptions(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOfDate,
      @RequestParam(required = false) Granularity granularity,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) BillingPeriod billingPeriod,
      @RequestParam(required = false) SubscriptionState subscriptionState,
      @RequestParam(required = false) InvoiceState invoiceState,
      @RequestParam(required = false) PaymentMethod paymentMethod,
      @RequestParam(required = false) Integer windowDays) {
    return ApiResponse.ok(
        analytics.getSubscriptionAnalytics(
            buildFilters(
                fromDate,
                toDate,
                asOfDate,
                granularity,
                customerId,
                planId,
                billingPeriod,
                subscriptionState,
                invoiceState,
                paymentMethod,
                windowDays)));
  }

  @GetMapping("/analytics/payments")
  ResponseEntity<ApiResponse<PaymentAnalyticsResponse>> payments(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOfDate,
      @RequestParam(required = false) Granularity granularity,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) BillingPeriod billingPeriod,
      @RequestParam(required = false) SubscriptionState subscriptionState,
      @RequestParam(required = false) InvoiceState invoiceState,
      @RequestParam(required = false) PaymentMethod paymentMethod,
      @RequestParam(required = false) Integer windowDays) {
    return ApiResponse.ok(
        analytics.getPaymentAnalytics(
            buildFilters(
                fromDate,
                toDate,
                asOfDate,
                granularity,
                customerId,
                planId,
                billingPeriod,
                subscriptionState,
                invoiceState,
                paymentMethod,
                windowDays)));
  }

  @GetMapping("/analytics/receivables")
  ResponseEntity<ApiResponse<ReceivablesResponse>> receivables(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOfDate,
      @RequestParam(required = false) Granularity granularity,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) BillingPeriod billingPeriod,
      @RequestParam(required = false) SubscriptionState subscriptionState,
      @RequestParam(required = false) InvoiceState invoiceState,
      @RequestParam(required = false) PaymentMethod paymentMethod,
      @RequestParam(required = false) Integer windowDays) {
    return ApiResponse.ok(
        analytics.getReceivables(
            buildFilters(
                fromDate,
                toDate,
                asOfDate,
                granularity,
                customerId,
                planId,
                billingPeriod,
                subscriptionState,
                invoiceState,
                paymentMethod,
                windowDays)));
  }

  @GetMapping("/analytics/renewals")
  ResponseEntity<ApiResponse<RenewalsResponse>> renewals(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOfDate,
      @RequestParam(required = false) Granularity granularity,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) BillingPeriod billingPeriod,
      @RequestParam(required = false) SubscriptionState subscriptionState,
      @RequestParam(required = false) InvoiceState invoiceState,
      @RequestParam(required = false) PaymentMethod paymentMethod,
      @RequestParam(required = false) Integer windowDays) {
    return ApiResponse.ok(
        analytics.getRenewals(
            buildFilters(
                fromDate,
                toDate,
                asOfDate,
                granularity,
                customerId,
                planId,
                billingPeriod,
                subscriptionState,
                invoiceState,
                paymentMethod,
                windowDays)));
  }

  @GetMapping(value = "/analytics/reports/export", produces = "text/csv")
  ResponseEntity<byte[]> exportCsv(
      @RequestParam(defaultValue = "revenue") String section,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOfDate,
      @RequestParam(required = false) Granularity granularity,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) BillingPeriod billingPeriod,
      @RequestParam(required = false) SubscriptionState subscriptionState,
      @RequestParam(required = false) InvoiceState invoiceState,
      @RequestParam(required = false) PaymentMethod paymentMethod,
      @RequestParam(required = false) Integer windowDays) {
    String safeSection =
        switch (section == null ? "revenue" : section.trim().toLowerCase(java.util.Locale.ROOT)) {
          case "subscriptions", "payments", "receivables" ->
              section.trim().toLowerCase(java.util.Locale.ROOT);
          default -> "revenue";
        };
    String csv =
        analytics.exportCsv(
            safeSection,
            buildFilters(
                fromDate,
                toDate,
                asOfDate,
                granularity,
                customerId,
                planId,
                billingPeriod,
                subscriptionState,
                invoiceState,
                paymentMethod,
                windowDays));
    var headers = new HttpHeaders();
    headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
    headers.setContentDisposition(
        ContentDisposition.attachment()
            .filename("brightloop-" + safeSection + "-report.csv")
            .build());
    return ResponseEntity.ok().headers(headers).body(csv.getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/portal/overview")
  ResponseEntity<ApiResponse<CustomerPortalOverviewResponse>> customerPortalOverview() {
    return ApiResponse.ok(analytics.getCustomerPortalOverview());
  }
}
