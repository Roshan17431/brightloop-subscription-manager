# Brightloop — Demo Walkthrough & Hackathon Submission Guide

This document provides the end-to-end judge walkthrough for **Brightloop** (`Odoo × Make-A-Ton 9.0 Kochi Hackathon 2026`), details the locally prepared demonstration video (`demo/brightloop-hackathon-demo.mp4`), and lists the final submission checklist.

---

## 1. Project Introduction

**Brightloop** is a unified subscription management platform that connects product catalogs, variant pricing, subscription plans, reusable quotation templates, recurring invoicing, automatic tax/discount rules, partial/full payment reconciliation, executive MRR/ARR analytics, and customer self-service in one PostgreSQL-backed system.

Instead of tracking subscriptions in spreadsheets, typing invoices by hand, and matching payments manually from bank statements, finance and operations teams work from a single transactional record of truth.

---

## 2. Recorded Demonstration Video (`demo/brightloop-hackathon-demo.mp4`)

An 8-chapter browser demonstration video has been recorded and verified locally using Playwright CLI against the running application:

- **Local File Path**: [`demo/brightloop-hackathon-demo.mp4`](../demo/brightloop-hackathon-demo.mp4) (WebM source at [`demo/brightloop-hackathon-demo.webm`](../demo/brightloop-hackathon-demo.webm))
- **Specifications**: `291.07 seconds` (`4m 51s`), `14,921,296 bytes`, `1600×1000` resolution at `30 fps`, `H.264` (`yuv420p`)
- **Audio / Captions**: Silent video with embedded chapter metadata (`demo/chapters.json`), on-screen chapter title cards, lower-third explanatory captions, cursor tracking, and interactive element highlights. An optional voiceover script is provided in [`demo/narration.md`](../demo/narration.md).
- **Verification**: Full-file FFmpeg frame decode passed (`demo/video-verification.json`) and Chromium HTML5 video playback verified to completion (`demo/verification/playback-verify.json`). Zero credentials or secrets appear in any recorded frame or text artifact (`demo/verification/artifact-review.json`).
- **Upload / Submission Status**: Prepared and verified locally. External video hosting or portal upload has **not** been performed automatically and should be completed by the team during final submission.

### Video Chapters (`demo/chapters.json`)

| Chapter | Start Time | Duration | Demonstrated Workflow |
| :--- | ---: | ---: | :--- |
| **1. Introduction** | `00:00` (`0.0s`) | `26.10s` | Executive overview of Brightloop and the subscription management workspace. |
| **2. Products and Plans** | `00:26` (`26.10s`) | `39.33s` | Creating a product, configuring variant price adjustments (`Kochi Workspace Suite` base `1,200.00` + `Pro support` variant `+200.00` = `1,400.00`; or `Odoo` brand `+560.00`), and reviewing `Kochi Pro Monthly` plan policies (`pausingAllowed`, `renewalAllowed`, `earlyClosingAllowed`). |
| **3. Subscription Lifecycle** | `01:05` (`65.43s`) | `44.03s` | Inspecting an `ACTIVE` subscription (`BL-...`), pausing recurring billing (`ACTIVE → PAUSED`), resuming (`PAUSED → ACTIVE` with date shifting), and renewing to extend `expiryDate` while preserving invoice history. |
| **4. Automated Billing** | `01:49` (`109.46s`) | `44.13s` | Reviewing the `18%` tax rule and `10%` recurring subscription discount, triggering recurring billing to generate the October invoice (`2,800.00` subtotal − `280.00` discount + `453.60` tax = `2,973.60`), and verifying that a second evaluation for the same period generates `0` duplicate invoices. |
| **5. Payments and Reconciliation** | `02:33` (`153.59s`) | `39.07s` | Confirming the generated invoice, recording a `1,000.00` partial UPI payment (`PARTIALLY_PAID`, `1,973.60` outstanding), then recording the `1,973.60` final bank transfer payment to automatically transition the invoice to `PAID` (`0.00` outstanding). |
| **6. Financial Analytics** | `03:12` (`192.66s`) | `44.33s` | Filtering the Executive Dashboard to customer Ananya Menon (`2,520.00` active pre-tax MRR, `30,240.00` ARR, `8,920.80` invoiced, `6,947.20` collected, `1,973.60` overdue), reviewing Recharts visualizations and overdue aging buckets, and exporting a CSV report (`demo/revenue-report.csv`). |
| **7. Customer Portal and Quotations** | `03:56` (`236.99s`) | `37.80s` | Viewing the customer-owned `/subscriptions` and `/subscriptions/{id}` workspace with permitted self-service controls, downloading a customer invoice PDF (`demo/customer-invoice.pdf`), and converting a confirmed quotation into a new `ACTIVE` subscription preserving agreed price snapshots. |
| **8. Conclusion** | `04:34` (`274.79s`) | `16.23s` | Summary of the modular monolith architecture, database integrity constraints, and automated test coverage. |

---

## 3. Live Interactive Walkthrough Guide (For Judges & Evaluators)

### Prerequisites

1. Start PostgreSQL 17 and Mailpit:
   ```sh
   docker compose --env-file infrastructure/.env up -d
   ```
2. Start the Spring Boot backend (`http://localhost:8080`):
   ```sh
   export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
   export PATH="$JAVA_HOME/bin:$PATH"
   cd backend && set -a && source .env && set +a && mvn spring-boot:run
   ```
3. Start the Next.js frontend (`http://localhost:3000`):
   ```sh
   cd frontend && npm run dev
   ```

### Step-by-Step Interactive Flow

1. **Product & Variant Setup (`/products`)**:
   - Sign in as `ADMIN`.
   - Open **Products → New product**. Create a software product (e.g., base sales price `1000.00`, cost price `400.00`) and add a variant `Odoo` with price adjustment `+560.00`.
   - Verify the effective variant unit price is `1,560.00` while the base product price remains `1,000.00`.
2. **Subscription Plan Setup (`/plans`)**:
   - Open **Plans → New plan**. Configure a `MONTHLY` (or `DAILY`/`WEEKLY`/`YEARLY`) plan, associate your product, set minimum quantity `1`, availability dates, and enable **Pausing Allowed**, **Renewal Allowed**, and **Early Closing Allowed**.
3. **Taxes & Discounts (`/taxes`, `/discounts`)**:
   - Under **Taxes**, create an active `18.00%` tax rule.
   - Under **Discounts**, create a `10.00%` percentage discount (or fixed discount) with optional minimum quantity/purchase thresholds and usage limit.
4. **Quotation Templates & Conversion (`/quotations`)**:
   - Create a reusable **Quotation Template** with your plan and product line.
   - Click **Generate quotation** for a customer, confirm the quotation, and click **Convert to subscription** to create an `ACTIVE` subscription with snapshotted line pricing.
5. **Subscription Lifecycle (`/subscriptions`)**:
   - Create a `DRAFT` subscription or open an existing subscription.
   - Step through `DRAFT → QUOTATION → CONFIRMED → ACTIVE`, or test **Pause**, **Resume** (observing `nextBillingDate` and `expiryDate` shift by paused days), **Renew**, and **Early close**.
6. **Automated Recurring Billing & Invoice Delivery (`/billing`, `/invoices`)**:
   - Open **Billing** to preview subscriptions due as of the target billing date, then click **Run billing now**.
   - Re-run billing for the same date to verify idempotency (`0` duplicate invoices created).
   - Open the generated invoice in **Invoices**, click **Export PDF** to download the PDF 1.4 statement, or click **Email invoice** and view the delivered email in Mailpit (`http://localhost:8025`).
7. **Partial & Full Payment Reconciliation (`/invoices/{id}`, `/payments`)**:
   - Confirm the draft invoice (`DRAFT → CONFIRMED`).
   - Record a partial payment (e.g., `UPI`) and observe `PARTIALLY_PAID` status and the updated `outstandingAmount`.
   - Record the remaining balance (e.g., `BANK_TRANSFER`) and observe the invoice automatically transition to `PAID` (`0.00` outstanding).
   - Optionally test **Reverse payment** on a payment detail view to see the invoice revert to `CONFIRMED` with full audit history.
8. **Executive Dashboard & Financial Reports (`/dashboard`, `/reports`)**:
   - Inspect real-time MRR, ARR, invoiced vs. collected charts, payment method distribution, and overdue aging buckets (`1–30`, `31–60`, `61–90`, `90+` days).
   - Filter by date range, granularity (`DAILY`, `MONTHLY`, `YEARLY`), plan, or customer, and click **Export CSV**.
9. **Customer Self-Service Portal (`/register`, `/login`)**:
   - Sign in as a `CUSTOMER` account.
   - Verify that administrative navigation items (`/customers`, `/billing`, `/taxes`, `/discounts`, `/team`, `/reports`) are hidden and API requests to them return `403 Forbidden`.
   - View owned subscriptions (`/subscriptions`), download owned invoice PDFs (`/invoices`), and use plan-permitted self-service controls (**Pause**, **Renew**, **Early close**).

---

## 4. Final Hackathon Submission Checklist

- [x] **All 5 Engineering Phases Implemented & Locally Verified**:
  - Phase 1: Auth, RBAC, Customers, Products/Variants, Plans, Subscriptions, Audit Logs (`V1`–`V3`)
  - Phase 2: Recurring Billing Engine, Invoices, PDF/Email, Tax & Discount Rules (`V4`)
  - Phase 3: Partial/Full Payments, Reconciliation, Reversals, Overdue Tracking, Pause/Resume/Renew/Close, Quotation Templates & Conversion (`V5`)
  - Phase 4: Executive Financial Analytics (MRR/ARR, Aging Buckets, Recharts, CSV Export) & Customer Portal (`V6`)
  - Phase 5: Security Hardening, Financial Edge Cases, 3,000+ Subscription Benchmark (`< 52ms` single-user p95, `378ms` 20-VU p95)
- [x] **Local Demonstration Video Recorded & Verified**:
  - `demo/brightloop-hackathon-demo.mp4` (`4m 51s`, `1600×1000` @ `30fps` H.264)
- [x] **Submission-Ready Documentation Completed**:
  - `README.md`, `AGENTS.md`, `docs/README.md`, `docs/features.md`, `docs/architecture.md`, `docs/api.md`, `docs/setup-and-deployment.md`, `docs/testing-and-quality.md`, `docs/demo-and-submission.md`, `docs/implementation-status.md`
- [x] **Zero Secrets Committed**:
  - `.env` files, passwords, and `.playwright-cli/` session logs excluded via `.gitignore` and verified via secret scan.
- [ ] **Before Final Remote Submission (Manual Team Action)**:
  - Review and commit final documentation changes.
  - Optionally remove `NEXT_PUBLIC_API_URL: http://localhost:8080` from `.github/workflows/ci.yml` so remote frontend `npm test` matches local execution.
  - Upload `demo/brightloop-hackathon-demo.mp4` to the hackathon submission portal / video link.
