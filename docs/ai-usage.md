# AI usage — PayLens

**Purpose:** Document intentional use of AI tooling during the PayLens assessment, in line with the expectation to *use AI while remaining responsible for architecture, correctness, and quality*.

**Positioning:** AI was treated as an **engineering accelerator and reviewer**, not as an autonomous author of the system. The developer retained ownership of product scope, architecture, security posture, performance trade-offs, test strategy, and final merge decisions. AI suggestions were accepted only after review against requirements, scale (≈10k employees), and runnable verification.

---

## 1. Why AI tools were used

| Reason | How it applied to PayLens |
| --- | --- |
| **Speed with structure** | Turn assessment constraints into reviewable plans and docs before large code changes. |
| **Breadth of review** | Surface N+1 risks, missing indexes, auth gaps, and test holes across backend + frontend faster than unaided scanning alone. |
| **Boilerplate reduction** | Draft Spring Security wiring, Angular guards/interceptors, Dockerfiles, and CI YAML once intent was clear. |
| **Adversarial QA** | Ask for “strict senior” style reviews that challenge happy-path assumptions. |
| **Documentation quality** | Keep `docs/` coherent with the code that actually shipped. |

AI was **not** used to invent scope (no microservices, Kafka, Redis, FX conversion, multi-tenant IdP) or to bypass understanding of salary semantics (current vs history, currency isolation).

---

## 2. Types of tasks AI assisted with

| Category | Examples in this repo |
| --- | --- |
| **Planning & architecture** | Modular monolith packaging; salary as dated rows; analytics partitioned by currency; Compose topology. |
| **Feature implementation** | Session-cookie HR auth; employee CRUD; salary upsert/history; dashboard analytics APIs; Angular list/dashboard/login. |
| **Performance review** | List salary hydration, currency-filter SQL shape, indexes, seed transaction batching, SPA analytics caching. |
| **Security review** | Public vs protected routes; secrets via env; CSRF trade-off documentation; BCrypt vs plaintext bootstrap pitfalls. |
| **Test design** | Meaningful employee/salary/analytics/security/DB constraint tests (not coverage padding). |
| **Ops packaging** | Multi-stage Dockerfiles, Compose healthchecks, `application-prod.yml`, GitHub Actions CI. |
| **Debugging** | Login failures from treating plaintext as a BCrypt hash; Angular shell `@if` async pitfall; Docker port conflicts. |

---

## 3. Representative prompts

The following are **representative** of prompts used (paraphrased for clarity; wording varied by turn). They illustrate intent, not a claim that every line of code came from a single prompt.

### Architecture review

> Review PayLens as a modular monolith for ~10k employees. Push back on microservices, Kafka, Redis, and Elasticsearch unless justified. Confirm salary history modeling, currency-safe analytics, and session-cookie auth behind same-origin nginx.

### SQL / performance review

> Production-readiness performance review of PayLens at ~10k employees. Look for N+1 queries, loading full employee sets, bad pagination, missing indexes, inefficient analytics CTEs, seed batching, and frontend refetch/debounce issues. Do not optimize things that do not matter. For each change: problem, change, benefit, trade-off.

### Security review

> Add lightweight Spring Security for a single HR Manager from env vars. Prefer BCrypt password hash; allow local password bootstrap only. Protect `/api/v1/**`, keep health and login public. Document CSRF trade-offs for the SPA assessment.

### Test generation (strict QA)

> Act as a very strict senior QA engineer. Do not generate random tests for coverage. Identify important business behaviors for employee, salary, analytics, security, and DB constraints. Prefer deterministic, isolated, meaningful tests. Use integration tests only where they provide value.

### Code review / edge cases

> Review the employee list path and currency filter. Identify edge cases: future-dated salary rows, terminated employees in analytics, duplicate `(employee_id, effective_from)`, page-size abuse, unknown sort properties.

### Containerization / CI

> Containerize PayLens with Docker Compose (Postgres, API, Angular). No secrets in Git. Healthchecks; backend waits for DB. Multi-stage builds; non-root where practical. Then add GitHub Actions CI for PRs and pushes: Maven verify + Angular test and production build. No deploy.

---

## 4. How generated code was reviewed

Before accepting AI output, changes were checked against:

1. **Assessment and persona** — HR Manager workflows only; no invented enterprise features.
2. **Existing patterns** — package layout, DTO shapes, exception handling, Angular standalone + Material conventions.
3. **Correctness of domain rules** — current salary = latest `effective_from ≤ today`; analytics exclude terminated where specified; never blend currencies.
4. **Diff hygiene** — reject drive-by refactors unrelated to the request (surgical changes).
5. **Security** — no committed secrets; env-driven credentials; session cookie flags; public route allowlist.
6. **Operability** — Flyway as schema source of truth; Compose/CI match how the app is actually run.

Review was **human-led**: AI drafts were treated as proposals until they survived reading and verification.

---

## 5. How correctness was verified

| Layer | Verification |
| --- | --- |
| **Automated tests** | Backend `mvn test` / `mvn verify`; frontend `npm run test:ci`. |
| **Manual / runtime** | Local API + UI flows (login, list, salary update, dashboard); Docker Compose smoke (health, login cookie through nginx, authenticated list). |
| **Config** | Prod profile requires env secrets; test profile uses known bootstrap credentials; H2 PostgreSQL mode for CI-speed integration tests. |
| **Docs ↔ code** | `docs/security.md`, `docs/performance.md`, `README.md` updated when behavior or ops changed. |

When verification failed (e.g. login rejecting valid credentials after a plaintext value was placed in `hr-password-hash`), the root cause was diagnosed and fixed before moving on—not papered over with more generation.

---

## 6. How tests validated AI-generated changes

AI-assisted changes were expected to land with **tests that encode business behavior**, not implementation trivia.

Examples:

- **Employee** — create/update, duplicates, validation, search/filter/pagination, not-found, deactivate (service + API integration).
- **Salary** — upsert append vs same-date correct, negative/invalid currency, history order, effective-date “current”, missing employee.
- **Analytics** — currency isolation, country payroll, department/designation aggregation, distribution bands, empty dataset.
- **Security** — unauthenticated 401; authenticated HR access; session login/logout.
- **Database** — unique codes/emails, salary effective-date uniqueness, positive salary CHECK.
- **Performance-related** — current-salary batch query, currency filter using latest salary only, sort whitelist.

When AI proposed large test files or speculative mocks, those were **trimmed or rewritten** so each test still answered: *what business rule would break if this failed?*

CI (`.github/workflows/ci.yml`) re-runs the same gates on every push/PR so AI-assisted diffs cannot merge green locally and fail silently later.

---

## 7. Examples where AI suggestions were rejected or modified

| Suggestion / draft | Decision | Why |
| --- | --- | --- |
| Microservices / Kafka / Redis / K8s for “scale” | **Rejected** | Out of scope for 10k employees and assessment timeline; modular monolith is enough. |
| JWT in `localStorage` as default auth | **Rejected** (cookie session chosen) | Compensation data; prefer HttpOnly session cookie; document CSRF trade-off explicitly. |
| Enable CSRF fully for SPA without bootstrap UX | **Deferred / documented trade-off** | Cookie CSRF done correctly needs token plumbing; assessment chose documented disable + SameSite=Lax. |
| Put plaintext password into `hr-password-hash` defaults | **Rejected / fixed** | Hash field must be BCrypt; plaintext belongs only in bootstrap `hr-password`. |
| Virtual scroll / Redis analytics cache / list DTO micro-projection | **Deferred** | Not material at page size ≤100 and single-HR workload (performance doc). |
| Random high-volume unit tests for getters | **Rejected** | Assessment QA brief: meaningful behaviors only. |
| Postgres service in GitHub Actions for every job | **Rejected** | Existing ITs use H2 `MODE=PostgreSQL` + Flyway; unused Postgres would slow CI without benefit. |
| Host port `8080` hard-coded for Compose UI | **Modified** | Port conflict locally; `FRONTEND_PORT` / CORS origin made configurable (default 8088 in local `.env.example`). |
| Over-broad frontend `.dockerignore` excluding `nginx.conf` | **Fixed** | Would break the image build; keep config in context. |

These rejections are intentional evidence that **AI output is negotiable**, not authoritative.

---

## 8. Security and privacy considerations when using AI

| Practice | Application |
| --- | --- |
| **No production secrets in prompts or Git** | HR passwords and DB credentials live in `.env` (gitignored); only `.env.example` placeholders are committed. |
| **Test-only credentials** | `application-test.yml` uses explicit test bootstrap secrets, not production values. |
| **Minimize sensitive data in chat** | Prefer synthetic ACME/`@acme.test` fixtures; avoid pasting real employee PII or live payroll extracts into prompts. |
| **Review auth and CORS carefully** | AI-drafted security config checked for public allowlists, role checks, and cookie name consistency (`PAYLENSSESSION`). |
| **Treat model output as untrusted** | Especially SQL, Docker `USER`, and dependency pins—verify before ship. |
| **Document trade-offs** | CSRF disable for SPA assessment is written in `docs/security.md` so reviewers are not surprised. |

---

## 9. Engineering decisions that remained human-owned

The following were **not delegated** to the model as final authority:

1. **Product scope** — HR Manager persona; in/out list from the assessment; no payroll run or FX.
2. **Architecture style** — modular monolith, single PostgreSQL, Angular SPA, Compose for runtime.
3. **Salary model** — dated salary rows; “current” defined by as-of date; unique `(employee_id, effective_from)`.
4. **Analytics integrity** — all money metrics grouped by currency; no blended totals.
5. **Auth approach** — single in-memory HR user from env; session cookie; assessment-appropriate CSRF stance.
6. **Performance bar** — what matters at 10k vs speculative optimization.
7. **Test philosophy** — business-critical scenarios over coverage theater.
8. **Ship gates** — Docker health model, CI jobs, what “done” means for the assessment.

AI accelerated drafting and cross-cutting review; **accountability for the system remains with the developer**.

---

## Summary

PayLens was built in an **AI-assisted** workflow: plans and reviews first, then implementation, then tests and ops packaging. AI increased throughput on boilerplate and review surface area. Human judgment constrained scope, enforced domain rules, rejected unsafe or overbuilt suggestions, and required automated + runtime proof before considering work complete.

Related docs: [assessment.md](./assessment.md), [architecture.md](./architecture.md), [security.md](./security.md), [performance.md](./performance.md), [README.md](../README.md).
