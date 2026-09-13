# Performance

Production-readiness notes for PayLens at the assessment workload of **~10,000 employees**.

## Current architecture

| Layer | Shape |
| --- | --- |
| Backend | Spring Boot modular monolith, PostgreSQL, Flyway, JDBC analytics, JPA for CRUD |
| Frontend | Angular SPA, Material table, Chart.js dashboard, session-cookie auth |
| Data | `department` (~10) → `employee` (~10k) → `salary` (~1–2 rows/employee history) |
| Open-in-view | Disabled (`spring.jpa.open-in-view: false`) — no lazy surprises after commit |

Employee directory is **server-paginated**. Analytics are **SQL aggregations** over “current salary” (latest `effective_from <= today` for employed staff). Seed is **opt-in JDBC batch**, not JPA entity loops.

## Expected workload

| Path | Pattern | Volume |
| --- | --- | --- |
| Employee list | Page 25 (max 100), filters + sort | Dominant HR read path |
| Employee detail | 1 employee + salary history | Few rows of history |
| Dashboard | 6 analytics GETs (cached in SPA) | Burst on visit / refresh |
| Writes | Occasional create/update/salary | Low vs reads |
| Seed | One-time / CI | 10k employees + ~12.5k salaries |

Assumptions for this doc: single HR user, one API instance, Postgres on localhost or small managed DB, history depth stays shallow (seed ≈ 1–2 rows per employee).

## Database indexing strategy

| Index | Why |
| --- | --- |
| `uq_employee_code`, `uq_employee_email` | Integrity + lookup |
| `uq_salary_employee_effective_from` | History uniqueness; supports “latest ≤ date” per employee |
| `idx_employee_country`, `idx_employee_department_id`, `idx_employee_designation`, `idx_employee_employment_status` | Common filters |
| `idx_employee_status_country` | Analytics headcount / employed filters |
| `idx_salary_currency`, `idx_salary_effective_from` | Analytics / as-of filtering |
| **`idx_employee_last_name`** (V4) | Default list sort `lastName ASC` |
| **`idx_employee_status_last_name`** (V4) | Default browse: ACTIVE + name order |

**Intentionally not indexed for 10k:** leading-wildcard `LIKE %term%` search (would need `pg_trgm` GIN). At 10k rows a sequential scan stays cheap; revisit if concurrency or row count grows a lot.

**Constraints already present:** NOT NULL, CHECKs (status, currency, positive salary, email shape), FKs. `department.name` is not unique (resolve-by-name is app convenience only).

## Pagination strategy

- Default page size **25**, hard clamp **100** (`EmployeeService`).
- Spring Data `Page` → content query **+ COUNT** (UI needs total for Material paginator).
- Sort whitelist: `lastName`, `firstName`, `employeeCode`, `joiningDate`, `country`, `designation`, `employmentStatus`, `department.name`. Unknown sorts fall back to `lastName`.
- List hydrates **current salary only** via `SalaryRepository.findCurrentByEmployeeIds` (not full history per page).
- Currency filter uses an **anti-join** (“no later salary ≤ as-of”) instead of nested `MAX` + `EXISTS`.

Deep `OFFSET` is acceptable at 10k. Keyset pagination is a future option if pages go deep under heavier load.

## Analytics strategy

- All money metrics stay **grouped by currency** (never blended).
- “Current salary” CTE: `MAX(effective_from)` join (portable, index-friendly) rather than window `ROW_NUMBER` over every salary row.
- Overview headcounts use **one** SQL scan (`EmployeeCounts`) plus compensation-by-currency.
- Separate chart endpoints remain (clear API surface for the assessment). The SPA **caches** them for 60s so dashboard re-entry does not re-fire six aggregates.
- Explicit Refresh on the dashboard uses `forceRefresh` to bypass cache.

## Frontend strategy

| Concern | Approach |
| --- | --- |
| Search / designation | Already `debounceTime(350)` + `distinctUntilChanged` |
| Departments | `shareReplay` cache in `DepartmentService` |
| Analytics | 60s TTL + `shareReplay`; Refresh forces reload |
| Employee table | `ChangeDetectionStrategy.OnPush` + `markForCheck` after fetches |
| Virtual scroll | Not used — page ≤ 100 rows is enough at this scale |
| Auth | `/me` only when session signal empty |

## Optimizations applied (this review)

### 1. List loaded full salary history for the page

- **Problem:** `findByEmployee_IdIn` + Java “pick max ≤ today” pulled every history row for page members.
- **Change:** JPQL `findCurrentByEmployeeIds` (max `effectiveFrom` correlated once per row).
- **Benefit:** Fewer rows over the wire from DB as history grows; less heap on list.
- **Trade-off:** Slightly richer SQL; still O(page size), not O(org).

### 2. Currency filter used nested MAX + EXISTS

- **Problem:** Correlated subqueries on both page and COUNT queries when `?currency=` set.
- **Change:** Anti-join form: current row = matching currency with no later `effective_from ≤ asOf`.
- **Benefit:** Clearer plan shape; uses `(employee_id, effective_from)` uniqueness well.
- **Trade-off:** Still correlated EXISTS (inherent to Criteria filter); denormalized `current_currency` would be faster if this filter becomes hot.

### 3. Missing indexes for default sort

- **Problem:** Default sort `lastName` had no supporting index.
- **Change:** Flyway `V4__list_sort_indexes.sql`.
- **Benefit:** Cheaper ORDER BY / filter+sort for directory browse.
- **Trade-off:** Slightly higher write cost on employee inserts/updates (negligible at 10k).

### 4. Analytics CTE ranked every salary row

- **Problem:** `ROW_NUMBER() … PARTITION BY employee_id` scanned/ranked all in-scope history.
- **Change:** `GROUP BY employee_id` max date, then join back to `salary` + `employee`.
- **Benefit:** Less work when history deepens; H2-test friendly.
- **Trade-off:** Two CTE steps instead of one window; measure with `EXPLAIN` if salary rows grow 10×+.

### 5. Overview issued three DB round-trips for counts

- **Problem:** `countAll` + `countEmployed` + compensation query.
- **Change:** Single `employeeCounts()` query.
- **Benefit:** One fewer round-trip on every overview.
- **Trade-off:** None meaningful.

### 6. Seed held one giant transaction + flattened all salary rows

- **Problem:** `@Transactional` around entire 10k insert; salary insert built a full intermediate list.
- **Change:** `TransactionTemplate` commit per batch; stream salaries into a fixed-size buffer.
- **Benefit:** Smaller WAL/lock window; lower peak heap during salary insert.
- **Trade-off:** Partial seed possible on crash mid-run (guard already refuses non-empty DB — truncate to retry).

### 7. Create/update re-fetched employee after save

- **Problem:** Extra `findById` + current-salary lookup after entities already in hand.
- **Change:** Map response from managed entity + `Salary` returned by `apply` / `findCurrent`.
- **Benefit:** Fewer queries on write path.
- **Trade-off:** Response omits a second DB round-trip’s “fresh” timestamps if listeners mutate them (none today).

### 8. Unbounded client sort properties

- **Problem:** Arbitrary `sort=` could force odd joins or errors.
- **Change:** Whitelist in `EmployeeService.clamp`.
- **Benefit:** Predictable plans; safer API.
- **Trade-off:** Unsupported sort fields silently fall back to `lastName`.

### 9. Dashboard / departments refetch on every navigation

- **Problem:** Cold Observables → six analytics + departments hit every visit.
- **Change:** Analytics TTL cache; department `shareReplay`.
- **Benefit:** Far fewer aggregate scans while browsing.
- **Trade-off:** Stale dashboard up to 60s unless user hits Refresh (by design).

### 10. Employee list default change detection

- **Problem:** Zone CD rechecked table templates (methods per row) on unrelated events.
- **Change:** OnPush + `markForCheck` after async updates.
- **Benefit:** Less CPU while paging/filtering.
- **Trade-off:** Future template bindings must trigger CD explicitly after async work.

## Known limits (accepted at 10k)

- Leading-wildcard search still table-scans names/emails (fine at 10k).
- Seed still **generates** the full dataset in heap before insert (~10k objects) — OK for assessment; stream generate if count rises to 100k+.
- Analytics endpoints still separate (not one `/dashboard` bundle) — SPA cache covers the cost.
- List DTO still full `EmployeeResponse` (email, timestamps) — payload small at page ≤ 100.
- Salary history API is unpaged (history depth is tiny).
- No Redis / query-result cache on the API — single-user assessment does not need it yet.
- `LIKE` designation filter uses `LOWER(designation)` without expression index (keep casing consistent from seed/UI).

## Future scaling options

1. **Denormalize** `current_annual_salary` / `current_currency` on `employee` (update on salary write) for list + currency filter.
2. **Keyset pagination** if deep pages + heavy filters become hot.
3. **`pg_trgm` GIN** on name/email if search QPS grows.
4. **Materialized view** or short Redis TTL for analytics under many concurrent HR users.
5. **Single dashboard bundle** endpoint to cut HTTP overhead further.
6. **Chunked seed generation** (generate → insert → discard) for 100k+ datasets.
7. **Read replica** for analytics if write traffic and reporting diverge.

## Verification

- Backend: unit + integration tests covering current-salary batch load, currency filter, analytics overview counts, sort whitelist, seed batching.
- Frontend: analytics/department cache specs; employee list remains debounced.
- Run full suites after changes (`mvn test`, `ng test` / project scripts).
