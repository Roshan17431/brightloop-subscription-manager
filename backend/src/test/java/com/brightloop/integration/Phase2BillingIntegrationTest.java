package com.brightloop.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.brightloop.config.AccountStatus;
import com.brightloop.modules.billing.*;
import com.brightloop.modules.customer.*;
import com.brightloop.modules.discount.*;
import com.brightloop.modules.invoice.*;
import com.brightloop.modules.plan.*;
import com.brightloop.modules.product.*;
import com.brightloop.modules.subscription.*;
import com.brightloop.modules.tax.*;
import com.brightloop.modules.user.*;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class Phase2BillingIntegrationTest {
  private static org.springframework.boot.web.server.servlet.context
          .ServletWebServerApplicationContext
      application;

  private static UserRepository users;
  private static CustomerRepository customers;
  private static ProductService productService;
  private static PlanService planService;
  private static SubscriptionService subscriptionService;
  private static SubscriptionRepository subscriptionRepo;
  private static TaxService taxService;
  private static DiscountService discountService;
  private static DiscountRepository discountRepo;
  private static BillingService billingService;
  private static InvoiceService invoiceService;
  private static InvoiceRepository invoiceRepo;
  private static PasswordEncoder encoder;
  private static UserAccount adminUser;
  private static String adminPassword;

  private final ObjectMapper json = new ObjectMapper();

  @BeforeAll
  static void startApplication() {
    application = NativeSpringApplication.start();
    users = application.getBean(UserRepository.class);
    customers = application.getBean(CustomerRepository.class);
    productService = application.getBean(ProductService.class);
    planService = application.getBean(PlanService.class);
    subscriptionService = application.getBean(SubscriptionService.class);
    subscriptionRepo = application.getBean(SubscriptionRepository.class);
    taxService = application.getBean(TaxService.class);
    discountService = application.getBean(DiscountService.class);
    discountRepo = application.getBean(DiscountRepository.class);
    billingService = application.getBean(BillingService.class);
    invoiceService = application.getBean(InvoiceService.class);
    invoiceRepo = application.getBean(InvoiceRepository.class);
    encoder = application.getBean(PasswordEncoder.class);

    adminPassword = UUID.randomUUID() + "Aa!";
    var u = new UserAccount();
    u.name = "Phase 2 Admin";
    u.email = "p2-admin-" + UUID.randomUUID() + "@example.test";
    u.passwordHash = encoder.encode(adminPassword);
    u.role = Role.ADMIN;
    adminUser = users.saveAndFlush(u);
  }

  @AfterAll
  static void stopApplication() {
    SecurityContextHolder.clearContext();
    if (application != null) application.close();
  }

  private void authenticateAsAdmin() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                adminUser.email,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private Customer createActiveCustomer() {
    var c = new Customer();
    c.name = "Customer " + UUID.randomUUID().toString().substring(0, 8);
    c.email = "cust-" + UUID.randomUUID() + "@example.test";
    c.companyName = "Brightloop Client Co";
    c.billingAddress = "42 MG Road, Kochi";
    c.status = AccountStatus.ACTIVE;
    return customers.saveAndFlush(c);
  }

  private SubscriptionDtos.View createActiveSubscription(
      Long customerId,
      Long planId,
      Long productId,
      Long variantId,
      int qty,
      BigDecimal fallbackTaxRate,
      LocalDate startDate,
      LocalDate expiryDate) {
    authenticateAsAdmin();
    var sub =
        subscriptionService.create(
            new SubscriptionDtos.Input(
                customerId,
                planId,
                startDate,
                expiryDate,
                "Net 30",
                List.of(
                    new SubscriptionDtos.LineInput(productId, variantId, qty, fallbackTaxRate))));
    subscriptionService.transition(sub.id(), SubscriptionState.QUOTATION);
    subscriptionService.transition(sub.id(), SubscriptionState.CONFIRMED);
    return subscriptionService.transition(sub.id(), SubscriptionState.ACTIVE);
  }

  @Test
  void endOfMonthAndCycleBillingAdvancesSchedulesAndCalculatesTaxesAndDiscounts() {
    authenticateAsAdmin();
    var customer = createActiveCustomer();
    var product =
        productService.create(
            new ProductDtos.Input(
                "Enterprise Suite " + UUID.randomUUID(),
                "SOFTWARE",
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                "Core subscription",
                AccountStatus.ACTIVE,
                List.of(new ProductDtos.VariantInput(null, "Odoo", new BigDecimal("560.00")))));
    Long variantId = product.variants().getFirst().id();

    var plan =
        planService.create(
            new PlanDtos.Input(
                "Monthly Enterprise " + UUID.randomUUID(),
                "Monthly plan",
                BillingPeriod.MONTHLY,
                new BigDecimal("1000.00"),
                1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                true,
                false,
                false,
                true,
                AccountStatus.ACTIVE,
                List.of(product.id())));

    var taxRule =
        taxService.create(
            new TaxDtos.Input(
                "GST 18% " + UUID.randomUUID(),
                "Standard software tax",
                new BigDecimal("18.00"),
                null,
                product.id(),
                false,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                AccountStatus.ACTIVE));

    var discount =
        discountService.create(
            new DiscountDtos.Input(
                "Seat Volume 10% " + UUID.randomUUID(),
                "VOL10-" + UUID.randomUUID().toString().substring(0, 6),
                "10% off 2+ seats",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                DiscountScope.PRODUCT,
                product.id(),
                null,
                2,
                new BigDecimal("2000.00"),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                10,
                AccountStatus.ACTIVE));

    var jan31 = LocalDate.of(2026, 1, 31);
    var sub =
        createActiveSubscription(
            customer.id,
            plan.id(),
            product.id(),
            variantId,
            2,
            BigDecimal.ZERO,
            jan31,
            LocalDate.of(2026, 6, 30));

    // 1. Bill for 2026-01-31
    String idempKey = "RUN-JAN31-" + sub.id();
    var run1 =
        billingService.triggerAdminBilling(new BillingDtos.RunRequest(jan31, idempKey, sub.id()));
    assertEquals("COMPLETED", run1.status());
    assertEquals(1, run1.invoicesGenerated());

    // Verify idempotency with same key
    var run1Replay =
        billingService.triggerAdminBilling(new BillingDtos.RunRequest(jan31, idempKey, sub.id()));
    assertEquals(run1.id(), run1Replay.id());

    // Verify idempotency on second run for same date with new key
    var run1DuplicateDate =
        billingService.triggerAdminBilling(
            new BillingDtos.RunRequest(jan31, "RUN-JAN31-B-" + sub.id(), sub.id()));
    assertEquals(0, run1DuplicateDate.invoicesGenerated());
    assertEquals(1, run1DuplicateDate.skippedCount());

    var invId = run1.items().getFirst().invoiceId();
    assertNotNull(invId);
    var inv = invoiceService.get(invId);
    // Unit price = 1000 + 560 = 1560.00; qty = 2 -> subtotal = 3120.00
    // 10% discount = 312.00 -> taxable = 2808.00
    // 18% GST = 505.44 -> total = 3313.44
    assertEquals(new BigDecimal("3120.00"), inv.subtotal());
    assertEquals(new BigDecimal("312.00"), inv.discountAmount());
    assertEquals(discount.id(), inv.discountId());
    assertEquals(new BigDecimal("505.44"), inv.taxAmount());
    assertEquals(new BigDecimal("3313.44"), inv.total());
    assertEquals(taxRule.id(), inv.lines().getFirst().taxRuleId());
    assertEquals(jan31, inv.billingPeriodStart());
    assertEquals(LocalDate.of(2026, 2, 27), inv.billingPeriodEnd());
    assertEquals(jan31.plusDays(30), inv.dueDate());

    // Check schedule advanced to 2026-02-28
    var updatedSub = subscriptionService.get(sub.id());
    assertEquals(LocalDate.of(2026, 2, 28), updatedSub.nextBillingDate());
    assertEquals(jan31, updatedSub.lastBilledDate());

    // 2. Bill for 2026-02-28 and verify end-of-month anchor restores to 2026-03-31
    var feb28 = LocalDate.of(2026, 2, 28);
    var run2 =
        billingService.triggerAdminBilling(new BillingDtos.RunRequest(feb28, null, sub.id()));
    assertEquals(1, run2.invoicesGenerated());
    var updatedSub2 = subscriptionService.get(sub.id());
    assertEquals(LocalDate.of(2026, 3, 31), updatedSub2.nextBillingDate());
    assertEquals(feb28, updatedSub2.lastBilledDate());
    assertEquals(2, discountService.get(discount.id()).usedCount());
  }

  @Test
  void handlesSubscriptionExpiryAndAutomaticClosing() {
    authenticateAsAdmin();
    var customer = createActiveCustomer();
    var product =
        productService.create(
            new ProductDtos.Input(
                "Daily Addon " + UUID.randomUUID(),
                "SERVICE",
                new BigDecimal("50.00"),
                new BigDecimal("10.00"),
                null,
                AccountStatus.ACTIVE,
                List.of()));
    var plan =
        planService.create(
            new PlanDtos.Input(
                "Daily Plan " + UUID.randomUUID(),
                "Closes automatically",
                BillingPeriod.DAILY,
                new BigDecimal("50.00"),
                1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                true,
                true,
                false,
                false,
                AccountStatus.ACTIVE,
                List.of(product.id())));

    var day1 = LocalDate.of(2026, 5, 10);
    // Single-day subscription (startDate == expiryDate)
    var singleDaySub =
        createActiveSubscription(
            customer.id, plan.id(), product.id(), null, 1, new BigDecimal("5.00"), day1, day1);

    // Billing on day1 bills the single day and immediately closes the subscription because nextBillingDate (day1+1) > expiryDate
    var runOnDay1 =
        billingService.triggerAdminBilling(
            new BillingDtos.RunRequest(day1, null, singleDaySub.id()));
    assertEquals(1, runOnDay1.invoicesGenerated());
    assertEquals(SubscriptionState.CLOSED, subscriptionService.get(singleDaySub.id()).state());

    // An active subscription that is already past expiryDate when billing runs is closed without invoicing
    var pastStart = LocalDate.of(2026, 4, 1);
    var pastExpiry = LocalDate.of(2026, 4, 5);
    var expiredSub =
        createActiveSubscription(
            customer.id,
            plan.id(),
            product.id(),
            null,
            1,
            BigDecimal.ZERO,
            pastStart,
            pastExpiry);
    var runAfterExpiry =
        billingService.triggerAdminBilling(
            new BillingDtos.RunRequest(LocalDate.of(2026, 5, 1), null, expiredSub.id()));
    assertEquals(0, runAfterExpiry.invoicesGenerated());
    assertEquals(1, runAfterExpiry.expiredClosedCount());
    assertEquals(SubscriptionState.CLOSED, subscriptionService.get(expiredSub.id()).state());
  }

  @Test
  void concurrentBillingAndDiscountUsageLimitPreventDuplicatesAndOveruse() throws Exception {
    authenticateAsAdmin();
    var customer = createActiveCustomer();
    var product =
        productService.create(
            new ProductDtos.Input(
                "Concurrent Product " + UUID.randomUUID(),
                "SOFTWARE",
                new BigDecimal("200.00"),
                new BigDecimal("50.00"),
                null,
                AccountStatus.ACTIVE,
                List.of()));
    var plan =
        planService.create(
            new PlanDtos.Input(
                "Weekly Plan " + UUID.randomUUID(),
                null,
                BillingPeriod.WEEKLY,
                new BigDecimal("200.00"),
                1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                false,
                false,
                false,
                true,
                AccountStatus.ACTIVE,
                List.of(product.id())));

    // Create a fixed discount with usageLimit = 1 for this product
    var singleUseDiscount =
        discountService.create(
            new DiscountDtos.Input(
                "One-Time Promo " + UUID.randomUUID(),
                "ONCE-" + UUID.randomUUID().toString().substring(0, 6),
                "Fixed 50 off once",
                DiscountType.FIXED,
                new BigDecimal("50.00"),
                DiscountScope.PRODUCT,
                product.id(),
                null,
                1,
                new BigDecimal("100.00"),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                1,
                AccountStatus.ACTIVE));

    var billDate = LocalDate.of(2026, 7, 1);
    var subA =
        createActiveSubscription(
            customer.id,
            plan.id(),
            product.id(),
            null,
            1,
            new BigDecimal("10.00"),
            billDate,
            billDate.plusMonths(2));
    var subB =
        createActiveSubscription(
            customer.id,
            plan.id(),
            product.id(),
            null,
            1,
            new BigDecimal("10.00"),
            billDate,
            billDate.plusMonths(2));

    var ready = new CountDownLatch(4);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<BillingDtos.RunView>> futures = new ArrayList<>();
      for (Long targetId : List.of(subA.id(), subA.id(), subB.id(), subB.id())) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  assertTrue(start.await(5, TimeUnit.SECONDS));
                  return billingService.executeBillingRun(
                      billDate, "CONC-" + UUID.randomUUID(), "SCHEDULED", targetId, null);
                }));
      }
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      for (var f : futures) {
        f.get(15, TimeUnit.SECONDS);
      }
    }

    // Both subA and subB must have exactly 1 invoice for billDate
    var invA = invoiceRepo.findBySubscriptionIdAndBillingPeriodStart(subA.id(), billDate);
    var invB = invoiceRepo.findBySubscriptionIdAndBillingPeriodStart(subB.id(), billDate);
    assertTrue(invA.isPresent());
    assertTrue(invB.isPresent());

    // Discount with usageLimit = 1 must have usedCount == 1 and applied to exactly one of the two invoices
    var refreshedDiscount = discountRepo.findById(singleUseDiscount.id()).orElseThrow();
    assertEquals(1, refreshedDiscount.usedCount);
    int discountedInvoices =
        (invA.get().discountAmount.signum() > 0 ? 1 : 0)
            + (invB.get().discountAmount.signum() > 0 ? 1 : 0);
    assertEquals(1, discountedInvoices);
  }

  @Test
  void transactionRollbackAndRetrySafetyPreserveAtomicity() {
    authenticateAsAdmin();
    var customer = createActiveCustomer();
    var product =
        productService.create(
            new ProductDtos.Input(
                "Rollback Product " + UUID.randomUUID(),
                "SERVICE",
                new BigDecimal("500.00"),
                new BigDecimal("100.00"),
                null,
                AccountStatus.ACTIVE,
                List.of()));
    var plan =
        planService.create(
            new PlanDtos.Input(
                "Yearly Plan " + UUID.randomUUID(),
                null,
                BillingPeriod.YEARLY,
                new BigDecimal("500.00"),
                1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                false,
                false,
                false,
                true,
                AccountStatus.ACTIVE,
                List.of(product.id())));
    var discount =
        discountService.create(
            new DiscountDtos.Input(
                "Rollback Discount " + UUID.randomUUID(),
                null,
                null,
                DiscountType.FIXED,
                new BigDecimal("100.00"),
                DiscountScope.PRODUCT,
                product.id(),
                null,
                1,
                null,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                5,
                AccountStatus.ACTIVE));

    var billDate = LocalDate.of(2026, 8, 15);
    var sub =
        createActiveSubscription(
            customer.id,
            plan.id(),
            product.id(),
            null,
            1,
            new BigDecimal("12.00"),
            billDate,
            billDate.plusYears(2));

    // Inject fault after invoice flush and discount increment inside the transaction
    var failedRun =
        billingService.executeBillingRun(
            billDate,
            "FAIL-RUN-" + UUID.randomUUID(),
            "MANUAL",
            sub.id(),
            s -> {
              throw new RuntimeException("Simulated mid-transaction failure");
            });

    assertEquals("FAILED", failedRun.status());
    assertEquals(0, failedRun.invoicesGenerated());
    assertEquals(1, failedRun.failedCount());
    assertEquals("FAILED", failedRun.items().getFirst().status());
    assertEquals(1, failedRun.items().getFirst().attemptCount());

    // Verify rollback: no invoice exists, discount usedCount is still 0, subscription nextBillingDate is still billDate
    assertTrue(
        invoiceRepo.findBySubscriptionIdAndBillingPeriodStart(sub.id(), billDate).isEmpty());
    assertEquals(0, discountRepo.findById(discount.id()).orElseThrow().usedCount);
    assertEquals(billDate, subscriptionRepo.findById(sub.id()).orElseThrow().nextBillingDate);

    // Retry the failed run and verify it succeeds cleanly with attemptCount = 2
    var retriedRun = billingService.retryRun(failedRun.id());
    assertEquals("COMPLETED", retriedRun.status());
    assertEquals(1, retriedRun.invoicesGenerated());
    assertEquals(0, retriedRun.failedCount());
    assertEquals(2, retriedRun.items().getFirst().attemptCount());
    assertTrue(
        invoiceRepo.findBySubscriptionIdAndBillingPeriodStart(sub.id(), billDate).isPresent());
    assertEquals(1, discountRepo.findById(discount.id()).orElseThrow().usedCount);
    assertEquals(
        billDate.plusYears(1), subscriptionRepo.findById(sub.id()).orElseThrow().nextBillingDate);
  }

  private class Browser {
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    String token;
    String header;

    String url(String endpoint) {
      return "http://localhost:" + application.getWebServer().getPort() + endpoint;
    }

    HttpResponse<String> request(String method, String endpoint, Object body, boolean csrf)
        throws Exception {
      if (csrf) {
        var response = request("GET", "/api/v1/auth/csrf", null, false);
        assertEquals(200, response.statusCode(), response.body());
        var data = json.readTree(response.body()).path("data");
        token = data.path("token").asText();
        header = data.path("headerName").asText();
      }
      var req =
          HttpRequest.newBuilder(URI.create(url(endpoint)))
              .header("Content-Type", "application/json");
      if (csrf) req.header(header, token);
      req.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<byte[]> requestBytes(String endpoint) throws Exception {
      var req = HttpRequest.newBuilder(URI.create(url(endpoint))).GET().build();
      return client.send(req, HttpResponse.BodyHandlers.ofByteArray());
    }

    void login(String email, String password) throws Exception {
      var res =
          request("POST", "/api/v1/auth/login", Map.of("email", email, "password", password), true);
      assertEquals(200, res.statusCode(), res.body());
    }
  }

  @Test
  void httpEndpointsEnforceAuthorizationInvoiceLifecyclePdfExportAndEmailDelivery()
      throws Exception {
    var adminBrowser = new Browser();
    adminBrowser.login(adminUser.email, adminPassword);

    String custPass = UUID.randomUUID() + "Aa!";
    String custAEmail = "custa-" + UUID.randomUUID() + "@example.test";
    String custBEmail = "custb-" + UUID.randomUUID() + "@example.test";

    var custABrowser = new Browser();
    assertEquals(
        201,
        custABrowser
            .request(
                "POST",
                "/api/v1/auth/register",
                Map.of("name", "Customer A", "email", custAEmail, "password", custPass),
                true)
            .statusCode());
    custABrowser.login(custAEmail, custPass);

    var custBBrowser = new Browser();
    assertEquals(
        201,
        custBBrowser
            .request(
                "POST",
                "/api/v1/auth/register",
                Map.of("name", "Customer B", "email", custBEmail, "password", custPass),
                true)
            .statusCode());
    custBBrowser.login(custBEmail, custPass);

    // Customer cannot create tax rules, discounts, or trigger billing
    assertEquals(
        403,
        custABrowser
            .request(
                "POST",
                "/api/v1/taxes",
                Map.of("name", "Forbidden Tax", "rate", "10.00", "isDefault", false),
                true)
            .statusCode());
    assertEquals(
        403,
        custABrowser
            .request(
                "POST",
                "/api/v1/discounts",
                Map.of(
                    "name",
                    "Forbidden Disc",
                    "type",
                    "PERCENTAGE",
                    "value",
                    "10.00",
                    "scope",
                    "SUBSCRIPTION"),
                true)
            .statusCode());
    assertEquals(
        403,
        custABrowser.request("POST", "/api/v1/billing/run", Map.of(), true).statusCode());
    assertEquals(
        403,
        custABrowser.request("GET", "/api/v1/billing/runs", null, false).statusCode());

    // Set up subscription and invoice for Customer A
    authenticateAsAdmin();
    var custAEntity =
        customers.findByUserId(users.findByEmail(custAEmail).orElseThrow().id).orElseThrow();
    var prod =
        productService.create(
            new ProductDtos.Input(
                "Portal Product " + UUID.randomUUID(),
                "SOFTWARE",
                new BigDecimal("300.00"),
                new BigDecimal("50.00"),
                null,
                AccountStatus.ACTIVE,
                List.of()));
    var plan =
        planService.create(
            new PlanDtos.Input(
                "Portal Plan " + UUID.randomUUID(),
                null,
                BillingPeriod.MONTHLY,
                new BigDecimal("300.00"),
                1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                false,
                false,
                false,
                true,
                AccountStatus.ACTIVE,
                List.of(prod.id())));
    var subDate = LocalDate.of(2026, 9, 1);
    var sub =
        createActiveSubscription(
            custAEntity.id,
            plan.id(),
            prod.id(),
            null,
            1,
            new BigDecimal("10.00"),
            subDate,
            subDate.plusMonths(6));

    // Trigger billing via Admin HTTP endpoint
    var runRes =
        adminBrowser.request(
            "POST",
            "/api/v1/billing/run",
            Map.of("billingDate", subDate.toString(), "subscriptionId", sub.id()),
            true);
    assertEquals(200, runRes.statusCode(), runRes.body());
    Long invoiceId =
        json.readTree(runRes.body())
            .path("data")
            .path("items")
            .get(0)
            .path("invoiceId")
            .asLong();

    // Customer A can view their invoice and download PDF
    var custAGet = custABrowser.request("GET", "/api/v1/invoices/" + invoiceId, null, false);
    assertEquals(200, custAGet.statusCode(), custAGet.body());
    String invNum = json.readTree(custAGet.body()).path("data").path("invoiceNumber").asText();

    var pdfRes = custABrowser.requestBytes("/api/v1/invoices/" + invoiceId + "/pdf");
    assertEquals(200, pdfRes.statusCode());
    assertTrue(
        pdfRes.headers().firstValue("content-type").orElse("").contains("application/pdf"));
    String pdfText = new String(pdfRes.body(), StandardCharsets.US_ASCII);
    assertTrue(pdfText.startsWith("%PDF-1.4"));
    assertTrue(pdfText.contains(invNum));

    // Customer B cannot read or download Customer A's invoice
    assertEquals(
        403,
        custBBrowser.request("GET", "/api/v1/invoices/" + invoiceId, null, false).statusCode());
    assertEquals(
        403, custBBrowser.requestBytes("/api/v1/invoices/" + invoiceId + "/pdf").statusCode());

    // Customer A cannot confirm invoice
    assertEquals(
        403,
        custABrowser
            .request(
                "POST",
                "/api/v1/invoices/" + invoiceId + "/transition",
                Map.of("state", "CONFIRMED"),
                true)
            .statusCode());

    // Admin confirms invoice (DRAFT -> CONFIRMED)
    var confirmRes =
        adminBrowser.request(
            "POST",
            "/api/v1/invoices/" + invoiceId + "/transition",
            Map.of("state", "CONFIRMED"),
            true);
    assertEquals(200, confirmRes.statusCode(), confirmRes.body());
    assertEquals(
        "CONFIRMED", json.readTree(confirmRes.body()).path("data").path("state").asText());

    // Admin cannot mark invoice PAID without payment evidence (Phase 3 boundary)
    var paidRes =
        adminBrowser.request(
            "POST",
            "/api/v1/invoices/" + invoiceId + "/transition",
            Map.of("state", "PAID"),
            true);
    assertEquals(409, paidRes.statusCode(), paidRes.body());

    // Admin sends invoice email via Mailpit
    var sendRes =
        adminBrowser.request("POST", "/api/v1/invoices/" + invoiceId + "/send", Map.of(), true);
    assertEquals(200, sendRes.statusCode(), sendRes.body());
    assertFalse(json.readTree(sendRes.body()).path("data").path("sentAt").isNull());

    // Admin cancels invoice (CONFIRMED -> CANCELLED)
    var cancelRes =
        adminBrowser.request(
            "POST",
            "/api/v1/invoices/" + invoiceId + "/transition",
            Map.of("state", "CANCELLED"),
            true);
    assertEquals(200, cancelRes.statusCode(), cancelRes.body());
    assertEquals(
        "CANCELLED", json.readTree(cancelRes.body()).path("data").path("state").asText());
  }
}
