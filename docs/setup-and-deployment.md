# Brightloop — Setup and Deployment Guide

This guide documents the exact commands and environment variables required to configure, run, migrate, and test Brightloop locally, as well as the boundaries between the locally verified development setup and production deployment.

---

## 1. Prerequisites

Install the following tools on macOS or Linux:

- **Java 21** (OpenJDK 21)
- **Maven** `3.9+`
- **Node.js** `22 LTS` and **npm**
- **Docker Desktop** (with Docker Compose v2)

On macOS with Homebrew:

```sh
brew install openjdk@21 maven
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
node --version
docker compose version
```

Ensure `java -version` and `mvn -version` both report **Java 21** before building or running the backend.

---

## 2. Create Environment Files

Never commit `.env` files, passwords, or reset tokens. Copy the three example environment files from the repository root:

```sh
cp -n infrastructure/.env.example infrastructure/.env
cp -n backend/.env.example backend/.env
cp -n frontend/.env.example frontend/.env.local
```

### 2.1 `infrastructure/.env` (Docker Compose PostgreSQL & Mailpit)

```dotenv
POSTGRES_DB=brightloop
POSTGRES_USER=brightloop
POSTGRES_PASSWORD=replace-with-a-local-password
POSTGRES_PORT=5432
MAILPIT_SMTP_PORT=1025
MAILPIT_HTTP_PORT=8025
```

- Replace `POSTGRES_PASSWORD` with a strong local development password before starting Docker Compose.

### 2.2 `backend/.env` (Spring Boot Application)

```dotenv
# Copy to .env and supply your own credentials. Never commit .env.
DB_URL=jdbc:postgresql://localhost:5432/brightloop
DB_USERNAME=brightloop
DB_PASSWORD=REPLACE_WITH_DATABASE_PASSWORD
PORT=8080
FRONTEND_URL=http://localhost:3000
COOKIE_SECURE=false
# Optional first-admin bootstrap. Both required together, created only if email absent.
ADMIN_EMAIL=
ADMIN_PASSWORD=
ADMIN_NAME='Brightloop Administrator'
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS=false
MAIL_FROM=no-reply@brightloop.local
```

- Set `DB_PASSWORD` to match `POSTGRES_PASSWORD` from `infrastructure/.env` (and adjust the port in `DB_URL` if you changed `POSTGRES_PORT`).
- To bootstrap the initial `ADMIN` account on first startup, set both `ADMIN_EMAIL` (e.g., `admin@brightloop.local`) and `ADMIN_PASSWORD` (must be `> 8` characters and contain uppercase, lowercase, and a special character; quote values containing shell metacharacters).

### 2.3 `frontend/.env.local` (Next.js Application)

```dotenv
BACKEND_URL=http://localhost:8080
# Optional: absolute API base for deployments that explicitly configure cookie CORS.
# NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
```

- Keep `NEXT_PUBLIC_API_URL` commented out during normal local development so browser requests use the Next.js same-origin `/api/v1` proxy route (`http://localhost:3000/api/v1/*`).

---

## 3. Start PostgreSQL 17 and Mailpit (Docker Compose)

The root `docker-compose.yml` includes `infrastructure/compose.yaml` and binds ports to `127.0.0.1` only:

```sh
docker compose --env-file infrastructure/.env up -d
docker compose --env-file infrastructure/.env ps
```

- **PostgreSQL 17**: `127.0.0.1:5432` (stored in named volume `postgres_data`)
- **Mailpit SMTP**: `127.0.0.1:1025`
- **Mailpit Web UI**: `http://localhost:8025`

To stop containers while preserving your database volume:

```sh
docker compose --env-file infrastructure/.env down
```

---

## 4. Database Migrations & Optional Sample Catalog Seed

### Automatic Flyway Migrations

Flyway executes automatically when the Spring Boot backend starts, applying:
- `V1__foundation.sql`
- `V2__session_principal_email_length.sql`
- `V3__subscription_plan_index.sql`
- `V4__billing_invoicing_taxes_discounts.sql`
- `V5__payments_quotations_lifecycle.sql`
- `V6__analytics_indexes.sql`

Hibernate then validates the schema (`spring.jpa.hibernate.ddl-auto: validate`). Never enable Hibernate schema auto-update (`update` or `create-drop`).

### Optional Credential-Free Catalog Seed

After the backend has applied migrations `V1`–`V6` at least once, you can optionally load sample products, variants, and plans (contains no user accounts or credentials):

```sh
docker compose --env-file infrastructure/.env exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < infrastructure/seed-development.sql
```

---

## 5. Start the Backend and Frontend

### Terminal 1 — Start Spring Boot Backend

```sh
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
set -a
source .env
set +a
mvn spring-boot:run
```

Verify the backend health and readiness probes:

```sh
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/health/readiness
```

### Terminal 2 — Start Next.js Frontend

```sh
cd frontend
npm ci
npm run dev
```

For a production Next.js build and server locally:

```sh
cd frontend
npm ci
npm run build
npm run start
```

---

## 6. Access the Local Application

- **Brightloop Web Application**: `http://localhost:3000`
  - Sign in using the `ADMIN_EMAIL` and `ADMIN_PASSWORD` configured in `backend/.env`, or click **Create account** on `/register` to register a `CUSTOMER` account.
- **Mailpit Email Inbox**: `http://localhost:8025`
  - Inspect delivered password-reset links and invoice notification emails.
- **Backend Actuator Health**: `http://localhost:8080/actuator/health`

---

## 7. Run Automated Tests

### Backend Unit & PostgreSQL Integration Tests

Integration tests require `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` (plus Mailpit on `SMTP_HOST`/`SMTP_PORT` for email delivery tests) exported in the shell:

```sh
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
set -a
source .env
set +a
mvn verify
```

### Frontend TypeScript, Unit Tests & Production Build

```sh
cd frontend
npm ci
npm run typecheck
npm test
npm run build
```

### End-to-End Packaged HTTP Smoke Suite

Against a running backend on `http://localhost:8080` and Mailpit on `http://localhost:8025`:

```sh
set -a
source backend/.env
set +a
export BASE_URL=http://localhost:8080
export MAILPIT_URL=http://localhost:8025
export SMOKE_ADMIN_EMAIL="$ADMIN_EMAIL"
export SMOKE_ADMIN_PASSWORD="$ADMIN_PASSWORD"
python3 infrastructure/smoke-test.py
```

### k6 Load & Latency Benchmark (Optional)

```sh
k6 run -e BASE_URL=http://localhost:8080 -e ADMIN_EMAIL="$ADMIN_EMAIL" -e ADMIN_PASSWORD="$ADMIN_PASSWORD" infrastructure/k6-load-test.js
```

---

## 8. Troubleshooting Common Issues

| Symptom | Cause & Resolution |
| :--- | :--- |
| `release version 21 not supported` during `mvn` | `JAVA_HOME` points to an older or different JDK. Run `export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"` and verify `mvn -version`. |
| Database connection refused (`localhost:5432`) | Ensure Docker Desktop is running and `docker compose --env-file infrastructure/.env ps` shows `postgres` healthy, or verify custom `POSTGRES_PORT` matches `DB_URL` in `backend/.env`. |
| `401 Unauthorized` or `403 Forbidden` on API mutations | Sign in again if the session expired. For custom API clients, fetch `GET /api/v1/auth/csrf` with cookies enabled and send `data.token` in the `X-CSRF-TOKEN` header. Also verify your role (`ADMIN` vs `STAFF` vs `CUSTOMER`) permits the action. |
| Password reset or invoice email not arriving | Check that `SMTP_HOST` and `SMTP_PORT` in `backend/.env` match your running Mailpit container (`localhost:1025`) and inspect `http://localhost:8025`. Note that `POST /api/v1/auth/forgot-password` returns a generic `200 OK` even for unknown emails to prevent account enumeration. |
| Flyway validation or duplicate migration error | Do not modify already-applied SQL files in `backend/src/main/resources/db/migration/`. If macOS iCloud Drive created `* 2.*` duplicate files inside `backend/target` or `frontend/.next`, remove them with `find backend/target -name "* 2*" -delete` or `rm -rf frontend/.next`. |

---

## 9. Production Deployment Considerations (Scope Boundary)

Brightloop is verified locally using Docker Compose (PostgreSQL 17 + Mailpit) and local Java 21 / Node 22 processes. **No live cloud deployment URL is claimed.** When deploying to a production environment:

1. Set `COOKIE_SECURE=true` in the backend environment and terminate TLS (`HTTPS`) at a reverse proxy or load balancer.
2. Configure `FRONTEND_URL` to the exact production origin.
3. Replace Mailpit with a production transactional SMTP provider (`SMTP_HOST`, `SMTP_PORT`, `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, `SMTP_USERNAME`, `SMTP_PASSWORD`).
4. Use managed PostgreSQL 17 with automated encrypted backups and restrict network ingress to the backend application tier.
