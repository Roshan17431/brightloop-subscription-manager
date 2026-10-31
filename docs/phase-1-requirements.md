# BRIGHTLOOP — PHASE 1: FOUNDATION & CORE MODULES

You are a senior software architect, Java Spring Boot engineer, Next.js engineer, database architect, security engineer and technical lead.

Build Phase 1 of our Odoo × Make-A-Ton 9.0 Kochi Hackathon 2026 project.

**Project:** Brightloop Subscription Management System

**Repository:** `brightloop-subscription-manager`

**Goal:** Build a fully runnable, maintainable and integrated foundation for a subscription business platform.

Do not merely generate scaffolding. Implement functioning features with actual PostgreSQL persistence, validated REST APIs and integrated frontend pages.

## 1. Source of truth

The original hackathon problem statement is `Subscription_Management_System.pdf`.

If the PDF exists in the repository, read it before implementation.

If unavailable, use the requirements in this prompt and document any assumptions requiring later confirmation. Do not stop implementation merely because the PDF is unavailable.

The complete application will eventually support:

- Customer management
- Product and variant management
- Subscription plans
- Quotations
- Subscription lifecycle management
- Recurring invoicing
- Taxes and discounts
- Payment tracking
- Revenue analytics
- Role-based authentication
- Audit logging

Phase 1 must implement only the foundation and core modules specified below. Billing, invoice processing, payment processing and reporting will be implemented in later phases.

## 2. Mandatory technology stack

### Frontend

- Next.js with App Router
- React and TypeScript
- Tailwind CSS
- shadcn/ui
- TanStack Query
- React Hook Form
- Zod
- Centralized typed Fetch API client
- Lucide icons

### Backend

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Security
- Spring Data JPA
- Hibernate
- Jakarta Bean Validation
- Lombok where useful
- MapStruct where useful
- Maven
- Spring Boot Actuator

### Database

- PostgreSQL
- Flyway migrations
- Appropriate indexes and foreign key constraints
- Transactional services
- BigDecimal for monetary values

Verify dependency compatibility before choosing versions. Use compatible stable versions of libraries. Avoid introducing unnecessary dependencies.

## 3. Architecture

Use a modular monolith for the backend and feature-based architecture for the frontend.

Repository structure:

- `frontend/` — Next.js application
- `backend/` — Java Spring Boot application
- `infrastructure/` — Docker and environment configuration
- `docs/` — Architecture and implementation status
- `.github/` — GitHub configuration
- `AGENTS.md` — Codex instructions
- `docker-compose.yml`
- `README.md`

Backend package root:

`com.brightloop`

Organize backend modules under:

`com.brightloop.modules`

Phase 1 modules:

- auth
- user
- customer
- product
- plan
- subscription
- audit

Shared backend infrastructure:

- config
- security
- exception
- response
- validation

Implement layers only where needed:

- Controller
- Service
- DTO
- Domain
- Entity
- Repository
- Mapper
- Validation

Apply SOLID, DRY, KISS and separation of concerns.

Use constructor injection.

Controllers must not contain business logic.

Do not expose JPA entities directly through REST endpoints.

Do not create empty classes or unnecessary interfaces.

## 4. Backend application setup

Create a functioning Spring Boot application.

Requirements:

1. Maven project with Java 21.
2. PostgreSQL configuration.
3. Flyway migrations.
4. Spring Security configuration.
5. Environment-based settings.
6. CORS configuration for the Next.js frontend.
7. Actuator health endpoint.
8. Global exception handling.
9. Standardized API responses.
10. Proper application logging.
11. Health and readiness checks.
12. Clear local startup instructions.

Use `/api/v1` as the API prefix.

Never hardcode passwords, API keys, database credentials or authentication secrets.

## 5. Authentication and authorization

Implement actual backend authentication.

### Customer registration

- Unique email address.
- Password must be longer than 8 characters.
- Password must contain uppercase, lowercase and special characters.
- Proper validation messages.
- Secure password hashing using Spring Security's password encoder.
- Duplicate registration protection.

### Authentication

- Login.
- Logout.
- Current authenticated user endpoint.
- Secure server-side authentication state.
- Password reset via an expiring, single-use email link.
- SMTP configuration through environment variables.
- Local email testing using a tool such as Mailpit.

Use secure HttpOnly cookies where appropriate. Apply appropriate CSRF protections and CORS settings for the chosen authentication mechanism.

Never store authentication secrets in browser localStorage.

### Roles

Implement:

**ADMIN**
- Manage all records.
- Manage customers, products, plans and subscriptions.
- Create staff users.
- Manage user roles.

**STAFF**
- Perform authorized day-to-day management.
- Access customer and subscription records as permitted.
- Cannot create administrators or staff users.

**CUSTOMER**
- Access only their own profile and subscriptions.
- Cannot access administrative resources.

Enforce authorization on backend endpoints, not only in frontend navigation.

Return correct 401 and 403 responses.

## 6. Customer management

Implement customer management using actual database records.

Fields should include:

- Customer ID
- Linked user account where applicable
- Name
- Email
- Phone
- Company name
- Billing address
- Account status
- Created timestamp
- Updated timestamp

Implement:

- Create customer
- Update customer
- Get customer by ID
- Paginated customer listing
- Search customers
- Filter customers
- Customer detail view

Apply role-based access controls.

Do not expose other customers' private records to a customer account.

## 7. Product management

Implement product CRUD.

Fields:

- Product ID
- Product name
- Product type
- Sales price
- Cost price
- Notes
- Status
- Created timestamp
- Updated timestamp

Implement product variants with configurable price adjustments.

Example from the problem statement:

Selecting the Odoo brand variant adds 560 to the base product price.

Calculate variant pricing correctly without changing the underlying base price.

Use BigDecimal for money.

Validate nonnegative prices and correct decimal precision.

Implement administrative listing, searching, editing and product detail views.

## 8. Subscription plan management

Implement plan creation and management.

Fields:

- Plan ID
- Name
- Description
- Billing frequency
- Price
- Minimum quantity
- Availability start date
- Availability end date
- Automatic closing enabled
- Early closing allowed
- Pausing allowed
- Renewal allowed
- Status

Supported billing frequencies:

- Daily
- Weekly
- Monthly
- Yearly

Implement:

- Create plan
- Update plan
- List plans
- View plan details
- Search and filter plans
- Validate availability dates
- Validate minimum quantity
- Associate appropriate products with plans

Keep plan definitions separate from individual customer subscriptions.

## 9. Subscription management foundation

Create a functioning subscription module.

Each subscription must contain:

- Unique subscription number
- Customer
- Subscription plan
- Start date
- Expiry date
- Payment terms
- Current status
- Subscription line items

Each line must store:

- Product
- Product variant where applicable
- Quantity
- Unit price
- Tax information where applicable
- Calculated amount

Use BigDecimal for financial values.

Implement these lifecycle statuses:

- DRAFT
- QUOTATION
- CONFIRMED
- ACTIVE
- CLOSED

Provide service methods for validated transitions.

Implement:

- Create draft subscription
- Edit draft
- View details
- List subscriptions
- Search and filter
- Move through allowed lifecycle states
- List customer-owned subscriptions

Validate relationships, quantity, dates and permissions.

Prevent invalid state transitions.

Do not yet implement automated recurring invoice generation. That belongs to Phase 2.

Do not fake completed billing functionality.

## 10. Database design and Flyway

Create normalized database tables for Phase 1.

Suggested tables:

- users
- user_roles or a suitable role representation
- customers
- products
- product_variants
- plans
- plan_products
- subscriptions
- subscription_lines
- password_reset_tokens
- audit_logs

Design relationships carefully.

Requirements:

- Primary keys
- Unique constraints
- Foreign keys
- NOT NULL constraints
- Appropriate numeric precision
- Timestamp fields
- Useful indexes
- Pagination support
- Optimized common queries

Use Flyway SQL migrations for every schema change.

Do not use Hibernate automatic schema update as the migration system.

Use correct transactional boundaries.

Include database seed data for development, but never rely on hardcoded frontend data.

Do not seed real credentials or expose production secrets.

## 11. Standard API responses

Use a consistent success response format containing:

- success
- statusCode
- status
- message
- data
- timestamp

Use a consistent error response format containing:

- success
- statusCode
- status
- message
- errorCode
- path
- timestamp
- details

Use `ResponseEntity<T>` where appropriate.

Return accurate HTTP status codes.

Examples:

- 200 — successful retrieval
- 201 — resource created
- 204 — successful operation without response body
- 400 — invalid request
- 401 — unauthenticated
- 403 — forbidden
- 404 — not found
- 409 — conflict
- 422 — business validation error
- 500 — internal server error

Never return HTTP 200 for failures.

Never expose stack traces or sensitive internal errors.

Implement global exception handling using `@RestControllerAdvice`, with security filter errors handled through Spring Security's appropriate entry points and access-denied handlers.

## 12. Frontend design and implementation

Build a professional B2B SaaS dashboard.

Design inspiration: clean enterprise software with a modern, minimal appearance.

Use a consistent typography scale, spacing system and reusable design tokens.

The interface must be responsive across desktop, tablet and mobile.

### Authentication screens

- Login
- Register
- Forgot password
- Reset password

### Admin application

Implement:

- Dashboard shell
- Sidebar navigation
- Top navigation
- Customer management
- Product management
- Product variants
- Plan management
- Subscription management
- User profile
- Account/logout controls

### Customer application

Implement:

- Customer dashboard shell
- Customer profile
- Customer-owned subscription list
- Subscription details

### Shared components

Create reusable:

- Buttons
- Inputs
- Selects
- Tables
- Search fields
- Filters
- Pagination
- Forms
- Dialogs
- Confirmation dialogs
- Status badges
- Loading skeletons
- Toast notifications
- Empty states
- Error states

Use shadcn/ui wherever it reduces implementation time.

### Frontend rules

- Strict TypeScript.
- Feature-based folder organization.
- Typed API contracts.
- Centralized API client.
- TanStack Query for server-state management.
- React Hook Form and Zod for forms.
- Correct loading and error states.
- Backend-driven data.
- No fake dashboard metrics.
- No hardcoded JSON used as a substitute for APIs.
- Accessible labels and keyboard-friendly controls.

Frontend and backend must work together.

## 13. Basic audit logging

Implement audit records for:

- Customer creation and updates
- Product creation and updates
- Plan creation and updates
- Subscription creation
- Subscription state transitions
- Staff creation
- Role changes

Store:

- Actor
- Action
- Entity type
- Entity ID
- Timestamp
- Relevant safe metadata

Never log passwords, reset tokens or secrets.

Implement audit logging within appropriate transaction boundaries.

## 14. Testing

Add meaningful automated tests.

Backend tests:

- Customer registration validation
- Duplicate email prevention
- Password validation
- Role authorization
- Product price calculations
- Product variant adjustments
- Plan validation
- Subscription lifecycle transitions
- Customer data isolation
- REST error responses

Frontend tests:

- Form validation
- Important component behavior
- Authentication state
- API error handling

Use JUnit 5, Mockito and Spring Boot Test. Use Testcontainers where integration testing against PostgreSQL is valuable and supported.

Do not spend Phase 1 configuring elaborate performance-testing infrastructure.

## 15. Docker and local development

Create a Docker Compose setup for:

- PostgreSQL
- Mailpit or an equivalent local email-testing server

The Spring Boot and Next.js applications may run locally during active development.

Provide:

- Environment examples
- Database configuration
- Local run commands
- Database migration instructions
- Test commands
- Troubleshooting instructions

The project should be straightforward to run on a Mac development machine.

## 16. Codex project memory and progress tracking

Before major implementation, create a repository-root `AGENTS.md`.

It must document:

- Project purpose
- Locked technology stack
- Architecture rules
- Coding conventions
- Package organization
- REST API standards
- Database migration rules
- Security requirements
- Financial data rules
- Test and build commands
- Repository workflow

Create:

`docs/architecture.md`

Document the system architecture and significant technical decisions.

Create:

`docs/implementation-status.md`

Track all six phases:

1. Foundation & Core Modules
2. Billing & Invoicing
3. Payments & Subscription Lifecycle
4. Dashboards & Customer Portal
5. Testing, Security & Performance
6. Deployment & Submission

For every phase, track:

- Not started / In progress / Completed
- Implemented features
- Pending features
- Files and modules changed
- Tests executed and results
- Known issues
- Next steps

After Phase 1, automatically update the implementation-status document.

Do not mark Phase 1 complete until all its acceptance criteria are met.

Keep `AGENTS.md` focused on durable instructions. Store ongoing development progress in `implementation-status.md`.

Read these files before starting subsequent phases.

## 17. Git and team collaboration

- Preserve the existing Git history.
- Do not overwrite teammates' changes.
- Keep commits logically organized.
- Never change Git author identity to impersonate a teammate.
- Do not push or force-push without my instruction.
- Document significant implementation changes.
- Include appropriate `.gitignore` settings.
- Never commit `.env` secrets or generated credentials.

## 18. Scope restrictions

Do NOT implement these in Phase 1:

- Full recurring billing engine
- Automatic invoice generation
- Payment reconciliation
- Complete tax and discount engines
- Revenue analytics
- Quotation templates
- Prometheus/Grafana dashboards
- Redis caching
- Kafka or microservices
- Complex cloud infrastructure

These belong to later phases.

Prioritize the working foundation rather than unnecessary abstractions or infrastructure.

## 19. Phase 1 acceptance criteria

Phase 1 is complete only when:

1. Spring Boot starts successfully.
2. PostgreSQL connects successfully.
3. Flyway migrations execute successfully.
4. Next.js starts successfully.
5. Customer registration and login work.
6. Authentication and role-based permissions work.
7. Admin can create a staff account.
8. Password reset works through an email link.
9. Customer management uses PostgreSQL.
10. Products and variants can be managed.
11. Plans can be managed.
12. Subscriptions can be created and managed through valid lifecycle states.
13. Customers cannot access other customers' data.
14. Frontend screens communicate with real backend APIs.
15. Relevant audit events are recorded.
16. Required validation works.
17. Backend tests execute successfully.
18. Frontend type checking and build succeed.
19. README contains working local setup instructions.
20. `AGENTS.md` and `docs/implementation-status.md` are updated.

If any requirement cannot be completed, clearly report it as pending rather than claiming Phase 1 is complete.

## 20. Execution instructions

Start by inspecting the existing repository.

Then:

1. Read existing project files and instructions.
2. Create or update `AGENTS.md`.
3. Set up the backend.
4. Implement database migrations.
5. Implement authentication and permissions.
6. Implement customer, product, plan and subscription modules.
7. Build the frontend.
8. Connect frontend to backend.
9. Add tests.
10. Run the applications and execute validation.
11. Fix discovered errors.
12. Update documentation and implementation status.

Work incrementally and keep the application runnable.

Do not stop at an architecture proposal or write only pseudocode.

Implement actual source code.

Avoid asking for confirmation for routine technical decisions; choose sound defaults and document significant assumptions.

At the end, report:

- Features completed
- Modules created or modified
- Database migrations added
- API endpoints implemented
- Frontend pages completed
- Test results
- Known issues
- Remaining work
- Exact commands to run the project

**BEGIN PHASE 1 IMPLEMENTATION NOW.**