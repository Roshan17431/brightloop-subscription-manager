package com.brightloop.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.brightloop.config.AccountStatus;
import com.brightloop.modules.analytics.AnalyticsDtos;
import com.brightloop.modules.analytics.AnalyticsDtos.FilterParams;
import com.brightloop.modules.analytics.AnalyticsDtos.Granularity;
import com.brightloop.modules.analytics.AnalyticsService;
import com.brightloop.modules.billing.BillingDtos;
import com.brightloop.modules.billing.BillingService;
import com.brightloop.modules.customer.CustomerDtos;
import com.brightloop.modules.customer.CustomerService;
import com.brightloop.modules.discount.DiscountDtos;
import com.brightloop.modules.discount.DiscountScope;
import com.brightloop.modules.discount.DiscountService;
import com.brightloop.modules.discount.DiscountType;
import com.brightloop.modules.invoice.Invoice;
import com.brightloop.modules.invoice.InvoiceRepository;
import com.brightloop.modules.invoice.InvoiceService;
import com.brightloop.modules.invoice.InvoiceState;
import com.brightloop.modules.payment.PaymentDtos;
import com.brightloop.modules.payment.PaymentMethod;
import com.brightloop.modules.payment.PaymentService;
import com.brightloop.modules.plan.BillingPeriod;
import com.brightloop.modules.plan.PlanDtos;
import com.brightloop.modules.plan.PlanService;
import com.brightloop.modules.product.ProductDtos;
import com.brightloop.modules.product.ProductService;
import com.brightloop.modules.subscription.SubscriptionDtos;
import com.brightloop.modules.subscription.SubscriptionService;
import com.brightloop.modules.subscription.SubscriptionState;
import com.brightloop.modules.user.Role;
import com.brightloop.modules.user.UserRepository;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class Phase4AnalyticsPortalIntegrationTest {

  private static org.springframework.boot.web.server.servlet.context
          .ServletWebServerApplicationContext
      application;

  private static int port;
  private static UserRepository users;
  private static PasswordEncoder passwordEncoder;
  private static CustomerService customerService;
  private static ProductService productService;
  private static PlanService planService;
  private static SubscriptionService subscriptionService;
  private static BillingService billingService;
  private static InvoiceService invoiceService;
  private static InvoiceRepository invoiceRepository;
  private static PaymentService paymentService;
  private static DiscountService discountService;
  private static AnalyticsService analyticsService;
  private static JdbcTemplate jdbcTemplate;
  private static String adminEmail;
  private static String adminPassword;

  private final ObjectMapper json = new ObjectMapper();
  private final String testPassword =
      "P4Pass!" + UUID.randomUUID().toString().substring(0, 8);

  @BeforeAll
  static void startApplication() {
    application = NativeSpringApplication.start();
    port = application.getWebServer().getPort();
    users = application.getBean(UserRepository.class);
    passwordEncoder = application.getBean(PasswordEncoder.class);
    customerService = application.getBean(CustomerService.class);
    productService = application.getBean(ProductService.class);
    planService = application.getBean(PlanService.class);
    subscriptionService = application.getBean(SubscriptionService.class);
    billingService = application.getBean(BillingService.class);
    invoiceService = application.getBean(InvoiceService.class);
    invoiceRepository = application.getBean(InvoiceRepository.class);
    paymentService = application.getBean(PaymentService.class);
    discountService = application.getBean(DiscountService.class);
    analyticsService = application.getBean(AnalyticsService.class);
    jdbcTemplate = application.getBean(JdbcTemplate.class);

    adminPassword = UUID.randomUUID() + "Aa!";
    var u = new com.brightloop.modules.user.UserAccount();
    u.name = "Phase 4 Admin";
    u.email = "p4-admin-" + UUID.randomUUID() + "@example.test";
    u.passwordHash = passwordEncoder.encode(adminPassword);
    u.role = Role.ADMIN;
    users.saveAndFlush(u);
    adminEmail = u.email;
  }

  @AfterAll
  static void stopApplication() {
    SecurityContextHolder.clearContext();
    if (application != null) application.close();
  }

  @BeforeEach
  void setUpAdmin() {
    authenticateAs(adminEmail, Role.ADMIN);
  }

  private void authenticateAs(String email, Role role) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  void testMrrArrNormalizationDiscountTaxExclusionAndPausedSeparation() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    var customer =
        customerService.create(
            new CustomerDtos.Input(
                "MRR Analytics Corp " + tag,
                "mrr-" + tag + "@brightloop.test",
                "+91 9800011111",
                "MRR Corp",
                "Kochi, Kerala",
                AccountStatus.ACTIVE));

    // Product with 100.00 base unit price
    var prod =
        productService.create(
            new ProductDtos.Input(
                "SaaS Seat " + tag,
                "SaaS",
                new BigDecimal("100.00"),
                new BigDecimal("20.00"),
                "Cloud seat",
                AccountStatus.ACTIVE,
                List.of()));

    var dailyPlan = createPlan("Daily Plan " + tag, BillingPeriod.DAILY, prod.id());
    var weeklyPlan = createPlan("Weekly Plan " + tag, BillingPeriod.WEEKLY, prod.id());
    var monthlyPlan = createPlan("Monthly Plan " + tag, BillingPeriod.MONTHLY, prod.id());
    var yearlyPlan = createPlan("Yearly Plan " + tag, BillingPeriod.YEARLY, prod.id());

    // 1. Daily subscription: qty 1 * 100.00 = 100.00/day (plus 18% tax -> total 118.00)
    // Pre-tax MRR = 100.00 * 30 = 3000.00
    var subDaily =
        createAndActivateSub(
            customer.id(),
            dailyPlan.id(),
            prod.id(),
            1,
            new BigDecimal("18.00"),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 12, 31));
    assertEquals(new BigDecimal("118.00"), subDaily.total());

    // 2. Weekly subscription: qty 3 * 100.00 = 300.00/week (plus 18% tax -> total 354.00)
    // Pre-tax MRR = 300.00 * 52 / 12 = 1300.00
    var subWeekly =
        createAndActivateSub(
            customer.id(),
            weeklyPlan.id(),
            prod.id(),
            3,
            new BigDecimal("18.00"),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 12, 31));
    assertEquals(new BigDecimal("354.00"), subWeekly.total());

    // 3. Monthly subscription: qty 10 * 100.00 = 1000.00/month (plus 18% tax -> total 1180.00)
    var subMonthly =
        createAndActivateSub(
            customer.id(),
            monthlyPlan.id(),
            prod.id(),
            10,
            new BigDecimal("18.00"),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 12, 31));

    // Apply an active 10% recurring discount scoped specifically to subMonthly
    // Net pre-tax monthly cycle = 1000.00 - 100.00 = 900.00
    discountService.create(
        new DiscountDtos.Input(
            "10Pct Monthly Sub " + tag,
            "M10-" + tag,
            "10% recurring discount",
            DiscountType.PERCENTAGE,
            new BigDecimal("10.00"),
            DiscountScope.SUBSCRIPTION,
            null,
            subMonthly.id(),
            null,
            null,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            null,
            AccountStatus.ACTIVE));

    // 4. Yearly subscription: qty 120 * 100.00 = 12000.00/year (plus 18% tax -> total 14160.00)
    // Pre-tax MRR = 12000.00 / 12 = 1000.00
    var subYearly =
        createAndActivateSub(
            customer.id(),
            yearlyPlan.id(),
            prod.id(),
            120,
            new BigDecimal("18.00"),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2027, 3, 31));

    // 5. Draft subscription (must not contribute to MRR)
    subscriptionService.create(
        new SubscriptionDtos.Input(
            customer.id(),
            monthlyPlan.id(),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 12, 31),
            "Net 15",
            List.of(new SubscriptionDtos.LineInput(prod.id(), null, 5, new BigDecimal("18.00")))));

    var filter =
        new FilterParams(
            null,
            null,
            LocalDate.of(2026, 4, 15),
            Granularity.MONTHLY,
            customer.id(),
            null,
            null,
            null,
            null,
            null,
            30);

    var overview = analyticsService.getOverview(filter);
    assertEquals(5L, overview.totalSubscriptions());
    assertEquals(4L, overview.activeSubscriptions());
    assertEquals(0L, overview.pausedSubscriptions());
    assertEquals(1L, overview.draftSubscriptions());
    // Active MRR = 3000.00 (daily) + 1300.00 (weekly) + 900.00 (monthly net of 10% discount) + 1000.00 (yearly) = 6200.00
    assertEquals(new BigDecimal("6200.00"), overview.mrr());
    // Active ARR = 6200.00 * 12 = 74400.00
    assertEquals(new BigDecimal("74400.00"), overview.arr());
    assertEquals(new BigDecimal("0.00"), overview.pausedMrr());

    // Now pause the Yearly subscription (1000.00 MRR)
    subscriptionService.pause(
        subYearly.id(),
        new SubscriptionDtos.PauseInput(LocalDate.of(2026, 4, 10), "Customer temporary hold"));

    var afterPause = analyticsService.getOverview(filter);
    assertEquals(3L, afterPause.activeSubscriptions());
    assertEquals(1L, afterPause.pausedSubscriptions());
    assertEquals(new BigDecimal("5200.00"), afterPause.mrr());
    assertEquals(new BigDecimal("62400.00"), afterPause.arr());
    assertEquals(new BigDecimal("1000.00"), afterPause.pausedMrr());
    assertEquals(new BigDecimal("12000.00"), afterPause.pausedArr());

    // Close the Daily subscription (3000.00 MRR) -> closed subscriptions contribute 0.00
    subscriptionService.closeEarly(
        subDaily.id(),
        new SubscriptionDtos.CloseInput("Cancelled daily tier", LocalDate.of(2026, 4, 12)));

    var subAnalytics = analyticsService.getSubscriptionAnalytics(filter);
    assertEquals(2L, subAnalytics.activeSubscriptions());
    assertEquals(1L, subAnalytics.pausedSubscriptions());
    assertEquals(1L, subAnalytics.closedSubscriptions());
    assertEquals(new BigDecimal("2200.00"), subAnalytics.mrr());
    assertEquals(new BigDecimal("26400.00"), subAnalytics.arr());
    assertEquals(new BigDecimal("1000.00"), subAnalytics.pausedMrr());
    assertEquals(4, subAnalytics.byPlan().size());
    assertEquals(4, subAnalytics.byBillingFrequency().size());
  }

  @Test
  void testRevenuePaymentsReceivablesAgingAndFilters() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    var customer =
        customerService.create(
            new CustomerDtos.Input(
                "Receivables Enterprise " + tag,
                "rec-" + tag + "@brightloop.test",
                "+91 9800022222",
                "Receivables Inc",
                "Bangalore",
                AccountStatus.ACTIVE));

    var prod =
        productService.create(
            new ProductDtos.Input(
                "Platform Node " + tag,
                "Infrastructure",
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                "Compute node",
                AccountStatus.ACTIVE,
                List.of()));

    var plan = createPlan("Monthly Enterprise " + tag, BillingPeriod.MONTHLY, prod.id());

    // Create 5 subscriptions so we can generate 5 distinct invoices across aging buckets
    LocalDate asOfDate = LocalDate.of(2026, 6, 15);

    // Invoice 1: Paid in full (Issue 2026-01-10, Total 5000.00)
    var sub1 =
        createAndActivateSub(
            customer.id(),
            plan.id(),
            prod.id(),
            5,
            BigDecimal.ZERO,
            LocalDate.of(2026, 1, 10),
            LocalDate.of(2026, 12, 31));
    Long inv1Id = billAndConfirm(sub1.id(), LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 25));
    paymentService.record(
        new PaymentDtos.Input(
            inv1Id,
            customer.id(),
            "UTR-JAN-" + tag,
            null,
            PaymentMethod.BANK_TRANSFER,
            new BigDecimal("5000.00"),
            LocalDate.of(2026, 1, 20),
            "Full Jan payment"),
        null);

    // Invoice 2: 90+ days overdue (Due 2026-02-01 -> 134 days overdue as of 2026-06-15), Total 4000, Paid 500 -> Balance 3500
    var sub2 =
        createAndActivateSub(
            customer.id(),
            plan.id(),
            prod.id(),
            4,
            BigDecimal.ZERO,
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 12, 31));
    Long inv2Id = billAndConfirm(sub2.id(), LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 1));
    paymentService.record(
        new PaymentDtos.Input(
            inv2Id,
            customer.id(),
            "UTR-FEB-PART-" + tag,
            null,
            PaymentMethod.UPI,
            new BigDecimal("500.00"),
            LocalDate.of(2026, 2, 5),
            "Partial payment"),
        null);

    // Invoice 3: 61-90 days overdue (Due 2026-04-01 -> 75 days overdue), Balance 3000.00
    var sub3 =
        createAndActivateSub(
            customer.id(),
            plan.id(),
            prod.id(),
            3,
            BigDecimal.ZERO,
            LocalDate.of(2026, 3, 15),
            LocalDate.of(2026, 12, 31));
    billAndConfirm(sub3.id(), LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 1));

    // Invoice 4: 31-60 days overdue (Due 2026-05-01 -> 45 days overdue), Balance 2000.00
    var sub4 =
        createAndActivateSub(
            customer.id(),
            plan.id(),
            prod.id(),
            2,
            BigDecimal.ZERO,
            LocalDate.of(2026, 4, 15),
            LocalDate.of(2026, 12, 31));
    billAndConfirm(sub4.id(), LocalDate.of(2026, 4, 15), LocalDate.of(2026, 5, 1));

    // Invoice 5: 1-30 days overdue (Due 2026-06-01 -> 14 days overdue), Balance 1000.00
    var sub5 =
        createAndActivateSub(
            customer.id(),
            plan.id(),
            prod.id(),
            1,
            BigDecimal.ZERO,
            LocalDate.of(2026, 5, 15),
            LocalDate.of(2026, 12, 31));
    billAndConfirm(sub5.id(), LocalDate.of(2026, 5, 15), LocalDate.of(2026, 6, 1));

    // Invoice 6: Current not-yet-due (Due 2026-06-25 >= 2026-06-15), Balance 1000.00
    var sub6 =
        createAndActivateSub(
            customer.id(),
            plan.id(),
            prod.id(),
            1,
            BigDecimal.ZERO,
            LocalDate.of(2026, 6, 10),
            LocalDate.of(2026, 7, 12));
    billAndConfirm(sub6.id(), LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 25));

    var filter =
        new FilterParams(
            null,
            null,
            asOfDate,
            Granularity.MONTHLY,
            customer.id(),
            null,
            null,
            null,
            null,
            null,
            30);

    // 1. Verify Receivables & Overdue Aging Buckets
    var rec = analyticsService.getReceivables(filter);
    assertEquals(5L, rec.openInvoiceCount());
    assertEquals(new BigDecimal("10500.00"), rec.totalOutstandingAmount());
    assertEquals(4L, rec.overdueInvoiceCount());
    assertEquals(new BigDecimal("9500.00"), rec.totalOverdueAmount());
    assertEquals(1L, rec.currentNotDueCount());
    assertEquals(new BigDecimal("1000.00"), rec.currentNotDueAmount());

    assertEquals(4, rec.agingBuckets().size());
    assertEquals(new BigDecimal("1000.00"), rec.agingBuckets().get(0).amount()); // 1-30
    assertEquals(new BigDecimal("2000.00"), rec.agingBuckets().get(1).amount()); // 31-60
    assertEquals(new BigDecimal("3000.00"), rec.agingBuckets().get(2).amount()); // 61-90
    assertEquals(new BigDecimal("3500.00"), rec.agingBuckets().get(3).amount()); // 90+
    assertEquals(4, rec.topOverdueInvoices().size());
    assertEquals("90+", rec.topOverdueInvoices().getFirst().agingBucket());

    // 2. Verify Revenue Analytics & Invoiced vs Collected
    var rev = analyticsService.getRevenue(filter);
    // Total invoiced = 5000 + 4000 + 3000 + 2000 + 1000 + 1000 = 16000.00
    assertEquals(new BigDecimal("16000.00"), rev.totalInvoicedRevenue());
    // Total collected = 5000 + 500 = 5500.00
    assertEquals(new BigDecimal("5500.00"), rev.totalCollectedPayments());
    assertEquals(new BigDecimal("10500.00"), rev.totalOutstandingReceivables());
    // Collection rate = 5500 / 16000 * 100 = 34.38%
    assertEquals(new BigDecimal("34.38"), rev.collectionRatePercent());
    assertFalse(rev.series().isEmpty());

    // 3. Verify Payment Analytics by Method
    var payAnalytics = analyticsService.getPaymentAnalytics(filter);
    assertEquals(2L, payAnalytics.completedPaymentCount());
    assertEquals(new BigDecimal("5500.00"), payAnalytics.totalCollectedAmount());
    var bankMethod =
        payAnalytics.byMethod().stream()
            .filter(m -> "BANK_TRANSFER".equals(m.method()))
            .findFirst()
            .orElseThrow();
    assertEquals(new BigDecimal("5000.00"), bankMethod.amount());
    assertEquals(new BigDecimal("90.91"), bankMethod.sharePercent());

    // 4. Verify Renewals & Expiring Subscriptions
    var renewals = analyticsService.getRenewals(filter);
    assertTrue(renewals.expiringSubscriptionsCount() >= 1); // sub6 expires 2026-06-28 (within 30 days of 2026-06-15)

    // 5. Verify CSV export for all 4 sections
    assertTrue(analyticsService.exportCsv("revenue", filter).contains("Invoiced Revenue"));
    assertTrue(analyticsService.exportCsv("payments", filter).contains("Collected Amount"));
    assertTrue(analyticsService.exportCsv("subscriptions", filter).contains("Plan Name"));
    assertTrue(analyticsService.exportCsv("receivables", filter).contains("Aging Bucket"));

    // 6. Verify invalid date range is rejected
    assertThrows(
        IllegalArgumentException.class,
        () ->
            analyticsService.getOverview(
                new FilterParams(
                    LocalDate.of(2026, 6, 10),
                    LocalDate.of(2026, 6, 1),
                    null,
                    Granularity.MONTHLY,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    30)));
  }

  @Test
  void testCustomerPortalAndSelfServiceLifecycleAuthorizationHttp() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    var adminClient = new SessionClient(port, json);
    adminClient.login(adminEmail, adminPassword);

    // Register Customer A and Customer B
    String custAEmail = "portal-a-" + tag + "@brightloop.test";
    String custBEmail = "portal-b-" + tag + "@brightloop.test";

    var custAClient = new SessionClient(port, json);
    assertEquals(
        201,
        custAClient
            .post(
                "/api/v1/auth/register",
                Map.of(
                    "name", "Portal Customer A",
                    "email", custAEmail,
                    "password", testPassword,
                    "phone", "+91 9700011111",
                    "companyName", "Portal A Co",
                    "billingAddress", "Kochi"))
            .statusCode());
    custAClient.login(custAEmail, testPassword);

    var custBClient = new SessionClient(port, json);
    assertEquals(
        201,
        custBClient
            .post(
                "/api/v1/auth/register",
                Map.of(
                    "name", "Portal Customer B",
                    "email", custBEmail,
                    "password", testPassword,
                    "phone", "+91 9700022222",
                    "companyName", "Portal B Co",
                    "billingAddress", "Trivandrum"))
            .statusCode());
    custBClient.login(custBEmail, testPassword);

    // Get Customer A ID via /api/v1/portal/overview
    var portalInitRes = custAClient.get("/api/v1/portal/overview");
    assertEquals(200, portalInitRes.statusCode());
    var portalInitData = (Map<?, ?>) json.readValue(portalInitRes.body(), Map.class).get("data");
    Long custAId = ((Number) portalInitData.get("customerId")).longValue();

    // Admin creates product, flexible plan (allows pause/renew/close), and locked plan (disallows pause/renew/close)
    authenticateAs(adminEmail, Role.ADMIN);
    var prod =
        productService.create(
            new ProductDtos.Input(
                "Portal Suite " + tag,
                "SaaS",
                new BigDecimal("2500.00"),
                new BigDecimal("500.00"),
                "Customer portal product",
                AccountStatus.ACTIVE,
                List.of()));

    var flexPlan =
        planService.create(
            new PlanDtos.Input(
                "Flex Plan " + tag,
                "Allows customer pause/renew/close",
                BillingPeriod.MONTHLY,
                new BigDecimal("2500.00"),
                1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                true,
                true,
                true,
                true,
                AccountStatus.ACTIVE,
                List.of(prod.id())));

    var lockedPlan =
        planService.create(
            new PlanDtos.Input(
                "Locked Plan " + tag,
                "Disallows customer pause/renew/close",
                BillingPeriod.MONTHLY,
                new BigDecimal("2500.00"),
                1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                true,
                false,
                false,
                false,
                AccountStatus.ACTIVE,
                List.of(prod.id())));

    var subFlex =
        createAndActivateSub(
            custAId,
            flexPlan.id(),
            prod.id(),
            1,
            new BigDecimal("18.00"),
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 12, 31));
    var subLocked =
        createAndActivateSub(
            custAId,
            lockedPlan.id(),
            prod.id(),
            1,
            new BigDecimal("18.00"),
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 12, 31));

    Long invId = billAndConfirm(subFlex.id(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15));

    // 1. Customer cannot access any /api/v1/analytics/** endpoints (403 Forbidden)
    for (String ep :
        List.of(
            "/api/v1/analytics/overview",
            "/api/v1/analytics/revenue",
            "/api/v1/analytics/subscriptions",
            "/api/v1/analytics/payments",
            "/api/v1/analytics/receivables",
            "/api/v1/analytics/renewals",
            "/api/v1/analytics/reports/export")) {
      assertEquals(403, custAClient.get(ep).statusCode(), "Expected 403 for " + ep);
    }

    // 2. Customer A can fetch /api/v1/portal/overview and sees accurate data
    var portalRes = custAClient.get("/api/v1/portal/overview");
    assertEquals(200, portalRes.statusCode());
    var portalData = (Map<?, ?>) json.readValue(portalRes.body(), Map.class).get("data");
    assertEquals(2, ((Number) portalData.get("activeSubscriptionCount")).intValue());
    assertEquals("5000.00", portalData.get("monthlyRecurringSpend")); // 2500 + 2500 pre-tax
    assertEquals("2950.00", portalData.get("outstandingBalance")); // 2500 + 18% tax = 2950.00

    // 3. Customer A can download their own invoice PDF, Customer B gets 403 Forbidden
    assertEquals(200, custAClient.get("/api/v1/invoices/" + invId + "/pdf").statusCode());
    assertEquals(403, custBClient.get("/api/v1/invoices/" + invId + "/pdf").statusCode());

    // 4. Customer A can pause, resume, renew, and early-close their own subscription when plan allows
    assertEquals(
        200,
        custAClient
            .post(
                "/api/v1/subscriptions/" + subFlex.id() + "/pause",
                Map.of("pauseDate", "2026-03-10", "reason", "Vacation"))
            .statusCode());
    assertEquals(
        200,
        custAClient
            .post(
                "/api/v1/subscriptions/" + subFlex.id() + "/resume",
                Map.of("resumeDate", "2026-03-15"))
            .statusCode());
    assertEquals(
        200,
        custAClient
            .post(
                "/api/v1/subscriptions/" + subFlex.id() + "/renew",
                Map.of("periods", 1, "idempotencyKey", "CUST-RENEW-" + tag))
            .statusCode());

    // 5. Customer A is blocked (409 Conflict) from pausing or renewing subLocked where plan disallows it
    assertEquals(
        409,
        custAClient
            .post("/api/v1/subscriptions/" + subLocked.id() + "/pause", Map.of())
            .statusCode());
    assertEquals(
        409,
        custAClient
            .post("/api/v1/subscriptions/" + subLocked.id() + "/renew", Map.of("periods", 1))
            .statusCode());

    // 6. Customer B is strictly blocked (403 Forbidden) from mutating Customer A's subscription
    assertEquals(
        403,
        custBClient
            .post("/api/v1/subscriptions/" + subFlex.id() + "/pause", Map.of())
            .statusCode());
    assertEquals(
        403,
        custBClient
            .post("/api/v1/subscriptions/" + subFlex.id() + "/renew", Map.of("periods", 1))
            .statusCode());
    assertEquals(
        403,
        custBClient
            .post("/api/v1/subscriptions/" + subFlex.id() + "/close", Map.of("reason", "Hack"))
            .statusCode());
  }

  @Test
  void testBulkDatasetAnalyticsPerformanceUnderTwoSeconds() {
    String tag = "PERF-" + UUID.randomUUID().toString().substring(0, 8);
    var customer =
        customerService.create(
            new CustomerDtos.Input(
                "Perf Benchmark Corp " + tag,
                "perf-" + tag + "@brightloop.test",
                "+91 9900099999",
                "Perf Corp",
                "Kochi",
                AccountStatus.ACTIVE));
    var prod =
        productService.create(
            new ProductDtos.Input(
                "Perf Product " + tag,
                "SaaS",
                new BigDecimal("500.00"),
                new BigDecimal("100.00"),
                "Bulk benchmark item",
                AccountStatus.ACTIVE,
                List.of()));
    var plan = createPlan("Perf Monthly " + tag, BillingPeriod.MONTHLY, prod.id());

    int bulkCount = 2500;
    Timestamp now = Timestamp.from(Instant.now());
    Date start = Date.valueOf(LocalDate.of(2026, 1, 1));
    Date expiry = Date.valueOf(LocalDate.of(2026, 12, 31));
    Date nextBill = Date.valueOf(LocalDate.of(2026, 6, 20));

    List<Object[]> subBatch = new ArrayList<>(bulkCount);
    for (int i = 0; i < bulkCount; i++) {
      String state = (i % 10 == 0) ? "PAUSED" : ((i % 15 == 0) ? "CLOSED" : "ACTIVE");
      subBatch.add(
          new Object[] {
            0L,
            tag + "-SUB-" + i,
            customer.id(),
            plan.id(),
            plan.name(),
            start,
            expiry,
            nextBill,
            "Net 15",
            state,
            new BigDecimal("590.00"),
            0,
            0,
            now,
            now
          });
    }

    jdbcTemplate.batchUpdate(
        "INSERT INTO subscriptions (version, number, customer_id, plan_id, plan_name, start_date, expiry_date, next_billing_date, payment_terms, state, total, total_paused_days, renewal_count, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        subBatch);

    List<Long> insertedSubIds =
        jdbcTemplate.queryForList(
            "SELECT id FROM subscriptions WHERE customer_id = ? ORDER BY id ASC",
            Long.class,
            customer.id());
    assertEquals(bulkCount, insertedSubIds.size());

    List<Object[]> lineBatch = new ArrayList<>(bulkCount);
    List<Object[]> invBatch = new ArrayList<>(bulkCount);
    Date issueDate = Date.valueOf(LocalDate.of(2026, 5, 1));
    Date dueDate = Date.valueOf(LocalDate.of(2026, 5, 16));
    for (int i = 0; i < insertedSubIds.size(); i++) {
      Long sId = insertedSubIds.get(i);
      lineBatch.add(
          new Object[] {
            sId,
            prod.id(),
            prod.name(),
            1,
            new BigDecimal("500.00"),
            new BigDecimal("18.00"),
            new BigDecimal("590.00")
          });
      invBatch.add(
          new Object[] {
            0L,
            tag + "-INV-" + i,
            sId,
            tag + "-SUB-" + i,
            customer.id(),
            customer.name(),
            customer.email(),
            plan.id(),
            plan.name(),
            "MONTHLY",
            issueDate,
            expiry,
            issueDate,
            dueDate,
            "Net 15",
            "CONFIRMED",
            new BigDecimal("500.00"),
            BigDecimal.ZERO,
            new BigDecimal("90.00"),
            new BigDecimal("590.00"),
            BigDecimal.ZERO,
            tag + "-IDEM-" + i,
            now,
            now
          });
    }

    jdbcTemplate.batchUpdate(
        "INSERT INTO subscription_lines (subscription_id, product_id, product_name, quantity, unit_price, tax_rate, amount) VALUES (?, ?, ?, ?, ?, ?, ?)",
        lineBatch);
    jdbcTemplate.batchUpdate(
        "INSERT INTO invoices (version, invoice_number, subscription_id, subscription_number, customer_id, customer_name, customer_email, plan_id, plan_name, billing_period, billing_period_start, billing_period_end, issue_date, due_date, payment_terms, state, subtotal, discount_amount, tax_amount, total, amount_paid, idempotency_key, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        invBatch);

    try {
      var filter =
          new FilterParams(
              null,
              null,
              LocalDate.of(2026, 6, 15),
              Granularity.MONTHLY,
              null,
              null,
              null,
              null,
              null,
              null,
              30);

      long t0 = System.nanoTime();
      var overview = analyticsService.getOverview(filter);
      long overviewMs = (System.nanoTime() - t0) / 1_000_000;

      long t1 = System.nanoTime();
      var revenue = analyticsService.getRevenue(filter);
      long revenueMs = (System.nanoTime() - t1) / 1_000_000;

      long t2 = System.nanoTime();
      var subs = analyticsService.getSubscriptionAnalytics(filter);
      long subsMs = (System.nanoTime() - t2) / 1_000_000;

      long t3 = System.nanoTime();
      var receivables = analyticsService.getReceivables(filter);
      long recMs = (System.nanoTime() - t3) / 1_000_000;

      assertTrue(overview.totalSubscriptions() >= bulkCount);
      assertFalse(revenue.series().isEmpty());
      assertTrue(subs.totalSubscriptions() >= bulkCount);
      assertTrue(receivables.openInvoiceCount() >= bulkCount);

      assertTrue(overviewMs < 2000, "Overview took " + overviewMs + "ms (expected < 2000ms)");
      assertTrue(revenueMs < 2000, "Revenue took " + revenueMs + "ms (expected < 2000ms)");
      assertTrue(subsMs < 2000, "Subscriptions took " + subsMs + "ms (expected < 2000ms)");
      assertTrue(recMs < 2000, "Receivables took " + recMs + "ms (expected < 2000ms)");
    } finally {
      jdbcTemplate.update("DELETE FROM invoices WHERE customer_id = ?", customer.id());
      jdbcTemplate.update(
          "DELETE FROM subscription_lines WHERE subscription_id IN (SELECT id FROM subscriptions WHERE customer_id = ?)",
          customer.id());
      jdbcTemplate.update("DELETE FROM subscriptions WHERE customer_id = ?", customer.id());
    }
  }

  private PlanDtos.View createPlan(String name, BillingPeriod period, Long productId) {
    return planService.create(
        new PlanDtos.Input(
            name,
            name + " description",
            period,
            new BigDecimal("100.00"),
            1,
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2028, 12, 31),
            true,
            true,
            true,
            true,
            AccountStatus.ACTIVE,
            List.of(productId)));
  }

  private SubscriptionDtos.View createAndActivateSub(
      Long customerId,
      Long planId,
      Long productId,
      int qty,
      BigDecimal taxRate,
      LocalDate startDate,
      LocalDate expiryDate) {
    var sub =
        subscriptionService.create(
            new SubscriptionDtos.Input(
                customerId,
                planId,
                startDate,
                expiryDate,
                "Net 15",
                List.of(new SubscriptionDtos.LineInput(productId, null, qty, taxRate))));
    subscriptionService.transition(sub.id(), SubscriptionState.QUOTATION);
    subscriptionService.transition(sub.id(), SubscriptionState.CONFIRMED);
    return subscriptionService.transition(sub.id(), SubscriptionState.ACTIVE);
  }

  private Long billAndConfirm(Long subId, LocalDate billingDate, LocalDate dueDate) {
    var run =
        billingService.triggerAdminBilling(
            new BillingDtos.RunRequest(
                billingDate, "P4-BILL-" + subId + "-" + UUID.randomUUID(), subId));
    Long invId = run.items().getFirst().invoiceId();
    invoiceService.transition(invId, InvoiceState.CONFIRMED);
    Invoice inv = invoiceRepository.findById(invId).orElseThrow();
    inv.dueDate = dueDate;
    invoiceRepository.saveAndFlush(inv);
    return invId;
  }

  private static final class SessionClient {
    private final int port;
    private final ObjectMapper json;
    private final HttpClient http;
    private String csrf;

    SessionClient(int port, ObjectMapper json) {
      this.port = port;
      this.json = json;
      var cookies = new CookieManager();
      cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
      this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    void refreshCsrf() throws Exception {
      var res = get("/api/v1/auth/csrf");
      var body = json.readValue(res.body(), Map.class);
      var data = (Map<?, ?>) body.get("data");
      this.csrf = String.valueOf(data.get("token"));
    }

    void login(String email, String password) throws Exception {
      refreshCsrf();
      var res = post("/api/v1/auth/login", Map.of("email", email, "password", password));
      assertEquals(200, res.statusCode());
      refreshCsrf();
    }

    HttpResponse<String> get(String path) throws Exception {
      var req =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
      return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    HttpResponse<String> post(String path, Object payload) throws Exception {
      if (csrf == null) refreshCsrf();
      String body = json.writeValueAsString(payload);
      var req =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .header("Content-Type", "application/json")
              .header("X-CSRF-TOKEN", csrf)
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();
      var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (path.startsWith("/api/v1/auth/register")) {
        refreshCsrf();
      }
      return res;
    }
  }
}
