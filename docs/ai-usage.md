# AI usage log

Intentional AI use for the PayLens assessment. Updated as work proceeds.

## 2026-09-12 — Technical / product plan (no application code)

**Intent:** Turn `docs/assessment.md` plus stack constraints (Java 21, Spring Boot, Angular, PostgreSQL, 10k employees) into a reviewable plan before any implementation.

**Kept:**

- Modular monolith; no Kafka/Redis/K8s/microservices.
- Salary history as dated rows with one current record (`effective_to` null).
- Analytics partitioned by currency; no FX.
- Server-side pagination/filter/search.
- One-page requirements split from the long technical plan.

**Rejected / not asked of the model:**

- Implementation code (explicit hold).
- Import pipelines, payroll, event sourcing, CQRS frameworks.

**Human owns:** scope cuts, currency rule, session-vs-JWT, implementation order.
