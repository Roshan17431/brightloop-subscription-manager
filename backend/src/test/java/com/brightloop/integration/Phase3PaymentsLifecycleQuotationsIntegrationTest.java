package com.brightloop.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.brightloop.config.AccountStatus;
import com.brightloop.exception.ApiException;
import com.brightloop.modules.billing.*;
import com.brightloop.modules.customer.*;
import com.brightloop.modules.invoice.*;
import com.brightloop.modules.payment.*;
import com.brightloop.modules.plan.*;
import com.brightloop.modules.product.*;
import com.brightloop.modules.quotation.*;
import com.brightloop.modules.subscription.*;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class Phase3PaymentsLifecycleQuotationsIntegrationTest {
  private static org.springframework.boot.web.server.servlet.context
          .ServletWebServerApplicationContext
      application;

  private static UserRepository users;
  private static CustomerRepository customers;
  private static ProductService productService;
  private static PlanService planService;
  private static SubscriptionService subscriptionService;
  private static BillingService billingService;
  private static InvoiceService invoiceService;
  private static PaymentService paymentService;
  private static QuotationService quotationService;
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
    billingService = application.getBean(BillingService.class);
    invoiceService = application.getBean(InvoiceService.class);
    paymentService = application.getBean(PaymentService.class);
    quotationService = application.getBean(QuotationService.class);
    encoder = application.getBean(PasswordEncoder.class);

    adminPassword = UUID.randomUUID() + "Aa!";
    var u = new UserAccount();
    u.name = "Phase 3 Admin";
    u.email = "p3-admin-" + UUID.randomUUID() + "@example.test";
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
    c.email = "p3-cust-" + UUID.randomUUID() + "@example.test";
    c.companyName = "Brightloop Phase 3 Corp";
    c.billingAddress = "100 Infopark, Kochi";
    c.status = AccountStatus.ACTIVE;
    return customers.saveAndFlush(c);
  }

  @Test
  void partialPaymentsMultiplePaymentsReconciliationAndReversal() {
    authenticateAsAdmin();
    var customer = createActiveCustomer();
    var product =
        productService.create(
            new ProductDtos.Input(
                "Cloud ERP " + UUID.randomUUID(),
                "SOFTWARE",
                new BigDecimal("10000.00"),
                new BigDecimal("2000.00"),
                "ERP license",
                AccountStatus.ACTIVE,
                List.of()));
    var plan =
        planService.create(
            new PlanDtos.Input(
                "Monthly ERP Plan " + UUID.randomUUID(),
                "Monthly billing",
                BillingPeriod.MONTHLY,
                new BigDecimal("10000.00"),
                1,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 12, 31),
                true,
                true,
                true,
                true,
                AccountStatus.ACTIVE,
                List.of(product.id())));

    var sub =
        subscriptionService.create(
            new SubscriptionDtos.Input(
                customer.id,
                plan.id(),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 12, 31),
                "Net 15",
                List.of(
                    new SubscriptionDtos.LineInput(
                        product.id(), null, 1, new BigDecimal("0.00")))));
    subscriptionService.transition(sub.id(), SubscriptionState.QUOTATION);
    subscriptionService.transition(sub.id(), SubscriptionState.CONFIRMED);
    subscriptionService.transition(sub.id(), SubscriptionState.ACTIVE);

    var run =
        billingService.executeBillingRun(
            LocalDate.of(2026, 3, 1),
            "P3-PAY-RUN-" + UUID.randomUUID(),
            "MANUAL",
            sub.id(),
            null);
    Long invoiceId = run.items().getFirst().invoiceId();
    var draftInv = invoiceService.get(invoiceId);
    assertEquals(new BigDecimal("10000.00"), draftInv.total());
    assertEquals(InvoiceState.DRAFT, draftInv.state());

    // Cannot record payment against a DRAFT invoice
    assertThrows(
        ApiException.class,
        () ->
            paymentService.record(
                new PaymentDtos.Input(
                    invoiceId,
                    customer.id,
                    "REF-DRAFT-" + UUID.randomUUID(),
                    null,
                    PaymentMethod.UPI,
                    new BigDecimal("4000.00"),
                    LocalDate.of(2026, 3, 2),
                    "Draft attempt"),
                null));

    // Confirm invoice
    invoiceService.transition(invoiceId, InvoiceState.CONFIRMED);

    // Record Payment 1: 4,000.00 (Partial payment)
    String ref1 = "UTR-4000-" + UUID.randomUUID().toString().substring(0, 8);
    String idem1 = "IDEM-1-" + UUID.randomUUID();
    var pay1 =
        paymentService.record(
            new PaymentDtos.Input(
                invoiceId,
                customer.id,
                ref1,
                idem1,
                PaymentMethod.BANK_TRANSFER,
                new BigDecimal("4000.00"),
                LocalDate.of(2026, 3, 3),
                "First installment"),
            idem1);
    assertEquals(new BigDecimal("4000.00"), pay1.amount());
    assertEquals(new BigDecimal("4000.00"), pay1.invoiceAmountPaid());
    assertEquals(new BigDecimal("6000.00"), pay1.invoiceOutstandingAmount());
    assertEquals(InvoiceState.CONFIRMED, pay1.invoiceState());
    assertEquals("PARTIALLY_PAID", pay1.invoicePaymentStatus());

    var partialInv = invoiceService.get(invoiceId);
    assertTrue(partialInv.partiallyPaid());
    assertEquals("PARTIALLY_PAID", partialInv.paymentStatus());
    assertEquals(new BigDecimal("4000.00"), partialInv.amountPaid());
    assertEquals(new BigDecimal("6000.00"), partialInv.outstandingAmount());

    // Idempotent retry with same idempotencyKey must NOT double count
    var replay =
        paymentService.record(
            new PaymentDtos.Input(
                invoiceId,
                customer.id,
                ref1,
                idem1,
                PaymentMethod.BANK_TRANSFER,
                new BigDecimal("4000.00"),
                LocalDate.of(2026, 3, 3),
                "First installment"),
            idem1);
    assertEquals(pay1.id(), replay.id());
    assertEquals(new BigDecimal("4000.00"), invoiceService.get(invoiceId).amountPaid());

    // Duplicate paymentReference with a new request must be rejected
    assertThrows(
        ApiException.class,
        () ->
            paymentService.record(
                new PaymentDtos.Input(
                    invoiceId,
                    customer.id,
                    ref1,
                    "DIFFERENT-IDEM-" + UUID.randomUUID(),
                    PaymentMethod.UPI,
                    new BigDecimal("1000.00"),
                    LocalDate.of(2026, 3, 4),
                    "Dup ref"),
                null));

    // Overpayment (> 6000.00 remaining) must be rejected
    assertThrows(
        ApiException.class,
        () ->
            paymentService.record(
                new PaymentDtos.Input(
                    invoiceId,
                    customer.id,
                    "UTR-OVER-" + UUID.randomUUID(),
                    null,
                    PaymentMethod.CARD,
                    new BigDecimal("6000.01"),
                    LocalDate.of(2026, 3, 4),
                    "Overpay attempt"),
                null));

    // Negative / zero payment amount must be rejected
    assertThrows(
        IllegalArgumentException.class,
        () ->
            paymentService.record(
                new PaymentDtos.Input(
                    invoiceId,
                    customer.id,
                    "UTR-ZERO-" + UUID.randomUUID(),
                    null,
                    PaymentMethod.CASH,
                    BigDecimal.ZERO,
                    LocalDate.of(2026, 3, 4),
                    "Zero attempt"),
                null));

    // Cannot cancel an invoice while it has active recorded payments
    assertThrows(
        ApiException.class,
        () -> invoiceService.transition(invoiceId, InvoiceState.CANCELLED));

    // Record Payment 2: 6,000.00 -> Invoice automatically becomes PAID
    String ref2 = "UTR-6000-" + UUID.randomUUID().toString().substring(0, 8);
    var pay2 =
        paymentService.record(
            new PaymentDtos.Input(
                invoiceId,
                customer.id,
                ref2,
                null,
                PaymentMethod.UPI,
                new BigDecimal("6000.00"),
                LocalDate.of(2026, 3, 5),
                "Final balance"),
            null);
    assertEquals(InvoiceState.PAID, pay2.invoiceState());
    assertEquals(new BigDecimal("10000.00"), pay2.invoiceAmountPaid());
    assertEquals(new BigDecimal("0.00"), pay2.invoiceOutstandingAmount());

    var paidInv = invoiceService.get(invoiceId);
    assertEquals(InvoiceState.PAID, paidInv.state());
    assertEquals("PAID", paidInv.paymentStatus());
    assertFalse(paidInv.partiallyPaid());
    assertNotNull(paidInv.paidAt());
    assertEquals(ref2, paidInv.paymentReference());

    // Reverse Payment 2 -> Invoice reverts to CONFIRMED (PARTIALLY_PAID) with 6,000.00 remaining
    var reversed =
        paymentService.reverse(
            pay2.id(), new PaymentDtos.ReversalInput("Customer bank reversal", null));
    assertEquals(PaymentStatus.REVERSED, reversed.status());
    assertEquals(InvoiceState.CONFIRMED, reversed.invoiceState());
    assertEquals(new BigDecimal("4000.00"), reversed.invoiceAmountPaid());
    assertEquals(new BigDecimal("6000.00"), reversed.invoiceOutstandingAmount());

    // Full history preserved (both COMPLETED and REVERSED records exist)
    var history = paymentService.listForInvoice(invoiceId);
    assertEquals(2, history.size());
  }

  @Test
  void concurrentPaymentRequestsPreventOverpaymentAndSerializeReconciliation()
      throws Exception {
    authenticateAsAdmin();
    var customer = createActiveCustomer();
    var product =
        productService.create(
            new ProductDtos.Input(
                "Concurrent Pay Product " + UUID.randomUUID(),
                "SERVICE",
                new BigDecimal("5000.00"),
                new BigDecimal("1000.00"),
                "Notes",
                AccountStatus.ACTIVE,
                List.of()));
    var plan =
        planService.create(
            new PlanDtos.Input(
                "Concurrent Pay Plan " + UUID.randomUUID(),
                "Monthly",
                BillingPeriod.MONTHLY,
                new BigDecimal("5000.00"),
                1,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 12, 31),
                true,
                true,
                true,
                true,
                AccountStatus.ACTIVE,
                List.of(product.id())));
    var sub =
        subscriptionService.create(
            new SubscriptionDtos.Input(
                customer.id,
                plan.id(),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 12, 31),
                "Net 15",
                List.of(
                    new SubscriptionDtos.LineInput(
                        product.id(), null, 1, BigDecimal.ZERO))));
    subscriptionService.transition(sub.id(), SubscriptionState.QUOTATION);
    subscriptionService.transition(sub.id(), SubscriptionState.CONFIRMED);
    subscriptionService.transition(sub.id(), SubscriptionState.ACTIVE);

    var run =
        billingService.executeBillingRun(
            LocalDate.of(2026, 4, 1),
            "P3-CONC-RUN-" + UUID.randomUUID(),
            "MANUAL",
            sub.id(),
            null);
    Long invoiceId = run.items().getFirst().invoiceId();
    invoiceService.transition(invoiceId, InvoiceState.CONFIRMED);

    int threads = 4;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger(0);
    AtomicInteger conflicts = new AtomicInteger(0);

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      futures.add(
          pool.submit(
              () -> {
                authenticateAsAdmin();
                ready.countDown();
                try {
                  start.await(5, TimeUnit.SECONDS);
                  paymentService.record(
                      new PaymentDtos.Input(
                          invoiceId,
                          customer.id,
                          "CONC-REF-" + UUID.randomUUID(),
                          null,
                          PaymentMethod.UPI,
                          new BigDecimal("3000.00"),
                          LocalDate.of(2026, 4, 2),
                          "Concurrent 3000 on 5000 invoice"),
                      null);
                  successes.incrementAndGet();
                } catch (ApiException ex) {
                  conflicts.incrementAndGet();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  SecurityContextHolder.clearContext();
                }
              }));
    }
    assertTrue(ready.await(5, TimeUnit.SECONDS));
    start.countDown();
    for (var f : futures) {
      f.get(10, TimeUnit.SECONDS);
    }
    pool.shutdownNow();

    assertEquals(1, successes.get(), "Exactly one 3,000 payment can fit into a 5,000 invoice");
    assertEquals(3, conflicts.get(), "Other 3 concurrent 3,000 payments must be rejected");
    authenticateAsAdmin();
    var inv = invoiceService.get(invoiceId);
    assertEquals(new BigDecimal("3000.00"), inv.amountPaid());
    assertEquals(new BigDecimal("2000.00"), inv.outstandingAmount());
    assertEquals(InvoiceState.CONFIRMED, inv.state());
  }

  @Test
  void overdueAndOutstandingCalculationsAndFollowup() {
    authenticateAsAdmin();
    var customer = createActiveCustomer();
    var product =
        productService.create(
            new ProductDtos.Input(
                "Overdue Product " + UUID.randomUUID(),
                "SERVICE",
                new BigDecimal("2000.00"),
                new BigDecimal("500.00"),
                "Notes",
                AccountStatus.ACTIVE,
                List.of()));
    var plan =
        planService.create(
            new PlanDtos.Input(
                "Overdue Plan " + UUID.randomUUID(),
                "Monthly",
                BillingPeriod.MONTHLY,
                new BigDecimal("2000.00"),
                1,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 12, 31),
                true,
                true,
                true,
                true,
                AccountStatus.ACTIVE,
                List.of(product.id())));
    var sub =
        subscriptionService.create(
            new SubscriptionDtos.Input(
                customer.id,
                plan.id(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "Net 10",
                List.of(
                    new SubscriptionDtos.LineInput(
                        product.id(), null, 1, BigDecimal.ZERO))));
    subscriptionService.transition(sub.id(), SubscriptionState.QUOTATION);
    subscriptionService.transition(sub.id(), SubscriptionState.CONFIRMED);
    subscriptionService.transition(sub.id(), SubscriptionState.ACTIVE);

    var run =
        billingService.executeBillingRun(
            LocalDate.of(2026, 1, 1),
            "P3-OVERDUE-RUN-" + UUID.randomUUID(),
            "MANUAL",
            sub.id(),
            null);
    Long invoiceId = run.items().getFirst().invoiceId();
    invoiceService.transition(invoiceId, InvoiceState.CONFIRMED);

    // Record partial payment of 500.00 (remaining 1500.00, due date 2026-01-11)
    paymentService.record(
        new PaymentDtos.Input(
            invoiceId,
            customer.id,
            "PART-OVERDUE-" + UUID.randomUUID(),
            null,
            PaymentMethod.CASH,
            new BigDecimal("500.00"),
            LocalDate.of(2026, 1, 5),
            "Partial"),
        null);

    var overdueList =
        invoiceService.list(
            "",
            null,
            sub.id(),
            customer.id,
            true,
            true,
            true,
            LocalDate.of(2026, 1, 21),
            0,
            10);
    assertEquals(1, overdueList.totalElements());
    var item = overdueList.content().getFirst();
    assertTrue(item.overdue());
    assertEquals(10L, item.daysOverdue());
    assertEquals(new BigDecimal("1500.00"), item.outstandingAmount());

    // Record follow-up
    var followedUp =
        invoiceService.recordFollowUp(
            invoiceId, new InvoiceDtos.FollowUpInput("Called customer finance team"));
    assertEquals("Called customer finance team", followedUp.followupNotes());
    assertNotNull(followedUp.lastFollowupAt());

    // Settle remaining 1500.00 -> Paid invoice must NOT appear as overdue
    paymentService.record(
        new PaymentDtos.Input(
            invoiceId,
            customer.id,
            "FINAL-OVERDUE-" + UUID.randomUUID(),
            null,
            PaymentMethod.BANK_TRANSFER,
            new BigDecimal("1500.00"),
            LocalDate.of(2026, 1, 22),
            "Settled"),
        null);

    var afterPaidOverdue =
        invoiceService.list(
            "",
            null,
            sub.id(),
            customer.id,
            null,
            true,
            null,
            LocalDate.of(2026, 1, 25),
            0,
            10);
    assertEquals(0, afterPaidOverdue.totalElements());
    var settledView = invoiceService.get(invoiceId);
    assertFalse(settledView.overdue());
    assertEquals(0L, settledView.daysOverdue());
  }

  @Test
  void pauseResumeRenewalEarlyClosingAndBillingExclusion() {
    authenticateAsAdmin();
    var customer = createActiveCustomer();
    var product =
        productService.create(
            new ProductDtos.Input(
                "Lifecycle Product " + UUID.randomUUID(),
                "SOFTWARE",
                new BigDecimal("1200.00"),
                new BigDecimal("300.00"),
                "Notes",
                AccountStatus.ACTIVE,
                List.of()));

    // 1. Restricted plan (pausingAllowed=false, renewalAllowed=false, earlyClosingAllowed=false)
    var strictPlan =
        planService.create(
            new PlanDtos.Input(
                "Strict Plan " + UUID.randomUUID(),
                "No lifecycle flexibility",
                BillingPeriod.MONTHLY,
                new BigDecimal("1200.00"),
                1,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 12, 31),
                true,
                false,
                false,
                false,
                AccountStatus.ACTIVE,
                List.of(product.id())));
    var strictSub =
        subscriptionService.create(
            new SubscriptionDtos.Input(
                customer.id,
                strictPlan.id(),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 12, 31),
                "Net 15",
                List.of(
                    new SubscriptionDtos.LineInput(
                        product.id(), null, 1, BigDecimal.ZERO))));
    subscriptionService.transition(strictSub.id(), SubscriptionState.QUOTATION);
    subscriptionService.transition(strictSub.id(), SubscriptionState.CONFIRMED);
    subscriptionService.transition(strictSub.id(), SubscriptionState.ACTIVE);

    assertThrows(
        ApiException.class,
        () ->
            subscriptionService.pause(
                strictSub.id(),
                new SubscriptionDtos.PauseInput(LocalDate.of(2026, 3, 5), "Try pause")));
    assertThrows(
        ApiException.class,
        () ->
            subscriptionService.renew(
                strictSub.id(),
                new SubscriptionDtos.RenewInput(LocalDate.of(2027, 6, 30), null, null),
                null));
    assertThrows(
        ApiException.class,
        () ->
            subscriptionService.closeEarly(
                strictSub.id(),
                new SubscriptionDtos.CloseInput("Try early close", LocalDate.of(2026, 4, 1))));

    // 2. Flexible plan (pausingAllowed=true, renewalAllowed=true, earlyClosingAllowed=true)
    var flexPlan =
        planService.create(
            new PlanDtos.Input(
                "Flex Plan " + UUID.randomUUID(),
                "Full lifecycle support",
                BillingPeriod.MONTHLY,
                new BigDecimal("1200.00"),
                1,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 12, 31),
                true,
                true,
                true,
                true,
                AccountStatus.ACTIVE,
                List.of(product.id())));
    var flexSub =
        subscriptionService.create(
            new SubscriptionDtos.Input(
                customer.id,
                flexPlan.id(),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 6, 1),
                "Net 15",
                List.of(
                    new SubscriptionDtos.LineInput(
                        product.id(), null, 1, BigDecimal.ZERO))));
    subscriptionService.transition(flexSub.id(), SubscriptionState.QUOTATION);
    subscriptionService.transition(flexSub.id(), SubscriptionState.CONFIRMED);
    subscriptionService.transition(flexSub.id(), SubscriptionState.ACTIVE);

    // Bill first cycle on 2026-03-01 -> nextBillingDate becomes 2026-04-01
    var run1 =
        billingService.executeBillingRun(
            LocalDate.of(2026, 3, 1),
            "FLEX-RUN-1-" + UUID.randomUUID(),
            "MANUAL",
            flexSub.id(),
            null);
    assertEquals(1, run1.invoicesGenerated());
    assertEquals(
        LocalDate.of(2026, 4, 1),
        subscriptionService.get(flexSub.id()).nextBillingDate());

    // Pause on 2026-03-10
    var paused =
        subscriptionService.pause(
            flexSub.id(),
            new SubscriptionDtos.PauseInput(LocalDate.of(2026, 3, 10), "Seasonal pause"));
    assertEquals(SubscriptionState.PAUSED, paused.state());
    assertNotNull(paused.pausedAt());
    assertEquals(adminUser.email, paused.pausedBy());

    // Repeated pause must fail
    assertThrows(
        ApiException.class,
        () ->
            subscriptionService.pause(
                flexSub.id(),
                new SubscriptionDtos.PauseInput(LocalDate.of(2026, 3, 12), "Repeat")));

    // Billing run on 2026-04-05 must exclude/skip the PAUSED subscription
    var pausedRun =
        billingService.executeBillingRun(
            LocalDate.of(2026, 4, 5),
            "FLEX-PAUSED-RUN-" + UUID.randomUUID(),
            "MANUAL",
            flexSub.id(),
            null);
    assertEquals(0, pausedRun.invoicesGenerated());

    // Resume on 2026-03-20 (10 paused days) -> nextBillingDate shifts from 2026-04-01 to 2026-04-11
    var resumed =
        subscriptionService.resume(
            flexSub.id(), new SubscriptionDtos.ResumeInput(LocalDate.of(2026, 3, 20)));
    assertEquals(SubscriptionState.ACTIVE, resumed.state());
    assertEquals(10, resumed.totalPausedDays());
    assertEquals(LocalDate.of(2026, 4, 11), resumed.nextBillingDate());
    assertEquals(LocalDate.of(2026, 6, 11), resumed.expiryDate());

    // Billing on 2026-04-05 (before shifted 2026-04-11) does not charge retroactively
    var earlyCheckRun =
        billingService.executeBillingRun(
            LocalDate.of(2026, 4, 5),
            "FLEX-EARLY-RUN-" + UUID.randomUUID(),
            "MANUAL",
            flexSub.id(),
            null);
    assertEquals(0, earlyCheckRun.invoicesGenerated());

    // Renew subscription with idempotency key
    String renewKey = "RENEW-KEY-" + UUID.randomUUID();
    var renewed =
        subscriptionService.renew(
            flexSub.id(),
            new SubscriptionDtos.RenewInput(LocalDate.of(2026, 12, 11), null, renewKey),
            renewKey);
    assertEquals(LocalDate.of(2026, 12, 11), renewed.expiryDate());
    assertEquals(1, renewed.renewalCount());

    // Duplicate renewal with same key must be rejected
    assertThrows(
        ApiException.class,
        () ->
            subscriptionService.renew(
                flexSub.id(),
                new SubscriptionDtos.RenewInput(LocalDate.of(2027, 1, 11), null, renewKey),
                renewKey));

    // Early close subscription
    var closed =
        subscriptionService.closeEarly(
            flexSub.id(),
            new SubscriptionDtos.CloseInput(
                "Customer migrated to enterprise contract", LocalDate.of(2026, 4, 15)));
    assertEquals(SubscriptionState.CLOSED, closed.state());
    assertNotNull(closed.closedAt());
    assertEquals(adminUser.email, closed.closedBy());

    // Billing excludes CLOSED subscription, and prior invoice still exists
    var closedRun =
        billingService.executeBillingRun(
            LocalDate.of(2026, 5, 1),
            "FLEX-CLOSED-RUN-" + UUID.randomUUID(),
            "MANUAL",
            flexSub.id(),
            null);
    assertEquals(0, closedRun.invoicesGenerated());
    assertEquals(
        1,
        invoiceService
            .list("", null, flexSub.id(), customer.id, null, null, null, null, 0, 10)
            .totalElements());
  }

  @Test
  void quotationTemplatesExpiryConfirmationAndConversionPreserveSnapshots() {
    authenticateAsAdmin();
    var customer = createActiveCustomer();
    var product =
        productService.create(
            new ProductDtos.Input(
                "Quoted Analytics " + UUID.randomUUID(),
                "SOFTWARE",
                new BigDecimal("2500.00"),
                new BigDecimal("500.00"),
                "Analytics",
                AccountStatus.ACTIVE,
                List.of()));
    var plan =
        planService.create(
            new PlanDtos.Input(
                "Quoted Plan " + UUID.randomUUID(),
                "Monthly",
                BillingPeriod.MONTHLY,
                new BigDecimal("2500.00"),
                1,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 12, 31),
                true,
                true,
                true,
                true,
                AccountStatus.ACTIVE,
                List.of(product.id())));

    // Create template with special default price 2,000.00 and validity 14 days
    var template =
        quotationService.createTemplate(
            new QuotationDtos.TemplateInput(
                "Startup Launch Bundle " + UUID.randomUUID(),
                "14-day promotional quote",
                plan.id(),
                14,
                "Net 15",
                AccountStatus.ACTIVE,
                List.of(
                    new QuotationDtos.TemplateLineInput(
                        product.id(),
                        null,
                        2,
                        new BigDecimal("2000.00"),
                        new BigDecimal("18.00")))));
    assertEquals(14, template.validityDays());
    assertEquals(new BigDecimal("4720.00"), template.estimatedTotal());

    // Generate quotation on 2026-03-01 -> validUntil = 2026-03-15
    var quote =
        quotationService.generateFromTemplate(
            template.id(),
            new QuotationDtos.GenerateInput(
                template.id(),
                customer.id,
                LocalDate.of(2026, 3, 1),
                null,
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2027, 3, 14),
                null,
                "Prepared for Kochi demo",
                null));
    assertEquals(LocalDate.of(2026, 3, 15), quote.validUntil());
    assertEquals(new BigDecimal("4000.00"), quote.subtotal());
    assertEquals(new BigDecimal("720.00"), quote.taxAmount());
    assertEquals(new BigDecimal("4720.00"), quote.total());

    // Change product catalog price to 3,500.00 AFTER quotation was issued
    productService.update(
        product.id(),
        new ProductDtos.Input(
            product.name(),
            product.type(),
            new BigDecimal("3500.00"),
            new BigDecimal("500.00"),
            "Price increased",
            AccountStatus.ACTIVE,
            List.of()));

    // Confirm quotation on 2026-03-10 (within validity)
    var confirmed =
        quotationService.confirm(
            quote.id(), new QuotationDtos.ConfirmInput(LocalDate.of(2026, 3, 10)));
    assertEquals(QuotationState.CONFIRMED, confirmed.state());

    // Convert quotation to subscription -> must preserve 2,000.00 unitPrice snapshot!
    var convertedSub =
        quotationService.convertToSubscription(
            quote.id(),
            new QuotationDtos.ConvertInput(
                null, null, true, LocalDate.of(2026, 3, 12)));
    assertEquals(SubscriptionState.ACTIVE, convertedSub.state());
    assertEquals(new BigDecimal("4720.00"), convertedSub.total());
    assertEquals(
        new BigDecimal("2000.00"), convertedSub.lines().getFirst().unitPrice());
    assertEquals(quote.id(), convertedSub.quotationId());

    // Duplicate conversion must be rejected with 409 Conflict
    assertThrows(
        ApiException.class,
        () ->
            quotationService.convertToSubscription(
                quote.id(),
                new QuotationDtos.ConvertInput(
                    null, null, true, LocalDate.of(2026, 3, 12))));

    // Expired quotation test
    var expiringQuote =
        quotationService.generateFromTemplate(
            template.id(),
            new QuotationDtos.GenerateInput(
                template.id(),
                customer.id,
                LocalDate.of(2026, 2, 1),
                null,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2027, 2, 28),
                null,
                "Short validity",
                null));
    assertEquals(LocalDate.of(2026, 2, 15), expiringQuote.validUntil());
    assertThrows(
        ApiException.class,
        () ->
            quotationService.confirm(
                expiringQuote.id(),
                new QuotationDtos.ConfirmInput(LocalDate.of(2026, 2, 20))));
    assertThrows(
        ApiException.class,
        () ->
            quotationService.convertToSubscription(
                expiringQuote.id(),
                new QuotationDtos.ConvertInput(
                    null, null, true, LocalDate.of(2026, 2, 20))));
  }

  @Test
  void fullEndToEndPhase3AcceptanceWorkflowAndRoleAuthorization() throws Exception {
    int port = application.getWebServer().getPort();
    var adminClient = new SessionClient(port, json);
    var staffClient = new SessionClient(port, json);
    var custAClient = new SessionClient(port, json);
    var custBClient = new SessionClient(port, json);

    adminClient.login(adminUser.email, adminPassword);

    // Create Staff user
    String staffEmail = "p3-staff-" + UUID.randomUUID() + "@example.test";
    String staffPass = UUID.randomUUID() + "Aa!";
    assertEquals(
        201,
        adminClient
            .post(
                "/api/v1/staff",
                Map.of(
                    "name", "Phase 3 Staff",
                    "email", staffEmail,
                    "password", staffPass))
            .statusCode());
    staffClient.login(staffEmail, staffPass);

    // Register two customers (custA and custB)
    String custAEmail = "p3-custa-" + UUID.randomUUID() + "@example.test";
    String custBEmail = "p3-custb-" + UUID.randomUUID() + "@example.test";
    String custPass = UUID.randomUUID() + "Aa!";
    var regA =
        custAClient.post(
            "/api/v1/auth/register",
            Map.of("name", "Customer A", "email", custAEmail, "password", custPass));
    assertEquals(201, regA.statusCode());
    custAClient.login(custAEmail, custPass);
    var regB =
        custBClient.post(
            "/api/v1/auth/register",
            Map.of("name", "Customer B", "email", custBEmail, "password", custPass));
    assertEquals(201, regB.statusCode());
    custBClient.login(custBEmail, custPass);

    authenticateAsAdmin();
    Long custAId =
        customers.findByEmail(custAEmail).orElseThrow().id;

    // 1. Admin creates product and subscription plan
    var prodRes =
        adminClient.post(
            "/api/v1/products",
            Map.of(
                "name", "Brightloop Enterprise " + UUID.randomUUID(),
                "type", "SOFTWARE",
                "salesPrice", "10000.00",
                "costPrice", "2000.00",
                "notes", "Phase 3 acceptance product",
                "status", "ACTIVE",
                "variants", List.of()));
    assertEquals(201, prodRes.statusCode());
    Long productId =
        ((Number)
                ((Map<?, ?>) json.readValue(prodRes.body(), Map.class).get("data"))
                    .get("id"))
            .longValue();

    var planRes =
        adminClient.post(
            "/api/v1/plans",
            new PlanDtos.Input(
                "Enterprise Monthly " + UUID.randomUUID(),
                "Supports pause, resume, renewal, early close",
                BillingPeriod.MONTHLY,
                new BigDecimal("10000.00"),
                1,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 12, 31),
                true,
                true,
                true,
                true,
                AccountStatus.ACTIVE,
                List.of(productId)));
    assertEquals(201, planRes.statusCode());
    Long planId =
        ((Number)
                ((Map<?, ?>) json.readValue(planRes.body(), Map.class).get("data"))
                    .get("id"))
            .longValue();

    // 2. Customer A receives an active subscription
    var subRes =
        staffClient.post(
            "/api/v1/subscriptions",
            Map.of(
                "customerId", custAId,
                "planId", planId,
                "startDate", "2026-03-01",
                "expiryDate", "2026-12-31",
                "paymentTerms", "Net 15",
                "lines",
                    List.of(
                        Map.of(
                            "productId", productId,
                            "quantity", 1,
                            "taxRate", "0.00"))));
    assertEquals(201, subRes.statusCode());
    Long subId =
        ((Number)
                ((Map<?, ?>) json.readValue(subRes.body(), Map.class).get("data"))
                    .get("id"))
            .longValue();

    for (String st : List.of("QUOTATION", "CONFIRMED", "ACTIVE")) {
      assertEquals(
          200,
          staffClient
              .post("/api/v1/subscriptions/" + subId + "/transition", Map.of("state", st))
              .statusCode());
    }

    // 3. Phase 2 billing creates a real invoice
    var billRes =
        adminClient.post(
            "/api/v1/billing/run",
            Map.of(
                "billingDate", "2026-03-01",
                "subscriptionId", subId,
                "idempotencyKey", "ACCEPT-BILL-" + UUID.randomUUID()));
    assertEquals(200, billRes.statusCode());
    var billData = (Map<?, ?>) json.readValue(billRes.body(), Map.class).get("data");
    var billItems = (List<?>) billData.get("items");
    Long invoiceId =
        ((Number) ((Map<?, ?>) billItems.getFirst()).get("invoiceId")).longValue();

    // Confirm the invoice so payments can be recorded
    assertEquals(
        200,
        staffClient.post("/api/v1/invoices/" + invoiceId + "/confirm", Map.of()).statusCode());

    // Verify CUSTOMER cannot record payments (403 Forbidden)
    assertEquals(
        403,
        custAClient
            .post(
                "/api/v1/payments",
                Map.of(
                    "invoiceId", invoiceId,
                    "method", "UPI",
                    "amount", "4000.00",
                    "paymentDate", "2026-03-02"))
            .statusCode());

    // 4 & 5. Staff records a partial payment of 4,000.00 and outstanding balance updates to 6,000.00
    var pay1Res =
        staffClient.post(
            "/api/v1/payments",
            Map.of(
                "invoiceId", invoiceId,
                "method", "UPI",
                "amount", "4000.00",
                "paymentDate", "2026-03-02",
                "paymentReference", "UPI-PART-1-" + UUID.randomUUID()));
    assertEquals(201, pay1Res.statusCode());
    var pay1Data = (Map<?, ?>) json.readValue(pay1Res.body(), Map.class).get("data");
    Long pay1Id = ((Number) pay1Data.get("id")).longValue();
    assertEquals("6000.00", pay1Data.get("invoiceOutstandingAmount"));
    assertEquals("PARTIALLY_PAID", pay1Data.get("invoicePaymentStatus"));

    // 6 & 7. Staff records remaining 6,000.00 payment -> Invoice automatically becomes PAID
    var pay2Res =
        staffClient.post(
            "/api/v1/payments",
            Map.of(
                "invoiceId", invoiceId,
                "method", "BANK_TRANSFER",
                "amount", "6000.00",
                "paymentDate", "2026-03-04",
                "paymentReference", "NEFT-PART-2-" + UUID.randomUUID()));
    assertEquals(201, pay2Res.statusCode());
    var pay2Data = (Map<?, ?>) json.readValue(pay2Res.body(), Map.class).get("data");
    assertEquals("PAID", pay2Data.get("invoiceState"));
    assertEquals("0.00", pay2Data.get("invoiceOutstandingAmount"));

    // 8. Payment history persists and is accessible to owning Customer A, but forbidden to Customer B
    var histRes = custAClient.get("/api/v1/invoices/" + invoiceId + "/payments");
    assertEquals(200, histRes.statusCode());
    var histList = (List<?>) json.readValue(histRes.body(), Map.class).get("data");
    assertEquals(2, histList.size());
    assertEquals(403, custBClient.get("/api/v1/payments/" + pay1Id).statusCode());
    assertEquals(403, custBClient.get("/api/v1/invoices/" + invoiceId + "/payments").statusCode());

    // 9 & 10. Admin pauses and resumes subscription; billing respects lifecycle state
    assertEquals(
        200,
        adminClient
            .post(
                "/api/v1/subscriptions/" + subId + "/pause",
                Map.of("pauseDate", "2026-03-10", "reason", "Maintenance window"))
            .statusCode());
    var pausedBillRes =
        adminClient.post(
            "/api/v1/billing/run",
            Map.of(
                "billingDate", "2026-04-01",
                "subscriptionId", subId,
                "idempotencyKey", "ACCEPT-PAUSED-BILL-" + UUID.randomUUID()));
    var pausedBillData =
        (Map<?, ?>) json.readValue(pausedBillRes.body(), Map.class).get("data");
    assertEquals(0, ((Number) pausedBillData.get("invoicesGenerated")).intValue());

    assertEquals(
        200,
        adminClient
            .post(
                "/api/v1/subscriptions/" + subId + "/resume",
                Map.of("resumeDate", "2026-03-15"))
            .statusCode());

    // 11 & 12. Staff creates quotation template, generates quotation, and converts it into a subscription
    var tmplRes =
        staffClient.post(
            "/api/v1/quotation-templates",
            Map.of(
                "name", "Standard Enterprise Quote " + UUID.randomUUID(),
                "description", "Reusable template",
                "planId", planId,
                "validityDays", 30,
                "paymentTerms", "Net 15",
                "status", "ACTIVE",
                "lines",
                    List.of(
                        Map.of(
                            "productId", productId,
                            "quantity", 1,
                            "unitPrice", "9500.00",
                            "taxRate", "18.00"))));
    assertEquals(201, tmplRes.statusCode());
    Long tmplId =
        ((Number)
                ((Map<?, ?>) json.readValue(tmplRes.body(), Map.class).get("data"))
                    .get("id"))
            .longValue();

    var genRes =
        staffClient.post(
            "/api/v1/quotation-templates/" + tmplId + "/generate",
            Map.of(
                "customerId", custAId,
                "startDate", "2026-05-01",
                "expiryDate", "2027-04-30",
                "notes", "Generated from template"));
    assertEquals(201, genRes.statusCode());
    Long quoteId =
        ((Number)
                ((Map<?, ?>) json.readValue(genRes.body(), Map.class).get("data"))
                    .get("id"))
            .longValue();

    // Customer B cannot view Customer A's quotation
    assertEquals(403, custBClient.get("/api/v1/quotations/" + quoteId).statusCode());
    // Customer A can view their own quotation
    assertEquals(200, custAClient.get("/api/v1/quotations/" + quoteId).statusCode());

    // Confirm and convert quotation to subscription
    assertEquals(
        200, staffClient.post("/api/v1/quotations/" + quoteId + "/confirm", Map.of()).statusCode());
    var convRes =
        staffClient.post("/api/v1/quotations/" + quoteId + "/convert", Map.of());
    assertEquals(201, convRes.statusCode());
    var convData = (Map<?, ?>) json.readValue(convRes.body(), Map.class).get("data");
    assertEquals("ACTIVE", convData.get("state"));
    assertEquals("11210.00", convData.get("total"));

    // Duplicate conversion attempt over HTTP returns 409 Conflict
    assertEquals(
        409, staffClient.post("/api/v1/quotations/" + quoteId + "/convert", Map.of()).statusCode());
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
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .GET()
              .build();
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
