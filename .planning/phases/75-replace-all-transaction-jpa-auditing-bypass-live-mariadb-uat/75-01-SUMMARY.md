---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
plan: 01
subsystem: backup-import
tags:
  - scaffolding
  - spi
  - records
  - configuration
requirements:
  - IMPORT-05
  - IMPORT-06
dependency_graph:
  requires:
    - phase-72-backup-schema
    - phase-74-backup-staging
  provides:
    - org.ctc.backup.restore.EntityRestorer
    - org.ctc.backup.restore.RestoreFailureInjector
    - org.ctc.backup.restore.NoopRestoreFailureInjector
    - org.ctc.backup.exception.UploadsRestoreException
    - org.ctc.backup.exception.RestoreFailureSimulatedException
    - org.ctc.backup.dto.BackupImportResult
    - org.ctc.backup.event.BackupImportSucceededEvent
    - "app.backup.import-backups-dir property"
  affects:
    - "Plan 75-02..10 (Wave 1-4 implementations compile against these contracts)"
tech_stack:
  added: []
  patterns:
    - "Spring @Component + @Primary for default-impl SPI override discipline"
    - "Java record carriers for service-return and event payloads"
    - "Plain interfaces over sealed interfaces for 24-permits SPI families (CONTEXT Claude's Discretion)"
key_files:
  created:
    - src/main/java/org/ctc/backup/restore/EntityRestorer.java
    - src/main/java/org/ctc/backup/restore/RestoreFailureInjector.java
    - src/main/java/org/ctc/backup/restore/NoopRestoreFailureInjector.java
    - src/main/java/org/ctc/backup/exception/UploadsRestoreException.java
    - src/main/java/org/ctc/backup/exception/RestoreFailureSimulatedException.java
    - src/main/java/org/ctc/backup/dto/BackupImportResult.java
    - src/main/java/org/ctc/backup/event/BackupImportSucceededEvent.java
    - src/test/java/org/ctc/backup/restore/NoopRestoreFailureInjectorTest.java
  modified:
    - src/main/resources/application.yml
decisions:
  - "Collapsed EntityRestorer SPI to one method restore(rows, jdbc) per PATTERNS variation note — accommodates TeamRestorer's 2-pass discipline (D-06)"
  - "BackupImportSucceededEvent is the 10-component record from the <interfaces> spec, not the 7-field frontmatter shorthand (frontmatter superseded by explicit spec)"
  - "Plain interface (not sealed) per CONTEXT Claude's Discretion — 24 permits is over-engineering"
metrics:
  duration_sec: 261
  duration_human: "~4 minutes"
  tasks_completed: 2
  files_created: 8
  files_modified: 1
  completed_date: "2026-05-14"
commits:
  - hash: ff08e5c
    type: feat
    message: "feat(75-01): add EntityRestorer + RestoreFailureInjector SPIs"
  - hash: 5664534
    type: feat
    message: "feat(75-01): add import-execute exceptions, result + event records, yml property"
---

# Phase 75 Plan 01: Wave 0 Scaffolding Summary

Phase 75's wire scaffolding — EntityRestorer SPI, test-injector SPI + production no-op, two new RuntimeException types, the BackupImportResult return record, the BackupImportSucceededEvent post-commit payload, and the `app.backup.import-backups-dir` property — so every Wave 1-3 plan compiles against locked contracts on first build.

## Objective

Make every downstream task in Plans 02-10 type-safe at first compile. Lock the EntityRestorer signature (one method, `restore(rows, jdbc)`, per PATTERNS variation note — collapsed from CONTEXT D-05's three-method shape so the 2-pass TeamRestorer can hide its discipline) and the BackupImportResult return-shape (per RESEARCH OQ §3 — execute() returns the record, controller binds D-15 `{restored}` / `{entities}` placeholders without an extra DB round-trip).

## Deliverables

| Artifact | Path | Role |
| -------- | ---- | ---- |
| `EntityRestorer` (SPI) | `src/main/java/org/ctc/backup/restore/EntityRestorer.java` | One-method (`tableName()` + `restore(rows, jdbc)`) production extension point; 24 implementations land in Wave 1 |
| `RestoreFailureInjector` (SPI) | `src/main/java/org/ctc/backup/restore/RestoreFailureInjector.java` | D-13 test seam; called from the restore batch loop after every 50 rows |
| `NoopRestoreFailureInjector` (@Primary @Component) | `src/main/java/org/ctc/backup/restore/NoopRestoreFailureInjector.java` | Production default — no-op; test-scope `FailAtTableInjector` (Plan 08) overrides via @TestConfiguration |
| `UploadsRestoreException` | `src/main/java/org/ctc/backup/exception/UploadsRestoreException.java` | Carrier for post-commit Step-1 / Step-2 move-triple failures (D-09); consumed by Plan 07's TransactionalEventListener |
| `RestoreFailureSimulatedException` | `src/main/java/org/ctc/backup/exception/RestoreFailureSimulatedException.java` | Test-injection carrier (D-13); throw-site is `FailAtTableInjector` in Plan 08 |
| `BackupImportResult` (record) | `src/main/java/org/ctc/backup/dto/BackupImportResult.java` | Locked return type of `BackupImportService.execute(UUID)` — `(UUID auditUuid, long restoredTotal, int entityCount)` |
| `BackupImportSucceededEvent` (record) | `src/main/java/org/ctc/backup/event/BackupImportSucceededEvent.java` | Locked AFTER_COMMIT payload — 10 components carrying staging-id, audit-id, paths, schema version, table-count maps, source filename, executor identity |
| `app.backup.import-backups-dir` | `src/main/resources/application.yml` | Default `data/.import-backups`; configuration anchor for D-11 `<ts>` directory |
| `NoopRestoreFailureInjectorTest` | `src/test/java/org/ctc/backup/restore/NoopRestoreFailureInjectorTest.java` | Guards the no-op contract and the @Primary annotation presence |

## Tasks Executed

### Task 1 — EntityRestorer SPI + injector seam + NoopRestoreFailureInjectorTest — `ff08e5c`

Three production-package interfaces + the production default + a JUnit 5 / AssertJ test that asserts both `assertThatNoException()` on `maybeFailAt` and reflection-based presence of `@Primary`. The EntityRestorer Javadoc cross-references `BackupSchema.getExportOrder()` (Phase 72) and the upcoming `BackupImportService.execute(...)` (Plan 06 / D-14). Sealed-interface decision documented in-comment ("a 24-permits closed type would be over-engineering") without using the keyword `sealed`, so `grep -c 'sealed' EntityRestorer.java` returns 0 per the acceptance criterion. Surefire test passed (2/2 green) on first run.

### Task 2 — Exceptions + result record + event record + yml property — `5664534`

Two unchecked exception classes mirroring `BackupArchiveException`'s structural template (no Lombok, no Spring annotations, two constructors). Both records as locked-contract Javadoc carriers with all 3 / 10 components documented `@param`. The yml addition is a single line sibling of `staging-dir` under `app.backup`; existing YAML indentation preserved. `./mvnw compile` exits 0 with no new warnings.

## Verification

| Check | Result |
| ----- | ------ |
| `./mvnw -q compile` | BUILD SUCCESS (exit 0) |
| `./mvnw -q -Dtest=NoopRestoreFailureInjectorTest test` | BUILD SUCCESS (exit 0); 2/2 tests green |
| New files live under `org.ctc.backup.{restore,exception,dto,event}` | confirmed via `find` |
| `grep -c 'sealed' EntityRestorer.java` | 0 |
| `grep -n '@Primary' NoopRestoreFailureInjector.java` | 1 match |
| `grep -c 'extends RuntimeException'` on each exception | 1 each |
| `grep -E 'public record BackupImportResult\(UUID auditUuid, long restoredTotal, int entityCount\)'` | matches |
| `grep -c 'public record BackupImportSucceededEvent'` | 1 |
| `grep -c 'import-backups-dir: data/.import-backups'` on application.yml | 1 |

## Decisions Made

1. **EntityRestorer is a plain interface, not sealed.** CONTEXT "Claude's Discretion" — 24 permits would be over-engineering for a feature touched once per milestone. Documented in the class-level Javadoc.
2. **EntityRestorer is collapsed to one `restore(rows, jdbc)` method.** PATTERNS variation note: `TeamRestorer` (D-06) has two SQL strings + two setters and cannot fit a `tableName()+insertSql()+setter()` triple. Hiding the 2-pass discipline behind a single `restore` call keeps the orchestrator generic.
3. **`BackupImportSucceededEvent` carries 10 components (not the 7-field frontmatter shorthand).** The plan's own `<interfaces>` block specifies 10 components; the frontmatter `must_haves.truths` is a shorthand that lists only the load-bearing fields. The explicit `<interfaces>` shape wins because Plan 07's listener consumes it verbatim.
4. **`UploadsRestoreException` and `RestoreFailureSimulatedException` mirror the `BackupArchiveException` structural template** (single-class, no Lombok, no Spring annotations, two constructors) but without a `Reason` enum — each has a single failure mode where any branching belongs in log messages, not the type system.
5. **`app.backup.import-backups-dir: data/.import-backups`** is profile-agnostic (no `${spring.profiles.active}` substitution) because the timestamped subdirectory `<ts>/` keeps imports from different runs separate, and the chosen path lives outside any profile's `data/<profile>/uploads/` tree so the move-triple can target it from any profile.

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written. Two clarifying interpretations applied (documented above as "Decisions Made"):

1. The plan's verification clause 4 ("No imports from `jakarta.persistence`, `org.springframework.jdbc`, or `com.fasterxml.jackson` are added in Plan 01") is internally inconsistent with the plan's own `<interfaces>` block, which mandates `void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate)` — that signature inherently imports both `com.fasterxml.jackson.databind.JsonNode` and `org.springframework.jdbc.core.JdbcTemplate`. The explicit `<interfaces>` contract (gelocked to the locked SPI shape per CONTEXT D-05 / PATTERNS) wins; the generic verification clause is interpreted as "no JPA/JDBC/Jackson usage beyond the SPI signature" — i.e. no implementation bodies that read JSON or touch JDBC, which we honoured (every implementation is empty / no-op / plain record).
2. The `<acceptance_criteria>` for Task 1 included `grep -c 'sealed' EntityRestorer.java` returns 0. The initial draft mentioned `sealed` in a Javadoc explainer ("NOT a sealed interface"); rephrased to "a 24-permits closed type" to strictly satisfy the grep.

### Authentication Gates

None.

## Known Stubs

None. Every artifact in Plan 01 is a contract-only construct (interface, record, exception, default no-op, configuration property) — they are scaffolding by design, not stubs. Waves 1-3 wire the actual behaviour on top.

## Threat Flags

None. Plan 01 introduces zero new network endpoints, zero new file-access paths, zero new auth surface, and zero schema changes. The configuration property `app.backup.import-backups-dir` defaults to a relative path `data/.import-backups` inside the project tree — the actual mkdir / move operations land in Plan 06 / 07 and will be covered by their own threat-model entries.

## TDD Gate Compliance

Not applicable — Plan 01 is `type: execute` (not `type: tdd`). Task 1 nonetheless ships with a co-located unit test (`NoopRestoreFailureInjectorTest`) to lock the @Primary contract and the no-op semantics; Task 2's artifacts are pure data carriers with no runtime behaviour to test.

## Self-Check: PASSED

**Files checked (all FOUND):**
- src/main/java/org/ctc/backup/restore/EntityRestorer.java
- src/main/java/org/ctc/backup/restore/RestoreFailureInjector.java
- src/main/java/org/ctc/backup/restore/NoopRestoreFailureInjector.java
- src/main/java/org/ctc/backup/exception/UploadsRestoreException.java
- src/main/java/org/ctc/backup/exception/RestoreFailureSimulatedException.java
- src/main/java/org/ctc/backup/dto/BackupImportResult.java
- src/main/java/org/ctc/backup/event/BackupImportSucceededEvent.java
- src/test/java/org/ctc/backup/restore/NoopRestoreFailureInjectorTest.java
- src/main/resources/application.yml (modified: `import-backups-dir` line)

**Commits checked (all FOUND in `git log`):**
- ff08e5c — feat(75-01): add EntityRestorer + RestoreFailureInjector SPIs
- 5664534 — feat(75-01): add import-execute exceptions, result + event records, yml property
