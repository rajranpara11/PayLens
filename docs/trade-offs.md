# PayLens trade-offs

## Currency aggregation (analytics)

**Decision:** Never treat amounts in different currencies as one number. Overview, department, designation, country payroll, and distribution endpoints **group by currency** (and by country/department/designation *within* that currency).

**Why:** `INR 2,000,000 + USD 100,000` is not a meaningful payroll total without an FX rate. MVP has no FX feed. A single “average salary” across ACME would be wrong and dangerous for HR decisions.

**What HR still gets:** Per-currency min / max / average / median / total for employed people (`ACTIVE` + `ON_LEAVE`), plus headcount by country (safe to combine — no money). Country payroll looks like `IN → INR → total`, `US → USD → total`.

**Rejected alternative:** Convert everything to USD in the API. That needs rates, effective dates, and audit rules we do not have.

## Database-level aggregation

**Decision:** Analytics use SQL (`JdbcTemplate` + `GROUP BY` / window functions) over a **current-salary** set. The API does **not** `findAll()` employees or salaries into the JVM to average them.

**Why:**

| Approach | At 10k employees |
| --- | --- |
| Load all rows, aggregate in Java | High heap, slow, easy to accidentally mix currencies |
| `GROUP BY currency` in Postgres | One pass, indexes help, amounts stay `NUMERIC` |

Current salary is derived in SQL: latest `effective_from <= today` per employee (`ROW_NUMBER`), then aggregates run on that set. Same definition as the salary module.

**Indexes added (Flyway V3):** `salary(currency)`, `salary(effective_from)`, `employee(employment_status, country)` — in addition to the unique `(employee_id, effective_from)` from V2.
