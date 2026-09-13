# PayLens

ACME HR employee salary management — Spring Boot API + Angular SPA + PostgreSQL.

## Quick start (Docker)

Requires:

1. [Docker Desktop](https://www.docker.com/products/docker-desktop/) for Windows/Mac (or Docker Engine + Compose v2 on Linux)
2. On Windows: **WSL 2** — install once in an **admin** PowerShell, then reboot:

```powershell
wsl --install
# reboot when prompted, then open Docker Desktop and wait until it says "Engine running"
```

```bash
# 1. Secrets — never commit .env
cp .env.example .env
# Edit .env: set POSTGRES_PASSWORD, PAYLENS_HR_USERNAME, PAYLENS_HR_PASSWORD (or HASH)

# 2. Build and start Postgres + API + UI
docker compose up --build
```

Open the app: **http://localhost:8088** by default (`FRONTEND_PORT` in `.env`; change if that port is taken).

Login with the HR credentials from `.env`.

Stop:

```bash
docker compose down
```

Data volume (`pgdata`) is kept. Wipe DB:

```bash
docker compose down -v
```

### Optional: seed ~10k employees

In `.env`:

```env
PAYLENS_SEED_ENABLED=true
```

Then start on an **empty** database (`docker compose down -v` first if re-seeding).

### Useful URLs

| URL | Purpose |
| --- | --- |
| http://localhost:8088 | Angular UI (nginx proxies `/api` → backend) |
| http://localhost:8088/api/v1/... | API via reverse proxy |
| http://localhost:8088/actuator/health | Backend health (proxied) |

Backend is not published on the host by default; only the frontend port is exposed.

### Environment variables

See [`.env.example`](.env.example). Important:

| Variable | Purpose |
| --- | --- |
| `POSTGRES_*` | Database name/user/password |
| `PAYLENS_HR_USERNAME` | HR Manager login |
| `PAYLENS_HR_PASSWORD` | Bootstrap plaintext password (encoded at startup) |
| `PAYLENS_HR_PASSWORD_HASH` | Preferred: BCrypt hash (overrides plaintext) |
| `PAYLENS_CORS_ORIGINS` | Allowed browser origin(s), comma-separated |
| `PAYLENS_SEED_ENABLED` | Opt-in JDBC seed |

**Do not put real credentials in Git.** Only `.env.example` (placeholders) is committed.

---

## Local development (without Docker)

### Backend

Java 21 +, Maven, PostgreSQL.

```bash
cd backend
# PowerShell example:
# $env:SPRING_PROFILES_ACTIVE="dev"
# $env:PAYLENS_HR_USERNAME="admin"
# $env:PAYLENS_HR_PASSWORD="admin"
mvn spring-boot:run
```

API: http://localhost:8081  
Docs: [`docs/security.md`](docs/security.md), [`docs/performance.md`](docs/performance.md)

### Frontend

Node 20+ recommended.

```bash
cd frontend
npm ci
npm start
```

UI: http://localhost:4200 (proxies `/api` → `localhost:8081`)

### Tests

```bash
cd backend && mvn test
cd frontend && npm run test:ci
```

---

## Project layout

```text
PayLens/
  backend/          Spring Boot API (Dockerfile)
  frontend/         Angular SPA (Dockerfile + nginx)
  docs/             Architecture, security, performance, seeding
  docker-compose.yml
  .env.example
```

## Docker design notes

- **Multi-stage builds** for backend (Maven → JRE) and frontend (Node → nginx).
- **Non-root** runtime users (`paylens` on API; `nginxinc/nginx-unprivileged` on UI).
- **Health checks** on Postgres (`pg_isready`), backend (`/actuator/health`), frontend (`/healthz`).
- Backend **`depends_on: service_healthy`** so it starts only after Postgres is ready.
- Postgres data in named volume **`pgdata`**.
- Production Spring profile: `application-prod.yml` (no credential defaults).
