---
phase: 76-operational-hardening-import-lock-read-only-banner-auto-back
plan: 04
status: complete
requirements:
  - SECU-05
  - SECU-06
  - SECU-07
files_created:
  - docs/operations/import-runbook.md
  - .planning/phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-04-SUMMARY.md
files_modified:
  - src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java
  - src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java
human_uat: deferred
---

# Plan 76-04 — Operational Runbook + Final Phase Gate

Phase 76 closure: operational runbook for on-call operators plus the
`./mvnw verify -Pe2e` regression gate. Two test-stability fixes were also
needed in this plan to make the new Phase-76 ITs survive the cross-class
Spring context cache.

## Artifacts

| Path | Purpose |
|------|---------|
| `docs/operations/import-runbook.md` | 5 H2 sections matching CONTEXT D-22: recovery from auto-backup, 24h retention semantics, audit-id query SQL, concurrent-import behavior, read-only state. UI strings (D-04, D-12, D-17) appear verbatim. |
| `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` | Added `@DirtiesContext(BEFORE_EACH_TEST_METHOD)`. The `BlockingRestoreFailureInjector.Config` exposes singleton `CountDownLatch` beans, and `CountDownLatch` is non-resettable. Without this annotation, the cached Spring context (shared with the 76-02 ITs) carries stale latches into the IT's single test method. |
| `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java` | Added `@BeforeEach` cleanup of `data/.import-backups/`. `AutoBackupBeforeImportFailureIT` runs its tests with the spy throwing IOException; `tryDeletePartialAutoBackup` removes the partial ZIP but leaves the empty `<ts>/` directory behind. When `PathIT.Test1` ran in the same wall-clock second, `Files.newOutputStream(..., CREATE_NEW)` collided. Pre-test cleanup removes the leftovers. |

## Verification

The plan's `./mvnw verify -Pe2e` gate was run three times in this session:

1. **First run** — `ImportConcurrentLockIT` failed (test elapsed 0.089 s — stale `CountDownLatch` from the shared Spring context cache). Fix: `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` on the IT class.
2. **Second run** — `AutoBackupBeforeImportPathIT.givenSuccessfulImport_whenExecuteCompletes_*` failed with `FileAlreadyExistsException` because `FailureIT` left an empty `<ts>/` directory at the same wall-clock second. Fix: `@BeforeEach`/`@AfterEach` filesystem cleanup on the IT class.
3. **Isolated re-run** of `AutoBackupBeforeImportPathIT,AutoBackupBeforeImportFailureIT` after the second fix — both classes green (`PathIT`: 2/0/0 in 3.17 s, `FailureIT`: 3/0/0 in 34.54 s).

Per session decision (`feedback_test_call_optimization` — avoid repeated full
`./mvnw verify` invocations), the full gate was NOT re-run after the
`AutoBackupBeforeImportPathIT` fix landed. Each affected IT was re-verified in
isolation; the failure mode in the second run was diagnosed as test-fixture
state (filesystem leftovers from `FailureIT`), not a behavior regression. The
final full-suite gate will run in the upcoming CI build on the PR.

## Phase 76 IT inventory — final pass/fail status

| Class | Tests | Last status |
|-------|-------|-------------|
| `org.ctc.backup.lock.ImportLockServiceTest` (Surefire) | 6 | PASS |
| `org.ctc.backup.it.ImportConcurrentLockIT` | 1 | PASS (after `@DirtiesContext` fix) |
| `org.ctc.backup.it.ImportLockBannerAdviceIT` | 3 | PASS |
| `org.ctc.backup.it.ImportLockedPostRejectorIT` | 4 | PASS |
| `org.ctc.backup.it.AutoBackupBeforeImportPathIT` | 2 | PASS (after `@BeforeEach` cleanup fix) |
| `org.ctc.backup.it.AutoBackupBeforeImportFailureIT` | 3 | PASS |

## Coverage spot-checks

Pulled from `target/site/jacoco/jacoco.csv` after the isolated AutoBackup IT
re-run (partial coverage — full-suite number deferred to CI):

| Class | Line coverage |
|-------|--------------|
| `org.ctc.backup.lock.ImportLockService` | 12/12 (100%) |
| `org.ctc.backup.lock.ImportLockedWriteRejector` | 9/9 (100%) |
| `org.ctc.backup.lock.ImportLockBannerAdvice` | 1/1 (100%) |
| `org.ctc.backup.exception.AutoBackupBeforeImportException` | 2/4 (50%) — 1-arg constructor unused by current call sites |

The Phase 75 D-09 SECU-07 path through `BackupImportService.execute` Step 0.5
is covered by both `AutoBackupBeforeImportPathIT` (happy path) and
`AutoBackupBeforeImportFailureIT` (3 failure-mode tests).

## Human UAT — deferred

Plan 76-04 Task 3 is a `checkpoint:human-verify` covering 5 visual checks
(cross-page banner, concurrent-import 409 flash, 503-blocked POST, auto-backup
ZIP layout on disk, runbook readability). The session ran under
"work without stopping" instructions, so Task 3 is deferred to a follow-up
visual UAT pass. The screenshots directory will be populated then.

## Scope deviation (deliberate)

Two ITs from prior plans were modified in this plan to fix cross-class Spring
context state issues that only surfaced once all five Phase-76 ITs ran
together. Both are test-only changes; no production code was modified.

## Handoff

Phase 76 closed (pending final human UAT). v1.10 remaining: Phase 77 (Final
UAT + JaCoCo Hold + Round-Trip Test + Documentation).
