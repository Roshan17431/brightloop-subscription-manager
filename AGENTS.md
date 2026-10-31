# Brightloop engineering instructions

Brightloop is the Odoo × Make-A-Ton 9.0 subscription management modular monolith. Read docs/Subscription_Management_System.pdf, docs/architecture.md and docs/implementation-status.md before subsequent phases. The PDF defines business requirements; current user instructions define implementation scope.

## Stack and layout
- backend/: Java 21, Spring Boot 4, MVC, Security, Data JPA/Hibernate, Jakarta Validation, Actuator, PostgreSQL, Flyway, Maven.
- Backend root com.brightloop; feature packages com.brightloop.modules.{auth,user,customer,product,plan,subscription,audit}; shared config/security/exception/response/validation where used.
- frontend/: Next.js App Router, strict TypeScript, Tailwind CSS, shadcn/ui, TanStack Query, React Hook Form, Zod, Lucide; Recharts only for real operational data.
- infrastructure/: local Docker PostgreSQL and Mailpit. docs/: decisions and progress. No empty modules or speculative dependencies.

## Conventions
Constructor injection; controllers adapt HTTP only, services enforce business rules with transaction boundaries. Return DTOs, never JPA entities. Interfaces only when they express a meaningful service boundary. Use pageable bounded queries, explicit fetching and projections to avoid N+1.
Use /api/v1, correct HTTP statuses, success envelopes with success/statusCode/status/message/data/timestamp; error envelopes with success/statusCode/status/message/errorCode/path/timestamp/details. Security entry points must follow error contract too. No internal stack traces in responses.
Schema changes require versioned Flyway SQL; Hibernate validates, never updates schema. Include FK, unique, NOT NULL, financial precision and useful indexes.
Use BigDecimal for prices, tax snapshots and totals. Validate precision, nonnegative values, quantities, date ranges and relationships. Subscription unit prices are server-derived snapshots, never trusted from client.

## Security
Only environment-based credentials. Never commit .env, reset tokens, generated secrets or passwords. Hash passwords with Spring Security. Enforce roles and customer ownership on backend, cookies HttpOnly with CSRF defenses. No authentication secrets in localStorage. Reset links must expire and be single use, with hashed token storage and SMTP delivery. Audit significant mutations within their transactions, excluding secrets. Only admin creates staff and changes roles.

## Verification and workflow
Use codex/ branches; preserve history and teammates' work. Never push or force-push without user instruction. Do not change git identity. Keep commits logical. No simulated billing or fabricated metrics. Phase 2+ billing/invoices/payments/tax engines/reporting remain excluded.
Backend: cd backend && mvn verify (JAVA_HOME must point to JDK21). Frontend: cd frontend && npm ci && npm run typecheck && npm test && npm run build. Full HTTP checks: see infrastructure/smoke-test.py and README for environment. Docker: docker compose --env-file infrastructure/.env up -d.
Run actual checks, report exact results and unverified limits. Track ongoing work and acceptance criteria in docs/implementation-status.md; don't mark phases complete before acceptance criteria pass.
