# PayLens Architecture

**Status:** Design only. No application source in this document.  
**Related:** [requirements.md](./requirements.md), [technical-plan.md](./technical-plan.md)  
**Scale target:** 10,000 employees, single organization (ACME), one HR role  
**Style:** Modular monolith · REST · layered packages · one PostgreSQL

---

## 1. System context

PayLens is a **single-tenant web system** used by ACME HR Managers. It replaces Excel as the system of record for employee identity, current base pay, and salary history, and it answers org-level “how we pay people” questions.

**Actors**

| Actor | Relationship |
| --- | --- |
| HR Manager | Only interactive user. Authenticates, maintains employees/salaries, reads dashboard. |
| Browser | Runs the Angular SPA. Talks only to the PayLens origin (nginx). |
| Operator / deploy | Starts Compose (or equivalent), sets secrets, loads seed once. |

**External systems in MVP:** none. No IdP, FX feed, email, object storage, or payroll bureau.

**Trust boundary:** everything inside nginx + API + PostgreSQL is ACME-internal. Compensation data does not leave that boundary.

```
                    ┌─────────────────────────────────────────┐
                    │                 ACME                    │
  HR Manager        │                                         │
      │             │   PayLens (this system)                 │
      ▼             │                                         │
  [Browser] ───────►│  Web UI  →  API  →  PostgreSQL          │
                    │                                         │
                    └─────────────────────────────────────────┘
                         ▲
                         │ no third-party integrations (MVP)
                         │
                    (none)
```

---

## 2. Component architecture

Three **runtime** components, one **logical** backend. Not three independently deployed business services.

| Component | Technology | Responsibility |
| --- | --- | --- |
| **web** | Angular + TypeScript, Angular Material, nginx | Static UI. Proxies `/api` to the backend. |
| **api** | Java 21, Spring Boot, Spring Web, Spring Data JPA, Bean Validation, Spring Security | Auth, commands, queries, transactions, validation. |
| **db** | PostgreSQL 16 | Source of truth for employees, salary records, users, departments. |

**Logical modules inside `api` (same JVM, same database):**

| Module | Owns |
| --- | --- |
| `identity` | HR users, login/logout/session |
| `employee` | Employee aggregate, directory search |
| `compensation` | Salary records, succession rules |
| `analytics` | Read-only aggregates (SQL) |
| `seed` | Idempotent 10k load when empty |
| `shared` | Paging, errors, money helpers |

Modules call each other **in-process** through services. Compensation does not reach into employee repositories from controllers; employee application service coordinates “create employee + first salary.” Analytics reads via repositories/queries, does not write salary rows.

```
+------------------+     /api/v1/*      +---------------------------+
|  web (nginx +    | -----------------> |  api (Spring Boot)        |
|  Angular SPA)    | <----------------- |                           |
+------------------+     JSON + cookie  |  identity                 |
                                        |  employee                 |
                                        |  compensation             |
                                        |  analytics                |
                                        |  seed / shared            |
                                        +-------------+-------------+
                                                      |
                                                      | JDBC / JPA
                                                      v
                                        +---------------------------+
                                        |  PostgreSQL               |
                                        +---------------------------+
```

**Why this split:** UI and API scale independently in *process* (static files vs JVM) but stay one product. Database stays one so employee + salary + analytics stay consistent without distributed transactions.

---

## 3. Frontend architecture

**Stack:** Angular (current major, TypeScript strict), Angular Material (tables, forms, dialogs, paginator, snackbars), Angular Router, HttpClient, reactive forms.

**Layout**

```
apps/web (or frontend/)
  src/app
    core/           # singleton: auth, HTTP interceptors, API error mapper
    shared/         # presentational widgets, pipes (money + currency label)
    features/
      login/
      dashboard/
      employees/    # list, create, detail, salary-change dialog
    models/         # TypeScript DTOs matching API (no domain duplication of rules)
```

**Rules**

- **No 10k-row client cache.** List state = current page + query params (`page`, `size`, `q`, filters).
- **Server is source of truth** for totals, analytics, and salary succession.
- **Auth:** `withCredentials` (cookie session). Interceptor adds CSRF header. 401 → login.
- **Money display:** always show `currency` next to amount. No “total” pipe that sums mixed currencies.
- **Salary range inputs** disabled until a currency is selected (mirrors API).
- Feature folders map to screens in the technical plan, not to backend packages 1:1.

**Component library:** Angular Material — mature, accessible tables/paginator, sufficient for an HR internal tool. No extra chart SaaS. Simple Material cards + tables for dashboard; optional lightweight chart later if needed, not a new platform.

---

## 4. Backend package structure

One Maven/Gradle module: `paylens-api`. **Modular monolith = package boundaries**, not extra JARs.

```
com.acme.paylens
  PaylensApplication.java

  identity
    api/          # AuthController
    application/  # AuthService
    domain/       # HrUser
    infra/        # HrUserRepository, SecurityConfig

  employee
    api/          # EmployeeController, request/response records
    application/  # EmployeeService (orchestrates compensation on create)
    domain/       # Employee, EmploymentStatus
    infra/        # EmployeeRepository, directory query

  compensation
    api/          # SalaryController (nested under employee resource)
    application/  # SalaryService (close previous + insert current, @Transactional)
    domain/       # SalaryRecord, PayFrequency, ChangeReason, Money rules
    infra/        # SalaryRecordRepository

  analytics
    api/          # AnalyticsController
    application/  # AnalyticsService
    infra/        # native / JPQL aggregate queries (no write model)

  seed
    SeedRunner    # ApplicationRunner: seed if employee count = 0

  shared
    api/          # ProblemDetail / error body, PageResponse
    validation/   # cross-field validators if needed
    config/       # JPA, Jackson, Web
```

**Layering (every feature module):**

```
Controller  →  Application service  →  Domain  →  Spring Data repository
     │                │                     │
     │                │                     └── entities, invariants
     │                └── transactions, use cases
     └── HTTP mapping, status codes, no business rules
```

**Forbidden shortcuts**

- Controllers calling repositories.
- Entities exposed as API JSON (use records/DTOs).
- Analytics computing averages in Java over `findAll()`.
- Domain methods that silently convert currency.

`employee.application` may depend on `compensation.application`. `compensation` must not depend on `analytics`. `analytics` may read employee/salary tables but not change them.

---

## 5. Database architecture

**One PostgreSQL database, one schema.** Flyway (preferred) or Liquibase. API owns migrations.

**Tables** (see technical plan for columns):

| Table | Role |
| --- | --- |
| `hr_user` | Login identities (BCrypt hash). |
| `department` | Filter/group dimension. |
| `employee` | Person + employment. Optimistic `version`. |
| `salary_record` | Time-bounded base pay. Current = `effective_to IS NULL`. |

**Integrity that the DB must enforce** (not only Java):

- Unique `employee_number`, `email`, `department.code`, `hr_user.email`.
- `salary_record.amount > 0`.
- `effective_to IS NULL OR effective_to >= effective_from`.
- **Partial unique index:** one current salary per employee  
  `UNIQUE (employee_id) WHERE effective_to IS NULL`.
- FK `employee.department_id` → `department`; `salary_record.employee_id` → `employee` `ON DELETE RESTRICT`.

**Indexes for 10k**

- `employee (department_id)`, `(country_code)`, `(status)`, `(last_name, first_name)`.
- Text search: `ILIKE` on number/email/name; add `pg_trgm` only if measured slow.
- `salary_record (employee_id, effective_from DESC)`.
- Partial index `salary_record (currency_code) WHERE effective_to IS NULL` for analytics/directory joins.

**Money:** `NUMERIC(15,2)` + `CHAR(3)` currency. Never `FLOAT`.

**History evolution:** new compensation types later = new columns or a `compensation_component` table pointing at `employee_id` with the same effective-dating pattern. Do **not** overwrite `amount` in place.

**No** read replica, no partitioning, no Timescale, no extra search engine. 10k rows + a few salary versions stay in the primary heap comfortably.

---

## 6. API architecture

**Style:** resource-oriented REST, JSON, prefix `/api/v1`. Version in path so a future breaking change can add `/v2` without a gateway product.

**Boundary**

| Area | Resources |
| --- | --- |
| Auth | `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` |
| Employees | `GET/POST /employees`, `GET/PATCH /employees/{id}` |
| Salaries | `GET/POST /employees/{id}/salaries` |
| Reference | `GET /departments` |
| Analytics | `GET /analytics/overview`, `.../compensation-by-currency`, `.../headcount-by-department`, `.../headcount-by-country`, `.../salary-bands?currencyCode=` |

**Query conventions**

- Pagination: `page` (0-based), `size` (default 25, max 100), `sort`.
- Directory filters: `q`, `departmentId`, `countryCode`, `status`, `currencyCode`, `minAnnual`, `maxAnnual`.
- `minAnnual` / `maxAnnual` **rejected** unless `currencyCode` is present.
- Analytics money payloads are **arrays keyed by currency**, never a single mixed total.

**Responses:** DTO records. List = `{ content, page, size, totalElements, totalPages }`.

**Idempotency:** GET/PATCH as usual. `POST .../salaries` is not idempotent (each call is a new event). Client does not retry blindly on 200-timeout; user re-reads history.

**No GraphQL, no BFF service, no public bulk export.**

---

## 7. Authentication / authorization

**Approach:** Spring Security + **HTTP-only session cookie** + **CSRF**, same origin via nginx.

| Topic | Choice | Why |
| --- | --- | --- |
| Who | HR users in `hr_user` | One persona |
| Password | BCrypt | Standard, enough for demo + real hash |
| Session | Servlet session, HTTP-only, `SameSite=Lax`, `Secure` in prod | Salary data must not sit in `localStorage` JWT |
| CSRF | Spring CSRF cookie; Angular interceptor copies token to header | Cookie session requires it |
| Roles | Single role `HR` | No RBAC matrix in MVP |
| Authorization | Authenticated ⇒ all employees visible | Single tenant |

**Login flow:** `POST /api/v1/auth/login` with JSON credentials → session cookie. `GET /auth/me` for bootstrap. Logout invalidates session.

**Not used in MVP:** OAuth2/OIDC, SAML, JWT access/refresh pair, API keys, method-level permissions per department.

**Dev vs prod:** Compose puts UI and API on one host name so cookies work. Local Angular proxy to API with `withCredentials` and matching cookie path.

---

## 8. Error handling

**API:** Spring `ProblemDetail` (RFC 7807) or a single `{ type, title, status, detail, fieldErrors[] }` shape. One `@ControllerAdvice` in `shared`.

| Situation | Status |
| --- | --- |
| Bad credentials | 401 (same body for unknown user and bad password) |
| Unauthenticated | 401 |
| Validation (Bean Validation + domain) | 422 |
| Salary range without currency | 422 |
| Unknown employee | 404 |
| Optimistic lock / second current salary | 409 |
| Uniqueness (email, employee number) | 409 |
| Unhandled | 500, generic message |

**Rules:** no stack traces to the client. Map `DataIntegrityViolationException` from the partial unique index to 409. Domain exceptions (`InvalidSalarySuccession`) stay out of controllers.

**UI:** interceptor maps 401 → login; 409/422 → field or snackbar; 500 → generic toast. Do not alert raw server JSON.

---

## 9. Validation

**Two layers, same rules.**

1. **Bean Validation** on request records (`@NotBlank`, `@Email`, `@Positive`, `@Size`, `@Pattern` for ISO country/currency). Fast fail at the controller.
2. **Domain / application validation** for rules that span rows: terminated ⇒ termination date; new `effective_from` ≥ previous; first salary required on create; salary range requires currency.

**Database** is the third backstop (checks, unique, partial unique). Java must not be the only guard.

**Frontend:** reactive-form validators for UX only. Never the authority.

Allowed country/currency lists live in one shared constant/enum on the server; seed and API use the same list.

---

## 10. Logging

**Goal:** operate and debug without leaking compensation or identity.

| Level | Use |
| --- | --- |
| INFO | Startup, seed completed (counts only), login success (`userId`, not email if we can avoid it — prefer opaque id) |
| WARN | 401 bursts, 409 conflicts, validation spikes |
| ERROR | Unhandled exceptions with request id |

**Never log:** password, session id, salary `amount`, full employee PII dumps, Authorization headers.

**Correlation:** one `X-Request-Id` generated at nginx or a filter; include in logs and error body.

**Access:** rely on Spring/nginx defaults; no ELK stack in MVP.

Actuator: `health` public to Compose; `info`/`env`/`heapdump` locked down.

---

## 11. Testing architecture

```
  Backend unit (no Spring/DB)          Backend integration
  - annualize, date succession         - Testcontainers PostgreSQL
  - range requires currency            - succession, unique current
                                       - pagination, analytics, auth 401

  Frontend unit (TestBed)
  - currency filter UX
  - dashboard does not total mixed currencies
```

**Must-have cases** (correctness, not coverage vanity):

- Second salary closes first; only one `effective_to IS NULL`.
- Backdated overlap rejected.
- Analytics fixture with USD + INR → two groups, **no** combined sum field.
- Directory does not return more than `size` rows; `totalElements` correct.
- Unauthenticated `GET /employees` → 401.

**Avoid:** mocking the repository so thoroughly that the partial unique index is never exercised; hitting a shared laptop Postgres with leftover seed data.

Frontend e2e (Playwright/Cypress) is optional for a single happy path after UI exists — not a gate for domain work.

---

## 12. Deployment architecture

```
+---------------------------------------------------------------+
|  Docker Compose (one host / one VM)                           |
|                                                               |
|   +-------------+     +----------------+     +-------------+  |
|   | web:nginx   |     | api:Java 21    |     | db:Postgres |  |
|   | :80         |     | :8080          |     | :5432       |  |
|   | static SPA  |     | Flyway then    |     | volume      |  |
|   | /api proxy  |---->| seed-if-empty  |---->|             |  |
|   +-------------+     +----------------+     +-------------+  |
+---------------------------------------------------------------+
```

**Startup:** Postgres healthy → API migrations → `SeedRunner` if `employee` count is 0 → nginx serves UI.

**Config:** environment only (`SPRING_DATASOURCE_*`, session secret, `SEED_HR_PASSWORD`). `.env.example` committed; real secrets not committed.

**Deploy target:** Compose on a VM or local demo. HTTPS at a reverse proxy if public. Health: `GET /actuator/health`.

**Not in the topology:** ingress controllers, service mesh, sidecars, multiple replicas of API (session is in-process; a second replica would need sticky sessions or Spring Session — **do not add** until a second instance is a real requirement).

---

## 13. Performance considerations

10,000 employees is **well within** one Postgres + one Spring Boot + paged Angular table.

| Risk | Mitigation |
| --- | --- |
| SPA downloads 10k rows | Server `LIMIT`/`OFFSET`; max page 100 |
| `findAll()` then stream in Java | Directory and analytics = SQL projections / `GROUP BY` |
| N+1 salaries on list | Join or subquery for **current** salary in one query |
| Slow seed | JDBC/`saveAll` batches of 500–1000 |
| Deep OFFSET | Acceptable at 10k; keyset later if needed |
| Count queries | `COUNT(*)` on 10k with indexes is cheap |

**Targets (local Compose):** directory p95 &lt; 200 ms; dashboard p95 &lt; 300 ms.

**Do not “fix” performance with Redis or ES first.** Measure. Add an index. Only then consider cache.

HikariCP defaults are enough for a handful of HR users.

---

## 14. Security considerations

Compensation is confidential. Treat the demo like an internal HR app.

- TLS in any shared/public deploy.
- Cookie session (HTTP-only); CSRF; no JWT in browser storage.
- BCrypt; generic login failures; disabled users cannot authenticate.
- Parameterized queries (JPA / named params).
- Angular escaping; no `innerHTML` for employee fields.
- Security headers from Spring Security defaults.
- CORS only for the known Angular origin in local split-mode; prod same-origin.
- No file uploads (no Excel parser attack surface).
- Secrets via env; default seed password documented and overridable.
- Logs without amounts or passwords (§10).
- Actuator locked except health.

Row-level security is unnecessary: every authenticated HR user may see all ACME employees.

---

## 15. Future scalability considerations

Design **leaves doors open** without paying for them now.

| If this happens | Then consider |
| --- | --- |
| 10× employees (100k) still one region, one app | Indexes, keyset pagination, maybe read-only replica |
| Many concurrent HR / managers | Stateless API + Spring Session (Redis) **or** sticky sessions — Redis earns its keep here |
| Employee self-service + managers | Real roles, row filters, maybe audit table |
| Nightly payroll export / integrations | Outbox table first; **Kafka only** if many consumers and volume justify a broker |
| Full-text across notes/docs | `pg_trgm` / Postgres FTS; Elasticsearch only if search product-izes |
| Multi-region active-active | Entirely different ops problem — not this app’s next step |
| Several teams shipping independently | Extract a module to its own service **after** a real team/boundary exists |

Salary history as rows already supports “pay as of date” and audit-ish timelines without event sourcing.

---

## Why we are **not** using certain infrastructure

These tools are useful in other systems. They are **not** justified by 10k employees, one HR persona, and one database.

### Microservices

**What they solve:** independent deploy, isolated data, multiple teams, different scale profiles.

**Why not:** one product, one team, one transaction (close old salary + insert new). Splitting `employee` and `compensation` would create distributed transactions or eventual consistency for a use case that must be atomic. Ops cost (many repos, many deploys, network failure modes) buys nothing at this size.

**When later:** a separate payroll engine or identity provider owned by another team, with a clear published API.

### Kafka

**What it solves:** durable pub/sub, fan-out to many consumers, peak decoupling.

**Why not:** no downstream consumers. Dashboard is a SQL read of the same DB that just committed. A raise does not need an event bus; it needs a transaction.

**When later:** many integrators (finance, BI, payroll bureau) and we outgrow a nightly export or transactional outbox polling.

### Redis

**What it solves:** shared cache, shared session, locks, rate lists.

**Why not:** working set is tiny. Postgres answers paged lists and aggregates in milliseconds. One API instance can hold servlet sessions. A cache would hide stale salary totals and add another failure mode.

**When later:** multiple API replicas need shared sessions, or a hot endpoint is measured slow after indexes.

### Kubernetes

**What it solves:** orchestration of many services, rolling deploys, autoscaling fleets.

**Why not:** three containers. Compose (or one VM + two processes) is the honest topology. K8s would dominate the assessment with YAML instead of product correctness.

**When later:** org standard platform, multiple apps, or real multi-instance HA requirements.

### Elasticsearch

**What it solves:** relevance search, logs, large unstructured corpora.

**Why not:** search is employee number, email, name, plus structured filters. Postgres `ILIKE` + indexes (and `pg_trgm` if needed) is enough. ES would duplicate PII and dual-write every hire.

**When later:** search across comments, documents, or org-wide free text with ranking needs Postgres cannot meet.

---

## ASCII architecture diagram (end-to-end)

```
                         +------------------------+
                         |      HR Manager        |
                         +-----------+------------+
                                     |
                                     | HTTPS (prod) / HTTP (local)
                                     v
                         +------------------------+
                         |  nginx  (web)          |
                         |  - Angular + Material  |
                         |  - TypeScript SPA      |
                         |  - /        -> static  |
                         |  - /api/v1  -> proxy   |
                         +-----------+------------+
                                     |
                                     | HTTP + session cookie + CSRF
                                     v
                         +------------------------+
                         |  Spring Boot API       |
                         |  Java 21  (one JVM)    |
                         |                        |
                         |  +------------------+  |
                         |  | Spring Security  |  |
                         |  | session + CSRF   |  |
                         |  +--------+---------+  |
                         |           |            |
                         |  REST controllers      |
                         |           |            |
                         |  +--------v---------+  |
                         |  | application      |  |
                         |  | services         |  |
                         |  | (transactions)   |  |
                         |  +--------+---------+  |
                         |           |            |
                         |  domain + Bean Valid.  |
                         |           |            |
                         |  Spring Data JPA       |
                         +-----------+------------+
                                     |
                                     | JDBC
                                     v
                         +------------------------+
                         |  PostgreSQL            |
                         |  department            |
                         |  employee              |
                         |  salary_record         |
                         |  hr_user               |
                         +------------------------+

     NOT in this picture (on purpose):
     microservices  |  Kafka  |  Redis  |  Kubernetes  |  Elasticsearch
```

---

## Design principles (recap)

1. **One deployable API, one database, one UI** — modular by package.
2. **10k is a data-volume problem for the UI, not a platform problem** — page and aggregate in SQL.
3. **Never combine money across currencies.**
4. **Salary history is rows**, so the model can grow without rewrite.
5. **Add infrastructure when a requirement appears**, not to look complete.

Implementation starts only after this document and the technical plan are accepted. No application code accompanies this file.
