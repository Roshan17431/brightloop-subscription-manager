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
  - Payment recording, payment reconciliation, and transitioning confirmed invoices to `PAID` with payment records belong to Phase 3.
- Next: Phase 3 payments, payment reconciliation, overdue tracking, and full lifecycle automation.

## 3. Payments & Subscription Lifecycle — Not started
- Implemented/files/tests: none for payments; Phase 1 includes core sequential subscription states only.
- Pending: payments, reconciliation, overdue tracking and full pause/renewal/closing automation.
- Known issues: no financial transactions or recurring processing yet.
- Next: implement against transactional invoices.

## 4. Dashboards & Customer Portal — Not started (core shell included in Phase 1)
- Implemented/files/tests: Phase 1 provides only operational counts and owned subscription/profile screens when verified.
- Pending: revenue reporting, financial filters and full portal workflows.
- Known issues: operational counts are not revenue analytics.
- Next: derive financial reporting from verified invoices/payments.

## 5. Testing, Security & Performance — Not started (basic checks included in Phase 1)
- Implemented/files/tests: Phase 1 test results recorded separately below once executed.
- Pending: comprehensive security review, deployment hardening, load measurement and performance tuning.
- Known issues: no claim of sub-two-second performance under scale.
- Next: test realistic datasets and verify boundaries.

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
