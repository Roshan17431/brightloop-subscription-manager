# Implementation status

Last updated: 2026-10-31. Source: Subscription_Management_System.pdf and phase-1-requirements.md.

## 1. Foundation & Core Modules — Completed

Implemented and verified locally against all 20 Phase 1 acceptance criteria.

- Backend: Java21/Spring Boot4.0.9, PostgreSQL/Flyway, MVC, Security, JPA/Hibernate, Jakarta validation, response/error envelopes, Actuator health/readiness, local CORS and environment settings.
- Features under com.brightloop.modules: auth, user, customer, product, plan, subscription, audit. Constructor injection, transactional services, DTOs, bounded pagination/search/filtering, FK/unique/check constraints, batching and explicit fetch plans.
- Identity: unique normalized emails, strong password rules and BCrypt, persistent HttpOnly sessions, CSRF, login/logout/me, admin-only staff/role management, SMTP reset links with hashed expiring single-use tokens and session revocation. Internal cost prices are redacted for customers.
- Customers: linked/unlinked profiles, contact/company/address/status, create/update/detail/search/filter, owned profile and isolation.
- Catalog: product CRUD/status/prices/notes, signed variant adjustments, separate plan CRUD/description/billing periods/availability/quantity/policy flags and plan-product associations.
- Subscriptions: owned customer creation, unique numbers, editable drafts, validated relationships/dates/quantity, exact server-derived financial snapshots and sequential DRAFT→QUOTATION→CONFIRMED→ACTIVE→CLOSED transitions, search/filter/pagination and transactional audit. Locks/versioning protect transitions/edits.
- Frontend: all four auth screens, responsive role-aware shell/dashboard, customers/products/variants/plans/subscriptions/detail/edit, profiles and team/roles. Typed same-origin API, TanStack Query, RHF/Zod, shadcn components, toasts/loading/errors/empty states. Financial strings retain cents with integer formatting.
- Infrastructure/docs: Docker PostgreSQL+Mailpit, env examples, optional credential-free sample catalog SQL, CI workflow, README, AGENTS.md, architecture/API/verification docs.
- Migrations: V1 normalized feature/audit/session schema; V2 session principal supports254-character emails; V3 subscription plan FK index. All three applied and verified.
- Tests executed: final `mvn verify` passed23 tests,0 failures/errors/skips on actual PostgreSQL. Final frontend typecheck/build passed;5 test files/33 tests passed. Full HTTP suite passed760 assertions against the packaged application. Docker services healthy. Browser registration→owned dashboard→persisted variant subscription verified; production and mobile layouts checked. See docs/verification/results.md and backend-tests.json.
- Compatibility decision: JUnit5.14.4 is pinned; integration tests launch actual SpringApplication instances directly because the Spring7 test extension requires JUnit6. Runtime Spring dependencies remain unchanged.
- Known limits: remote CI has not run (no push); no production deployment or measured performance SLA. Disposable development database retains test records. No unresolved Phase 1 acceptance blockers.
- Pending by design: plan policy flags are stored definitions; recurring billing, invoice/payment/tax engines, quotation templates, automatic closing/pause/renewal execution and financial analytics belong to subsequent phases.
- Files changed: backend/, frontend/, infrastructure/, docs/, .github/, AGENTS.md, README.md, docker-compose.yml and .gitignore. Work remains local on codex/phase-1; no push performed.
- Next: Phase2 transactional billing/invoicing; read project memory and agree detailed billing rules before implementation.

## 2. Billing & Invoicing — Completed
- Implemented:
  - Transactional recurring billing engine (`com.brightloop.modules.billing`) supporting `DAILY`, `WEEKLY`, `MONTHLY`, and `YEARLY` billing periods, end-of-month anchor preservation (`Jan 31 -> Feb 28 -> Mar 31 -> Apr 30`), and leap-year handling.
  - Scheduled daily execution (`BillingScheduler`, `@Scheduled`) and admin-triggered billing (`POST /api/v1/billing/run`, `GET /api/v1/billing/due`, `GET /api/v1/billing/runs`, `POST /api/v1/billing/runs/{id}/retry`).
  - Automatic subscription expiry handling: expired `ACTIVE` subscriptions (`expiryDate < billingDate`) are automatically transitioned to `CLOSED` (`SUBSCRIPTION_EXPIRED_CLOSED`), and plans with `automaticClosing = true` close upon completing their final billing cycle.
  - Database-enforced duplicate invoice prevention (`uq_invoices_subscription_period` on `(subscription_id, billing_period_start)` and unique `idempotency_key`), pessimistic row-level locks (`SELECT ... FOR UPDATE`), per-subscription `PROPAGATION_REQUIRES_NEW` transaction isolation, rollback atomicity, and retry safety (`attemptCount` tracking).
  - Invoice lifecycle (`DRAFT`, `CONFIRMED`, `PAID`, `CANCELLED`), historical customer/plan/product/variant/discount/tax snapshots, search/filter/pagination, customer ownership isolation, self-contained PDF 1.4 export (`GET /api/v1/invoices/{id}/pdf`), print layout, and SMTP email delivery (`POST /api/v1/invoices/{id}/send`). Invoices cannot be marked `PAID` without recorded payment evidence (enforced in both `Rules.invoiceTransition` and database `CHECK` constraint; full payment processing belongs to Phase 3).
  - Admin-managed tax rules (`com.brightloop.modules.tax`: product-specific, product-type, and default percentage rules with effective dates) and discount rules (`com.brightloop.modules.discount`: `PERCENTAGE` and `FIXED` discounts, `SUBSCRIPTION` and `PRODUCT` scopes, date validity, minimum quantity/purchase thresholds, and concurrency-safe `PESSIMISTIC_WRITE` usage limit tracking).
  - Frontend workspace screens (`frontend/src/features/billing/*`): Invoice list/detail/actions (Confirm, Cancel, Email, Export PDF, Print), Billing execution controls & due preview, Billing execution history & retry, Tax rules CRUD, Discount rules CRUD, and Subscription detail schedule & generated invoices table.
- Files changed:
  - `backend/src/main/resources/db/migration/V4__billing_invoicing_taxes_discounts.sql`
  - `backend/src/main/java/com/brightloop/modules/{tax,discount,invoice,billing}/*`
  - `backend/src/main/java/com/brightloop/modules/subscription/{Subscription,SubscriptionDtos,SubscriptionRepository,SubscriptionService}.java`
  - `backend/src/main/java/com/brightloop/validation/Rules.java`, `config/SecurityConfig.java`, `BrightloopApplication.java`
  - `backend/src/test/java/com/brightloop/validation/RulesTest.java`, `backend/src/test/java/com/brightloop/integration/Phase2BillingIntegrationTest.java`
  - `frontend/src/features/billing/{schemas.ts,invoices.tsx,billing.tsx,taxes-discounts.tsx,billing.test.tsx}`
  - `frontend/src/lib/types.ts`, `frontend/src/components/{shell.tsx,shared.tsx}`, `frontend/src/features/subscriptions/subscriptions.tsx`, `frontend/src/app/[[...path]]/page.tsx`, `frontend/src/app/globals.css`
- Tests executed:
  - Backend `mvn verify`: 31 tests passed, 0 failures, 0 errors, 0 skips against PostgreSQL 17.11 (including `Phase2BillingIntegrationTest` covering end-of-month/cycle advancement, taxes/discounts, idempotency, 4-thread concurrent billing and single-use discount locking, mid-transaction fault rollback and retry safety, expiry auto-closing, authorization, PDF export, and SMTP email delivery).
  - Frontend `npm run typecheck`, `npm test` (6 files / 37 tests), and `npm run build`: all passed.
- Pending / Known boundaries:
  - Phase 2 billing/invoicing is integrated with Phase 3 payment recording and reconciliation.
- Next: Phase 3 payments, payment reconciliation, overdue tracking, and full lifecycle automation.

## 3. Payments, Subscription Lifecycle & Quotations — Completed
- Implemented:
  - Payment Management Module (`com.brightloop.modules.payment`):
    - Record full, partial, and multiple payments (`BANK_TRANSFER`, `CASH`, `UPI`, `CARD`, `OTHER`) against confirmed invoices (`POST /api/v1/payments`, `POST /api/v1/invoices/{invoiceId}/payments`).
    - Automatic invoice-payment reconciliation: tracks `amountPaid` and `outstandingAmount` with `BigDecimal(19,2)` precision, derives `paymentStatus` (`UNPAID`, `PARTIALLY_PAID`, `PAID`, `CANCELLED`) and `partiallyPaid` indicator without breaking `InvoiceState` (`DRAFT`, `CONFIRMED`, `PAID`, `CANCELLED`), and automatically transitions `CONFIRMED` invoices to `PAID` with recorded `paymentReference` and `paidAt` when reconciled payments equal `total`.
    - Financial safety & concurrency: `PESSIMISTIC_WRITE` row-level locking on invoice during payment recording and reversal, unique `payment_reference` and `idempotency_key` constraints, duplicate payment reference rejection, overpayment prevention, zero/negative amount rejection, draft/cancelled invoice payment prevention, safe zero-value invoice settlement (`POST /api/v1/invoices/{id}/settle-zero`), and non-destructive payment reversal (`POST /api/v1/payments/{id}/reverse`) that preserves audit history and reverts `PAID` invoices to `CONFIRMED`.
  - Outstanding & Overdue Invoices:
    - Real-time calculation of `amountPaid`, `outstandingAmount`, `overdue` indicator, and `daysOverdue` (0 for `PAID`/`CANCELLED` invoices; calculated from remaining balance and `dueDate` for unpaid/partially paid `CONFIRMED` invoices).
    - Filtering by `outstanding=true`, `overdue=true`, and `partiallyPaid=true` (`GET /api/v1/invoices`), plus overdue follow-up logging (`POST /api/v1/invoices/{id}/follow-up`).
  - Subscription Lifecycle Completion (`com.brightloop.modules.subscription`):
    - Pause (`POST /api/v1/subscriptions/{id}/pause`): enforces `plan.pausingAllowed`, transitions `ACTIVE -> PAUSED`, records `pausedAt`, `pausedBy`, `pausedDate`, prevents repeated pauses, and excludes paused subscriptions from recurring billing.
    - Resume (`POST /api/v1/subscriptions/{id}/resume`): restores `PAUSED -> ACTIVE`, shifts `nextBillingDate` and `expiryDate` forward by the paused duration (`totalPausedDays`), preserves billing schedule date-anchor behavior, and avoids retroactive duplicate billing.
    - Renewal (`POST /api/v1/subscriptions/{id}/renew`): enforces `plan.renewalAllowed`, advances `expiryDate` by explicit date or billing cycles, preserves prior invoice/billing history, and prevents duplicate renewal requests via `Idempotency-Key` / `lastRenewalKey`.
    - Early Closing (`POST /api/v1/subscriptions/{id}/close`, `POST /api/v1/subscriptions/{id}/early-close`): enforces `plan.earlyClosingAllowed`, transitions to `CLOSED`, records `closedAt`, `closedBy`, and `closureReason`, prevents future billing, and preserves historical invoices.
  - Quotation Templates & Quotations Module (`com.brightloop.modules.quotation`):
    - Reusable quotation templates (`GET/POST /api/v1/quotation-templates`, `GET/PUT /api/v1/quotation-templates/{id}`) with plan association, validity duration in days, payment terms, default quantities, and default unit prices.
    - Quotation generation (`POST /api/v1/quotation-templates/{id}/generate`, `POST /api/v1/quotations`), expiry validation (`validUntil`), confirmation (`POST /api/v1/quotations/{id}/confirm`), cancellation (`POST /api/v1/quotations/{id}/cancel`), and transactional conversion into an active subscription (`POST /api/v1/quotations/{id}/convert`) preserving snapshotted quotation unit prices and preventing duplicate conversions (`UNIQUE (converted_subscription_id)`).
  - Frontend Workspace Screens:
    - Payments list, filters, outstanding/overdue summary cards, overdue follow-up dialog, Record Payment screen, and Payment Detail with reversal (`frontend/src/features/payments/payments.tsx`).
    - Invoice list balance filters and Invoice Detail payment summary cards, inline Record Payment dialog, and Payment History table (`frontend/src/features/billing/invoices.tsx`).
    - Subscription Detail lifecycle controls (Pause, Resume, Renew, Early close) with confirmation dialogs and audit metadata (`frontend/src/features/subscriptions/subscriptions.tsx`).
    - Quotation Templates & Customer Quotations list, Template Editor, Template Detail with Generate Quotation dialog, and Quotation Detail with Confirm and Convert to Subscription actions (`frontend/src/features/quotations/quotations.tsx`).
- Files changed:
  - `backend/src/main/resources/db/migration/V5__payments_quotations_lifecycle.sql`
  - `backend/src/main/java/com/brightloop/modules/payment/*`
  - `backend/src/main/java/com/brightloop/modules/quotation/*`
  - `backend/src/main/java/com/brightloop/modules/invoice/{Invoice,InvoiceDtos,InvoiceRepository,InvoiceService,InvoiceController,InvoicePdfGenerator}.java`
  - `backend/src/main/java/com/brightloop/modules/subscription/{SubscriptionState,Subscription,SubscriptionDtos,SubscriptionService,SubscriptionController}.java`
  - `backend/src/main/java/com/brightloop/modules/billing/BillingService.java`, `config/SecurityConfig.java`
  - `backend/src/test/java/com/brightloop/integration/Phase3PaymentsLifecycleQuotationsIntegrationTest.java`
  - `frontend/src/features/payments/{payments.tsx,payments.test.tsx}`
  - `frontend/src/features/quotations/quotations.tsx`
  - `frontend/src/features/billing/{invoices.tsx,billing.test.tsx}`, `frontend/src/features/subscriptions/subscriptions.tsx`
  - `frontend/src/lib/{types.ts,api.ts}`, `frontend/src/components/{shell.tsx,shared.tsx,ui/dialog.tsx}`, `frontend/src/app/[[...path]]/page.tsx`
- Tests executed:
  - Backend `mvn verify`: 37 tests passed, 0 failures, 0 errors, 0 skips against PostgreSQL 17.11 (including `Phase3PaymentsLifecycleQuotationsIntegrationTest` covering partial/multiple payments, reconciliation, idempotency, duplicate reference rejection, overpayment rejection, reversal integrity, 4-thread concurrent payment serialization, overdue balance & follow-up tracking, pause/resume/renew/early-close lifecycle rules, quotation template generation/expiry/conversion snapshot preservation, and the 13-step end-to-end HTTP acceptance workflow with RBAC & customer isolation).
  - Frontend `npm run typecheck`, `npm test` (7 files / 41 tests), and `npm run build`: all passed.
- Known issues / boundaries:
  - Recorded payments represent ledger/operational reconciliation (not external live payment gateway webhooks). Advanced Phase 4 revenue analytics remain excluded by design.
- Next: Phase 4 financial dashboards, revenue reporting, and analytics.

## 4. Dashboards & Customer Portal — Completed
- Implemented:
  - Financial Analytics & Reporting Module (`com.brightloop.modules.analytics`):
    - Database-driven executive analytics endpoints under `/api/v1/analytics` (`GET /overview`, `/revenue`, `/subscriptions`, `/payments`, `/receivables`, `/renewals`, `/reports/export`) using `NamedParameterJdbcTemplate` SQL aggregations and composite indexes (`V6__analytics_indexes.sql`) with zero N+1 queries.
    - Exact `BigDecimal` MRR and ARR calculations (`AnalyticsService.normalizeToMrr` & `mrrToArr`):
      - Derived from active subscription line pre-tax subtotals (`unit_price * quantity`), minus applicable active recurring discounts (`DiscountService` eligibility/specificity rules), strictly excluding taxes.
      - Normalized by plan `billing_period`: `MONTHLY = amount × 1`, `YEARLY = amount ÷ 12`, `WEEKLY = amount × 52 ÷ 12`, `DAILY = amount × 30` (`RoundingMode.HALF_UP`, scale 2), and `ARR = MRR × 12`.
      - Paused subscriptions (`state = 'PAUSED'`) are excluded from active `mrr`/`arr` and reported separately as `pausedMrr`/`pausedArr` (on-hold recurring revenue) so active recurring revenue is never overstated; `DRAFT`, `QUOTATION`, `CONFIRMED`, and `CLOSED` subscriptions contribute `0.00` to active MRR.
    - Revenue & Payment Analytics:
      - Time-series revenue trends by `DAILY`, `MONTHLY`, and `YEARLY` granularity distinguishing pre-tax invoiced subtotal, applied discounts, taxes, total invoiced revenue, collected payments, open balance, and collection rate percentage.
      - Payment velocity over time and breakdown by payment method (`BANK_TRANSFER`, `CASH`, `UPI`, `CARD`, `OTHER`) with share percentages and reversal tracking.
    - Receivables & Overdue Aging Buckets:
      - Open balance tracking across `CONFIRMED` invoices (`total - amount_paid`), distinguishing current not-yet-due balances from overdue balances (`due_date < asOfDate`).
      - Four overdue aging buckets (`1-30`, `31-60`, `61-90`, `90+` days overdue) plus top overdue invoices schedule.
    - Subscription Analytics & Renewals:
      - Counts and MRR/ARR breakdowns by subscription status (`DRAFT`, `QUOTATION`, `CONFIRMED`, `ACTIVE`, `PAUSED`, `CLOSED`), by plan, and by billing frequency, plus upcoming billing/renewals and expiring subscriptions within configurable `windowDays`.
    - Advanced Filtering & CSV Export:
      - Supports filtering across `fromDate`, `toDate`, `asOfDate`, `granularity`, `customerId`, `planId`, `billingPeriod`, `subscriptionState`, `invoiceState`, and `paymentMethod`, plus RFC-compliant CSV export (`GET /api/v1/analytics/reports/export?section=revenue|payments|subscriptions|receivables`).
  - Customer Self-Service Portal (`GET /api/v1/portal/overview` & `/dashboard`):
    - Customer-isolated self-service dashboard displaying active/paused/closed subscription counts, pre-tax monthly recurring spend, total billed, total paid, open/overdue balance alerts, earliest next billing date, recent invoices with one-click PDF download (`/api/v1/invoices/{id}/pdf`), and payment history.
    - Self-service subscription lifecycle actions: `CUSTOMER` accounts can pause, resume, renew, and early-close their own subscriptions when allowed by the associated plan (`pausingAllowed`, `renewalAllowed`, `earlyClosingAllowed`), while `SubscriptionService.owned(...)` and `SecurityConfig` enforce strict ownership isolation (`403 Forbidden` on other customers' records and on all `/api/v1/analytics/**` endpoints).
  - Frontend Executive Dashboard & Financial Reports (`frontend/src/features/dashboard/*`, `frontend/src/features/analytics/reports.tsx`):
    - Executive Dashboard (`/dashboard` for `ADMIN`/`STAFF`) with 6-filter bar, 12 KPI cards, 6 Recharts visualizations (`RevenueTrendChart`, `InvoicedVsCollectedChart`, `SubscriptionStatusChart`, `MrrByPlanChart`, `PaymentMethodsChart`, `OverdueAgingChart`), and 4 operational tables (Upcoming Renewals, Expiring Subscriptions, Recent Invoices, Recent Payments).
    - Financial Reports screen (`/reports`) with 4 report tabs (Revenue, Payments, Subscriptions & MRR, Outstanding & Overdue Aging), search/filter bar, and one-click CSV export.
- Files changed:
  - `backend/src/main/resources/db/migration/V6__analytics_indexes.sql`
  - `backend/src/main/java/com/brightloop/modules/analytics/{AnalyticsDtos,AnalyticsService,AnalyticsController}.java`
  - `backend/src/main/java/com/brightloop/config/SecurityConfig.java`, `modules/subscription/SubscriptionService.java`
  - `backend/src/test/java/com/brightloop/integration/Phase4AnalyticsPortalIntegrationTest.java`
  - `frontend/src/features/dashboard/{dashboard.tsx,analytics-charts.tsx,dashboard.test.tsx}`
  - `frontend/src/features/analytics/reports.tsx`, `frontend/src/features/subscriptions/subscriptions.tsx`
  - `frontend/src/lib/types.ts`, `frontend/src/components/shell.tsx`, `frontend/src/app/[[...path]]/page.tsx`
- Tests executed:
  - Backend `mvn verify`: 41 tests passed, 0 failures, 0 errors, 0 skips against PostgreSQL 17.11 (including `Phase4AnalyticsPortalIntegrationTest` covering MRR/ARR normalization across `DAILY`/`WEEKLY`/`MONTHLY`/`YEARLY` plans, tax exclusion, recurring discount deduction, paused MRR separation, revenue/payment/receivables aging buckets, CSV export, customer portal ownership & self-service lifecycle authorization over HTTP, and a 2,500-subscription PostgreSQL bulk performance benchmark completing all analytics queries well under the 2,000ms SLA).
  - Frontend `npm run typecheck`, `npm test` (8 files / 44 tests), and `npm run build`: all passed.
- Known issues / boundaries:
  - Phase 5 comprehensive security hardening and Phase 6 deployment/submission remain separate phases by design.
- Next: Phase 5 testing, security & performance review.

## 5. Testing, Security & Performance — Completed
- Implemented:
  - GitHub Actions CI Reliability (`.github/workflows/ci.yml`):
    - Root cause identified: `.github/workflows/ci.yml` previously started only `postgres:17-alpine` without an SMTP service container, causing `Phase2BillingIntegrationTest`'s SMTP invoice delivery test (`POST /api/v1/invoices/{id}/send`) to fail on `localhost:1025` in CI, and branch triggers only matched `codex/**` and `main`.
    - Fix: Added `mailpit` service container (`axllent/mailpit:v1.22`, ports `1025:1025` and `8025:8025`), configured `SMTP_HOST`, `SMTP_PORT`, `MAIL_FROM`, `FRONTEND_URL`, and `NEXT_PUBLIC_API_URL`, and expanded branch triggers to `[main, Phase-1, 'phase-*', 'antigravity/**', 'codex/**']`. (Note: remote CI execution is not claimed until pushed and confirmed on GitHub.)
  - Security Audit & Hardening:
    - Restricted `GET /api/v1/taxes/**` (`/tax-rules/**`) and `GET /api/v1/discounts/**` (`/discount-rules/**`) in both `SecurityConfig` and `TaxService`/`DiscountService` (`requireManager()`) to `ADMIN` and `STAFF` so `CUSTOMER` accounts cannot enumerate internal discount promo codes or other customers' subscription-scoped discount rules.
    - Enforced explicit HTTP security headers in `SecurityConfig`: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and `Permissions-Policy: camera=(), microphone=(), geolocation=()`, alongside `HttpOnly` and `SameSite=Lax` session cookies.
    - Hardened SQL `LIKE` wildcard escaping in `PageResponse.search(String q)` to escape `\` (`\\`) prior to `%` and `_`.
    - Enforced cross-customer `invoiceId` ownership checks for `CUSTOMER` sessions and date ordering validation (`toDate >= fromDate`) in `PaymentService.list(...)`.
    - Hardened `AnalyticsService.csvEscape` against CSV formula injection (prefixing leading `=`, `+`, `-`, `@`, `\t`, `\r` with `'` and quoting) and sanitized `section` in `AnalyticsController.exportCsv` `Content-Disposition` headers.
    - Verified full RBAC and cross-customer IDOR isolation matrix across customers, subscriptions, lifecycle transitions, quotations, invoices, PDF downloads, payments, tax/discount rules, billing runs, analytics, and audit logs (confirming zero plaintext passwords or reset tokens in `audit_logs` and zero stack traces in error envelopes).
  - Financial Integrity & Lifecycle Edge-Case Verification:
    - Verified leap-year (`2028-02-29 -> 2029-02-28`) and end-of-month (`Jan 31 -> Feb 28 -> Mar 31 -> Apr 30`) billing schedule advancement, `100%`-discounted zero-value (`0.00`) invoice settlement (`POST /api/v1/invoices/{id}/settle-zero`) without fake payment rows, historical invoice snapshot immutability after product price and tax rate edits, database `CHECK` constraint enforcement (`state <> 'PAID' OR (payment_reference IS NOT NULL AND paid_at IS NOT NULL)`), 4-thread concurrent billing/payment serialization, and payment reversal reconciliation.
  - Performance Optimization & 3,000+ Subscription Benchmark:
    - Eliminated N+1 queries in `BillingService.listRuns` via batch loading (`BillingRunItemRepository.findByBillingRunIdInOrderByIdAsc`).
    - Optimized `AnalyticsService.getRenewals` to compute pre-tax cycle totals via an inline subquery on the bounded `LIMIT 50` upcoming renewal rows instead of scanning all subscriptions.
    - Added repeatable k6 load script (`infrastructure/k6-load-test.js`) and automated PostgreSQL 3,000+ subscription benchmark (`Phase5SecurityFinancialPerformanceIntegrationTest`).
- Measured Performance Benchmark (PostgreSQL 17.11 — 3,000+ Subscriptions, 3,000+ Invoices, 2,000+ Payments):

| Endpoint / Scenario | p50 Latency | p95 Latency | p99 Latency | Throughput (req/s) | Errors |
| --- | --- | --- | --- | --- | --- |
| `GET /api/v1/analytics/overview` | 35.56 ms | 51.34 ms | 51.34 ms | 27.1 req/s | 0 |
| `GET /api/v1/analytics/revenue` | 5.32 ms | 6.09 ms | 6.09 ms | 186.3 req/s | 0 |
| `GET /api/v1/analytics/subscriptions` | 24.26 ms | 34.33 ms | 34.33 ms | 39.8 req/s | 0 |
| `GET /api/v1/analytics/payments` | 3.18 ms | 4.01 ms | 4.01 ms | 300.6 req/s | 0 |
| `GET /api/v1/analytics/receivables` | 3.35 ms | 4.27 ms | 4.27 ms | 291.7 req/s | 0 |
| `GET /api/v1/analytics/renewals` | 9.79 ms | 10.31 ms | 10.31 ms | 102.4 req/s | 0 |
| `GET /api/v1/analytics/reports/export` | 2.77 ms | 3.64 ms | 3.64 ms | 356.7 req/s | 0 |
| `GET /api/v1/subscriptions?page=0&size=20` | 6.66 ms | 7.75 ms | 7.75 ms | 149.5 req/s | 0 |
| `GET /api/v1/invoices?page=0&size=20&overdue=true` | 15.19 ms | 16.69 ms | 16.69 ms | 72.8 req/s | 0 |
| `GET /api/v1/payments?page=0&size=20` | 5.76 ms | 6.96 ms | 6.96 ms | 174.2 req/s | 0 |
| `CONCURRENT 20 VUs GET /api/v1/analytics/overview` | 207.36 ms | 378.23 ms | 454.75 ms | 80.6 req/s | 0 |

- Files changed:
  - `.github/workflows/ci.yml`
  - `backend/src/main/java/com/brightloop/config/SecurityConfig.java`, `response/PageResponse.java`
  - `backend/src/main/java/com/brightloop/modules/tax/TaxService.java`, `modules/discount/DiscountService.java`
  - `backend/src/main/java/com/brightloop/modules/payment/PaymentService.java`
  - `backend/src/main/java/com/brightloop/modules/billing/{BillingRunItemRepository,BillingService}.java`
  - `backend/src/main/java/com/brightloop/modules/analytics/{AnalyticsController,AnalyticsService}.java`
  - `backend/src/test/java/com/brightloop/integration/Phase5SecurityFinancialPerformanceIntegrationTest.java`
  - `frontend/src/components/shell.test.tsx`
  - `infrastructure/k6-load-test.js`
  - `docs/{implementation-status.md,api.md,architecture.md}`, `README.md`
- Tests executed:
  - Backend `mvn verify`: **44 tests passed, 0 failures, 0 errors, 0 skips** against PostgreSQL 17.11 and Mailpit.
  - Frontend `npm run typecheck`, `npm test` (**9 test files / 48 tests passed**), and `npm run build`: all passed cleanly.
- Known issues / boundaries:
  - Local verification is complete on branch `phase-5`; remote GitHub Actions execution will run when pushed. Production cloud deployment belongs to Phase 6.
- Next: Phase 6 deployment & submission (only upon explicit user instruction).

## 6. Deployment & Submission — Not started
- Implemented/files/tests: local developer infrastructure only.
- Pending: production environment, delivery pipeline, domain/TLS, backup/restore, submission assets.
- Known issues: no production deployment; no push requested.
- Next: deploy only on explicit user instruction.

## Phase 1 acceptance ledger

| Criterion | Evidence |
| --- | --- |
| Spring Boot starts | Packaged Java21 application running on8080 |
| PostgreSQL connects | Actual PostgreSQL17; Hibernate validates schema |
| Flyway migrations execute | V1,V2,V3 success in flyway_schema_history |
| Next.js starts | Production build/start; HTTP200 on3000 |
| Customer registration/login | HTTP suite and real browser flow |
| Authentication/roles | HTTP CSRF/session/role/isolation checks |
| Admin creates staff | HTTP suite including forbidden staff/customer attempts |
| Reset email link | Mailpit actual email, link, password replacement, replay and session revocation; expiry/concurrency JUnit tests |
| Customer persistence | Real create/update/list/search/filter/detail and DB records |
| Product/variant management | CRUD, variant price checks, associated/referenced deletion constraints |
| Plan management | CRUD, periods/availability/quantity/product association validation |
| Subscription lifecycle | Own drafts edited and all five states tested sequentially |
| Customer isolation | Foreign profile/subscription reads and writes rejected |
| Frontend/backend integration | Browser persisted3681.60 subscription from actual API options |
| Audit records |15 expected action types observed in PostgreSQL |
| Validation | Passwords, dates, money, relations, null array elements, error404/405 regressions |
| Backend tests |23 passed,0 failures/errors/skips |
| Frontend typecheck/build | Both passed;33 tests passed |
| README setup commands | Local startup, Compose, seed, migration and test commands documented |
| AGENTS/status updated | Durable instructions and all six phases documented |
