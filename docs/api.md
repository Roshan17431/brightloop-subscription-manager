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

The strict lifecycle is `DRAFT → QUOTATION → CONFIRMED → ACTIVE → CLOSED`. Skipping, repeating or reversing states fails. Closed subscriptions cannot be edited. Recurring billing, invoices, payments, pause/renewal automation and revenue reports are excluded from Phase 1.

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
