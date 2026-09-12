# PayLens seeding

## Goal

Load **exactly 10,000** realistic employees (plus departments and salary history) for local demo and analytics. Generation is **deterministic** (`random-seed=42`).

## Important: not automatic in production

Default config:

```yaml
paylens:
  seed:
    enabled: false
```

`SeedRunner` is only created when `paylens.seed.enabled=true`. A normal `dev` or production start **does not** insert 10k rows.

## How to run (development)

From `backend/`:

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
```

PowerShell:

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,seed"
```

That activates `application-seed.yml`, which sets `paylens.seed.enabled=true`.

One-shot without keeping the HTTP server up:

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,seed" "-Dspring-boot.run.arguments=--spring.main.web-application-type=none"
```

Ensure PostgreSQL is reachable (`DATABASE_URL` / username / password as in `application-dev.yml`).

## Safe re-runs

| Database state | Behavior |
| --- | --- |
| 0 employees | Seed 10,000 employees + salaries |
| ≥ 10,000 employees | Skip (log and continue) |
| 1–9,999 employees | Skip with warning — truncate tables to re-seed cleanly |

So repeating the seed command in an already-seeded DB is safe.

To force a clean re-seed (dev only):

```sql
TRUNCATE salary, employee, department CASCADE;
```

Then run the seed command again.

## What gets created

- **10 departments:** Engineering, Product, Human Resources, Finance, Sales, Marketing, Operations, Legal, IT, Customer Success
- **5 countries:** IN, US, GB, DE, CA with currencies INR, USD, GBP, EUR, CAD
- **10,000 employees:** unique `EMP00001`…`EMP10000`, unique emails, varied designations and statuses
- **Salaries:** at least one hire salary per employee; ~25% also have a later raise (history)
- **Pay logic:** higher seniority designations land higher in each country’s band; seniors generally earn more than juniors

## Implementation notes

| Piece | Role |
| --- | --- |
| `DeterministicSeedGenerator` | Pure Java; fixed `Random(42)`; unit-tested |
| `JdbcBatchSeeder` | JDBC `batchUpdate` in chunks of 500 — not 10k individual `save()` calls |
| `SeedRunner` | Opt-in `ApplicationRunner` |

## Verification queries

```sql
SELECT COUNT(*) FROM employee;          -- 10000
SELECT COUNT(*) FROM salary;            -- > 10000
SELECT COUNT(DISTINCT employee_code) FROM employee;  -- 10000
SELECT COUNT(DISTINCT email) FROM employee;          -- 10000
SELECT country, COUNT(*) FROM employee GROUP BY country;
SELECT currency, COUNT(*) FROM salary GROUP BY currency;
```

## Config knobs

| Property | Default | Meaning |
| --- | --- | --- |
| `paylens.seed.enabled` | `false` | Master switch |
| `paylens.seed.employee-count` | `10000` | Row target |
| `paylens.seed.random-seed` | `42` | RNG seed |
| `paylens.seed.batch-size` | `500` | JDBC batch size |
