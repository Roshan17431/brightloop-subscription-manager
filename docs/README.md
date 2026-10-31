# Brightloop Documentation Index

Welcome to the technical documentation for **Brightloop — Subscription Management System** (`Odoo × Make-A-Ton 9.0 Kochi Hackathon 2026`).

---

## Core Documentation

| Document | Description |
| :--- | :--- |
| **[Root `README.md`](../README.md)** | Main repository landing page: overview, problem statement, solution, screenshots, architecture diagram, quick-start commands, roles, benchmarks, and security highlights. |
| **[`docs/features.md`](features.md)** | Complete functional catalog across **Phases 1–5** (Authentication, RBAC, Catalog/Variants, Plans, Subscriptions, Recurring Billing, Invoices/PDF/Email, Taxes & Discounts, Payments & Reconciliation, Quotations, Executive Analytics, Customer Portal, and Security Hardening). |
| **[`docs/architecture.md`](architecture.md)** | System architecture, Next.js same-origin `/api/v1` proxy, Spring Boot 4 modular monolith design, CSRF/session sequence diagram, PostgreSQL ER diagram, Flyway `V1`–`V6` migration strategy, and recurring billing transaction isolation. |
| **[`docs/api.md`](api.md)** | Comprehensive REST API reference (`/api/v1`) covering authentication, customers, catalog, plans, subscriptions, lifecycle operations, quotations, billing runs, tax/discount rules, invoices, payments, financial analytics, and customer portal endpoints. |
| **[`docs/setup-and-deployment.md`](setup-and-deployment.md)** | Step-by-step local installation, `.env` configuration (`infrastructure/.env`, `backend/.env`, `frontend/.env.local`), Docker Compose startup, Flyway migrations, test execution, and troubleshooting guide. |
| **[`docs/testing-and-quality.md`](testing-and-quality.md)** | Quality and verification report summarizing the 44+ backend PostgreSQL integration tests, 48 frontend Vitest tests, 760-assertion HTTP smoke test, 3,000+ subscription performance benchmark (`p50`/`p95`/`p99`), Playwright browser QA, and current GitHub Actions CI status. |
| **[`docs/demo-and-submission.md`](demo-and-submission.md)** | Interactive judge walkthrough guide, 8-chapter breakdown of the local Playwright browser recording ([`demo/brightloop-hackathon-demo.mp4`](../demo/brightloop-hackathon-demo.mp4)), and final hackathon submission checklist. |

---

## Project Specifications, Status Ledger & Historical Records

| Document / Directory | Description |
| :--- | :--- |
| **[`docs/Subscription_Management_System.pdf`](Subscription_Management_System.pdf)** | Original hackathon business problem statement ("Meet Brightloop Software"). |
| **[`docs/implementation-status.md`](implementation-status.md)** | Chronological engineering status ledger tracking Phases 1–6, files changed, test counts, benchmarks, and demo verification notes. |
| **[`docs/phase-1-requirements.md`](phase-1-requirements.md)** | Historical Phase 1 architectural and foundation specification. |
| **[`docs/verification/`](verification/results.md)** | Historical Phase 1 verification logs ([`results.md`](verification/results.md), [`backend-tests.json`](verification/backend-tests.json), [`http-smoke.txt`](verification/http-smoke.txt), [`toolchain.txt`](verification/toolchain.txt)). |
| **[`demo/README.md`](../demo/README.md)** | Detailed documentation and verification log for the recorded 4m 51s Playwright demonstration video (`demo/brightloop-hackathon-demo.mp4`, [`demo/chapters.json`](../demo/chapters.json), [`demo/narration.md`](../demo/narration.md)). |
| **[`AGENTS.md`](../AGENTS.md)** | Durable engineering instructions and conventions for coding agents working in this repository. |
