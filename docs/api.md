# Brightloop Phase 1 API

The backend serves `/api/v1`. The Next.js application proxies that path to the backend so browser requests use the same origin. All application endpoints exchange JSON. IDs are numbers, dates use `YYYY-MM-DD`, timestamps are ISO 8601, and all monetary DTO values are exact decimal strings. Clients must preserve those strings through display rather than converting them to JavaScript Number. Monetary input fields accept decimal JSON values. Customer catalog responses redact internal cost prices.

Successful application responses contain `success`, `statusCode`, `status`, `message`, `data`, and `timestamp`. Errors contain `success: false`, `statusCode`, `status`, `message`, `errorCode`, `path`, `timestamp`, and `details`. HTTP status codes carry the result: validation failures are 400 or 422, unauthenticated requests 401, forbidden actions 403, missing records 404, and conflicting state or referenced deletion 409. Security filter failures use the same error envelope. Actuator health is the standard Actuator response and does not use this envelope.

Lists return `data: {content, totalElements, totalPages, number, size}` and accept zero-based `page` and `size` between 1 and 100 (default 20). Supported module lists accept `q` for text search, limited to 200 characters; customers, products and plans accept `status`, and subscriptions accept `state`.

## Sessions and CSRF

Use a cookie jar or browser `credentials: "include"`. Authentication creates a server-side session with an HttpOnly session cookie. Obtain a CSRF token through `GET /api/v1/auth/csrf`, then send `data.token` under the header named by `data.headerName` on POST, PUT, PATCH and DELETE, including registration, login and reset requests. Obtain a fresh token after login or logout. Never store authentication secrets in localStorage.

| Method | Path after `/api/v1` | Request / purpose |
| --- | --- | --- |
| GET | `/auth/csrf` | CSRF token and header name; available before login |
| POST | `/auth/register` | `{name,email,password}`; creates a customer account, 201 |
| POST | `/auth/login` | `{email,password}`; starts session |
| GET | `/auth/me` | Current `{id,name,email,role}` |
| POST | `/auth/logout` | Invalidates session |
| POST | `/auth/forgot-password` | `{email}`; generic response prevents account enumeration |
| POST | `/auth/reset-password` | `{token,password}`; consumes an expiring, single-use email token |
| POST | `/staff` | Admin-only `{name,email,password}`; creates STAFF user, 201 |
| GET | `/users` | Admin-only user list |
| PATCH | `/users/{id}/role` | Admin-only `{role:"ADMIN"|"STAFF"|"CUSTOMER"}` |

Emails are normalized and unique. Passwords must be longer than eight characters and contain uppercase, lowercase and special characters. Reset tokens are delivered through SMTP, hashed in storage, expire and can be consumed once. Initial admin credentials come exclusively from environment configuration.

## Customers

Staff and admins can create, list, search, filter, read and update customers. Customers can read and update their own profile and cannot access another customer's private record.

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | `/customers` | Paginated list / create customer |
| GET, PUT | `/customers/{id}` | Read / update customer |
| GET, PUT | `/customers/me` | Current customer's profile |

Create and update payload: `{name,email,phone,companyName,billingAddress,status}`. `status` is `ACTIVE` or `INACTIVE`; optional contact fields may be omitted. Responses also include the ID, linked user where applicable, and created/updated timestamps. Customer removal is outside this API's Phase 1 scope.

## Catalog

Authenticated users can read catalog records. Only admins can create, change or delete products and plans. Staff catalog writes are forbidden by the server. Deleting a catalog record referenced by a subscription returns a conflict.

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | `/products` | Paginated list / create product |
| GET, PUT, DELETE | `/products/{id}` | Detail / update / delete |
| GET, POST | `/plans` | Paginated list / create plan |
| GET, PUT, DELETE | `/plans/{id}` | Detail / update / delete |

Product payload:

```json
{
  "name": "Support service",
  "type": "SERVICE",
  "salesPrice": "100.00",
  "costPrice": "40.00",
  "notes": "Monthly support",
  "status": "ACTIVE",
  "variants": [{"name": "Odoo", "priceAdjustment": "5.60"}]
}
```

Send a variant's returned `id` when updating it. Product base prices remain separate from variant adjustments. Base and cost prices must be nonnegative with at most two decimal places. A variant adjustment can reduce the price provided the resulting unit price remains nonnegative. Responses include product/variant IDs and product timestamps.

Plan payload:

```json
{
  "name": "Monthly support",
  "description": "Standard support plan",
  "billingPeriod": "MONTHLY",
  "price": "25.00",
  "minimumQuantity": 1,
  "availableFrom": "2026-01-01",
  "availableUntil": "2027-12-31",
  "automaticClosing": true,
  "earlyClosingAllowed": false,
  "pausingAllowed": false,
  "renewalAllowed": true,
  "status": "ACTIVE",
  "productIds": [1]
}
```

Billing periods are `DAILY`, `WEEKLY`, `MONTHLY` and `YEARLY`. Dates must be ordered and minimum quantity must be positive. `productIds` stores one or more plan/product associations. Plan price is definition metadata; it does not run billing or replace subscription line pricing. Capability flags are stored configuration for future lifecycle features, not automated invoicing or renewal behavior.

## Subscriptions

Staff and admins can manage subscriptions. Customers can create and edit their own drafts and read only their own subscriptions. Only staff and admins may transition state.

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | `/subscriptions` | Paginated list / create numbered DRAFT |
| GET, PUT | `/subscriptions/{id}` | Read detail / edit DRAFT |
| POST | `/subscriptions/{id}/transition` | `{state}`; advance one lifecycle step |

Draft payload:

```json
{
  "customerId": 1,
  "planId": 1,
  "startDate": "2026-11-01",
  "expiryDate": "2026-12-01",
  "paymentTerms": "Net 30",
  "lines": [{"productId": 1, "variantId": 1, "quantity": 2, "taxRate": "10.00"}]
}
```

For a customer session, `customerId` can be omitted to use its own linked customer; specifying another customer is forbidden. The server validates customer, plan, product and variant relationships, plan availability, date order, minimum total quantity across lines, positive quantities, and financial precision. `variantId` is optional.

The server calculates and snapshots each line's `unitPrice` from product sales price plus variant adjustment. `amount` includes quantity and the explicit percentage `taxRate`; `total` sums line amounts. For example, `(100.00 + 5.60) × 2 × 1.10 = 232.32`. Later catalog changes do not rewrite existing snapshots. Clients cannot choose the unit price. Tax snapshots are not a tax rules engine.

Responses contain `id`, unique `number`, customer and plan IDs/names, dates, payment terms, `state`, `total`, and `lines`. Lines include product/variant identity and names, quantity, unit price, tax rate and calculated amount.

The strict lifecycle is `DRAFT → QUOTATION → CONFIRMED → ACTIVE → CLOSED`. Skipping, repeating or reversing states fails. Closed subscriptions cannot be edited.

## Tax Rules, Discounts, Recurring Billing, and Invoices (Phase 2)

| Method | Path after `/api/v1` | Purpose |
| --- | --- | --- |
| GET, POST | `/taxes` (or `/tax-rules`) | Staff/Admin list tax rules / Admin-only create tax rule |
| GET, PUT, DELETE | `/taxes/{id}` | Staff/Admin read / Admin-only update or delete tax rule |
| GET, POST | `/discounts` (or `/discount-rules`) | Staff/Admin list discounts / Admin-only create discount rule |
| GET, PUT, DELETE | `/discounts/{id}` | Staff/Admin read / Admin-only update or delete discount rule |
| GET | `/billing/due` | Staff/Admin list of active subscriptions due as of `billingDate` |
| GET | `/billing/runs` | Staff/Admin paginated billing execution history |
| GET | `/billing/runs/{id}` | Staff/Admin billing run detail and per-subscription items |
| POST | `/billing/run` (or `/billing/runs`) | Admin-only trigger recurring billing (`{billingDate, idempotencyKey, subscriptionId}`) |
| POST | `/billing/runs/{id}/retry` | Admin-only retry failed items from a billing run |
| GET | `/invoices` | Paginated invoice list (`q`, `state`, `subscriptionId`, `customerId`); customer-isolated |
| GET | `/invoices/{id}` | Invoice detail with historical pricing, discount, and tax snapshots |
| GET | `/invoices/{id}/pdf` | Download binary `application/pdf` invoice document |
| POST | `/invoices/{id}/transition` | Staff/Admin transition (`CONFIRMED`, `CANCELLED`; `PAID` requires Phase 3 payment evidence) |
| POST | `/invoices/{id}/confirm` | Staff/Admin confirm draft invoice |
| POST | `/invoices/{id}/cancel` | Staff/Admin cancel draft or unpaid confirmed invoice |
| POST | `/invoices/{id}/settle-zero` | Staff/Admin settle a confirmed `0.00` invoice as `PAID` without fake payment rows |
| POST | `/invoices/{id}/follow-up` | Staff/Admin record overdue collection follow-up notes (`{notes}`) |
| POST | `/invoices/{id}/send` | Staff/Admin deliver invoice email via SMTP |

## Payments, Subscription Lifecycle, and Quotations (Phase 3)

| Method | Path after `/api/v1` | Purpose |
| --- | --- | --- |
| GET | `/payments` | Paginated payment list (`q`, `invoiceId`, `customerId`, `method`, `status`, `fromDate`, `toDate`); customer-isolated |
| GET | `/payments/{id}` | Payment detail with reconciled invoice totals; customer-isolated |
| POST | `/payments` | Staff/Admin record full or partial payment (`{invoiceId, method, amount, paymentDate, paymentReference, idempotencyKey, note}`) |
| GET | `/invoices/{invoiceId}/payments` | Payment history for an invoice; customer-isolated |
| POST | `/invoices/{invoiceId}/payments` | Staff/Admin record payment for a specific invoice |
| POST | `/payments/{id}/reverse` | Staff/Admin reverse a recorded payment (`{reason, reversalReference}`) and reconcile invoice |
| POST | `/subscriptions/{id}/pause` | Staff/Admin pause an `ACTIVE` subscription (`{pauseDate, reason}`); requires `plan.pausingAllowed` |
| POST | `/subscriptions/{id}/resume` | Staff/Admin resume a `PAUSED` subscription (`{resumeDate}`) and shift billing/expiry dates |
| POST | `/subscriptions/{id}/renew` | Staff/Admin renew an `ACTIVE` or `CLOSED` subscription (`{newExpiryDate, periods, idempotencyKey}`); requires `plan.renewalAllowed` |
| POST | `/subscriptions/{id}/close` (or `/early-close`) | Staff/Admin early-close subscription (`{reason, closeDate}`); requires `plan.earlyClosingAllowed` |
| GET, POST | `/quotation-templates` | Staff/Admin list or create reusable quotation templates |
| GET, PUT | `/quotation-templates/{id}` | Staff/Admin read or update quotation template |
| POST | `/quotation-templates/{id}/generate` | Staff/Admin generate customer quotation from template (`{customerId, issueDate, validUntil, startDate, expiryDate, paymentTerms, notes}`) |
| GET, POST | `/quotations` | List quotations (customer-isolated) / Staff/Admin generate quotation |
| GET | `/quotations/{id}` | Quotation detail with snapshotted lines and expiry status; customer-isolated |
| POST | `/quotations/{id}/confirm` | Staff/Admin confirm an eligible, non-expired quotation |
| POST | `/quotations/{id}/cancel` | Staff/Admin cancel a non-converted quotation |
| POST | `/quotations/{id}/convert` | Staff/Admin convert quotation into an active subscription preserving price snapshots |

## Financial Analytics, Reporting, and Customer Portal (Phase 4)

All `/api/v1/analytics/**` endpoints are restricted to `ADMIN` and `STAFF` (`403 Forbidden` for `CUSTOMER`). They accept optional query parameters: `fromDate` (`YYYY-MM-DD`), `toDate` (`YYYY-MM-DD`), `asOfDate` (`YYYY-MM-DD`), `granularity` (`DAILY`, `MONTHLY`, `YEARLY`), `customerId`, `planId`, `billingPeriod` (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`), `subscriptionState`, `invoiceState`, `paymentMethod`, and `windowDays` (`1..365`, default `30`).

| Method | Path after `/api/v1` | Purpose |
| --- | --- | --- |
| GET | `/analytics/overview` | Executive KPI summary (MRR, ARR, paused MRR, subscription counts, invoiced/collected/outstanding/overdue totals, recent invoices/payments, upcoming renewals, expiring subscriptions) |
| GET | `/analytics/revenue` | Time-series revenue & cash collection breakdown (`DAILY`, `MONTHLY`, `YEARLY`) with pre-tax subtotal, discounts, taxes, invoiced total, collected payments, and collection rate % |
| GET | `/analytics/subscriptions` | Subscription counts and normalized MRR/ARR breakdown by lifecycle status, plan, and billing frequency |
| GET | `/analytics/payments` | Completed vs. reversed payment totals, breakdown by payment method with share %, and time-series collections |
| GET | `/analytics/receivables` | Open receivables, current not-yet-due balance, overdue aging buckets (`1-30`, `31-60`, `61-90`, `90+` days), and top overdue invoices schedule |
| GET | `/analytics/renewals` | Upcoming subscription billing dates and expiring subscriptions within `windowDays` |
| GET | `/analytics/reports/export` | Download `text/csv` report for `section=revenue\|payments\|subscriptions\|receivables` |
| GET | `/portal/overview` | Customer-scoped self-service portal summary (active/paused/closed subscriptions, monthly recurring spend, outstanding/overdue balance, next billing date, recent invoices, recent payments) |

Customer accounts (`ROLE_CUSTOMER`) may also invoke `POST /api/v1/subscriptions/{id}/{pause,resume,renew,close,early-close}` on **their own** subscriptions when permitted by the subscription's plan (`pausingAllowed`, `renewalAllowed`, `earlyClosingAllowed`). Attempting to mutate another customer's subscription returns `403 Forbidden`.

## Executable HTTP verification

Run the backend against a disposable development PostgreSQL database, provision an admin using the backend environment, and start Mailpit SMTP on port 1025 and its HTTP API on 8025. The Python runner uses real cookies, CSRF headers, HTTP requests, persisted records and delivered reset email. It has no third-party Python dependency.

```sh
export BASE_URL=http://localhost:8080
export MAILPIT_URL=http://localhost:8025
export SMOKE_ADMIN_EMAIL="$ADMIN_EMAIL"
export SMOKE_ADMIN_PASSWORD="$ADMIN_PASSWORD"
python3 infrastructure/smoke-test.py
```

Run from the repository root. `BASE_URL` may also point to the Next.js same-origin proxy if it forwards `/api/v1` and `/actuator/health`; direct backend execution is the default. The script exits nonzero at the first failure and logs neither passwords nor reset tokens. It requires both admin variables and does not use a default secret.

The runner checks health, envelopes, CSRF, authentication, normalized duplicate/weak registration, customer CRUD and isolation, staff/role permissions, product/variant CRUD, precision validation, plan CRUD and associations, draft editing, calculated money snapshots, subscription ownership, all sequential transitions, invalid transitions, referenced deletion conflicts, unreferenced deletion, real SMTP reset delivery, token replay rejection, old/new password behavior and logout. Reset token expiry requires a separate time-controlled backend test; the HTTP runner does not wait for expiry. Database audit rows, SMTP storage hashing, CORS policy, browser rendering and concurrent transition races require separate checks.

Each run generates uniquely named accounts and retains customer/staff/subscription fixtures plus referenced catalog records for inspection. It removes only disposable catalog records it creates. Use a disposable database; repeated runs accumulate fixtures. The final line reports a safe fixture suffix and subscription ID for matching persisted records and audit entries.
