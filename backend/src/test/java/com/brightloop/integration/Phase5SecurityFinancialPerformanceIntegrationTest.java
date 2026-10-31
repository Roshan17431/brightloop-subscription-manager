package com.brightloop.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.brightloop.config.AccountStatus;
import com.brightloop.modules.audit.AuditRepository;
import com.brightloop.modules.billing.BillingDtos;
import com.brightloop.modules.billing.BillingService;
import com.brightloop.modules.customer.CustomerDtos;
import com.brightloop.modules.customer.CustomerRepository;
import com.brightloop.modules.customer.CustomerService;
import com.brightloop.modules.discount.DiscountDtos;
import com.brightloop.modules.discount.DiscountScope;
import com.brightloop.modules.discount.DiscountService;
import com.brightloop.modules.discount.DiscountType;
import com.brightloop.modules.invoice.InvoiceDtos;
import com.brightloop.modules.invoice.InvoiceService;
import com.brightloop.modules.invoice.InvoiceState;
import com.brightloop.modules.payment.PaymentDtos;
import com.brightloop.modules.payment.PaymentMethod;
import com.brightloop.modules.payment.PaymentService;
import com.brightloop.modules.payment.PaymentStatus;
import com.brightloop.modules.plan.BillingPeriod;
import com.brightloop.modules.plan.PlanDtos;
import com.brightloop.modules.plan.PlanService;
import com.brightloop.modules.product.ProductDtos;
import com.brightloop.modules.product.ProductService;
import com.brightloop.modules.quotation.QuotationDtos;
import com.brightloop.modules.quotation.QuotationService;
import com.brightloop.modules.subscription.SubscriptionDtos;
import com.brightloop.modules.subscription.SubscriptionService;
import com.brightloop.modules.subscription.SubscriptionState;
import com.brightloop.modules.tax.TaxDtos;
import com.brightloop.modules.tax.TaxService;
import com.brightloop.modules.user.Role;
import com.brightloop.modules.user.UserAccount;
import com.brightloop.modules.user.UserRepository;
import com.brightloop.validation.Rules;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class Phase5SecurityFinancialPerformanceIntegrationTest {
  private static final Logger log =
      LoggerFactory.getLogger(Phase5SecurityFinancialPerformanceIntegrationTest.class);

  private static org.springframework.boot.web.server.servlet.context
          .ServletWebServerApplicationContext
      application;

  private static int port;
  private static UserRepository users;
  private static CustomerRepository customers;
  private static CustomerService customerService;
  private static ProductService productService;
  private static PlanService planService;
  private static TaxService taxService;
  private static DiscountService discountService;
  private static QuotationService quotationService;
  private static SubscriptionService subscriptionService;
  private static BillingService billingService;
  private static InvoiceService invoiceService;
  private static PaymentService paymentService;
  private static AuditRepository auditRepository;
  private static JdbcTemplate jdbcTemplate;
  private static PasswordEncoder passwordEncoder;

  private static String adminEmail;
  private static String adminPassword;

  private final ObjectMapper json = new ObjectMapper();
  private final String testPassword = "P5Secure!" + UUID.randomUUID().toString().substring(0, 8);

  @BeforeAll
  static void startApplication() {
    application = NativeSpringApplication.start();
    port = application.getWebServer().getPort();
    users = application.getBean(UserRepository.class);
    customers = application.getBean(CustomerRepository.class);
    customerService = application.getBean(CustomerService.class);
    productService = application.getBean(ProductService.class);
    planService = application.getBean(PlanService.class);
    taxService = application.getBean(TaxService.class);
    discountService = application.getBean(DiscountService.class);
    quotationService = application.getBean(QuotationService.class);
    subscriptionService = application.getBean(SubscriptionService.class);
    billingService = application.getBean(BillingService.class);
    invoiceService = application.getBean(InvoiceService.class);
    paymentService = application.getBean(PaymentService.class);
    auditRepository = application.getBean(AuditRepository.class);
    jdbcTemplate = application.getBean(JdbcTemplate.class);
    passwordEncoder = application.getBean(PasswordEncoder.class);

    adminPassword = "AdminP5!" + UUID.randomUUID().toString().substring(0, 8);
    var admin = new UserAccount();
    admin.name = "Phase 5 Security Admin";
    admin.email = "p5-admin-" + UUID.randomUUID() + "@brightloop.test";
    admin.passwordHash = passwordEncoder.encode(adminPassword);
    admin.role = Role.ADMIN;
    users.saveAndFlush(admin);
    adminEmail = admin.email;
  }

  @AfterAll
  static void stopApplication() {
    SecurityContextHolder.clearContext();
    if (application != null) application.close();
  }

  @BeforeEach
  void authenticateAdminContext() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                adminEmail, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  @Test
  void testSecurityHeadersCsrfRbacIdorSqlInjectionAndAuditSafety() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    var anonClient = new HttpSessionClient(port, json);
    var adminClient = new HttpSessionClient(port, json);
    var staffClient = new HttpSessionClient(port, json);
    var custAClient = new HttpSessionClient(port, json);
    var custBClient = new HttpSessionClient(port, json);

    // 1. Verify HTTP Security Headers & Session Cookie Flags on initial CSRF handshake
    var csrfRes = anonClient.get("/api/v1/auth/csrf");
    assertEquals(200, csrfRes.statusCode());
    assertEquals(
        "nosniff",
        csrfRes.headers().firstValue("x-content-type-options").orElse("").toLowerCase(Locale.ROOT));
    assertEquals(
        "deny", csrfRes.headers().firstValue("x-frame-options").orElse("").toLowerCase(Locale.ROOT));
    assertEquals(
        "strict-origin-when-cross-origin",
        csrfRes.headers().firstValue("referrer-policy").orElse("").toLowerCase(Locale.ROOT));
    assertTrue(
        csrfRes.headers().firstValue("permissions-policy").orElse("").contains("camera=()"));
    String setCookie =
        String.join(";", csrfRes.headers().allValues("set-cookie")).toLowerCase(Locale.ROOT);
    assertTrue(setCookie.contains("httponly"), "Session cookie must be HttpOnly");
    assertTrue(setCookie.contains("samesite=lax"), "Session cookie must have SameSite=Lax");

    // 2. Verify Unauthenticated access returns 401 with standard ApiError contract and no stack trace
    for (String protectedEp :
        List.of(
            "/api/v1/auth/me",
            "/api/v1/customers",
            "/api/v1/subscriptions",
            "/api/v1/invoices",
            "/api/v1/payments",
            "/api/v1/analytics/overview",
            "/api/v1/portal/overview")) {
      var unauth = anonClient.get(protectedEp);
      assertEquals(401, unauth.statusCode(), "Expected 401 for " + protectedEp);
      JsonNode err = json.readTree(unauth.body());
      assertFalse(err.path("success").asBoolean());
      assertEquals(401, err.path("statusCode").asInt());
      assertEquals("UNAUTHENTICATED", err.path("errorCode").asText());
      assertFalse(unauth.body().contains("Exception"));
    }

    // 3. Verify CSRF enforcement: POST/PUT/PATCH/DELETE without token or with forged token -> 403
    adminClient.login(adminEmail, adminPassword);
    assertEquals(
        403,
        adminClient
            .rawMutation(
                "POST",
                "/api/v1/products",
                Map.of(
                    "name", "No CSRF",
                    "type", "SaaS",
                    "salesPrice", "100.00",
                    "costPrice", "10.00",
                    "status", "ACTIVE",
                    "variants", List.of()),
                null)
            .statusCode());
    assertEquals(
        403,
        adminClient
            .rawMutation(
                "POST",
                "/api/v1/products",
                Map.of(
                    "name", "Forged CSRF",
                    "type", "SaaS",
                    "salesPrice", "100.00",
                    "costPrice", "10.00",
                    "status", "ACTIVE",
                    "variants", List.of()),
                "forged-csrf-token-12345")
            .statusCode());

    // 4. Create Staff, Customer A, and Customer B
    String staffEmail = "p5-staff-" + tag + "@brightloop.test";
    assertEquals(
        201,
        adminClient
            .post(
                "/api/v1/staff",
                Map.of("name", "P5 Staff", "email", staffEmail, "password", testPassword))
            .statusCode());
    staffClient.login(staffEmail, testPassword);

    String custAEmail = "p5-custa-" + tag + "@brightloop.test";
    String custBEmail = "p5-custb-" + tag + "@brightloop.test";
    assertEquals(
        201,
        custAClient
            .post(
                "/api/v1/auth/register",
                Map.of(
                    "name", "=CMD|'/C calc'!A0 Customer A " + tag,
                    "email", custAEmail,
                    "password", testPassword))
            .statusCode());
    custAClient.login(custAEmail, testPassword);

    assertEquals(
        201,
        custBClient
            .post(
                "/api/v1/auth/register",
                Map.of(
                    "name", "Customer B " + tag,
                    "email", custBEmail,
                    "password", testPassword))
            .statusCode());
    custBClient.login(custBEmail, testPassword);

    Long custAId = customers.findByEmail(custAEmail).orElseThrow().id;
    Long custBId = customers.findByEmail(custBEmail).orElseThrow().id;
    Long staffUserId = users.findByEmail(staffEmail).orElseThrow().id;

    // 5. Verify Staff vs Admin privilege boundaries (Staff cannot perform Admin-only actions)
    assertEquals(
        403,
        staffClient
            .post(
                "/api/v1/staff",
                Map.of(
                    "name", "Unauthorized Staff",
                    "email", "unauth-" + tag + "@brightloop.test",
                    "password", testPassword))
            .statusCode());
    assertEquals(403, staffClient.get("/api/v1/users").statusCode());
    assertEquals(
        403,
        staffClient
            .patch("/api/v1/users/" + staffUserId + "/role", Map.of("role", "ADMIN"))
            .statusCode());
    assertEquals(
        403,
        staffClient
            .post(
                "/api/v1/products",
                Map.of(
                    "name", "Staff Forbidden Product",
                    "type", "SaaS",
                    "salesPrice", "100.00",
                    "costPrice", "10.00",
                    "status", "ACTIVE",
                    "variants", List.of()))
            .statusCode());
    assertEquals(
        403,
        staffClient.post("/api/v1/billing/run", Map.of()).statusCode());

    // 6. Admin sets up catalog, tax, discount, quotation, subscription, invoice, and payment for Customer A
    authenticateAdminContext();
    var prod =
        productService.create(
            new ProductDtos.Input(
                "Sec Product " + tag,
                "SOFTWARE",
                new BigDecimal("1000.00"),
                new BigDecimal("250.00"),
                "Internal notes",
                AccountStatus.ACTIVE,
                List.of()));
    var plan =
        planService.create(
            new PlanDtos.Input(
                "Sec Plan " + tag,
                "Monthly plan",
                BillingPeriod.MONTHLY,
                new BigDecimal("1000.00"),
                1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                true,
                true,
                true,
                true,
                AccountStatus.ACTIVE,
                List.of(prod.id())));
    var tax =
        taxService.create(
            new TaxDtos.Input(
                "Sec Tax " + tag,
                "18% GST",
                new BigDecimal("18.00"),
                null,
                prod.id(),
                false,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                AccountStatus.ACTIVE));
    var discount =
        discountService.create(
            new DiscountDtos.Input(
                "Sec Secret Promo " + tag,
                "SECRET-" + tag,
                "Internal promo",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                DiscountScope.PRODUCT,
                prod.id(),
                null,
                1,
                null,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                100,
                AccountStatus.ACTIVE));

    var tmpl =
        quotationService.createTemplate(
            new QuotationDtos.TemplateInput(
                "Sec Template " + tag,
                "Template",
                plan.id(),
                15,
                "Net 15",
                AccountStatus.ACTIVE,
                List.of(
                    new QuotationDtos.TemplateLineInput(
                        prod.id(), null, 1, new BigDecimal("1000.00"), new BigDecimal("18.00")))));
    var quoteA =
        quotationService.generateFromTemplate(
            tmpl.id(),
            new QuotationDtos.GenerateInput(
                tmpl.id(),
                custAId,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 16),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 12, 31),
                "Net 15",
                "Quote for Customer A",
                null));
    quotationService.confirm(quoteA.id(), new QuotationDtos.ConfirmInput(LocalDate.of(2026, 3, 2)));
    var subA =
        quotationService.convertToSubscription(
            quoteA.id(),
            new QuotationDtos.ConvertInput(null, null, true, LocalDate.of(2026, 3, 2)));

    var billRun =
        billingService.executeBillingRun(
            LocalDate.of(2026, 3, 1), "P5-SEC-RUN-" + tag, "MANUAL", subA.id(), null);
    Long invAId = billRun.items().getFirst().invoiceId();
    invoiceService.transition(invAId, InvoiceState.CONFIRMED);
    var payA =
        paymentService.record(
            new PaymentDtos.Input(
                invAId,
                custAId,
                "P5-REF-" + tag,
                null,
                PaymentMethod.UPI,
                new BigDecimal("200.00"),
                LocalDate.of(2026, 3, 3),
                "Partial payment"),
            null);

    // 7. Verify Customer cannot enumerate internal tax rules or discount promo codes (403 Forbidden)
    assertEquals(403, custAClient.get("/api/v1/taxes").statusCode());
    assertEquals(403, custAClient.get("/api/v1/taxes/" + tax.id()).statusCode());
    assertEquals(403, custAClient.get("/api/v1/discounts").statusCode());
    assertEquals(403, custAClient.get("/api/v1/discounts/" + discount.id()).statusCode());

    // Staff CAN view tax rules and discounts, but CANNOT mutate or delete them
    assertEquals(200, staffClient.get("/api/v1/taxes").statusCode());
    assertEquals(200, staffClient.get("/api/v1/discounts").statusCode());
    assertEquals(403, staffClient.delete("/api/v1/taxes/" + tax.id()).statusCode());
    assertEquals(403, staffClient.delete("/api/v1/discounts/" + discount.id()).statusCode());

    // 8. Verify Product costPrice is hidden from CUSTOMER but visible to STAFF/ADMIN
    var custProdRes = custAClient.get("/api/v1/products/" + prod.id());
    assertEquals(200, custProdRes.statusCode());
    assertTrue(
        json.readTree(custProdRes.body()).path("data").path("costPrice").isNull(),
        "Customer must not see internal product costPrice");
    var staffProdRes = staffClient.get("/api/v1/products/" + prod.id());
    assertEquals(
        "250.00", json.readTree(staffProdRes.body()).path("data").path("costPrice").asText());

    // 9. Verify Cross-Customer IDOR Isolation Matrix (Customer B attempting to access Customer A's resources)
    assertEquals(403, custBClient.get("/api/v1/customers/" + custAId).statusCode());
    assertEquals(
        403,
        custBClient
            .put(
                "/api/v1/customers/" + custAId,
                Map.of(
                    "name", "Hacked",
                    "email", custAEmail,
                    "status", "ACTIVE"))
            .statusCode());
    // Customer A cannot change their own account status to INACTIVE
    assertEquals(
        403,
        custAClient
            .put(
                "/api/v1/customers/me",
                Map.of(
                    "name", "Customer A",
                    "email", custAEmail,
                    "status", "INACTIVE"))
            .statusCode());

    // Subscriptions IDOR
    assertEquals(403, custBClient.get("/api/v1/subscriptions/" + subA.id()).statusCode());
    assertEquals(
        403,
        custBClient
            .post("/api/v1/subscriptions/" + subA.id() + "/pause", Map.of())
            .statusCode());
    assertEquals(
        403,
        custBClient
            .post("/api/v1/subscriptions/" + subA.id() + "/resume", Map.of())
            .statusCode());
    assertEquals(
        403,
        custBClient
            .post("/api/v1/subscriptions/" + subA.id() + "/renew", Map.of("periods", 1))
            .statusCode());
    assertEquals(
        403,
        custBClient
            .post("/api/v1/subscriptions/" + subA.id() + "/close", Map.of("reason", "IDOR"))
            .statusCode());

    // Quotations IDOR
    assertEquals(403, custBClient.get("/api/v1/quotations/" + quoteA.id()).statusCode());
    assertEquals(
        403, custBClient.get("/api/v1/quotations?customerId=" + custAId).statusCode());

    // Invoices IDOR
    assertEquals(403, custBClient.get("/api/v1/invoices/" + invAId).statusCode());
    assertEquals(403, custBClient.get("/api/v1/invoices/" + invAId + "/pdf").statusCode());
    assertEquals(
        403, custBClient.get("/api/v1/invoices?customerId=" + custAId).statusCode());

    // Payments IDOR
    assertEquals(403, custBClient.get("/api/v1/payments/" + payA.id()).statusCode());
    assertEquals(
        403, custBClient.get("/api/v1/invoices/" + invAId + "/payments").statusCode());
    assertEquals(
        403, custBClient.get("/api/v1/payments?customerId=" + custAId).statusCode());
    assertEquals(
        403, custBClient.get("/api/v1/payments?invoiceId=" + invAId).statusCode());

    // Customer B's list endpoints must never contain Customer A's records
    assertEquals(
        0,
        json.readTree(custBClient.get("/api/v1/subscriptions").body())
            .path("data")
            .path("totalElements")
            .asInt());
    assertEquals(
        0,
        json.readTree(custBClient.get("/api/v1/invoices").body())
            .path("data")
            .path("totalElements")
            .asInt());
    assertEquals(
        0,
        json.readTree(custBClient.get("/api/v1/payments").body())
            .path("data")
            .path("totalElements")
            .asInt());

    // 10. Verify SQL Injection & Wildcard Escaping safety across search parameters
    for (String payload :
        List.of(
            "' OR 1=1 --",
            "'; DROP TABLE users; --",
            "100%\\_test",
            "<script>alert('xss')</script>")) {
      String encoded = URLEncoder.encode(payload, StandardCharsets.UTF_8);
      for (String ep :
          List.of(
              "/api/v1/customers?q=" + encoded,
              "/api/v1/products?q=" + encoded,
              "/api/v1/subscriptions?q=" + encoded,
              "/api/v1/invoices?q=" + encoded,
              "/api/v1/payments?q=" + encoded)) {
        var res = adminClient.get(ep);
        assertEquals(200, res.statusCode(), "Search payload failed on " + ep + ": " + res.body());
      }
    }

    // 11. Verify CSV Formula Injection protection on receivables export
    var csvExportRes =
        adminClient.get(
            "/api/v1/analytics/reports/export?section=receivables&asOfDate=2026-06-01&customerId="
                + custAId);
    assertEquals(200, csvExportRes.statusCode());
    assertTrue(
        csvExportRes.body().contains("\"'=CMD|'/C calc'!A0 Customer A " + tag + "\""),
        "CSV export must neutralize leading '=' formula injection: " + csvExportRes.body());

    // 12. Verify Audit Logs never contain raw passwords or tokens
    var audits = auditRepository.findAll();
    assertFalse(audits.isEmpty());
    for (var event : audits) {
      if (event.metadata != null) {
        assertFalse(
            event.metadata.contains(testPassword), "Audit log must never contain raw password");
        assertFalse(
            event.metadata.contains(adminPassword), "Audit log must never contain admin password");
      }
    }
  }

  @Test
  void testFinancialIntegrityLeapYearZeroInvoiceHistoricalImmutabilityAndCompleteFlow() {
    authenticateAdminContext();
    String tag = UUID.randomUUID().toString().substring(0, 8);

    // 1. Verify Leap-Year & End-of-Month Schedule Advancement rules
    // 2028 is a leap year (Feb has 29 days)
    LocalDate jan31Leap = LocalDate.of(2028, 1, 31);
    LocalDate feb29Leap =
        Rules.advanceBillingDate(jan31Leap, BillingPeriod.MONTHLY, jan31Leap);
    assertEquals(LocalDate.of(2028, 2, 29), feb29Leap);
    LocalDate mar31AfterLeap =
        Rules.advanceBillingDate(feb29Leap, BillingPeriod.MONTHLY, jan31Leap);
    assertEquals(LocalDate.of(2028, 3, 31), mar31AfterLeap);

    // Yearly subscription anchored on Feb 29, 2028 advances to Feb 28, 2029, and back to Feb 29, 2032
    LocalDate feb28NextYear =
        Rules.advanceBillingDate(feb29Leap, BillingPeriod.YEARLY, feb29Leap);
    assertEquals(LocalDate.of(2029, 2, 28), feb28NextYear);
    LocalDate feb29Leap2032 =
        Rules.advanceBillingDate(LocalDate.of(2031, 2, 28), BillingPeriod.YEARLY, feb29Leap);
    assertEquals(LocalDate.of(2032, 2, 29), feb29Leap2032);

    // 2. Verify Zero-Value Invoice Settlement (100% discount) & Historical Snapshot Immutability
    var customer =
        customerService.create(
            new CustomerDtos.Input(
                "Fin Integrity Corp " + tag,
                "fin-" + tag + "@brightloop.test",
                "+91 9847000001",
                "Fin Integrity Pvt Ltd",
                "Infopark Phase 2, Kochi",
                AccountStatus.ACTIVE));

    var product =
        productService.create(
            new ProductDtos.Input(
                "Core Platform " + tag,
                "SOFTWARE",
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                "Enterprise license",
                AccountStatus.ACTIVE,
                List.of(
                    new ProductDtos.VariantInput(null, "Enterprise Tier", new BigDecimal("500.00")))));
    Long variantId = product.variants().getFirst().id();

    var plan =
        planService.create(
            new PlanDtos.Input(
                "Enterprise Monthly " + tag,
                "Monthly billing",
                BillingPeriod.MONTHLY,
                new BigDecimal("1500.00"),
                1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2029, 12, 31),
                true,
                true,
                true,
                true,
                AccountStatus.ACTIVE,
                List.of(product.id())));

    var taxRule =
        taxService.create(
            new TaxDtos.Input(
                "GST 18 " + tag,
                "18% GST",
                new BigDecimal("18.00"),
                null,
                product.id(),
                false,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2029, 12, 31),
                AccountStatus.ACTIVE));

    // Create subscription for 2 seats @ (1000 + 500) = 3000.00 pre-tax subtotal
    var sub =
        subscriptionService.create(
            new SubscriptionDtos.Input(
                customer.id(),
                plan.id(),
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 12, 31),
                "Net 15",
                List.of(
                    new SubscriptionDtos.LineInput(
                        product.id(), variantId, 2, new BigDecimal("18.00")))));
    subscriptionService.transition(sub.id(), SubscriptionState.QUOTATION);
    subscriptionService.transition(sub.id(), SubscriptionState.CONFIRMED);
    subscriptionService.transition(sub.id(), SubscriptionState.ACTIVE);

    // Create a 1-time 100% promotional discount scoped to this subscription for the first cycle
    discountService.create(
        new DiscountDtos.Input(
            "First Month Free 100% " + tag,
            "FREE100-" + tag,
            "100% off first cycle",
            DiscountType.PERCENTAGE,
            new BigDecimal("100.00"),
            DiscountScope.SUBSCRIPTION,
            null,
            sub.id(),
            1,
            null,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            1,
            AccountStatus.ACTIVE));

    // Cycle 1 (2026-01-31): 100% discount consumes usageLimit=1 -> total = 0.00
    var run1 =
        billingService.executeBillingRun(
            LocalDate.of(2026, 1, 31), "FIN-RUN1-" + tag, "MANUAL", sub.id(), null);
    assertEquals(1, run1.invoicesGenerated());
    Long zeroInvId = run1.items().getFirst().invoiceId();
    var zeroInv = invoiceService.get(zeroInvId);
    assertEquals(new BigDecimal("3000.00"), zeroInv.subtotal());
    assertEquals(new BigDecimal("3000.00"), zeroInv.discountAmount());
    assertEquals(new BigDecimal("0.00"), zeroInv.taxAmount());
    assertEquals(new BigDecimal("0.00"), zeroInv.total());

    // Settle zero-value invoice directly
    var settledZero = invoiceService.settleZeroInvoice(zeroInvId);
    assertEquals(InvoiceState.PAID, settledZero.state());
    assertEquals("PAID", settledZero.paymentStatus());
    assertEquals(new BigDecimal("0.00"), settledZero.outstandingAmount());
    assertTrue(settledZero.paymentReference().startsWith("ZERO-SETTLEMENT-"));

    // Cycle 2 (2026-02-28): 1-time discount is exhausted -> full 3000.00 + 18% GST (540.00) = 3540.00
    var run2 =
        billingService.executeBillingRun(
            LocalDate.of(2026, 2, 28), "FIN-RUN2-" + tag, "MANUAL", sub.id(), null);
    assertEquals(1, run2.invoicesGenerated());
    Long inv2Id = run2.items().getFirst().invoiceId();
    var inv2 = invoiceService.get(inv2Id);
    assertEquals(new BigDecimal("3000.00"), inv2.subtotal());
    assertEquals(new BigDecimal("0.00"), inv2.discountAmount());
    assertEquals(new BigDecimal("540.00"), inv2.taxAmount());
    assertEquals(new BigDecimal("3540.00"), inv2.total());

    // Now mutate Product price (1000 -> 9999) and Tax Rule rate (18% -> 28%) AFTER invoice generation
    productService.update(
        product.id(),
        new ProductDtos.Input(
            product.name(),
            product.type(),
            new BigDecimal("9999.00"),
            new BigDecimal("200.00"),
            "Price hiked",
            AccountStatus.ACTIVE,
            List.of(
                new ProductDtos.VariantInput(variantId, "Enterprise Tier", new BigDecimal("500.00")))));
    taxService.update(
        taxRule.id(),
        new TaxDtos.Input(
            taxRule.name(),
            "28% GST",
            new BigDecimal("28.00"),
            null,
            product.id(),
            false,
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2029, 12, 31),
            AccountStatus.ACTIVE));

    // Historical Invoice 2 must remain 100% immutable!
    var inv2AfterCatalogEdit = invoiceService.get(inv2Id);
    assertEquals(new BigDecimal("3000.00"), inv2AfterCatalogEdit.subtotal());
    assertEquals(new BigDecimal("540.00"), inv2AfterCatalogEdit.taxAmount());
    assertEquals(new BigDecimal("3540.00"), inv2AfterCatalogEdit.total());
    assertEquals(
        new BigDecimal("1500.00"), inv2AfterCatalogEdit.lines().getFirst().unitPrice());
    assertEquals(
        new BigDecimal("18.00"), inv2AfterCatalogEdit.lines().getFirst().taxRate());
  }

  @Test
  void testThreeThousandSubscriptionsLatencyThroughputAndConcurrencyBenchmark() throws Exception {
    authenticateAdminContext();
    String tag = "P5BM-" + UUID.randomUUID().toString().substring(0, 8);

    var customer =
        customerService.create(
            new CustomerDtos.Input(
                "Benchmark Enterprise " + tag,
                "bm-" + tag + "@brightloop.test",
                "+91 9847099999",
                "Benchmark Corp",
                "Kochi",
                AccountStatus.ACTIVE));
    var prod =
        productService.create(
            new ProductDtos.Input(
                "Benchmark Cloud Seat " + tag,
                "SaaS",
                new BigDecimal("500.00"),
                new BigDecimal("100.00"),
                "Load test product",
                AccountStatus.ACTIVE,
                List.of()));
    var plan =
        planService.create(
            new PlanDtos.Input(
                "Benchmark Monthly Plan " + tag,
                "Monthly",
                BillingPeriod.MONTHLY,
                new BigDecimal("500.00"),
                1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2028, 12, 31),
                true,
                true,
                true,
                true,
                AccountStatus.ACTIVE,
                List.of(prod.id())));

    int bulkCount = 3000;
    Timestamp now = Timestamp.from(Instant.now());
    Date startDate = Date.valueOf(LocalDate.of(2026, 1, 1));
    Date expiryDate = Date.valueOf(LocalDate.of(2026, 12, 31));
    Date nextBillingDate = Date.valueOf(LocalDate.of(2026, 6, 20));

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
            startDate,
            expiryDate,
            nextBillingDate,
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

    List<Long> subIds =
        jdbcTemplate.queryForList(
            "SELECT id FROM subscriptions WHERE customer_id = ? ORDER BY id ASC",
            Long.class,
            customer.id());
    assertEquals(bulkCount, subIds.size());

    List<Object[]> lineBatch = new ArrayList<>(bulkCount);
    List<Object[]> invBatch = new ArrayList<>(bulkCount);
    Date issueDate = Date.valueOf(LocalDate.of(2026, 5, 1));
    Date dueDate = Date.valueOf(LocalDate.of(2026, 5, 16));

    for (int i = 0; i < subIds.size(); i++) {
      Long sId = subIds.get(i);
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
      boolean paid = (i < 2000);
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
            expiryDate,
            issueDate,
            dueDate,
            "Net 15",
            paid ? "PAID" : "CONFIRMED",
            new BigDecimal("500.00"),
            BigDecimal.ZERO,
            new BigDecimal("90.00"),
            new BigDecimal("590.00"),
            paid ? new BigDecimal("590.00") : BigDecimal.ZERO,
            tag + "-IDEM-" + i,
            paid ? tag + "-PAY-" + i : null,
            paid ? now : null,
            now,
            now,
            now
          });
    }

    jdbcTemplate.batchUpdate(
        "INSERT INTO subscription_lines (subscription_id, product_id, product_name, quantity, unit_price, tax_rate, amount) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
        lineBatch);

    jdbcTemplate.batchUpdate(
        "INSERT INTO invoices (version, invoice_number, subscription_id, subscription_number, customer_id, customer_name, customer_email, plan_id, plan_name, billing_period, billing_period_start, billing_period_end, issue_date, due_date, payment_terms, state, subtotal, discount_amount, tax_amount, total, amount_paid, idempotency_key, payment_reference, paid_at, confirmed_at, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        invBatch);

    List<Long> invIds =
        jdbcTemplate.queryForList(
            "SELECT id FROM invoices WHERE customer_id = ? ORDER BY id ASC LIMIT 2000",
            Long.class,
            customer.id());
    List<Object[]> payBatch = new ArrayList<>(invIds.size());
    for (int i = 0; i < invIds.size(); i++) {
      payBatch.add(
          new Object[] {
            0L,
            tag + "-PAY-" + i,
            tag + "-PAY-IDEM-" + i,
            invIds.get(i),
            tag + "-INV-" + i,
            customer.id(),
            customer.name(),
            "UPI",
            new BigDecimal("590.00"),
            issueDate,
            "COMPLETED",
            adminEmail,
            now,
            now
          });
    }
    jdbcTemplate.batchUpdate(
        "INSERT INTO payments (version, payment_reference, idempotency_key, invoice_id, invoice_number, customer_id, customer_name, method, amount, payment_date, status, recorded_by, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        payBatch);

    var adminClient = new HttpSessionClient(port, json);
    adminClient.login(adminEmail, adminPassword);

    Map<String, String> benchmarkEndpoints = new LinkedHashMap<>();
    benchmarkEndpoints.put(
        "GET /api/v1/analytics/overview", "/api/v1/analytics/overview?asOfDate=2026-06-15");
    benchmarkEndpoints.put(
        "GET /api/v1/analytics/revenue", "/api/v1/analytics/revenue?asOfDate=2026-06-15");
    benchmarkEndpoints.put(
        "GET /api/v1/analytics/subscriptions",
        "/api/v1/analytics/subscriptions?asOfDate=2026-06-15");
    benchmarkEndpoints.put(
        "GET /api/v1/analytics/payments", "/api/v1/analytics/payments?asOfDate=2026-06-15");
    benchmarkEndpoints.put(
        "GET /api/v1/analytics/receivables",
        "/api/v1/analytics/receivables?asOfDate=2026-06-15");
    benchmarkEndpoints.put(
        "GET /api/v1/analytics/renewals", "/api/v1/analytics/renewals?asOfDate=2026-06-15");
    benchmarkEndpoints.put(
        "GET /api/v1/analytics/reports/export",
        "/api/v1/analytics/reports/export?section=receivables&asOfDate=2026-06-15");
    benchmarkEndpoints.put(
        "GET /api/v1/subscriptions (paged)", "/api/v1/subscriptions?page=0&size=20");
    benchmarkEndpoints.put(
        "GET /api/v1/invoices (paged overdue)",
        "/api/v1/invoices?overdue=true&asOfDate=2026-06-15&page=0&size=20");
    benchmarkEndpoints.put("GET /api/v1/payments (paged)", "/api/v1/payments?page=0&size=20");

    int iterations = 15;
    StringBuilder report =
        new StringBuilder("\n=== BRIGHTLOOP PHASE 5 PERFORMANCE BENCHMARK (3,000+ SUBSCRIPTIONS) ===\n");
    for (var entry : benchmarkEndpoints.entrySet()) {
      String label = entry.getKey();
      String path = entry.getValue();
      // Warmup
      assertEquals(200, adminClient.get(path).statusCode());

      List<Double> latenciesMs = new ArrayList<>(iterations);
      int errors = 0;
      long batchStart = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        long t0 = System.nanoTime();
        var res = adminClient.get(path);
        double elapsedMs = (System.nanoTime() - t0) / 1_000_000.0;
        latenciesMs.add(elapsedMs);
        if (res.statusCode() != 200) errors++;
      }
      double totalSec = (System.nanoTime() - batchStart) / 1_000_000_000.0;
      Collections.sort(latenciesMs);
      double p50 = percentile(latenciesMs, 50);
      double p95 = percentile(latenciesMs, 95);
      double p99 = percentile(latenciesMs, 99);
      double rps = iterations / Math.max(totalSec, 0.001);

      report.append(
          String.format(
              Locale.ROOT,
              "%-40s | p50: %6.2f ms | p95: %6.2f ms | p99: %6.2f ms | rps: %6.1f | errors: %d%n",
              label,
              p50,
              p95,
              p99,
              rps,
              errors));

      assertEquals(0, errors, "Zero errors required for " + label);
      assertTrue(
          p95 < 2000.0,
          label + " p95 latency exceeded 2000ms threshold: " + p95 + "ms");
    }

    // Concurrent load test: 20 virtual threads firing 5 requests each (100 concurrent HTTP requests)
    int concurrentWorkers = 20;
    int reqsPerWorker = 5;
    AtomicInteger concurrentErrors = new AtomicInteger(0);
    List<Double> concurrentLatencies = new CopyOnWriteArrayList<>();
    long concStart = System.nanoTime();
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> futures = new ArrayList<>();
      for (int w = 0; w < concurrentWorkers; w++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int r = 0; r < reqsPerWorker; r++) {
                    long t0 = System.nanoTime();
                    try {
                      var res =
                          adminClient.get("/api/v1/analytics/overview?asOfDate=2026-06-15");
                      concurrentLatencies.add((System.nanoTime() - t0) / 1_000_000.0);
                      if (res.statusCode() != 200) concurrentErrors.incrementAndGet();
                    } catch (Exception e) {
                      concurrentErrors.incrementAndGet();
                    }
                  }
                }));
      }
      for (var f : futures) f.get(30, TimeUnit.SECONDS);
    }
    double concTotalSec = (System.nanoTime() - concStart) / 1_000_000_000.0;
    Collections.sort(concurrentLatencies);
    double concP50 = percentile(concurrentLatencies, 50);
    double concP95 = percentile(concurrentLatencies, 95);
    double concP99 = percentile(concurrentLatencies, 99);
    double concRps = (concurrentWorkers * reqsPerWorker) / Math.max(concTotalSec, 0.001);
    report.append(
        String.format(
            Locale.ROOT,
            "%-40s | p50: %6.2f ms | p95: %6.2f ms | p99: %6.2f ms | rps: %6.1f | errors: %d%n",
            "CONCURRENT 20VUs /analytics/overview",
            concP50,
            concP95,
            concP99,
            concRps,
            concurrentErrors.get()));
    log.info(report.toString());

    assertEquals(0, concurrentErrors.get(), "Zero errors under 20-VU concurrent load");
    assertTrue(concP95 < 2000.0, "Concurrent p95 must be under 2000ms: " + concP95);
  }

  private static double percentile(List<Double> sorted, int p) {
    if (sorted.isEmpty()) return 0.0;
    int idx = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
    idx = Math.max(0, Math.min(idx, sorted.size() - 1));
    return sorted.get(idx);
  }

  private static final class HttpSessionClient {
    private final int port;
    private final ObjectMapper json;
    private final HttpClient http;
    private String csrf;

    HttpSessionClient(int port, ObjectMapper json) {
      this.port = port;
      this.json = json;
      var cookies = new CookieManager();
      cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
      this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    void refreshCsrf() throws Exception {
      var res = get("/api/v1/auth/csrf");
      var data = json.readTree(res.body()).path("data");
      this.csrf = data.path("token").asText();
    }

    void login(String email, String password) throws Exception {
      refreshCsrf();
      var res = post("/api/v1/auth/login", Map.of("email", email, "password", password));
      assertEquals(200, res.statusCode(), res.body());
      refreshCsrf();
    }

    HttpResponse<String> get(String path) throws Exception {
      var req =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
      return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    HttpResponse<String> post(String path, Object payload) throws Exception {
      return mutate("POST", path, payload);
    }

    HttpResponse<String> put(String path, Object payload) throws Exception {
      return mutate("PUT", path, payload);
    }

    HttpResponse<String> patch(String path, Object payload) throws Exception {
      return mutate("PATCH", path, payload);
    }

    HttpResponse<String> delete(String path) throws Exception {
      return mutate("DELETE", path, null);
    }

    HttpResponse<String> rawMutation(
        String method, String path, Object payload, String explicitCsrf) throws Exception {
      var builder =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .header("Content-Type", "application/json");
      if (explicitCsrf != null) {
        builder.header("X-CSRF-TOKEN", explicitCsrf);
      }
      builder.method(
          method,
          payload == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(
                  json.writeValueAsString(payload), StandardCharsets.UTF_8));
      return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> mutate(String method, String path, Object payload)
        throws Exception {
      if (csrf == null) refreshCsrf();
      var res = rawMutation(method, path, payload, csrf);
      if (path.startsWith("/api/v1/auth/register")) {
        refreshCsrf();
      }
      return res;
    }
  }
}
