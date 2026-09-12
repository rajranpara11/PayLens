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
| currency | CHAR(3) NOT NULL | ISO 4217, uppercase; allowlisted in the API |
| effective_from | DATE NOT NULL | Start of this pay period |
| created_at, updated_at | TIMESTAMP WITH TIME ZONE NOT NULL | |

No amount or currency on `employee`.

## Salary history decisions

**Model:** one row per employee per `effective_from`. There is no `effective_to` column.

**Period:** implicit half-open interval  
`[effective_from, next_effective_from)`  
and the last row continues until a later row exists. Periods cannot overlap: uniqueness of `(employee_id, effective_from)` plus this rule is enough. We do not need a second overlap constraint.

**Current salary:** the row with the greatest `effective_from` that is `<= today`. A future-dated PUT is stored in history but does not become current until that date.

**PUT `/salary`:**

| Incoming `effective_from` | Effect |
| --- | --- |
| New date | **Insert** a row. Older rows stay (history). |
| Existing date | **Update** that row in place (same-day correction). Not a second insert. |

Never overwrite a different date’s row. Never delete history on update.

**Why not `UNIQUE (employee_id)` only:** that would allow one salary forever and block history.

**Why not `effective_to` yet:** derived current + unique start date answers “pay as of date” and “current pay.” We can add `effective_to` later plus `UNIQUE (employee_id) WHERE effective_to IS NULL` if we want an explicit current flag.

**API:**

- `GET .../salary` — current (as of today), 404 if employee missing or no applicable row
- `GET .../salary-history` — all rows, newest `effective_from` first
- `PUT .../salary` — insert or correct as above; employee must exist

**Money:** `NUMERIC` / `BigDecimal` only. Supported currencies: USD, EUR, GBP, INR, SGD, AUD, CAD, CHF, JPY, NZD.

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
- Soft-delete of employees (status `TERMINATED` in the API)
- Application-level `updated_at` triggers (entity callbacks set `updated_at`)
