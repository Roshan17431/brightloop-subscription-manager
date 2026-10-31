# Brightloop — Testing, Security, Performance & CI Quality Report

This report summarizes the automated test results, security audit findings, financial integrity checks, PostgreSQL 3,000+ subscription performance benchmarks, Playwright browser verification, and remote GitHub Actions CI status.

---

## 1. Automated Test Execution Summary

| Verification Suite | Tooling / Environment | Recorded Result | Evidence / Location |
| :--- | :--- | :--- | :--- |
| **Backend Unit & Integration Suite (Committed Phase 5)** | Java 21, JUnit 5.14.4, Spring Boot 4.0.9, PostgreSQL 17.11, Mailpit | **44 tests run, 44 passed, 0 failures, 0 errors, 0 skips** (`34.02s`) | `backend/src/test/java/com/brightloop/**` |
| **Backend Suite + Local Query-Date Binding Test** | Same as above + `QueryDateBindingIntegrationTest` | **48 tests run, 48 passed, 0 failures, 0 errors, 0 skips** (`35.89s`) | `output/playwright/analytics/backend-verify-final.log` |
| **Frontend TypeScript Validation** | TypeScript `5.9.3` (`npm run typecheck`) | **Passed** (`0` compiler errors) | `frontend/tsconfig.json` |
| **Frontend Component & Unit Suite** | Vitest `4.0.18`, Testing Library, JSDOM (`npm test`) | **9 test files / 48 tests passed** (`0` failures) | `frontend/src/**/*.test.{ts,tsx}` |
| **Frontend Production Build** | Next.js `16.4.3` (`npm run build`) | **Passed** (clean production bundle) | `frontend/package.json` |
| **Packaged HTTP Smoke Suite** | Python 3 HTTP runner against packaged Spring Boot JAR + Mailpit | **760 assertions passed**, exit code `0` | `infrastructure/smoke-test.py`, `docs/verification/results.md` |
| **Playwright CLI Browser QA & Demo Verification** | Chromium (`1600×1000`, `1440px`, `768px`, `390px`) | **42/42** auth/RBAC/responsive checks passed; 8-chapter workflow verified | `output/playwright/auth/results.json`, `demo/verification/verify-workflows.json` |

---

## 2. Backend Test Breakdown

All backend integration tests boot the real Spring Boot 4.0.9 servlet container (`NativeSpringApplication`) against a live PostgreSQL 17 database and Mailpit SMTP server—no H2 database emulation or mocked repositories are used for integration tests.

| Test Class | Tests | Coverage Scope |
| :--- | ---: | :--- |
| `ApiIntegrationTest` | 10 | Health/readiness envelopes, CSRF enforcement, registration validation, login/logout, RBAC (`ADMIN`/`STAFF`/`CUSTOMER`), customer CRUD & ownership isolation, product/variant pricing (`+560.00` Odoo adjustment), plan policies, sequential `DRAFT → QUOTATION → CONFIRMED → ACTIVE → CLOSED` lifecycle, and FK deletion protection (`409 Conflict`). |
| `PasswordResetIntegrationTest` | 3 | Real SMTP password-reset email delivery via Mailpit, single-use token consumption, replay rejection, token expiry enforcement, BCrypt password replacement, and active session revocation. |
| `Phase2BillingIntegrationTest` | 5 | Recurring billing engine (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`), end-of-month date-anchor preservation (`Jan 31 → Feb 28 → Mar 31`), tax & discount rule resolution, 4-thread concurrent billing idempotency and single-use discount locking, mid-transaction fault rollback and retry (`POST /api/v1/billing/runs/{id}/retry`), expiry auto-close, PDF 1.4 export, and SMTP invoice email delivery. |
| `Phase3PaymentsLifecycleQuotationsIntegrationTest` | 6 | Partial and multiple payment reconciliation (`UNPAID → PARTIALLY_PAID → PAID`), overpayment/duplicate reference rejection, non-destructive payment reversal (`PAID → CONFIRMED`), 4-thread concurrent payment serialization, overdue balance & follow-up notes, subscription `pause`/`resume`/`renew`/`early-close`, quotation template generation, and quotation-to-subscription conversion preserving price snapshots. |
| `Phase4AnalyticsPortalIntegrationTest` | 4 | Exact `BigDecimal` MRR/ARR normalization across `DAILY`/`WEEKLY`/`MONTHLY`/`YEARLY` plans, tax exclusion, recurring discount deduction, paused MRR separation, revenue/payment/receivables aging buckets (`1–30`, `31–60`, `61–90`, `90+` days), CSV export, customer portal ownership isolation, and 2,500-subscription query verification. |
| `Phase5SecurityFinancialPerformanceIntegrationTest` | 3 | Full security header verification, CSRF & session fixation checks, RBAC & cross-customer IDOR isolation matrix across all modules, SQL `LIKE` wildcard & injection payload resistance, CSV formula injection neutralization (`=`, `+`, `-`, `@`), audit log secret redaction, leap-year (`2028-02-29 → 2029-02-28`) & zero-value (`100%` discount) invoice settlement, historical invoice snapshot immutability, and 3,000+ subscription latency & 20-VU concurrency benchmark. |
| `RulesTest` | 8 | Unit tests for password complexity, money scale/non-negativity, date ordering, subscription/invoice state machines, and billing date advancement. |
| `CollectionValidationTest` | 2 | Jakarta Bean Validation on nested collection DTOs and null-element rejection. |
| `ProductVisibilityTest` | 2 | Redaction of internal `costPrice` from customer-facing product DTOs while preserving visibility for `STAFF` and `ADMIN`. |
| `FinancialSerializationTest` | 1 | Exact two-decimal string serialization of `BigDecimal` monetary values in JSON DTOs. |
| **Total (Committed Phase 5)** | **44** | *(Plus 4 tests in untracked local `QueryDateBindingIntegrationTest` = 48 total)* |

---

## 3. Frontend Test Breakdown

Executed via `cd frontend && npm test` (Vitest + React Testing Library):

| Test File | Tests | Coverage Scope |
| :--- | ---: | :--- |
| `src/lib/api.test.ts` | 5 | Typed fetch client, pre-mutation `/api/v1/auth/csrf` acquisition, `X-CSRF-TOKEN` header injection, `204 No Content` handling, standardized error envelope parsing, and query string encoding. |
| `src/components/shell.test.tsx` | 4 | Role-based navigation isolation (`CUSTOMER` vs `STAFF` vs `ADMIN`), mobile navigation drawer accessibility, `401` unauthenticated redirect, and sign-out session clearing. |
| `src/features/auth/auth.test.tsx` | 7 | Login, registration password strength rules, forgot-password, and reset-password form validations. |
| `src/features/customers/customers.test.tsx` | 5 | Customer creation/update schema validation and customer profile rendering. |
| `src/features/catalog/catalog.test.tsx` | 6 | Product base price, signed variant price adjustment (`+560.00` / `-25.00`), and plan policy form validation. |
| `src/features/subscriptions/subscriptions.test.tsx` | 6 | Subscription draft creation, line item quantity/tax validation, and lifecycle transition controls. |
| `src/features/billing/billing.test.tsx` | 6 | Tax rule, discount rule, and recurring billing execution form validations and invoice detail rendering. |
| `src/features/payments/payments.test.tsx` | 4 | Payment recording validation, overpayment/non-positive amount rejection, and payment reversal dialog checks. |
| `src/features/dashboard/dashboard.test.tsx` | 5 | Executive analytics KPI cards, Recharts container rendering, filter state propagation, and customer portal view. |
| **Total** | **48** | **9 test files / 48 tests passed** |

---

## 4. Performance Benchmark (3,000+ Subscriptions on PostgreSQL 17.11)

### Test Context

- **Dataset**: Seeded within `Phase5SecurityFinancialPerformanceIntegrationTest` into live PostgreSQL 17.11:
  - **3,000+ Subscriptions** (`ACTIVE`, `PAUSED`, `CLOSED` across `MONTHLY` and `YEARLY` plans)
  - **3,000+ Subscription Lines**
  - **3,000+ Invoices** (`2,000 PAID`, `1,000 CONFIRMED` open/overdue)
  - **2,000+ Completed Payments**
- **Target SLA**: Problem statement requires every page and report to respond in **under 2,000 ms (2.0 seconds)** with thousands of subscriptions on the books.

### Measured Latency & Throughput Results

| Endpoint / Scenario | p50 Latency | p95 Latency | p99 Latency | Throughput (req/s) | Error Rate |
| :--- | ---: | ---: | ---: | ---: | ---: |
| `GET /api/v1/analytics/overview` | 35.56 ms | 51.34 ms | 51.34 ms | 27.1 req/s | 0.0% |
| `GET /api/v1/analytics/revenue` | 5.32 ms | 6.09 ms | 6.09 ms | 186.3 req/s | 0.0% |
| `GET /api/v1/analytics/subscriptions` | 24.26 ms | 34.33 ms | 34.33 ms | 39.8 req/s | 0.0% |
| `GET /api/v1/analytics/payments` | 3.18 ms | 4.01 ms | 4.01 ms | 300.6 req/s | 0.0% |
| `GET /api/v1/analytics/receivables` | 3.35 ms | 4.27 ms | 4.27 ms | 291.7 req/s | 0.0% |
| `GET /api/v1/analytics/renewals` | 9.79 ms | 10.31 ms | 10.31 ms | 102.4 req/s | 0.0% |
| `GET /api/v1/analytics/reports/export?section=revenue` | 2.77 ms | 3.64 ms | 3.64 ms | 356.7 req/s | 0.0% |
| `GET /api/v1/subscriptions?page=0&size=20` | 6.66 ms | 7.75 ms | 7.75 ms | 149.5 req/s | 0.0% |
| `GET /api/v1/invoices?page=0&size=20&overdue=true` | 15.19 ms | 16.69 ms | 16.69 ms | 72.8 req/s | 0.0% |
| `GET /api/v1/payments?page=0&size=20` | 5.76 ms | 6.96 ms | 6.96 ms | 174.2 req/s | 0.0% |
| **Concurrent 20 VUs** `GET /api/v1/analytics/overview` | 207.36 ms | 378.23 ms | 454.75 ms | 80.6 req/s | 0.0% |

All single-user p95 latencies are **<= 51.34 ms** and the 20-virtual-user concurrent p95 latency on the heaviest endpoint (`/api/v1/analytics/overview`) is **378.23 ms** (`p99 = 454.75 ms`), well within the 2,000 ms target.

---

## 5. Security & Financial Integrity Audit Summary

1. **Authentication & Session Security**:
   - Passwords hashed with BCrypt; reset tokens hashed with SHA-256, single-use, and time-limited; active user sessions revoked upon password reset.
   - Session cookies configured with `HttpOnly` and `SameSite=Lax`; session ID rotated on login to prevent session fixation.
2. **HTTP Security Headers & CSRF**:
   - Verified `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and `Permissions-Policy: camera=(), microphone=(), geolocation=()` on API responses.
   - All `POST`, `PUT`, `PATCH`, and `DELETE` endpoints reject requests lacking a valid session `X-CSRF-TOKEN` (`403 Forbidden`).
3. **RBAC & Cross-Customer IDOR Isolation**:
   - `CUSTOMER` accounts receive `403 Forbidden` when attempting to read or mutate another customer's profile, subscription, quotation, invoice, PDF, or payment, or when accessing `/api/v1/customers`, `/api/v1/users`, `/api/v1/staff`, `/api/v1/billing/**`, `/api/v1/taxes/**`, `/api/v1/discounts/**`, or `/api/v1/analytics/**`.
   - `STAFF` accounts receive `403 Forbidden` when attempting admin-only actions (`POST /api/v1/staff`, `PATCH /api/v1/users/{id}/role`, product/plan/tax/discount writes, or triggering manual billing runs).
4. **Injection & Export Hardening**:
   - SQL `LIKE` searches escape `\`, `%`, and `_` (`PageResponse.search`).
   - CSV report exports (`AnalyticsService.csvEscape`) prefix cells starting with `=`, `+`, `-`, `@`, `\t`, or `\r` with a single quote (`'`) and wrap them in double quotes to prevent spreadsheet formula execution.
5. **Financial & Concurrency Integrity**:
   - Verified leap-year (`2028-02-29 → 2029-02-28`) and end-of-month (`Jan 31 → Feb 28 → Mar 31`) billing schedule progression.
   - Verified historical invoice immutability after product price and tax rate changes.
   - Verified database `CHECK` constraints preventing `PAID` invoices without `payment_reference`, `paid_at`, and `amount_paid = total`.

---

## 6. Playwright CLI Browser Verification

Browser verification evidence is stored in `output/playwright/` and `demo/verification/`:

- **Authentication, RBAC & Responsive Layout (`output/playwright/auth/results.json`)**:
  - **42 / 42 checks passed**, verifying unauthenticated redirect, `HttpOnly` session cookies, customer navigation isolation, direct URL protection (`/customers`, `/team`, `/billing`, `/taxes`, `/discounts`), Mailpit password reset link consumption and replay rejection, staff operational vs. admin restrictions, and responsive layouts across `1440px` (desktop), `768px` (tablet), and `390px` (mobile).
- **End-to-End Demo Workflow Verification (`demo/verification/verify-workflows.json`)**:
  - Verified live browser execution of catalog/variant setup, subscription pause/resume/renew, recurring invoice generation (`2,800.00` subtotal − `280.00` discount + `453.60` tax = `2,973.60`), duplicate billing prevention (`repeatInvoicesGenerated: 0`), `1,000.00` partial payment + `1,973.60` final payment reconciliation (`PAID`, `0.00` outstanding), quotation conversion (`CONVERTED`), PDF invoice download, and CSV report export.
  - **Documented Limitation Found During Browser QA**: `GET /api/v1/portal/overview` returned accurate `activeCount` and balance totals, but its inline `subscriptions` array and `nextBillingDate` summary field were empty/`null` for the demo fixture (`output/playwright/portal/results.json` and `demo/verification/verify-workflows.json`). Customer-owned `/subscriptions`, `/subscriptions/{id}`, `/invoices`, and `/payments` pages work properly.

---

## 7. Current Remote GitHub Actions CI Status

Inspection of the latest remote GitHub Actions workflow run (`Run #41180459763` on branch `Phase-1`, commit `20459c0`, and `Run #41180441497` on `phase-5`, commit `3e79b0b`):

| CI Job | Remote Status | Details |
| :--- | :--- | :--- |
| **`backend`** (`ubuntu-latest`, Java 21, `postgres:17-alpine`, `axllent/mailpit:v1.22`) | **PASSED (`success`)** | All steps succeeded, including Flyway `V1`–`V6` migration validation and `mvn -B verify` (44 tests passed against disposable PostgreSQL 17 and Mailpit). |
| **`frontend`** (`ubuntu-latest`, Node 22) | **FAILED (`failure` on `npm test`)** | `npm ci` and `npm run typecheck` succeeded. `npm test` failed on 1 assertion in `src/lib/api.test.ts`. |

### Root Cause of Remote Frontend CI Failure

1. In `frontend/src/lib/api.ts`:
   ```ts
   const base = process.env.NEXT_PUBLIC_API_URL || "/api/v1";
   ```
2. In `frontend/src/lib/api.test.ts`:
   ```ts
   expect(fetch.mock.calls[0][0]).toBe("/api/v1/auth/csrf");
   ```
3. In `.github/workflows/ci.yml` (line 47):
   ```yaml
   env:
     NEXT_PUBLIC_API_URL: http://localhost:8080
   ```
   Because `.github/workflows/ci.yml` injects `NEXT_PUBLIC_API_URL=http://localhost:8080` into the `frontend` job environment, `api.ts` constructs `http://localhost:8080/auth/csrf` instead of the default same-origin proxy path `/api/v1/auth/csrf` expected by `src/lib/api.test.ts`. Locally, where `NEXT_PUBLIC_API_URL` is unset, all 48 frontend tests pass. Removing `NEXT_PUBLIC_API_URL: http://localhost:8080` (or setting it to `/api/v1`) in `.github/workflows/ci.yml` will align the remote `frontend` CI job with local execution.
