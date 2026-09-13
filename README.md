# PayLens

Employee salary management for ACME HR — assessment submission.

## Overview

PayLens is a web application that lets an **HR Manager** maintain employee records and base compensation, review salary history, and explore org-level pay analytics for a workforce of about **10,000 employees**.

It is a **modular monolith**: Angular SPA + Spring Boot API + PostgreSQL, runnable via local tooling or Docker Compose.

## Problem

Growing organizations often track headcount and pay in spreadsheets. That breaks down at scale: hard to search and filter, weak audit of raises, easy to mix currencies incorrectly, and no trustworthy dashboard for “how we pay people.”

## Product Goal

Give ACME’s HR Manager a single system of record for:

- employee identity and employment status  
- current base salary and dated salary history  
- compensation analytics that **never silently blend currencies**

Persona: **HR Manager** (single organization, assessment scope).

## Key Features

- Authenticated HR access (session cookie)
- Employee directory with search, filters, sort, and server-side pagination
- Create / update employees; soft deactivate (`TERMINATED`)
- Current salary and salary history (dated rows)
- Dashboard analytics: overview, headcount by country, payroll by country×currency, department and designation aggregates, salary distribution
- Deterministic seed of **10,000** employees for demo/load
- OpenAPI / Swagger UI for the HTTP API
- Docker Compose stack (Postgres + API + UI)
- GitHub Actions CI (tests + builds)

## Architecture

Three runtime pieces, one logical backend (not microservices):

```
                    ┌──────────────────────────────────────────────┐
                    │                   ACME                       │
  HR Manager        │                                              │
      │             │   ┌────────────┐    /api/v1/*    ┌─────────┐ │
      ▼             │   │  web       │ ───────────────►│  api     │ │
  [Browser] ───────►│   │  Angular   │ ◄───────────────│  Spring  │ │
                    │   │  + nginx   │   JSON+cookie   │  Boot    │ │
                    │   └────────────┘                 └────┬────┘ │
                    │                                       │      │
                    │                                       │ JDBC │
                    │                                       ▼      │
                    │                              ┌──────────────┐│
                    │                              │  PostgreSQL  ││
                    │                              │  employees   ││
                    │                              │  salary hist ││
                    │                              └──────────────┘│
                    └──────────────────────────────────────────────┘
```

API package modules (same JVM): `employee`, `salary`, `analytics`, `seed`, `security`.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Frontend | Angular 19, TypeScript, Angular Material, Chart.js |
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway |
| Analytics / seed | JDBC (`JdbcTemplate` / batch inserts) |
| Database | PostgreSQL 16 (H2 PostgreSQL-mode for automated tests) |
| API docs | springdoc OpenAPI / Swagger UI |
| Runtime | Docker Compose (optional local Maven + `ng serve`) |
| CI | GitHub Actions |

## Project Structure

```text
PayLens/
  backend/                 Spring Boot API
  frontend/                Angular SPA
  docs/                    Requirements, architecture, trade-offs, etc.
  .github/workflows/       CI
  docker-compose.yml
  .env.example
  README.md
```

## Local Setup

### Prerequisites

- **Java 21** and **Maven 3.9+**
- **Node.js 20+** (22 recommended) and npm
- **PostgreSQL 16** listening locally (or use [Docker Setup](#docker-setup) instead)

### Environment variables

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | JDBC URL (default in `dev`: `jdbc:postgresql://localhost:5432/paylens`) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials (dev defaults: `postgres` / `postgres`) |
| `PAYLENS_HR_USERNAME` | **Required** HR login username |
| `PAYLENS_HR_PASSWORD` | Bootstrap plaintext password (encoded at startup) |
| `PAYLENS_HR_PASSWORD_HASH` | Preferred: BCrypt hash (if set and valid, used instead of plaintext) |
| `PAYLENS_CORS_ORIGINS` | Allowed browser origin(s), e.g. `http://localhost:4200` |
| `PAYLENS_SESSION_COOKIE_SECURE` | Prod: session `Secure` flag (default `true`; set `false` for local HTTP Compose) |
| `SERVER_PORT` | API port (default `8081`) |
| `SPRING_PROFILES_ACTIVE` | Default `dev` if unset; Compose uses `prod` |

There are **no** default HR credentials in config. Set `PAYLENS_HR_USERNAME` and either `PAYLENS_HR_PASSWORD` or `PAYLENS_HR_PASSWORD_HASH` before starting the API. Never commit real secrets.

### PostgreSQL

Create an empty database, for example:

```sql
CREATE DATABASE paylens;
```

Flyway applies schema migrations on API startup (`V1`–`V4`).

### Backend startup

```bash
cd backend
mvn spring-boot:run
```

API: **http://localhost:8081**

PowerShell (credentials required):

```powershell
$env:PAYLENS_HR_USERNAME = "hr.manager"
$env:PAYLENS_HR_PASSWORD = "choose-a-strong-password"
cd backend
mvn spring-boot:run
```

### Frontend startup

```bash
cd frontend
npm ci
npm start
```

UI: **http://localhost:4200** (dev server proxies `/api` → `http://localhost:8081`).

### Seed command

Seed is **opt-in** (`paylens.seed.enabled=false` by default). From `backend/` with Postgres available and an empty employee table:

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
```

One-shot without leaving the HTTP server running:

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,seed" "-Dspring-boot.run.arguments=--spring.main.web-application-type=none"
```

Details: [docs/seeding.md](docs/seeding.md).

## Docker Setup

```bash
cp .env.example .env
# Edit .env: POSTGRES_*, PAYLENS_HR_*, PAYLENS_CORS_ORIGINS, FRONTEND_PORT

docker compose up --build
```

- UI (nginx + Angular production build): **http://localhost:8088** by default (`FRONTEND_PORT` in `.env`)
- API is reached via the UI origin (`/api/...`); not published on the host by default
- Postgres data persists in the `pgdata` volume

Optional seed inside Compose: set `PAYLENS_SEED_ENABLED=true` on an **empty** database (see `.env.example`).

Stop: `docker compose down` · wipe DB: `docker compose down -v`

**Windows note:** Docker Desktop requires WSL 2.

## API Documentation

With the API running locally (`dev` profile on port **8081**):

| Resource | URL |
| --- | --- |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| Health | http://localhost:8081/actuator/health |

In Docker Compose the API port is **not** published by default (only the UI/`FRONTEND_PORT` is). Swagger is served by the API process; open it via local `mvn spring-boot:run`, or temporarily publish `8081` if you need Swagger against the Compose API. The UI nginx proxies `/api/` and `/actuator/` only—not `/swagger-ui.html`.

## Testing

### Backend

```bash
cd backend
mvn test
```

Package (runs tests unless skipped):

```bash
cd backend
mvn -B verify
```

### Frontend

```bash
cd frontend
npm ci
npm run test:ci
```

Production build:

```bash
cd frontend
npm run build -- --configuration=production
```

CI mirrors these gates: [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Seed Data

- Target: **exactly 10,000** employees, 10 departments, salary rows (hire ± occasional raise)
- **Deterministic** from `paylens.seed.random-seed` (default `42`) — same seed ⇒ same dataset
- JDBC batch inserts; skips if the DB already has employees (safe re-run; truncate to re-seed)
- Countries/currencies consistent (e.g. IN/INR, US/USD); statuses include ACTIVE / ON_LEAVE / TERMINATED

See [docs/seeding.md](docs/seeding.md).

## Security

- Single **HR Manager** identity from environment (BCrypt hash preferred; plaintext bootstrap for local/demo only)
- **HTTP session** cookie `PAYLENSSESSION` (HttpOnly, SameSite=Lax)
- `/api/v1/**` requires `ROLE_HR_MANAGER` except login; health and OpenAPI remain public on the API
- CSRF disabled for the JSON SPA — documented trade-off in [docs/security.md](docs/security.md)
- Secrets belong in env / `.env` (gitignored), not in source

## Performance

Designed for ~**10k** employees without loading the full directory into the browser or JVM:

- Server-side pagination (default 25, max 100) and clamped sort fields
- List hydrates **current** salary in batch (not full history per row)
- Analytics aggregate in **SQL** over “current salary,” always **grouped by currency**
- Indexes for common filters/sorts (Flyway); opt-in JDBC seed with per-batch commits
- SPA short-TTL cache for analytics GETs to avoid refetch storms on navigation

Details: [docs/performance.md](docs/performance.md).

## Design Decisions

| Document | Contents |
| --- | --- |
| [docs/requirements.md](docs/requirements.md) | Product requirements and scope |
| [docs/architecture.md](docs/architecture.md) | System and module architecture |
| [docs/database-design.md](docs/database-design.md) | Schema and constraints |
| [docs/performance.md](docs/performance.md) | Workload, indexes, pagination, limits |
| [docs/trade-offs.md](docs/trade-offs.md) | Major engineering decisions and alternatives |
| [docs/ai-usage.md](docs/ai-usage.md) | Intentional AI use during development |

Also useful: [docs/security.md](docs/security.md), [docs/seeding.md](docs/seeding.md).

## Demo

> **Placeholder — replace before submission**
>
> | Item | URL |
> | --- | --- |
> | **Deployed application** | `_TODO: paste public app URL_` |
> | **Demo video** | `_TODO: paste recording URL_` |

## Future Improvements

- Corporate IdP (OIDC) and CSRF token flow for cookie sessions  
- Denormalized current salary on `employee` if list/currency filters get hot  
- Keyset pagination for very deep pages  
- Shared short-TTL cache for analytics under many concurrent HR users  
- Optional FX reporting currency (explicit rates only — never silent blending)

---

*Assessment stack: Java 21, Spring Boot, Angular, PostgreSQL. No Kafka, Redis, or Kubernetes in this submission.*
