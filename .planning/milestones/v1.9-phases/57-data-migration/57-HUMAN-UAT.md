---
status: partial
phase: 57-data-migration
source: [57-VERIFICATION.md, 57-VALIDATION.md, 57-REVIEW.md]
started: 2026-04-27
updated: 2026-04-27
---

## Current Test

[awaiting human testing on local MariaDB before prod merge]

## Tests

### 1. MariaDB NOT NULL flip executes cleanly on local MariaDB instance
expected: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (with fresh local MariaDB seeded only through V3) boots green; Flyway log shows `V4__MigrateSeasonsToPhases` runs without error; `DESCRIBE matchdays;` and `DESCRIBE playoffs;` confirm `phase_id` is `NOT NULL` after migration. Out-of-CI because GitHub Actions runs against H2 only.
result: [pending]

### 2. MariaDB DDL non-atomicity does not produce corrupted state on partial failure
expected: Per CONTEXT.md D-04 and REVIEW.md CR-03, MariaDB issues an implicit commit before/after each `ALTER TABLE`. If `flipNotNullConstraints` partially fails (e.g., only `matchdays.phase_id` flip succeeds, `playoffs.phase_id` flip fails), the prior DML steps (1-4) are already auto-committed. Verify on local MariaDB that the migration sequence either fully succeeds or leaves a manually-recoverable state. Documented in 57-03-SUMMARY.md "Manual MariaDB Verification Checklist" (7 steps).
result: [pending]

### 3. Spring Boot dev profile starts cleanly with V4 migration applied
expected: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` boots green; the empty-DB guard in `flipNotNullConstraints` (auto-fix from 57-02) prevents `DevDataSeeder` from violating the NOT-NULL constraint on `matchday.phase_id`. Phase 59 will rebuild the seeder on the new model.
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps

(none yet — gaps populated if manual tests fail)
