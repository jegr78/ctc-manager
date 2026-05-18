---
phase: 76-operational-hardening-import-lock-read-only-banner-auto-back
plan: "01"
subsystem: backup-lock
tags: [concurrency, mutex, import-lock, secu-05, reentrant-lock, model-and-view, 409]
dependency_graph:
  requires: []
  provides: [ImportLockService, BackupController.importExecute-ModelAndView, ImportConcurrentLockIT]
  affects: [BackupController.java, org.ctc.backup.lock]
tech_stack:
  added:
    - "java.util.concurrent.locks.ReentrantLock (ImportLockService singleton)"
    - "Spring ModelAndView + RedirectView with setStatusCode(CONFLICT) + setHttp10Compatible(false)"
  patterns:
    - "non-blocking tryLock (zero-timeout) with idempotent finally-unlock guard"
    - "View-mode redirect for HTTP 409 (RESEARCH Pitfall #1 avoidance)"
    - "2-thread Failsafe IT with CountDownLatch coordination"
key_files:
  created:
    - src/main/java/org/ctc/backup/lock/ImportLockService.java
    - src/test/java/org/ctc/backup/lock/ImportLockServiceTest.java
    - src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java
    - src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java
  modified:
    - src/main/java/org/ctc/backup/BackupController.java
decisions:
  - "HTTP 409 produced via RedirectView.setStatusCode(CONFLICT) + setHttp10Compatible(false) — NOT via response.setStatus(409) + return redirect: (avoids RESEARCH Pitfall #1 status-leak-to-302)"
  - "importExecute return type changed String → ModelAndView to support View-mode redirect"
  - "Lock released in finally AFTER execute() returns; synchronous AFTER_COMMIT listener means no cross-thread coordination needed"
  - "BlockingRestoreFailureInjector in support/ sub-package (CD-05 default) for future IT reuse"
metrics:
  duration: "~18 minutes"
  completed: "2026-05-14"
  tasks_completed: 3
  tasks_total: 3
  files_changed: 5
---

# Phase 76 Plan 01: Import Lock Service + BackupController 409 Guard Summary

Ring 1 of Phase 76's defense-in-depth — `ImportLockService` singleton mutex wired into `BackupController.importExecute` with HTTP 409 View-mode redirect, proven by a 2-thread Failsafe IT.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create ImportLockService + unit test (TDD RED→GREEN) | 62b8f81, 6c838d4 | ImportLockService.java, ImportLockServiceTest.java |
| 2 | Upgrade BackupController.importExecute (String → ModelAndView, tryLock/finally, 409 View-mode) | cd53adb | BackupController.java |
| 3 | BlockingRestoreFailureInjector + ImportConcurrentLockIT | 0e7a56b | BlockingRestoreFailureInjector.java, ImportConcurrentLockIT.java |

## Artifacts Shipped

### `ImportLockService` (new singleton — `org.ctc.backup.lock`)

Public API contract:
- `boolean tryLock()` — non-blocking (zero-timeout), logs thread name on acquire; returns `false` immediately if another thread holds the lock
- `void unlock()` — idempotent; guarded by `lock.isHeldByCurrentThread()` so stray `finally { unlock(); }` after a failed `tryLock()` is a silent no-op
- `boolean isLocked()` — read-only; does NOT require current thread to hold the lock; used by Ring 2 banner advice and 503 interceptor (Plans 76-02)

### `BackupController.importExecute` (upgraded)

- Return type: `String` → `ModelAndView`
- 409 guard at method entry (before binding-error check): calls `importLockService.tryLock()`; on failure: `RedirectView("/admin/backup")` + `setStatusCode(CONFLICT)` + `setHttp10Compatible(false)` + errorMessage flash
- Full method body wrapped in `try { ... } finally { importLockService.unlock(); }` covering all exit paths
- All existing catch clauses preserved verbatim; every `return "..."` converted to `return new ModelAndView("...")`
- No `response.setStatus()` call anywhere in the method (RESEARCH Pitfall #1 negative check)

### `BlockingRestoreFailureInjector` (new test infrastructure — `org.ctc.backup.it.support`)

- Implements `RestoreFailureInjector`; blocks at `race_results:50` via `CountDownLatch` coordination instead of throwing
- `Config` inner class: `@Bean(name="noopRestoreFailureInjector") @Primary` — exact mirror of `FailAtTableInjector.Config` bean-override discipline
- Two `CountDownLatch` beans (`hasAcquired`, `releaseLatch`) exposed for IT autowire

### `ImportConcurrentLockIT` (new Failsafe IT — `org.ctc.backup.it`)

- 2-thread scenario via `ExecutorService`: thread A holds the lock mid-restore; thread B hits HTTP 409 + locked errorMessage flash; thread A completes with HTTP 302 + success; exactly 1 new `data_import_audit` row with `success=true`
- Proves SECU-05: "concurrent import attempt rejected at controller edge with HTTP 409"

## Verification Commands and Results

| Command | Exit Code |
|---------|-----------|
| `./mvnw test -Dtest=ImportLockServiceTest` | 0 (6/6 pass) |
| `./mvnw test -Dtest=ImportLockServiceTest,BackupControllerTest` | 0 (12/12 pass) |
| `./mvnw verify -Dit.test=ImportConcurrentLockIT` | 0 (1/1 pass, JaCoCo checks met) |

## Deviations from Plan

None — plan executed exactly as written.

- HTTP 409 mechanism used: `RedirectView + setStatusCode(CONFLICT) + setHttp10Compatible(false)` as specified in RESEARCH Pattern 3 / CONTEXT D-05. No fallback to `response.setStatus()` was needed.
- All CONTEXT D-04 / D-05 / D-06 contracts implemented without deviation.
- `BlockingRestoreFailureInjector` placed in `support/` sub-package as CD-05 default (not inlined into the IT class).

## TDD Gate Compliance

- RED commit: `62b8f81` — `test(76-01): add failing unit tests for ImportLockService` (compilation fails — `ImportLockService` did not exist)
- GREEN commit: `6c838d4` — `feat(76-01): implement ImportLockService singleton with ReentrantLock` (6/6 tests pass)

## Next Plan Guidance

Ring 1 ready — `BackupController.importExecute` returns `ModelAndView`; `lockService.isLocked()` is observable. Plan 76-02 wires Ring 2 (banner + 503-rejector) without further changes to `BackupController.java`.

## Self-Check: PASSED

All created files found on disk. All commits verified in git log.

| Item | Status |
|------|--------|
| `ImportLockService.java` | FOUND |
| `ImportLockServiceTest.java` | FOUND |
| `BlockingRestoreFailureInjector.java` | FOUND |
| `ImportConcurrentLockIT.java` | FOUND |
| `76-01-SUMMARY.md` | FOUND |
| Commit `62b8f81` (test RED) | FOUND |
| Commit `6c838d4` (feat GREEN) | FOUND |
| Commit `cd53adb` (feat controller) | FOUND |
| Commit `0e7a56b` (feat IT) | FOUND |
