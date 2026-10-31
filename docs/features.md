# Brightloop — Implemented Features (Phases 1–5)

This document catalogs all implemented functionality across Phases 1 through 5 of the Brightloop Subscription Management System, reconciled directly against the backend (`backend/src/main/java/com/brightloop`) and frontend (`frontend/src/features`) source code and database schema (`V1`–`V6`).

---

## Phase 1 — Foundation & Core Modules

### 1. Authentication & Password Reset (`com.brightloop.modules.auth`)
- **Customer Self-Registration (`POST /api/v1/auth/register`)**:
  - Normalizes email addresses to lowercase and enforces case-insensitive uniqueness (`idx_users_email`).
  - Validates strong passwords (`> 8` characters, requiring at least one uppercase letter, one lowercase letter, and one special character).
  - Hashes passwords with Spring Security `PasswordEncoder` (BCrypt) and automatically provisions both the `CUSTOMER` user and a linked `customers` profile record within a single transaction.
- **Session-Based Login & Logout (`POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`)**:
  - Uses server-side sessions persisted in PostgreSQL (`SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES`) with `HttpOnly`, `SameSite=Lax` cookies.
  - Rotates session IDs on login (`request.changeSessionId()`) and issues fresh CSRF tokens via `HttpSessionCsrfTokenRepository` (`GET /api/v1/auth/csrf`).
- **Password Reset via Email (`POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password`)**:
  - Generates cryptographically random tokens, stores only their SHA-256 hash in `password_reset_tokens`, sets an expiration timestamp, and sends the reset link via SMTP (`JavaMailSender` / Mailpit).
  - Enforces single-use consumption (`used_at`), rejects expired or replayed tokens, updates the BCrypt password hash, and revokes all active sessions for that principal (`SessionRevoker`).

### 2. Role-Based Access Control (`com.brightloop.modules.user`, `SecurityConfig`)
- **Three System Roles**:
  - `ADMIN`: Full system control, initial environment-bootstrapped administrator (`AdminBootstrap`), staff provisioning (`POST /api/v1/staff`), user role changes (`PATCH /api/v1/users/{id}/role`), catalog/plan/tax/discount writes, and manual billing execution.
  - `STAFF`: Operational management of customers, subscriptions, lifecycle transitions, quotation templates, quotations, invoice confirmations/cancellations/emails, payment recording/reversals, and executive analytics/reports.
  - `CUSTOMER`: Strictly isolated to their own customer profile (`/api/v1/customers/me`), their own subscriptions, quotations, invoices, payments, and customer portal overview.

### 3. Customer Management (`com.brightloop.modules.customer`)
- Supports both self-registered customers (linked to a `users` account) and staff-created customer records (`GET/POST /api/v1/customers`, `GET/PUT /api/v1/customers/{id}`).
- Tracks name, normalized email, phone, company name, billing address, account status (`ACTIVE`, `INACTIVE`), and created/updated timestamps.
- Provides bounded pagination (`page`, `size` 1–100), text search (`q`), and status filtering.

### 4. Product & Variant Management (`com.brightloop.modules.product`)
- Full CRUD for products (`GET/POST /api/v1/products`, `GET/PUT/DELETE /api/v1/products/{id}`) with name, product type, `salesPrice`, `costPrice`, notes, and `ACTIVE`/`INACTIVE` status.
- Supports configurable product variants (`product_variants`) with signed `BigDecimal(14,2)` price adjustments (for example, selecting the `Odoo` brand variant adds `+560.00` to the base product sales price without mutating the base product price).
- Redacts internal `costPrice` from `CUSTOMER` catalog responses (`ProductVisibilityTest`).
- Prevents deletion of products or variants referenced by existing subscriptions, quotations, or invoices (`409 Conflict`).

### 5. Subscription Plans (`com.brightloop.modules.plan`)
- Full CRUD for subscription plans (`GET/POST /api/v1/plans`, `GET/PUT/DELETE /api/v1/plans/{id}`).
- Supports `DAILY`, `WEEKLY`, `MONTHLY`, and `YEARLY` billing frequencies (`BillingPeriod`), base display price, minimum total line quantity (`minimumQuantity`), availability date range (`availableFrom`, `availableUntil`), associated product IDs (`plan_products`), and four lifecycle policy flags:
  - `automaticClosing`
  - `earlyClosingAllowed`
  - `pausingAllowed`
  - `renewalAllowed`

### 6. Core Subscription Lifecycle (`com.brightloop.modules.subscription`)
- Creates numbered subscriptions (`BL-...`) linking a customer, plan, start date, expiry date, payment terms, and line items (`POST /api/v1/subscriptions`).
- Snapshots server-derived `unitPrice` (`salesPrice + variantAdjustment`), `taxRate`, and line `amount` at creation/update time; clients cannot override unit prices.
- Enforces sequential lifecycle transitions (`DRAFT → QUOTATION → CONFIRMED → ACTIVE → CLOSED`) via `POST /api/v1/subscriptions/{id}/transition`, validating plan availability dates, product-plan associations, and minimum quantity rules.

### 7. Transactional Audit Logging (`com.brightloop.modules.audit`)
- Records actor email, action type, entity type, entity ID, timestamp, and sanitized metadata into `audit_logs` within the same database transaction as the domain mutation.
- Explicitly excludes plaintext passwords, password hashes, and reset tokens from audit metadata.

---

## Phase 2 — Recurring Billing, Invoicing, Taxes & Discounts

### 1. Automated Recurring Billing Engine (`com.brightloop.modules.billing`)
- **Due Subscription Discovery (`GET /api/v1/billing/due`)**:
  - Identifies `ACTIVE` subscriptions where `next_billing_date <= billingDate`.
- **Scheduled & Admin-Triggered Execution (`BillingScheduler`, `POST /api/v1/billing/run`)**:
  - Supports daily scheduled execution (`@Scheduled`) and on-demand admin-triggered runs (optionally scoped to a specific `billingDate`, `subscriptionId`, and `Idempotency-Key`).
- **Cycle Advancement & Date Anchoring (`Rules.advanceBillingDate`)**:
  - Advances `nextBillingDate` across `DAILY`, `WEEKLY`, `MONTHLY`, and `YEARLY` periods while preserving the subscription's `startDate` day-of-month anchor across short months and leap years (e.g., `Jan 31 → Feb 28 → Mar 31 → Apr 30`, and `2028-02-29 → 2029-02-28`).
- **Automatic Expiry & Auto-Closing**:
  - Automatically closes `ACTIVE` subscriptions whose `expiryDate < billingDate` (`EXPIRED_CLOSED`) or whose plan has `automaticClosing = true` upon completing their final billing cycle.
- **Idempotency, Concurrency & Retry Safety**:
  - Prevents duplicate invoices via `uq_invoices_subscription_period` (`UNIQUE (subscription_id, billing_period_start)`), unique invoice `idempotency_key`, and `PESSIMISTIC_WRITE` locks on each subscription.
  - Isolates each subscription's billing execution in `Propagation.REQUIRES_NEW` so a single subscription failure rolls back cleanly without aborting the entire batch, and supports admin retries (`POST /api/v1/billing/runs/{id}/retry`).

### 2. Tax & Discount Engines (`com.brightloop.modules.tax`, `com.brightloop.modules.discount`)
- **Admin-Managed Tax Rules (`/api/v1/taxes`, `/api/v1/tax-rules`)**:
  - Supports product-specific (`productId`), product-type (`productType`), and default (`isDefault = true`) percentage tax rules with effective date ranges (`effectiveFrom`, `effectiveUntil`).
  - Resolves tax rules deterministically by specificity: `productId` match → `productType` match → `isDefault` fallback → subscription line tax snapshot.
- **Admin-Managed Discount Rules (`/api/v1/discounts`, `/api/v1/discount-rules`)**:
  - Supports `PERCENTAGE` (`<= 100%`) and `FIXED` amount discounts scoped to either `PRODUCT` or `SUBSCRIPTION`.
  - Validates date validity (`validFrom`, `validUntil`), minimum line quantity (`minimumQuantity`), minimum purchase subtotal (`minimumPurchase`), and concurrency-safe usage limits (`usageLimit` vs. `usedCount` locked via `PESSIMISTIC_WRITE`).

### 3. Invoice Management, PDF Export & Email Delivery (`com.brightloop.modules.invoice`)
- Generates uniquely numbered invoices (`INV-YYYYMMDD-...`) with immutable customer, plan, billing period, line item, discount, and tax snapshots.
- Supports `DRAFT`, `CONFIRMED`, `PAID`, and `CANCELLED` states (`POST /api/v1/invoices/{id}/confirm`, `/cancel`, `/transition`).
- Enforces at both service and PostgreSQL `CHECK` constraint levels that an invoice cannot enter `PAID` state without recorded payment evidence (`payment_reference IS NOT NULL AND paid_at IS NOT NULL` and `amount_paid = total`).
- Generates self-contained binary PDF 1.4 documents (`GET /api/v1/invoices/{id}/pdf`), print-ready views, and SMTP email notifications (`POST /api/v1/invoices/{id}/send`).

---

## Phase 3 — Payments, Subscription Lifecycle Extensions & Quotations

### 1. Partial & Full Payment Recording (`com.brightloop.modules.payment`)
- Records payments (`POST /api/v1/payments`, `POST /api/v1/invoices/{invoiceId}/payments`) across `BANK_TRANSFER`, `CASH`, `UPI`, `CARD`, and `OTHER` methods with unique `paymentReference` and `idempotencyKey`.
- Locks the target invoice row with `PESSIMISTIC_WRITE` during payment recording to prevent concurrent overpayments.
- Rejects payments against `DRAFT` or `CANCELLED` invoices, rejects zero/negative amounts, and rejects any payment exceeding the invoice's remaining `outstandingAmount`.
- Provides `POST /api/v1/invoices/{id}/settle-zero` to settle confirmed `0.00` total invoices (such as 100%-discounted cycles) without fabricating dummy payment rows.

### 2. Automatic Reconciliation, Reversals & Overdue Tracking
- Tracks `amountPaid` and `outstandingAmount` (`total - amountPaid`) on every invoice and derives `paymentStatus` (`UNPAID`, `PARTIALLY_PAID`, `PAID`, `CANCELLED`) and `partiallyPaid`.
- Automatically transitions `CONFIRMED` invoices to `PAID` when cumulative completed payments equal `total`.
- Supports non-destructive payment reversals (`POST /api/v1/payments/{id}/reverse`), marking the payment `REVERSED` with `reversedAt`, `reversedBy`, `reversalReason`, and `reversalReference`, recalculating `amountPaid`, and reverting `PAID` invoices back to `CONFIRMED`.
- Computes real-time `overdue` and `daysOverdue` for unpaid/partially paid `CONFIRMED` invoices past `dueDate`, and records collection follow-up notes (`POST /api/v1/invoices/{id}/follow-up`).

### 3. Extended Subscription Lifecycle (`pause`, `resume`, `renew`, `close`)
- **Pause (`POST /api/v1/subscriptions/{id}/pause`)**: Transitions `ACTIVE → PAUSED` when `plan.pausingAllowed` is true, records `pausedAt`, `pausedBy`, and `pausedDate`, and excludes the subscription from recurring billing runs.
- **Resume (`POST /api/v1/subscriptions/{id}/resume`)**: Transitions `PAUSED → ACTIVE`, accumulates `totalPausedDays`, and shifts both `nextBillingDate` and `expiryDate` forward by the paused duration.
- **Renewal (`POST /api/v1/subscriptions/{id}/renew`)**: Extends `expiryDate` by a specified number of billing periods or explicit date when `plan.renewalAllowed` is true, increments `renewalCount`, reactivates expired/closed subscriptions, and prevents duplicate renewals via `Idempotency-Key` / `lastRenewalKey`.
- **Early Closing (`POST /api/v1/subscriptions/{id}/close`, `/early-close`)**: Transitions to `CLOSED` when `plan.earlyClosingAllowed` is true, records `closedAt`, `closedBy`, and `closureReason`, and halts future recurring billing while preserving historical invoices.

### 4. Quotation Templates & Conversion (`com.brightloop.modules.quotation`)
- Manages reusable quotation templates (`GET/POST /api/v1/quotation-templates`, `GET/PUT /api/v1/quotation-templates/{id}`) with associated plan, validity days, payment terms, and template product lines.
- Generates customer quotations from templates (`POST /api/v1/quotation-templates/{id}/generate`, `POST /api/v1/quotations`), tracks `validUntil` expiry, supports confirmation (`POST /api/v1/quotations/{id}/confirm`) and cancellation (`POST /api/v1/quotations/{id}/cancel`), and converts confirmed quotations into `ACTIVE` subscriptions (`POST /api/v1/quotations/{id}/convert`) while preserving snapshotted quotation line prices and preventing duplicate conversion (`UNIQUE (converted_subscription_id)`).

---

## Phase 4 — Executive Financial Analytics & Customer Self-Service Portal

### 1. Executive Financial Dashboard & Analytics Engine (`com.brightloop.modules.analytics`)
- **Database-Driven Aggregations**: Powered by `NamedParameterJdbcTemplate` SQL queries and composite indexes (`V6__analytics_indexes.sql`) across `/api/v1/analytics/{overview,revenue,subscriptions,payments,receivables,renewals}`.
- **Exact `BigDecimal` MRR & ARR Normalization**:
  - Computes active recurring revenue from `ACTIVE` subscription line pre-tax subtotals (`unit_price * quantity`), deducts applicable active recurring discounts, and strictly excludes taxes.
  - Normalizes by plan frequency (`MONTHLY × 1`, `YEARLY ÷ 12`, `WEEKLY × 52 ÷ 12`, `DAILY × 30` with `RoundingMode.HALF_UP`, scale 2) and computes `ARR = MRR × 12`.
  - Reports `PAUSED` subscriptions separately as `pausedMrr` / `pausedArr` so active recurring revenue is never overstated.
- **Revenue, Collections & Receivables Aging**:
  - Time-series breakdowns by `DAILY`, `MONTHLY`, and `YEARLY` granularity separating pre-tax subtotal, discounts, taxes, invoiced total, collected cash payments, open balance, and collection rate percentage.
  - Categorizes overdue receivables (`CONFIRMED` invoices with `due_date < asOfDate` and `total > amount_paid`) into `1–30`, `31–60`, `61–90`, and `90+` days overdue buckets.
- **Multi-Dimensional Filtering & CSV Export**:
  - Filters by `fromDate`, `toDate`, `asOfDate`, `granularity`, `customerId`, `planId`, `billingPeriod`, `subscriptionState`, `invoiceState`, `paymentMethod`, and `windowDays`, and exports RFC-compliant CSV reports (`GET /api/v1/analytics/reports/export?section=revenue|payments|subscriptions|receivables`).

### 2. Customer Self-Service Portal (`/api/v1/portal/overview`)
- Provides customers with a dedicated workspace displaying their own subscriptions, quotations, invoices (with PDF download), payment history, profile settings, and plan-permitted self-service lifecycle controls (`pause`, `resume`, `renew`, `early-close`).

---

## Phase 5 — Security Hardening, Performance Optimization & CI Reliability

- **Security Hardening**:
  - Enforced `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and `Permissions-Policy: camera=(), microphone=(), geolocation=()` headers.
  - Restricted `GET /api/v1/taxes/**` and `GET /api/v1/discounts/**` to `ADMIN` and `STAFF` to prevent customer enumeration of promo codes or other customers' subscription-scoped discounts.
  - Hardened SQL `LIKE` wildcard backslash escaping in `PageResponse.search(q)`, enforced cross-customer `invoiceId` ownership checks in `PaymentService.list`, and neutralized spreadsheet formula injection (`=`, `+`, `-`, `@`, `\t`, `\r`) in CSV exports.
- **Query Optimization & 3,000+ Subscription Load Verification**:
  - Eliminated N+1 queries in `BillingService.listRuns` via batch item fetching (`findByBillingRunIdInOrderByIdAsc`) and optimized `AnalyticsService.getRenewals` with an inline subquery on bounded `LIMIT 50` rows.
  - Verified sub-55ms single-user p95 latencies and 378.23ms 20-VU concurrent p95 latency across 3,000+ subscriptions, 3,000+ invoices, and 2,000+ payments in PostgreSQL 17.11.
- **GitHub Actions CI Improvements**:
  - Added the `axllent/mailpit:v1.22` service container and SMTP environment variables to `.github/workflows/ci.yml` so integration tests exercising SMTP password reset and invoice email delivery pass in CI.
