# PayLens Requirements (MVP)

**Product:** PayLens — employee salary management for ACME  
**Persona:** HR Manager  
**Scale:** ~10,000 employees, multiple countries  
**Date:** 2026-09-12

## Goal

Replace spreadsheet-based salary tracking with a single web application so HR can (1) maintain employee compensation accurately and (2) answer “how does ACME pay people?” without exporting Excel.

## Problem

Salary data lives in Excel. At 10k people and multiple countries that is slow, error-prone, and poor at org-level questions (pay by country, department, currency). Spreadsheets also have no safe history of raises.

## In scope

- Web UI for one HR role to view, update, search, and deactivate employees (create/seed via API and seed tooling).
- Current base salary per employee: amount, ISO currency, and effective date.
- Salary changes as dated history rows (latest `effective_from ≤ today` is current; same-date upsert corrects in place).
- Server-side pagination, search, and filters on the employee directory.
- Dashboard analytics that never mix currencies.
- Seed of 10,000 realistic employees across countries/currencies.
- Login for HR (single organization, no employee portal).
- Deployable stack: Angular UI, Java 21 / Spring Boot API, PostgreSQL.
- Automated tests for core rules (salary succession, validation, currency-safe analytics, pagination).

## Out of scope (deliberate)

| Left out | Why |
| --- | --- |
| Payroll run, tax, benefits, bonuses, equity | Different product; assessment is salary *management* and pay insight, not payroll engine |
| Excel/CSV import-export | Goal is leave Excel; import is a later migration tool, not MVP |
| FX conversion / org-wide “total payroll in USD” | Silent currency mixing is wrong; no reliable FX feed in MVP |
| Pay frequency / change-reason fields | MVP tracks annual amount + currency + effective date; extra fields add schema without assessment value |
| In-UI hire wizard | Onboarding is available via API + seed; directory focuses on manage/search/update |
| Employee self-service, managers, multi-role RBAC | One persona: HR Manager |
| Approvals, workflows, email | Process overhead; one user can edit directly |
| Multi-tenant / multi-company | ACME is one org |
| Microservices, Kafka, Redis, Kubernetes | 10k rows do not justify that complexity |
| Time & attendance, org-chart editor, performance | Adjacent HRIS, not salary |

## Success

HR logs in, finds anyone in seconds, changes a salary with a clear effective date, and reads a dashboard that shows headcount and **per-currency** pay stats. System runs from Docker Compose (or equivalent) with seed data loaded.

## Constraints

Java 21 + Spring Boot, Angular, PostgreSQL, modular monolith, production-quality code and tests, incremental Git commits, design artifacts in-repo.
