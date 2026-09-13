# PayLens — final principal-engineer review

**Role:** Pre-submission review (read-only).  
**Date:** 2026-09-13  
**Scope:** Entire repository as inspected (backend, frontend, tests, Docker/CI, docs, Git history, product vs assessment).  
**Stance:** Evidence-based. No code changes in this pass.

---

## 1. Requirement checklist

### Assessment brief (`docs/assessment.md`)

| Requirement | Status | Evidence |
| --- | --- | --- |
| End-to-end software (backend + UI) | **Met** | Spring Boot API + Angular SPA |
| Relational DB | **Met** | PostgreSQL (+ H2 for tests) |
| UI framework (Angular allowed with Java) | **Met** | Angular 19 + Material |
| Seed ~10,000 employees | **Met** | Deterministic JDBC seed (`dev,seed` / Compose env) |
| Meaningful unit/integration tests | **Met** | ~98 backend tests; frontend service/unit specs |
| Fast, deterministic, readable tests | **Met** | Fixed clocks, H2, seed RNG=42 |
| Good structure / maintainability | **Met** | Feature packages, docs, Compose |
| Incremental Git history | **Met** | ~21 commits: docs → schema → APIs → seed → UI → analytics → security → perf → tests → Docker → CI → docs |
| Artifacts: requirements, architecture, trade-offs, AI, performance | **Met** | Under `docs/` |
| **Fully functional deployed software** | **Not met (blocker for “readiness”)** | README Demo placeholders only |
| **Video demo** | **Not met (blocker for “readiness”)** | Placeholder in README |
| Intentional AI use documented | **Met** | `docs/ai-usage.md` |

### Product requirements (`docs/requirements.md`)

| Requirement | Status | Notes |
| --- | --- | --- |
| Create / view / update / search employees | **Partial** | API supports create; **UI has no create-hire flow** |
| Current base salary: amount, currency, effective date | **Met** | `SalaryRequest` / UI update dialog |
| Pay frequency | **Not implemented** | Listed in requirements; absent from schema/API/UI |
| Optional reason on salary change | **Not implemented** | Absent from `Salary` / DTOs |
| Salary change with history | **Met (alternate model)** | Append/correct by `effective_from`; no `effective_to` “close” |
| Server-side pagination / search / filters | **Met** | Page ≤100; list filters (no currency filter in UI) |
| Dashboard analytics; never mix currencies | **Met** | SQL `GROUP BY currency`; UI currency lens |
| Seed 10k | **Met** | Opt-in |
| HR login | **Met** | Session cookie + env user |
| Deployable stack | **Met locally** | Compose works; public deploy TBD |

### Product questions (HR Manager)

| Question | Answer |
| --- | --- |
| Can HR manage employees? | **Mostly** — list, detail, edit, deactivate. **Cannot create** via UI. |
| Can HR manage salary? | **Yes** — current + history; update dialog; API upsert. |
| Can HR answer compensation questions? | **Yes** — dashboard per currency / country / dept / designation / distribution. |
| Is currency handling correct? | **Yes** — no cross-currency totals; documented note. |
| Does dashboard communicate quickly? | **Yes** — overview + charts + currency selector; Refresh supported. |

---

## 2. Architecture assessment

**Verdict:** Appropriate for the assessment. Strong engineering judgment.

**Strengths**

- Modular monolith with clear packages (`employee`, `salary`, `analytics`, `seed`, `security`).
- Hybrid data access: JPA for CRUD/list; JDBC for analytics and seed.
- Salary history as dated rows; current = latest `effective_from ≤ as-of`.
- `open-in-view: false`; Flyway-owned schema; CHECK/UNIQUE/FK.
- Compose: Postgres + API + nginx UI; CI without unused Postgres service (H2 MODE=PostgreSQL).
- Explicit non-goals (Kafka/Redis/K8s/LLM analytics) documented in trade-offs.

**Weaknesses**

- `DepartmentController` talks to repository directly (thin layering slip).
- Requirements doc mentions pay frequency/reason that the system never modeled — **docs/product drift**.
- Architecture/plan still mention `/employees/new` and CSRF interceptor; product shipped cookie session with CSRF disabled (security doc is accurate; some older plan text drifts).

**Overall:** Architecture is submission-quality. Product completeness (create UI + deploy/video) is the gap, not the shape of the system.

---

## 3. Security findings

| Severity | Finding |
| --- | --- |
| **Critical (ops)** | Default Spring profile is `dev` with **admin/admin** defaults if `PAYLENS_HR_*` unset. Mis-running without `prod` exposes known credentials. |
| **Critical (ops)** | Session cookie has **no `Secure` flag** in prod config — unsafe if TLS is expected at the app. |
| **Medium** | CSRF disabled with cookie sessions (documented trade-off; acceptable for assessment if called out). |
| **Medium** | Swagger/OpenAPI **permitAll** in all profiles — expands attack surface in a public deploy. |
| **Medium** | Prod still allows plaintext `PAYLENS_HR_PASSWORD` bootstrap (hash preferred but not enforced). |
| **Low** | H2 on runtime classpath (`pom.xml`). |
| **OK** | Deny-by-default beyond `/api/v1/**`; actuator health-only; no secret logging observed; `.env` gitignored; `.env.example` placeholders. |

Auth model (single env HR user + `PAYLENSSESSION`) matches assessment persona. Not enterprise IAM — and should not pretend to be.

---

## 4. Performance findings

**Verdict:** Fit for ~10k employees.

**Strengths**

- Server pagination; sort allow-list; EntityGraph + batch current-salary hydration.
- Analytics in SQL with currency grouping; indexes V2–V4.
- Seed JDBC batches with per-batch commits.
- SPA debounce + analytics TTL cache; list OnPush.

**Residual risks (non-blocking at 10k)**

- Leading-wildcard search (full scan — OK at 10k).
- Analytics CTE recomputed per endpoint (SPA cache mitigates).
- Seed mid-failure → partial DB stuck until truncate (**ops medium**).
- Analytics cache not cleared after salary/employee mutations (stale up to 60s unless Refresh).

---

## 5. Testing findings

**Verdict:** Strong business-logic coverage for an assessment.

**Strengths**

- Employee lifecycle (incl. API IT), salary rules, currency-safe analytics, security IT, schema constraints, seed determinism.
- Tests are deterministic (fixed clocks, H2, fixed seed).
- CI runs `mvn verify` + frontend `test:ci` + production build.

**Gaps**

- No automated UI/e2e (create flow would have caught missing hire screen).
- Frontend tests thin vs backend (services/insights; little component coverage).
- No Testcontainers Postgres (accepted trade-off; H2 MODE=PostgreSQL).

---

## 6. UX findings

**Strengths**

- Coherent shell, login, dashboard currency lens, employee list/detail, edit/salary/deactivate dialogs.
- Error states with retry; loading indicators; handset sidenav.
- Deactivate confirmation; terminated state disables re-deactivate.

**Gaps**

- **No “Add employee” / hire UI** despite `EmployeeService.create()`.
- List filters not reflected in URL (share/refresh loses context).
- No currency filter on directory (API supports `currency`).
- Nested row link + View control (keyboard/SR awkwardness).
- Charts canvas-only (limited non-visual access).
- Authenticated user can still open `/login`.

---

## 7. Critical issues

1. **No create-employee UI** — HR cannot onboard hires in the product UI; contradicts requirements “create… employees” and own technical plan `/employees/new`. API exists unused by UI.
2. **Assessment readiness: no public deploy URL** — brief requires fully functional deployed software; README still has `_TODO_` placeholder.
3. **Assessment readiness: no demo video URL** — brief requires a video demo; placeholder only.
4. **Ops footgun: `dev` + admin/admin defaults** — dangerous if someone runs the jar without `SPRING_PROFILES_ACTIVE=prod`.
5. **Prod session cookie missing `Secure`** — if the deploy is HTTPS-facing without terminating cookie rewrite, session may be sent over HTTP.

*(Items 2–3 are submission-process blockers more than code defects; items 1, 4–5 are product/security.)*

---

## 8. Medium issues

1. Requirements list **pay frequency** and **salary reason** — not in schema/API/UI (doc/product mismatch). Align docs or implement minimally.
2. CSRF off with cookie auth (documented; still a finding for hostile-browser prod).
3. Public Swagger in prod profile.
4. Analytics/department caches never invalidated after writes.
5. Seed partial failure leaves DB in “skip forever” state until truncate.
6. Directory: no URL-synced filters; no currency filter in UI.
7. `.env.example` currently **untracked** in Git status — Compose onboarding docs assume it is present in the repo.
8. Uncommitted `README.md` changes — ensure submission branch includes the professional README.
9. `DepartmentController` bypasses service layer.
10. Nested interactive controls on employee table rows (a11y).

---

## 9. Nice-to-have issues

1. OnPush only on employee list; dashboard/detail default CD.
2. No guest redirect away from `/login` when already authenticated.
3. Money display inconsistently pairs currency in every cell.
4. Chart textual alternative / data table.
5. Enforce `effectiveFrom >= joiningDate`.
6. Keyset pagination / denormalized current salary (future scale).
7. Remove H2 from runtime scope; restrict Swagger to non-prod.
8. Frontend component tests for login/list critical paths.

---

## 10. Final recommendation

**Ship readiness for “engineering assessment of judgment + working software”:** **Conditional GO.**

The system demonstrates clear thinking, appropriate architecture, solid backend tests, currency-safe analytics, security-aware auth for a single HR persona, performance discipline at 10k, Docker/CI, and strong written artifacts. Git history is incremental and readable.

**Do not claim “complete assessment readiness” until:**

1. HR can **create** an employee (with initial salary) in the UI, **or** requirements are explicitly revised to drop create-from-UI (not recommended — API already supports it).  
2. **Deployed app URL** and **demo video URL** are filled in (or the submission channel provides them another way).  
3. Prod/runtime defaults are tightened enough that a careless start is not `admin`/`admin` on a public host, and HTTPS deploys set **Secure** cookies (or document TLS termination rewriting cookies).

Everything else is polish relative to those three.

---

## Issues worth fixing before submission

Prioritized for **maximum assessment impact / minimum scope**. Still **not fixing in this pass**.

| Priority | Issue | Why fix before submit | Effort |
| --- | --- | --- | --- |
| **P0** | Create-employee UI (+ route) wired to existing API | Closes the largest product hole vs HR Manager story | Medium |
| **P0** | Fill Demo placeholders (deploy + video) | Explicit assessment “Readiness” | Process (not code) |
| **P0** | Ensure `README.md` + `.env.example` committed | Reviewers must run Compose from clone | Trivial |
| **P1** | Prod: require non-dev profile / no admin defaults in images; cookie `secure` when appropriate | Avoids embarrassing security footgun on deploy | Small |
| **P1** | Align `docs/requirements.md` with reality (drop or implement frequency/reason) | Stops reviewers marking “missing fields” | Small (prefer doc align) |
| **P2** | Invalidate analytics cache after salary/employee mutations | Dashboard honesty after edits | Small |
| **P2** | Restrict Swagger in `prod` (or document “demo only”) | Cleaner public surface | Small |
| **Skip for now** | URL filter state, currency list filter, a11y polish, CSRF tokens, Testcontainers, OnPush everywhere | Nice; not submission-blocking given time | — |

**Recommended pre-submit sequence:** commit docs/env example → add hire UI → harden prod cookie/profile → record demo + deploy → paste URLs into README.

---

*End of review. No code was modified.*
