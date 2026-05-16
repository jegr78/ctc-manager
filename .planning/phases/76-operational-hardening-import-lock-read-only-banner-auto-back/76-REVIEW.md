---
phase: 76-operational-hardening-import-lock-read-only-banner-auto-back
reviewed: 2026-05-14T22:00:00Z
depth: deep
files_reviewed: 16
files_reviewed_list:
  - src/main/java/org/ctc/backup/lock/ImportLockService.java
  - src/main/java/org/ctc/backup/lock/ImportLockBannerAdvice.java
  - src/main/java/org/ctc/backup/lock/ImportLockedWriteRejector.java
  - src/main/java/org/ctc/backup/exception/AutoBackupBeforeImportException.java
  - src/main/java/org/ctc/backup/service/BackupImportService.java
  - src/main/java/org/ctc/backup/BackupController.java
  - src/main/java/org/ctc/admin/WebConfig.java
  - src/main/resources/templates/admin/layout.html
  - src/test/java/org/ctc/backup/lock/ImportLockServiceTest.java
  - src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java
  - src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java
  - src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java
  - src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java
  - src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java
  - src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java
  - docs/operations/import-runbook.md
findings:
  critical: 3
  warning: 4
  info: 3
  total: 10
status: issues_found
---

# Phase 76: Code Review Report

**Reviewed:** 2026-05-14T22:00:00Z
**Depth:** deep
**Files Reviewed:** 16
**Status:** issues_found

## Summary

Phase 76 ships three concentric safety rings (mutex lock, read-only banner + 503 interceptor, pre-import auto-backup ZIP) on top of the Phase 75 wipe+restore path. The production code is largely correct and the architectural decisions from CONTEXT.md and RESEARCH.md are faithfully implemented. Three critical defects were found: the `AutoBackupBeforeImportException` catch clause in `BackupController.importExecute` appears after `UploadsRestoreException` in the chain but — more importantly — the `AutoBackupBeforeImportException` catch has an explicit `return` statement that exits the try-block early, leaving the default `return new ModelAndView("redirect:/admin/backup")` at line 337 unreachable for that path (this is harmless functionally but the catch semantically short-circuits the outer try correctly). A more serious structural finding: the `BackupController.importExecute` try-block contains a nested `try {reparse; execute}` with its own catch chain, and `AutoBackupBeforeImportException` is caught in that inner chain at line 305 — which is AFTER `UploadsRestoreException` at line 295 but BEFORE `BackupImportException` at line 318. However, `UploadsRestoreException` is NOT a supertype of `AutoBackupBeforeImportException`, so the catch ordering between these two siblings is immaterial; Pitfall #3 (subclass-before-parent) IS correctly observed between `AutoBackupBeforeImportException` and `BackupImportException`. The real critical issues are: (1) the 503 interceptor only checks for `POST` but the CONTEXT D-08 specification (and the 76-02-SUMMARY) says PUT/PATCH/DELETE should also be blocked — the implementation allows PUT/PATCH/DELETE mutations during an active import; (2) a duplicate `Files.createDirectories(importBackupDir)` call at line 508 in `BackupImportService.execute` wastes a syscall and signals incomplete refactoring; (3) the `AutoBackupBeforeImportException` 1-arg convenience constructor is dead code (its only call site uses the 3-arg variant exclusively), and the JaCoCo spot-check in the 76-04 SUMMARY explicitly flags 50% line coverage on that class — suggesting coverage falls below the 82% project minimum for this class in isolation.

---

## Critical Issues

### CR-01: 503 Interceptor Only Blocks POST — PUT/PATCH/DELETE Pass Through Unchecked

**File:** `src/main/java/org/ctc/backup/lock/ImportLockedWriteRejector.java:62`

**Issue:** The `preHandle` check at line 62 tests only `"POST".equalsIgnoreCase(req.getMethod())`, returning `true` (allowing) for any other method. The 76-02-SUMMARY artifact description explicitly states the rejector handles "POST/PUT/PATCH/DELETE requests under `/admin/**`". While the current admin controllers use only POST for mutations (confirmed by grepping — no `@PutMapping`, `@PatchMapping`, or `@DeleteMapping` annotations exist in the codebase today), this is a latent security gap: any future controller that uses PUT/PATCH/DELETE for data mutations will bypass the Ring 2 write lock entirely without any code change or test failure. The CONTEXT D-08 pseudocode only mentions POST, but D-09 states "no writes anywhere during an import" as the goal. The 76-02-SUMMARY is the delivered contract and it names all four mutating methods.

**Fix:**
```java
// Replace the single-method check with a set-based check covering all mutating verbs
private static final Set<String> MUTATING_METHODS =
        Set.of("POST", "PUT", "PATCH", "DELETE");

@Override
public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
        throws IOException {
    if (!MUTATING_METHODS.contains(req.getMethod().toUpperCase())) return true;  // non-mutating: allow
    if (!importLockService.isLocked()) return true;                               // no lock: allow
    if ("/admin/backup/import-execute".equals(req.getRequestURI())) return true;  // whitelist (D-09/D-10)
    // ... reject with 503
}
```

---

### CR-02: Duplicate `Files.createDirectories(importBackupDir)` in `execute()` — Second Call May Silently Mask Step 0.5 Failure

**File:** `src/main/java/org/ctc/backup/service/BackupImportService.java:488` and `508`

**Issue:** `Files.createDirectories(importBackupDir)` is called twice in `execute()`: once at line 488 inside the Step 0.5 auto-backup try-block (correct — needed before `Files.newOutputStream`), and again at line 508 in the main try-block body (Step 2 uploads-extract path). The second call at line 508 is unreachable on the auto-backup success path because the directory was already created at line 488. On the auto-backup failure path, the method throws `AutoBackupBeforeImportException` before reaching line 508. The redundancy indicates the Phase 75 call was not removed when Phase 76 moved the directory creation into Step 0.5 (per D-15). While currently harmless (idempotent), it creates a maintenance trap: a future reader may incorrectly conclude that line 508 is the primary directory creation site and inadvertently remove line 488, breaking `Files.newOutputStream(..., CREATE_NEW)` with `NoSuchFileException`.

**Fix:** Remove the redundant `Files.createDirectories(importBackupDir)` at line 508. The auto-backup step (line 488) is the sole creation site for `importBackupDir`:
```java
// Step 2 — only uploadsNewDir needs creation here; importBackupDir was created in Step 0.5
Files.createDirectories(uploadsNewDir);
backupArchive.extractUploadsTo(staged, uploadsNewDir);
```

---

### CR-03: `AutoBackupBeforeImportException` 1-Arg Constructor Is Dead Code — JaCoCo Coverage Failure

**File:** `src/main/java/org/ctc/backup/exception/AutoBackupBeforeImportException.java:33-35`

**Issue:** The convenience constructor `AutoBackupBeforeImportException(UUID auditUuid, Throwable cause)` (lines 33–35) is never called anywhere in the codebase. The single call site in `BackupImportService.java:499` always uses the 3-arg constructor: `throw new AutoBackupBeforeImportException(auditUuid, auditWritten, autoExportEx)`. The 76-04-SUMMARY coverage spot-check explicitly documents this: `AutoBackupBeforeImportException` shows 2/4 lines covered (50%), calling it out as "1-arg constructor unused by current call sites." The project minimum is 82% line coverage (CLAUDE.md Constraints). Even if the overall project coverage remains above 82%, shipping a constructor that the Javadoc incorrectly describes as "the common path" ("`tryRecordFailure` returned `true`") when it is in fact never invoked is misleading documentation. The full CI JaCoCo gate (deferred to Phase 77) may fail if the class-level ratio falls below the threshold.

**Fix:** Remove the 1-arg convenience constructor entirely. The 3-arg constructor is sufficient and forces callers to propagate the `auditWritten` flag correctly (WR-03 discipline). If the 1-arg constructor is kept for future use, add `@SuppressWarnings("unused")` and a `// called from tests` comment to prevent confusion, but do not describe it as "the common path":
```java
// Remove or annotate:
// public AutoBackupBeforeImportException(UUID auditUuid, Throwable cause) — DEAD CODE, remove
```

---

## Warnings

### WR-01: `BackupController.importExecute` Catch Chain — `AutoBackupBeforeImportException` Misplaced Relative to Its Comment

**File:** `src/main/java/org/ctc/backup/BackupController.java:289-318`

**Issue:** The catch chain is ordered: `BackupArchiveException` → `IOException` → `UploadsRestoreException` → `AutoBackupBeforeImportException` → `BackupImportException`. The inline comment at line 307 states "The catch MUST appear BEFORE `BackupImportException` (parent type)" — which is satisfied. However, the `UploadsRestoreException` clause (line 295) appears between `IOException` and `AutoBackupBeforeImportException`. `UploadsRestoreException` is thrown by the post-commit AFTER_COMMIT listener (Plan 75-07), not by `execute()` directly. The comment at line 298 acknowledges this is "rarely-to-never hit in practice." This ordering creates subtle confusion: a reader auditing Pitfall #3 (subclass-before-parent) sees `UploadsRestoreException` at line 295 between `IOException` and `AutoBackupBeforeImportException`, and may incorrectly infer that `UploadsRestoreException` could shadow `AutoBackupBeforeImportException` if there were an inheritance relationship. There is no such relationship — both extend `RuntimeException` directly — but the ordering makes code review harder. Conventionally, sibling catches should be ordered in the sequence they can be thrown in the request lifecycle: `BackupArchiveException` (from `reparse`) → `AutoBackupBeforeImportException` (from Step 0.5 of `execute`) → `BackupImportException` (from wipe/restore) → `UploadsRestoreException` (from AFTER_COMMIT listener) → `IOException` (rare).

**Fix:** Reorder to match the request-lifecycle throw sequence:
```java
} catch (BackupArchiveException ex) { ... }
} catch (IOException ex) { ... }
} catch (AutoBackupBeforeImportException ex) { ... }  // Step 0.5 — before parent
} catch (BackupImportException ex) { ... }            // wipe/restore path — parent
} catch (UploadsRestoreException ex) { ... }          // AFTER_COMMIT listener — last
```

---

### WR-02: `ImportConcurrentLockIT` Duplicate Assertion and Unused Variable

**File:** `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java:213-226`

**Issue:** The assertion `assertThat(postTestSuccessCount - preTestSuccessCount).isEqualTo(1L)` is stated identically at lines 213–215 and again at lines 224–226 with a different `.as(...)` description but the same predicate. This is a copy-paste artifact: the second assertion adds no additional coverage and never provides incremental failure information. Additionally, `failureRowsAdded` (line 219) is computed but never asserted — the variable is declared and assigned but then discarded. A comment at line 222 explains the intent ("we only assert the delta for success rows") but the code should either assert on `failureRowsAdded` (e.g., using a pre-test snapshot of failure-row count to prove thread B contributed 0 failure rows) or remove the dead computation.

**Fix:**
```java
// Remove the duplicate assertion at lines 224-226.
// Either assert the failure delta properly (requires pre-test failure count capture):
long preTestFailureCount = dataImportAuditRepository.findAll().stream()
        .filter(a -> !a.isSuccess()).count();
// ... (after test)
long postTestFailureCount = ...; // at assertion time
assertThat(postTestFailureCount - preTestFailureCount)
        .as("thread B contributed 0 failure audit rows — it was rejected before reaching the service")
        .isEqualTo(0L);
// Or: simply remove the failureRowsAdded computation if it isn't being asserted.
```

---

### WR-03: `BlockingRestoreFailureInjector` Assumes `race_results` Has At Least 50 Rows in Test Fixture — No Guard

**File:** `src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java:114`

**Issue:** The injector targets `race_results` at row index 50 (`FAIL_INJECT_INTERVAL = 50`). `maybeFailAt` is called only when `rowIndex % 50 == 0`, so the first eligible row is row 50. If `TestDataService.seed()` produces fewer than 50 `race_results` rows in the H2 dev fixture, `hasAcquired` never counts down, `hasAcquired.await(10, SECONDS)` returns `false`, and all three slow-import ITs (`ImportConcurrentLockIT`, `ImportLockBannerAdviceIT`, `ImportLockedPostRejectorIT`) fail with a misleading assertion error ("hasAcquired latch must count down within 10 s"). The current fixture produces well over 50 rows based on the seeding code (multiple rounds of match/race generation), so the tests pass today. However, any future reduction of the fixture (e.g., for test-speed optimization) would silently break all three ITs without an obvious error message pointing to the root cause. The row-count assumption is entirely implicit.

**Fix:** Add a fail-fast guard in `@BeforeAll` of the affected ITs, or add a comment in `BlockingRestoreFailureInjector.Config` naming the minimum required row count:
```java
// In BlockingRestoreFailureInjector.Config.blockingInjector javadoc:
// REQUIRES: TestDataService.seed() must produce >= 50 race_results rows.
// The injector fires at rowIndex=50 (FAIL_INJECT_INTERVAL); if the fixture
// has fewer rows, hasAcquired.await() will timeout and all slow-import ITs fail.
```
Alternatively, target a table and row index that is guaranteed non-empty regardless of fixture size (e.g., row 1 of `seasons`), but this changes the Phase 75 D-13 RESEARCH Assumption A1 targetting; confirm with the project author before changing.

---

### WR-04: `AutoBackupBeforeImportPathIT.createdTsDirs` Field Is Declared But Never Populated or Used

**File:** `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java:70`

**Issue:** `List<Path> createdTsDirs` is declared as an instance field at line 70 with a Javadoc comment ("Tracks `<ts>` dirs created during each test for `@AfterEach` cleanup"), but it is never assigned, never appended to, and never consulted in either `@BeforeEach` or `@AfterEach`. The actual cleanup in `cleanupImportBackupsDirs()` simply removes all children of `importBackupsDir` unconditionally — it does not use `createdTsDirs`. This is a dead field from a refactoring that was partially completed. It is misleading because a reader expects it to drive cleanup scope, but the cleanup is actually coarser-grained (clears ALL `<ts>` dirs).

**Fix:** Remove the `createdTsDirs` field entirely. The actual cleanup strategy (unconditional sweep of `importBackupsDir`) does not need a tracking list:
```java
// Remove: List<Path> createdTsDirs;  // never populated, never used
```

---

## Info

### IN-01: `Assumptions.assumeFalse(true, ...)` in Test 2 of `AutoBackupBeforeImportFailureIT` — Use `assumeTrue(false)` or `Assumptions.abort()`

**File:** `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java:202`

**Issue:** The Windows skip at line 202 uses `Assumptions.assumeFalse(true, "Windows file-locking...")` — a double-negation idiom for "always abort this test." `assumeFalse(true, ...)` is functionally correct but confusing to read. The canonical JUnit 5 form is `Assumptions.assumeTrue(isWindows() == false, ...)` or (cleaner) a guard at the top of the test: `Assumptions.assumeFalse(isWindows(), "Skipped on Windows...")`. The current form only executes the assumption inside an `if (isWindows())` branch, which inverts the intent twice.

**Fix:**
```java
// At the start of Test 2:
Assumptions.assumeFalse(isWindows(), "Windows file-locking prevents Files.deleteIfExists on open streams — skipping cleanup assertion (D-19)");
// ... then the if (!newDirs.isEmpty()) assertion block runs unconditionally
```

---

### IN-02: `ImportLockedWriteRejector` Javadoc Claims Scope Includes PUT/PATCH/DELETE but Implementation Only Checks POST

**File:** `src/main/java/org/ctc/backup/lock/ImportLockedWriteRejector.java:48` (class-level Javadoc + 76-02-SUMMARY)

**Issue:** The class-level Javadoc at lines 18-20 states "Intercepts every POST under `/admin/**`" — which matches the implementation. However the 76-02-SUMMARY artifact description states "`ImportLockedWriteRejector` — `HandlerInterceptor.preHandle` rejecting POST/PUT/PATCH/DELETE requests". The discrepancy between documentation and implementation is the surface symptom; CR-01 addresses the substantive fix. This IN item notes the Javadoc itself needs updating once CR-01 is applied.

**Fix:** After applying CR-01, update the `preHandle` Javadoc at line 48 to read: "Short-circuits non-whitelisted POST/PUT/PATCH/DELETE requests while the import lock is held."

---

### IN-03: Runbook Section 1 "When App is DOWN" Recommends `mariadb-import` Without Qualification

**File:** `docs/operations/import-runbook.md:36-43`

**Issue:** Section 1 ("When the app is DOWN") states "do not attempt `mariadb-import < anything.sql` against the live DB without a separate `mysqldump`". The `auto-backup-before-import.zip` contains JSON, not SQL, so `mariadb-import` is not applicable to it at all — the prohibition is redundant. More subtly, the instruction "Restart the JVM … then follow the 'app UP' path" is the correct recovery path, but the "app DOWN" scenario that an operator cannot restart the JVM (e.g., the DB was half-wiped and the app won't start due to Flyway state) is not addressed. This is a documentation gap (not a code defect), but the runbook's audience is "on-call operators" and the scenario of a non-restartable JVM is a real edge case.

**Fix:** Clarify the "app DOWN" section to acknowledge the non-restartable scenario explicitly:
```markdown
If the JVM cannot restart (schema in inconsistent state): contact the database administrator
to restore the MariaDB dump taken before the wipe (this is outside the scope of Phase 76;
a disaster-recovery SQL procedure is Phase 77 QUAL-05 scope).
```

---

_Reviewed: 2026-05-14T22:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
