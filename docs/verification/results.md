# Phase 1 verification — 2026-10-31

## Executed
- Java21 (21.0.13), Maven3.10, native PostgreSQL17.11 toolchain confirmed; exact toolchain in toolchain.txt.
- `docker compose -p brightloop-verify --env-file /tmp/brightloop-compose.env up -d --wait`: PostgreSQL17 and Mailpit1.32 healthy. Test-specific ports55433/11025/18025 avoid native services. Temporary credentials are outside the repository.
- Actual Spring Boot4.0.9 startup, PostgreSQL connectivity, Hibernate schema validation, FlywayV1+V2+V3 success, Actuator healthUP and readiness.
- Real HTTP smoke suite:760 assertions, run independently to completion against the final packaged application. Includes generic errors, sessions+CSRF, normalized emails, roles, customer CRUD/isolation, products/variants/plans, draft editing, money snapshots, sequential lifecycle, foreign key deletion conflicts, SMTP reset delivery/single use/password replacement/session revocation/logout.
- Native database audit query found15 action types including customer/product/plan mutations, staff/role changes, subscription creation/edit/transitions and password resets.
- Backend `mvn verify`:23 tests passed on JUnit5.14.4 after final formatting and V3 migration. Includes real HTTP10, reset expiry/replay/concurrency3, business rules5, collection validation2, exact decimal serialization1 and cost-price privacy2.
- Frontend clean `npm ci` passed; final `npm run typecheck`, `npm test` (5files/33tests), `npm run build`: all passed; Next16.4.3/React19.3.1/Tailwind4.3.3.
- Browser: registration automatically signs in and reaches owned dashboard; real plan/product/variant selection creates persisted subscription; 2 × (1000+560) ×1.18 =3681.60 matches API/detail page. Production preview started successfully. Mobile390×844 checked; title line rectangles fit viewport, table/lifecycle scroll within containers. Desktop restored afterward.
- Optional development SQL seed executed successfully; no users or passwords in seed.

## Limits
- CI configuration authored, but remote GitHub Actions has not run because no push was requested.
- Tests use disposable local PostgreSQL (and Compose health tests), rather than Testcontainers; no database emulation/H2.
- No production deployment, throughput/latency load claim or complete production security assessment.
- Test fixtures remain in the disposable local database. They are real persisted test records and are not application seed metrics.

## Compatibility decision
Spring Boot4.0.9 / Spring7 test extension requires JUnit6. The requested JUnit5 is pinned to5.14.4; real integration tests launch and close SpringApplication directly, without SpringExtension. Spring Boot Test remains available for compatible utilities. Runtime Spring dependencies were not downgraded.

Final packaged-application smoke run:760 assertions passed, exit0. Backend suite23 tests has0 failures/errors/skips, confirmed from Surefire XML. Frontend production/login HTTP200 and Actuator readinessUP verified after final restart.
