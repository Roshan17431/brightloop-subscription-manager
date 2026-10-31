# Brightloop engineering instructions

Brightloop is the Odoo × Make-A-Ton 9.0 subscription management modular monolith. Read docs/Subscription_Management_System.pdf, docs/architecture.md, docs/features.md and docs/implementation-status.md before making changes. Phases 1–5 (foundation, recurring billing, invoicing, taxes/discounts, payments/reconciliation, subscription pause/resume/renew/early-close, quotation templates/conversion, executive MRR/ARR analytics, customer portal, security hardening, and 3,000+ subscription performance verification) are implemented and locally verified.

## Stack and layout
- backend/: Java 21, Spring Boot 4, MVC, Security, Data JPA/Hibernate, NamedParameterJdbcTemplate, Jakarta Validation, Actuator, JavaMailSender, PostgreSQL 17, Flyway (V1–V6), Maven.
- Backend root com.brightloop; feature packages com.brightloop.modules.{auth,user,customer,product,plan,quotation,subscription,tax,discount,billing,invoice,payment,analytics,audit}; shared config/security/exception/response/validation where used.
- frontend/: Next.js App Router, strict TypeScript, Tailwind CSS, shadcn/ui, TanStack Query, React Hook Form, Zod, Lucide; Recharts for real database-backed operational analytics.
- infrastructure/: local Docker PostgreSQL, Mailpit, smoke-test.py, and k6-load-test.js. docs/: architecture, API, features, setup, testing, demo, and implementation status. demo/: Playwright demonstration recording and verification artifacts.

## Conventions
Constructor injection; controllers adapt HTTP only, services enforce business rules with transaction boundaries. Return DTOs, never JPA entities. Interfaces only when they express a meaningful service boundary. Use pageable bounded queries, explicit fetching and projections to avoid N+1.
Use /api/v1, correct HTTP statuses, success envelopes with success/statusCode/status/message/data/timestamp; error envelopes with success/statusCode/status/message/errorCode/path/timestamp/details. Security entry points must follow error contract too. No internal stack traces in responses.
Schema changes require versioned Flyway SQL; Hibernate validates, never updates schema. Include FK, unique, NOT NULL, financial precision and useful indexes.
Use BigDecimal for prices, discounts, tax snapshots, payments and totals. Validate precision, nonnegative values, quantities, date ranges and relationships. Subscription, quotation, and invoice unit prices are server-derived snapshots, never trusted from client.

## Security
Only environment-based credentials. Never commit .env, reset tokens, generated secrets or passwords. Hash passwords with Spring Security. Enforce roles and customer ownership on backend, cookies HttpOnly/SameSite=Lax with CSRF defenses and security headers. No authentication secrets in localStorage. Reset links must expire and be single use, with hashed token storage and SMTP delivery. Audit significant mutations within their transactions, excluding secrets. Only admin creates staff, changes roles, mutates catalog/tax/discount rules, and triggers manual billing runs.

## Verification and workflow
Preserve history and teammates' work. Never push or force-push without user instruction. Do not change git identity. Keep commits logical. No simulated billing or fabricated metrics—all billing, payment reconciliation, and analytics must remain backed by PostgreSQL.
Backend: cd backend && mvn verify (JAVA_HOME must point to JDK21; export DB_URL/DB_USERNAME/DB_PASSWORD and SMTP_HOST/SMTP_PORT for integration tests). Frontend: cd frontend && npm ci && npm run typecheck && npm test && npm run build. Full HTTP checks: see infrastructure/smoke-test.py, infrastructure/k6-load-test.js, and docs/setup-and-deployment.md. Docker: docker compose --env-file infrastructure/.env up -d.
Run actual checks, report exact results and unverified limits. Track ongoing work and acceptance criteria in docs/implementation-status.md.
