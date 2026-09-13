# PayLens engineering trade-offs

Major decisions for the ACME HR salary system (~10k employees, single organization, HR Manager persona). Grounded in what the repo actually ships: a **Spring Boot modular monolith**, **PostgreSQL** (H2 only in tests), **Angular SPA**, **Docker Compose**, session-cookie auth, and **SQL analytics**—not invented platforms.

Related: [architecture.md](./architecture.md), [security.md](./security.md), [performance.md](./performance.md), [database-design.md](./database-design.md).

---

## 1. Modular monolith vs microservices

| | |
| --- | --- |
| **Decision** | One deployable API (`paylens-api`) with package modules (`employee`, `salary`, `analytics`, `seed`, `security`). Separate **runtime** containers for web / api / db only. |
| **Alternatives** | Employee / compensation / analytics microservices; API gateway; event-driven sync between services. |
| **Why chosen** | One persona, one tenant, one database of record. In-process calls avoid distributed transactions for “create employee + first salary.” Assessment timeline favors a coherent codebase over service mesh overhead. |
| **Trade-offs** | Modules can still couple if discipline slips; cannot scale analytics CPU independently of CRUD. Deployment is all-or-nothing for the API. |
| **Future** | Extract analytics to a read replica or separate process **if** reporting load dominates; only then consider service boundaries with an explicit integration contract. |

---

## 2. PostgreSQL vs SQLite

| | |
| --- | --- |
| **Decision** | **PostgreSQL 16** for local Docker / prod profile. **H2 `MODE=PostgreSQL`** only for automated tests (`application-test.yml`). |
| **Alternatives** | SQLite file DB; H2 for everything including Compose. |
| **Why chosen** | Need concurrent access, `NUMERIC` money, CHECK/UNIQUE/FK, Flyway migrations, and analytics (`GROUP BY`, percentiles, indexes). Compose already runs Postgres. Tests stay fast without a CI Postgres service. |
| **Trade-offs** | Operators must run Postgres (or Compose). H2 is not a perfect Postgres substitute—some SQL dialect quirks remain, so critical SQL is also exercised against real Postgres via Docker. |
| **Future** | Managed Postgres (RDS/Cloud SQL) with the same Flyway scripts; keep H2 for unit/IT speed unless dialect drift forces Testcontainers Postgres. |

---

## 3. JPA vs direct SQL

| | |
| --- | --- |
| **Decision** | **Hybrid:** Spring Data JPA + Specifications for employee CRUD/list; **JdbcTemplate** for analytics; **JDBC batch** for seed. |
| **Alternatives** | JPA everywhere (entities + Criteria for aggregates); jOOQ / MyBatis for all access; raw JDBC only. |
| **Why chosen** | CRUD benefits from entities, validation, and EntityGraph for department. Analytics need controlled SQL over “current salary” without loading 10k entities. Seed needs bulk inserts without persistence-context bloat. |
| **Trade-offs** | Two access styles to maintain; analytics SQL must stay aligned with salary “current” semantics in JPA services. |
| **Future** | Optional query projections for list DTOs; keep aggregates in SQL. Avoid moving analytics into entity loops. |

---

## 4. Separate salary table vs salary columns on employee

| | |
| --- | --- |
| **Decision** | `salary` table: `employee_id`, `annual_salary`, `currency`, `effective_from`, unique `(employee_id, effective_from)`. Employee has **no** embedded current-pay columns. |
| **Alternatives** | `employee.annual_salary` + `currency` only; JSON history blob on employee; EAV pay components. |
| **Why chosen** | Assessment requires history and “current as of date.” Separate rows preserve raises without overwriting. List/detail hydrate **current** salary via query (`findCurrentByEmployeeIds` / latest ≤ today). |
| **Trade-offs** | Every list page needs a salary lookup (batched, not N+1). Filters by currency touch salary. Slightly more schema than a denormalized “current only” column. |
| **Future** | Optional denormalized `current_annual_salary` / `current_currency` on `employee` updated on salary write—if list/filter hotspots demand it (`docs/performance.md`). |

---

## 5. Salary history

| | |
| --- | --- |
| **Decision** | History = many dated `salary` rows. **Current** = latest `effective_from ≤ as-of` (usually today). Upsert: same date → correct in place; new date → append. Future rows exist but are not current yet. |
| **Alternatives** | Single mutable salary; `effective_to` ranges; event-sourced compensation ledger. |
| **Why chosen** | Matches HR mental model (raise on a date). Unique constraint blocks two rows for the same day. Analytics and list share one definition of current. |
| **Trade-offs** | No closed intervals—overlap is prevented by uniqueness of start dates, not by `effective_to`. Deep history grows row count (seed keeps history shallow: ~1–2 rows/employee). |
| **Future** | Paginate history API if careers accumulate many corrections; audit who changed what; optional `effective_to` if legal needs closed intervals. |

---

## 6. Server-side pagination

| | |
| --- | --- |
| **Decision** | `GET /employees` returns Spring Data `Page` (default size **25**, max **100**). UI uses Material paginator + debounced search. No client-side load of all 10k. |
| **Alternatives** | Infinite scroll dumping large windows; client filter over full export; GraphQL cursor API. |
| **Why chosen** | Directory UX needs totals and stable pages. Clamp + sort whitelist protect the DB. Angular never holds the org in memory. |
| **Trade-offs** | Each page runs content + COUNT. Deep `OFFSET` is fine at 10k but would degrade at much larger scales. |
| **Future** | Keyset/`seek` pagination for deep pages; optional “export CSV” async job instead of huge page sizes. |

---

## 7. Database-level analytics

| | |
| --- | --- |
| **Decision** | `AnalyticsRepository` builds a **current-salary CTE** (max `effective_from` join) then `GROUP BY` / `PERCENTILE_CONT` / bucket `CASE` in SQL. SPA charts call dedicated endpoints (with short TTL cache). |
| **Alternatives** | `findAll` + stream aggregates in Java; embed OLAP/Cube; precomputed nightly warehouse. |
| **Why chosen** | Correct money math stays in `NUMERIC`; one DB pass beats heap aggregation; matches indexes (V3/V4). Avoids accidental currency mixing in application code. |
| **Trade-offs** | Analytics SQL is less “ORM-pretty.” Multiple endpoints recompute the CTE (mitigated by SPA cache; overview counts combined). |
| **Future** | Materialized view or Redis TTL under many concurrent HR users; single `/analytics/dashboard` bundle endpoint. |

---

## 8. Currency handling

| | |
| --- | --- |
| **Decision** | Store ISO currency on each salary row (`CHAR(3)` + CHECK). **Never** sum/average across currencies. All compensation analytics return rows keyed by currency (and dimension). UI selects one currency for charts. |
| **Alternatives** | Convert everything to USD in API; store only major currency; ignore currency in aggregates. |
| **Why chosen** | ACME pays in multiple countries. Without an FX feed, a blended “total payroll” is wrong and dangerous for HR. Headcount-by-country remains safe (no money). |
| **Trade-offs** | Dashboard needs a currency selector; HR cannot see one global average without FX policy. |
| **Future** | Optional FX service with rate date + audit if leadership demands a reporting currency—never silent conversion. |

---

## 9. Soft deletion / deactivation

| | |
| --- | --- |
| **Decision** | `DELETE /employees/{id}` → set `employment_status = TERMINATED` (soft). No hard delete of employees. Analytics compensation set = `ACTIVE` + `ON_LEAVE` only. |
| **Alternatives** | Hard `DELETE` with `ON DELETE CASCADE` on salary; separate `deleted_at` flag; archive tables. |
| **Why chosen** | Preserves salary history and directory audit. Status CHECK already models `ACTIVE` / `ON_LEAVE` / `TERMINATED`. Matches HR “left the company” rather than “erase person.” |
| **Trade-offs** | Terminated rows still occupy storage and appear when status filter allows. Must remember to exclude them from pay aggregates (tests cover this). |
| **Future** | GDPR-style anonymize job; optional hard-delete after retention window with explicit admin action. |

---

## 10. Authentication approach

| | |
| --- | --- |
| **Decision** | Spring Security: **one HR Manager** from env (`PAYLENS_HR_USERNAME` + password hash or bootstrap password). **HTTP session** cookie `PAYLENSSESSION` (HttpOnly, SameSite=Lax). CSRF **disabled** for the JSON SPA (documented in `docs/security.md`). |
| **Alternatives** | OAuth2/OIDC; JWT in `localStorage`; form login + full CSRF; DB-backed multi-user directory. |
| **Why chosen** | Assessment needs protection of APIs without IdP complexity. Cookie + same-origin nginx avoids storing tokens in JS. Single persona matches “HR Manager” brief. |
| **Trade-offs** | CSRF must be revisited for hostile-network production browsers; in-memory user is not multi-admin; session affinity matters if API replicas appear later. |
| **Future** | Corporate IdP (OIDC); CSRF cookie/header pair for cookie sessions; optional Redis/JDBC session store for multi-instance API. |

---

## 11. Why no Kafka

| | |
| --- | --- |
| **Decision** | No message bus. Writes are synchronous request/response inside one DB transaction boundary. |
| **Alternatives** | Salary-changed events; CDC to analytics; async seed. |
| **Why chosen** | No external consumers, no multi-service sync, no peak ingest beyond opt-in seed. Kafka would add ops cost without a product requirement. |
| **Trade-offs** | Cannot fan out compensation changes to other ACME systems later without adding a bus or webhooks. |
| **Future** | Introduce events only when a second bounded context (payroll bureau, benefits) must react asynchronously. |

---

## 12. Why no Redis

| | |
| --- | --- |
| **Decision** | No Redis. Session is servlet memory; analytics caching is a **short TTL in the Angular service**, not a shared cache. |
| **Alternatives** | Redis session store; Redis cache for overview aggregates; rate-limiting store. |
| **Why chosen** | Single HR user, one API instance in Compose. Extra cache tier obscures correctness and complicates Docker for little gain at 10k with SQL aggregates. |
| **Trade-offs** | Session lost on API restart; concurrent HR users would each re-hit SQL (still fine at small concurrency). |
| **Future** | Redis (or similar) if multiple API replicas or many concurrent dashboard users need shared sessions / aggregate TTL. |

---

## 13. Why no Kubernetes

| | |
| --- | --- |
| **Decision** | **Docker Compose** for local/demo runtime (Postgres + API + nginx UI). CI is GitHub Actions only—no cluster deploy. |
| **Alternatives** | Minikube/kind manifests; Helm; serverless API. |
| **Why chosen** | Assessment asks for a runnable system, not platform engineering. Compose matches three processes, healthchecks, and volumes without cluster complexity. |
| **Trade-offs** | No rolling deploys, autoscaling, or multi-AZ story in-repo. |
| **Future** | Lift the same images into Kubernetes/ECS when ops requirements appear; keep Compose for developer experience. |

---

## 14. Why no LLM-based analytics in MVP

| | |
| --- | --- |
| **Decision** | Dashboard insights are **deterministic** (SQL aggregates + small rule-based copy in the UI). No LLM calls for “explain payroll” or NL queries. |
| **Alternatives** | Chat-over-SQL; LLM summaries of compensation; embedding search over employees. |
| **Why chosen** | Compensation data is sensitive; LLM answers can hallucinate totals or mix currencies. Assessment values correct aggregates and intentional AI *in development*, not opaque AI *in the product path*. Deterministic metrics are testable. |
| **Trade-offs** | No natural-language exploration; insights are limited to coded rules. |
| **Future** | If NL query is required: grounded tool-calling over the same SQL APIs with strict currency rules, human-visible citations of query results—never free-form invented numbers. |

---

## Summary table

| Topic | Choice in PayLens |
| --- | --- |
| Shape | Modular monolith + Compose |
| Database | PostgreSQL (H2 in tests) |
| Access | JPA CRUD + JDBC analytics/seed |
| Pay model | Separate `salary` history table |
| Directory | Server-side page ≤100 |
| Analytics | SQL, per currency |
| Leavers | Soft `TERMINATED` |
| Auth | Env HR user + session cookie |
| Not in MVP | Kafka, Redis, Kubernetes, LLM analytics, FX |

These trade-offs optimize for **correct compensation semantics**, **assessment-scale simplicity**, and **verifiable behavior**—not for hypothetical hyperscale.
