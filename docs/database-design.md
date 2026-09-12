# PayLens database design

**Database:** PostgreSQL  
**Migrations:** Flyway (`backend/src/main/resources/db/migration`)  
**Money:** `NUMERIC(15,2)` only — never `FLOAT` / `DOUBLE` / `REAL`

## Tables

| Table | Purpose |
| --- | --- |
| `paylens_schema_meta` | V1 baseline marker |
| `department` | Normalized org bucket (code + name) |
| `employee` | Person and employment attributes. **No salary columns.** |
| `salary` | Dated annual base pay. Many rows per employee. |

```
department 1──* employee 1──* salary
```

## Employee

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | Assigned by the application (JPA). No DB default so migrations stay portable. |
| employee_code | VARCHAR(32) UNIQUE NOT NULL | Stable HR identifier |
| first_name, last_name | VARCHAR(100) NOT NULL | Blank rejected |
| email | VARCHAR(255) UNIQUE NOT NULL | Must contain `@` |
| country | CHAR(2) NOT NULL | ISO 3166-1 alpha-2, stored uppercase |
| department_id | UUID NOT NULL FK → department | Employee “department” field |
| designation | VARCHAR(120) NOT NULL | Free text; titles are high-cardinality |
| employment_status | VARCHAR(20) NOT NULL | `ACTIVE`, `ON_LEAVE`, `TERMINATED` |
| joining_date | DATE NOT NULL | |
| created_at, updated_at | TIMESTAMP WITH TIME ZONE NOT NULL | Default `NOW()` |

Country is a code, not a table: ISO set is fixed and we store no extra country attributes yet.

## Salary (history-ready)

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| employee_id | UUID NOT NULL FK → employee | `ON DELETE RESTRICT` |
| annual_salary | NUMERIC(15,2) NOT NULL | `> 0`; BigDecimal in Java |
| currency | CHAR(3) NOT NULL | ISO 4217, uppercase |
| effective_from | DATE NOT NULL | When this amount became current |
| created_at, updated_at | TIMESTAMP WITH TIME ZONE NOT NULL | |

**Current pay** (until `effective_to` exists) = the row with the greatest `effective_from` that is `<=` today.

No amount or currency on `employee`. A raise is a new `salary` row.

## Uniqueness on salary — decision

**Do not** `UNIQUE (employee_id)`. That would allow only one salary row per person and block history.

**Do** `UNIQUE (employee_id, effective_from)`:

- One compensation event per employee per calendar date.
- History is unlimited across dates.
- “Current” is derived, not a second flag, so we cannot get two currents on the same day.
- A same-day correction is a later product (or an update of that row), not a second insert.

We did **not** add `effective_to` in this migration. The unique key still leaves a clean path: later add `effective_to` and a partial unique index `UNIQUE (employee_id) WHERE effective_to IS NULL` if we want an explicit current row.

## Indexes

| Index | Why |
| --- | --- |
| UNIQUE `employee_code`, `email` | Identity lookup + integrity |
| UNIQUE `department.code` | Stable department key |
| `employee(country)` | Directory / analytics filter |
| `employee(department_id)` | Filter + FK join |
| `employee(designation)` | Filter |
| `employee(employment_status)` | Active vs terminated lists |
| UNIQUE `(salary.employee_id, effective_from)` | History order + “pay as of date” |

`employee_id` + `effective_from` is covered by that unique constraint; no extra index.

## Checks

- Non-blank codes, names, designation
- Status enum
- Country length 2 and uppercase
- Currency length 3 and uppercase
- `annual_salary > 0`

## What this schema does not do

- FX / a single global payroll total
- Pay frequency other than annual (amount is annual)
- Soft-delete of employees
- Application-level `updated_at` triggers (defaults only; services will set `updated_at` later)
