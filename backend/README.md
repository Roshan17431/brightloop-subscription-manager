# Backend development

Use Java 21 and Maven. Copy `.env.example` to `.env`, configure credentials and SMTP, then:

```sh
set -a
source .env
set +a
mvn verify
mvn spring-boot:run
```

The integration tests launch the actual application on ephemeral ports and use the configured PostgreSQL database. Use a disposable development or CI database: tests retain uniquely named records. Without `DB_URL`, database integration tests are skipped; domain, DTO validation, privacy and serialization tests still run.

Spring Boot 4.0.9 uses Spring 7 whose JUnit extension requires JUnit 6. To honor the project's JUnit 5 requirement, this project pins JUnit Jupiter 5.14.4 and the integration tests manage the actual `SpringApplication` lifecycle directly, without `SpringExtension`. Spring Boot Test remains available for its compatible test utilities. No runtime framework dependencies are downgraded.

All monetary DTO values are exact decimal strings. Subscription line prices include selected variant adjustments; amounts round the final quantity and explicit tax calculation to two decimal places with HALF_UP. Plan price and policy flags are definition metadata; no billing, invoicing, automatic closing, renewal or pause scheduling runs in Phase 1. Plan minimum quantity is validated against the sum of subscription line quantities.

API resources use `/api/v1`. Initial admin provisioning requires both `ADMIN_EMAIL` and `ADMIN_PASSWORD`; it creates an administrator only when that email is absent and never promotes an existing account. Cookies are HttpOnly and CSRF tokens are session backed. Set `COOKIE_SECURE=true` when serving through HTTPS.
