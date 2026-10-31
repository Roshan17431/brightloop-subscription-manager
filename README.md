# Brightloop Subscription Management

Phase 1 foundation for the Odoo × Make-A-Ton 9.0 hackathon. The primary problem statement is [the original PDF](docs/Subscription_Management_System.pdf). Expanded scope is preserved in [Phase 1 requirements](docs/phase-1-requirements.md).

Java 21 / Spring Boot 4 modular monolith + PostgreSQL/Flyway + Next.js App Router/TypeScript. See [architecture](docs/architecture.md), [API](docs/api.md), and [implementation status](docs/implementation-status.md) for actual progress and verification limits.

## Prerequisites (Mac)

```sh
brew install openjdk@21 maven
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
node --version
```

Use Java 21, Maven 3.9+, Node 22 LTS, npm, and Docker Desktop/Compose. Start Docker Desktop before the next step.

## Database and email

```sh
cp -n infrastructure/.env.example infrastructure/.env
# Edit infrastructure/.env and choose your local POSTGRES_PASSWORD.
docker compose --env-file infrastructure/.env up -d
docker compose --env-file infrastructure/.env ps
```

PostgreSQL listens on localhost:5432; Mailpit SMTP on localhost:1025 and email UI at http://localhost:8025. Ports bind loopback only. Compose stores PostgreSQL data in a named volume. `docker compose --env-file infrastructure/.env down` stops services and preserves that volume. Don't use `down -v` unless intentionally removing local data.

Optional sample products and plans (no accounts or credentials) can be loaded after the backend has applied Flyway:

```sh
docker compose --env-file infrastructure/.env exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < infrastructure/seed-development.sql
```

The seed is idempotent and is intended only for disposable development databases.

## Backend

See backend/.env.example for exact variable names. Copy to backend/.env, set DB credentials to match infrastructure/.env, and choose an initial admin email and strong password. No administrator credentials ship in the repository.

```sh
cd backend
cp -n .env.example .env
# Edit .env before starting. Values containing shell metacharacters must be quoted.
set -a
source .env
set +a
mvn spring-boot:run
```

Flyway runs migrations on startup, and Hibernate validates the resulting schema. Backend defaults to port 8080. Never enable Hibernate schema auto-update. Database changes go in a new versioned Flyway migration.

## Frontend

In a second terminal:

```sh
cd frontend
cp -n .env.example .env.local
npm ci
npm run dev
```

Open http://localhost:3000. The same-origin Next.js API proxy forwards `/api/v1` to `BACKEND_URL`; cookies and CSRF headers are retained. Sign in using the admin provisioned through backend environment variables, or register a customer. Reset links arrive in Mailpit locally.

## Checks

```sh
cd backend
mvn verify
```

```sh
cd frontend
npm run typecheck
npm test
npm run build
```

HTTP smoke test needs a running backend and explicit admin credentials; see [API documentation](docs/api.md). Tests create records with randomized names; use a disposable development database.

## Troubleshooting

- Database connection refused: check Docker is running and `docker compose --env-file infrastructure/.env ps`; verify host, port and credentials in backend environment.
- Java release 21 unsupported: explicitly export JAVA_HOME as above; `mvn -version` must show Java 21.
- 401: sign in again. 403 on a mutation: confirm the CSRF request completed and the role permits the operation. The typed client handles token acquisition.
- Reset email missing: verify SMTP settings and Mailpit UI. The public endpoint deliberately does not reveal whether an account exists.
- Port collision: change POSTGRES_PORT and the matching backend database URL, or BACKEND_URL if changing backend port.
- Schema mismatch: use an empty disposable database or apply all migrations; do not edit already-applied migration files.

No automated invoices, payments, recurring billing or financial reporting are implemented in Phase 1. Do not infer billing from plan prices or operational subscription counts.

## Current local verification environment

This workspace has an ignored `backend/.env` with generated development admin credentials. Inspect it locally for the login; never commit or share it. The verification database runs natively on `127.0.0.1:55432`; Mailpit runs on SMTP1025/UI8025. The separately verified Compose project uses ports55433/11025/18025. These ports are verification choices, not defaults required by the application.

To restart the applications with the existing ignored local configuration:

```sh
# Terminal 1, repository root
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
cd backend
set -a; source .env; set +a
mvn spring-boot:run
```

```sh
# Terminal 2, repository root
cd frontend
npm ci
npm run dev
```

Run the full HTTP suite against a running disposable backend and local Mailpit:

```sh
# Repository root; this creates persisted test records
set -a; source backend/.env; set +a
export SMOKE_ADMIN_EMAIL="$ADMIN_EMAIL"
export SMOKE_ADMIN_PASSWORD="$ADMIN_PASSWORD"
python3 infrastructure/smoke-test.py
```

For backend HTTP/database tests, export `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` before `mvn verify`. Without `DB_URL`, database integration tests are explicitly skipped; unit tests still run. CI provides a real PostgreSQL service. See [verification results](docs/verification/results.md) for checks actually executed.
