# Brightloop architecture

## Scope and source
The supplied Subscription_Management_System.pdf is preserved here as the business specification. phase-1-requirements.md preserves the expanded implementation scope. Phase 1 builds identity, customers, products/variants, plan definitions and subscription lifecycle. Recurring billing, quotation templates, invoices, tax/discount rules, payments and financial analytics are later phases.

## Runtime
Next.js App Router serves the UI; a same-origin /api/v1 proxy forwards requests to Spring Web MVC. TanStack Query owns server state, React Hook Form/Zod validate forms, and the server remains authoritative. Java 21 and Spring Boot 4 run a modular monolith using PostgreSQL and Flyway. Hibernate validates the migrated schema. Local Compose runs PostgreSQL and Mailpit; application processes run locally for fast development.

## Boundaries
com.brightloop.modules contains auth, user, customer, product, plan, subscription and audit features. Constructor-injected services enforce transactions and domain validation. Controllers return DTO envelopes. Foreign keys and unique constraints protect relationships against races. Catalog writes are admin-only; staff manage day-to-day customers/subscriptions. Customers see only their own records.

## Identity
Persistent server-side sessions use HttpOnly cookies and CSRF protection. No browser localStorage tokens. Reset tokens are cryptographically random, stored hashed, expire, and can be consumed once. SMTP delivers links; Mailpit is development delivery only. Initial admin provisioning requires explicit environment variables, never a seeded password. Role mutations are admin-only and audited.

## Financial and lifecycle decisions
BigDecimal stores money. Financial DTOs serialize decimal strings and the UI formats with integer cents to avoid loss of precision for large totals. Internal cost prices are redacted from customer catalog views. Subscription lines snapshot the product base sales price plus selected variant adjustment; the underlying product price is unchanged. Explicit tax-rate snapshots support line totals only, not a tax-rules engine. Plan prices remain definition metadata, not simulated billing. Lifecycle transitions are validated and concurrent edits protected. Availability and quantity are checked when drafts are created/edited; plan definitions remain separate from customer subscriptions.

## Operational choices
Bounded pagination and search, database constraints/indexes and explicit fetch plans support normal list/detail use. Audit records participate in mutation transactions and never include credentials. Readiness and health use Actuator. Performance under thousands of subscriptions needs measurement in a later phase; no unmeasured latency claims.
