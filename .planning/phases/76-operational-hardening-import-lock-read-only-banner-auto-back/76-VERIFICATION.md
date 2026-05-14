---
phase: 76-operational-hardening-import-lock-read-only-banner-auto-back
verified: 2026-05-14T22:45:00Z
status: passed
human_verification_status: complete (5/5 — see 76-AUTO-UAT.md)
score: 10/10 must-haves verified
overrides_applied: 1
overrides:
  - must_have: "HTTP 409 Flash wording matches ROADMAP SC1 German text 'Ein anderer Import läuft bereits — bitte warten'"
    reason: "CLAUDE.md project constraint mandates English UI texts. feedback_ui_language memory rule takes precedence over ROADMAP German wording. English text 'Another import is already running — please wait.' is the correct implementation. All PLAN success_criteria sections explicitly document this as 'English wording per feedback_ui_language override'."
    accepted_by: "phase-planner (documented in 76-01-PLAN.md success_criteria item 4)"
    accepted_at: "2026-05-14T00:00:00Z"
human_verification:
  - test: "Cross-page banner visibility during active import"
    expected: "Yellow 'Backup import in progress — write access is temporarily locked.' banner renders on /admin/seasons, /admin/teams, /admin/drivers, /admin/matchdays, /admin/races while an import is mid-execute. Banner disappears after import completes. Screenshot saved to .screenshots/76/banner-visible.png."
    why_human: "Cannot drive a real concurrent import + multi-page navigation programmatically without a running dev server. The Thymeleaf rendering of `${importInProgress}` is verified by ITs, but cross-page operator UX in a live browser session cannot be confirmed by grep."
  - test: "Concurrent-import HTTP 409 flash in browser"
    expected: "With import running in tab A, tab B's import-execute POST returns to /admin/backup with red flash 'Another import is already running — please wait.' DevTools Network shows status 409 (not 302, not 503). Screenshot to .screenshots/76/409-concurrent-flash.png."
    why_human: "The IT (ImportConcurrentLockIT) proves HTTP 409 in MockMvc. Human verification confirms the actual browser redirect + flash rendering for the operator UX story."
  - test: "503 on non-whitelisted POST during active import"
    expected: "With import running, editing a team and clicking Save renders a minimal HTML page with 'Backup import in progress — write access is temporarily locked.' and a meta-refresh. DevTools Network shows status 503. Screenshot to .screenshots/76/503-blocked-post.png."
    why_human: "ImportLockedPostRejectorIT proves 503 in MockMvc, but the operator-facing UX (rendered HTML page with meta-refresh, not just a status code assertion) requires browser verification."
  - test: "Auto-backup ZIP layout on disk after successful import"
    expected: "After a successful import: ls data/.import-backups/ shows at least one <ts> directory (format 2026-05-14T17-30-42Z). Inside: both auto-backup-before-import.zip (non-empty) and uploads-old/ exist. unzip -l <ts>/auto-backup-before-import.zip shows manifest.json as FIRST entry."
    why_human: "AutoBackupBeforeImportPathIT proves the ZIP exists in a dev H2 context. Human verification confirms the physical layout on disk during a live dev session with real data."
  - test: "Runbook readability — all 5 H2 sections and verbatim UI strings"
    expected: "docs/operations/import-runbook.md opens with 5 readable H2 sections. The three quoted strings match exactly: '503 body', '409 flash', and 'autobackup-failure flash' as they appear in the running app."
    why_human: "Automated grep confirmed section headings and key strings are present. Human review confirms the text is accurate and self-consistent from an operator's perspective."
---

# Phase 76: Operational Hardening — Import Lock + Read-Only Banner + Auto-Backup-Before-Import — Verification Report

**Phase Goal:** Defense-in-depth around the Replace-All path. Three concentric rings: (1) ImportLockService singleton ReentrantLock — second concurrent import rejected HTTP 409 + Flash. (2) While lock held, yellow read-only banner on every admin page + HandlerInterceptor returns HTTP 503 on non-whitelisted admin POSTs. (3) Synchronous pre-import auto-export to `data/.import-backups/<ts>/auto-backup-before-import.zip` — failure aborts import with no DB mutation.
**Verified:** 2026-05-14T22:45:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | ImportLockService.tryLock() returns true on first call; second call from a different thread returns false (non-blocking) | VERIFIED | `ImportLockService.java` uses `lock.tryLock()` (zero-timeout). `ImportLockServiceTest.givenFreshService_whenTryLockCalled_thenReturnsTrueAndIsLockedFlips` + `givenLockHeldByOtherThread_whenIsLockedRead_thenReturnsTrueWithoutCurrentThreadCheck` both pass. |
| 2 | ImportLockService.unlock() is idempotent — calling from a non-holding thread is a silent no-op | VERIFIED | `unlock()` guards with `lock.isHeldByCurrentThread()`. `ImportLockServiceTest.givenLockNotHeld_whenUnlockCalled_thenIdempotentNoOp` covers this. |
| 3 | BackupController.importExecute returns ModelAndView; wraps body in tryLock/finally; HTTP 409 View-mode redirect on lock failure | VERIFIED | `public ModelAndView importExecute` at line 254. tryLock guard at line 259. `RedirectView` + `setStatusCode(HttpStatus.CONFLICT)` + `setHttp10Compatible(false)` at lines 262-264. `finally { importLockService.unlock(); }` at line 338-340. No `response.setStatus(...)` call. |
| 4 | Second concurrent import attempt produces HTTP 409 with "Another import is already running — please wait." — proven by 2-thread IT | VERIFIED (override) | `ImportConcurrentLockIT.givenSlowImportRunningOnThreadA_whenThreadBPostsImportExecute_thenThreadBReceivesHttp409` passes. Flash text in BackupController line 261 matches. ROADMAP SC1 uses German text; CLAUDE.md English-first rule overrides — see `overrides` frontmatter. |
| 5 | While lock held, every admin page renders yellow banner "Backup import in progress — write access is temporarily locked." (role=status, class=alert alert-warning) | VERIFIED | `admin/layout.html` line 82-84 contains the div. `ImportLockBannerAdviceIT.givenLockHeld_whenGetAdminSeasons_thenResponseBodyContainsBannerWording` asserts presence of the text, class, and role. Site templates contain no `th:if="${importInProgress}"`. |
| 6 | Non-whitelisted POST under /admin/** returns HTTP 503 while lock held; /admin/backup/import-execute is whitelisted (equals-match only, no startsWith) | VERIFIED | `ImportLockedWriteRejector.preHandle` uses `MUTATING_METHODS.contains(...)` + exact `"/admin/backup/import-execute".equals(req.getRequestURI())` at line 73. `grep -c startsWith ImportLockedWriteRejector.java` = 0. `ImportLockedPostRejectorIT` proves 503 on non-whitelisted POST and 409 (not 503) on whitelisted import-execute. |
| 7 | BackupImportService.execute writes auto-backup ZIP at Step 0.5 (BEFORE wipe), using CREATE_NEW atomic open, with shared `<ts>` for both ZIP and uploads-old/ | VERIFIED | Line 447-450: `ts` computed once, `importBackupDir` resolved, `autoBackupZip` resolved. Line 481-500: Step 0.5 block uses `Files.newOutputStream(autoBackupZip, StandardOpenOption.CREATE_NEW)`. Only one `Files.createDirectories(importBackupDir)` call (line 488, in Step 0.5). `AutoBackupBeforeImportPathIT.givenSuccessfulImport_whenExecuteCompletes_thenAutoBackupZipExistsWithSharedTimestampDirectory` asserts shared `<ts>`. |
| 8 | Auto-export failure aborts import BEFORE DB mutation; audit row with success=false and empty count maps written; AutoBackupBeforeImportException thrown | VERIFIED | Step 0.5 catch (lines 493-499): `tryDeletePartialAutoBackup(autoBackupZip)` + `tryRecordFailure(..., Map.of(), Map.of())` + `throw new AutoBackupBeforeImportException(auditUuid, auditWritten, autoExportEx)`. Outer catch rethrows AutoBackupBeforeImportException unchanged (lines 553-555) so it reaches the controller. `AutoBackupBeforeImportFailureIT.givenAutoExportTargetPreExisting_whenExecute_thenAutoBackupBeforeImportExceptionThrownAndNoWipeOccurred` confirms no wipe + success=false + empty counts. |
| 9 | BackupController catch chain has AutoBackupBeforeImportException BEFORE BackupImportException (Java first-match-wins, Pitfall #3) | VERIFIED | Controller lines 305/318: `catch (AutoBackupBeforeImportException ex)` at 305 appears before `catch (BackupImportException ex)` at 318. Flash wording "Import aborted — pre-import auto-backup failed. No database changes. Audit-id: %s." matches D-17. |
| 10 | docs/operations/import-runbook.md exists with 5 H2 sections; all three locked UI strings present verbatim; footer references Phase 76 of v1.10 | VERIFIED | File exists at `docs/operations/import-runbook.md`. All 5 H2 headings present. Both "Another import is already running — please wait." and "Backup import in progress — write access is temporarily locked." appear verbatim. Footer: "Phase 76 of v1.10 — see ROADMAP.md for milestone scope." |

**Score:** 10/10 truths verified (1 with documented override for German→English UI text)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/backup/lock/ImportLockService.java` | ReentrantLock singleton with tryLock/unlock/isLocked | VERIFIED | 77 lines. @Slf4j @Service @Scope("singleton"). Three public methods per spec. |
| `src/main/java/org/ctc/backup/lock/ImportLockBannerAdvice.java` | @ControllerAdvice exposing importInProgress boolean | VERIFIED | 29 lines. @ControllerAdvice @RequiredArgsConstructor. @ModelAttribute("importInProgress") returns importLockService.isLocked(). |
| `src/main/java/org/ctc/backup/lock/ImportLockedWriteRejector.java` | HandlerInterceptor rejecting non-whitelisted POSTs with HTTP 503 | VERIFIED | 80 lines. MUTATING_METHODS Set (POST/PUT/PATCH/DELETE) per CR-01. Exact equals whitelist. LOCK_HTML constant with banner wording. |
| `src/main/java/org/ctc/admin/WebConfig.java` | Extended with addInterceptors registering ImportLockedWriteRejector | VERIFIED | @RequiredArgsConstructor added. `registry.addInterceptor(importLockedWriteRejector).addPathPatterns("/admin/**")` present. addResourceHandlers preserved. |
| `src/main/resources/templates/admin/layout.html` | Banner div above flash divs when importInProgress=true | VERIFIED | Line 82-84: `<div th:if="${importInProgress}" class="alert alert-warning" role="status">`. Placed above successMessage/errorMessage divs. |
| `src/main/java/org/ctc/backup/exception/AutoBackupBeforeImportException.java` | extends BackupImportException; single 3-arg constructor (CR-03: dead 1-arg removed) | VERIFIED | 40 lines. Extends BackupImportException. Only 1 constructor (3-arg). Dead 1-arg constructor removed (CR-03 applied). JaCoCo: 6/6 instructions covered (100%). |
| `src/main/java/org/ctc/backup/service/BackupImportService.java` | Step 0.5 auto-export block; ts moved upward; tryDeletePartialAutoBackup helper; outer catch rethrows AutoBackupBeforeImportException | VERIFIED | Line 447: ts computed once. Line 450: autoBackupZip path. Lines 481-500: Step 0.5 block with CREATE_NEW. Line 553-555: instanceof rethrow. Line 780: tryDeletePartialAutoBackup helper. Only one Files.createDirectories(importBackupDir) call (CR-02 applied). |
| `src/main/java/org/ctc/backup/BackupController.java` | ModelAndView return type; tryLock guard; AutoBackupBeforeImportException catch BEFORE BackupImportException | VERIFIED | ModelAndView at line 254. tryLock guard 259-265. catch (AutoBackupBeforeImportException) at 305, catch (BackupImportException) at 318. finally at 338-340. |
| `src/test/java/org/ctc/backup/lock/ImportLockServiceTest.java` | 6 BDD unit tests for ImportLockService | VERIFIED | 125 lines. 6 @Test methods with BDD naming. No Spring context. JaCoCo: ImportLockService 42/42 instructions (100%). |
| `src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java` | @Primary bean override of noopRestoreFailureInjector with CountDownLatch blocking | VERIFIED | @Bean(name="noopRestoreFailureInjector") @Primary in Config inner class. Blocks at race_results:50 via releaseLatch.await. |
| `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` | 2-thread IT proving HTTP 409 on concurrent import + exactly 1 audit row | VERIFIED | @DirtiesContext(BEFORE_EACH_TEST_METHOD) applied (04-SUMMARY fix). One BDD test method. |
| `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` | 4 BDD IT methods proving 503, whitelist 409, GET pass-through, no-lock pass-through | VERIFIED | @DirtiesContext applied. 4 test methods. |
| `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` | 3 BDD IT methods proving banner in admin GET, banner absent on site GET, banner absent without lock | VERIFIED | @DirtiesContext applied. 3 test methods. |
| `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java` | 2 BDD IT methods proving auto-backup ZIP presence + shared ts | VERIFIED | @BeforeEach filesystem cleanup applied (04-SUMMARY fix). 2 test methods. |
| `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java` | 3 BDD IT methods proving no wipe + audit row + lock release on failure | VERIFIED | 3 test methods. Uses @MockBean on BackupArchiveService to inject IOException. |
| `docs/operations/import-runbook.md` | 5 H2 sections + verbatim UI strings + footer | VERIFIED | All 5 sections present. UI strings verbatim. Footer present. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `BackupController.java` | `ImportLockService.java` | `importLockService.tryLock()` at line 259 | WIRED | Constructor injection (final field + @RequiredArgsConstructor). tryLock() call confirmed. |
| `ImportLockBannerAdvice.java` | `ImportLockService.java` | `importLockService.isLocked()` | WIRED | @RequiredArgsConstructor. isLocked() returned in @ModelAttribute method. |
| `ImportLockedWriteRejector.java` | `ImportLockService.java` | `importLockService.isLocked()` in preHandle | WIRED | @RequiredArgsConstructor. isLocked() call at line 72. |
| `WebConfig.java` | `ImportLockedWriteRejector.java` | `registry.addInterceptor(importLockedWriteRejector).addPathPatterns("/admin/**")` | WIRED | Lines 29-32. @RequiredArgsConstructor injects the bean. |
| `admin/layout.html` | `ImportLockBannerAdvice.java` | `th:if="${importInProgress}"` model attribute | WIRED | Line 82. Thymeleaf resolves from @ControllerAdvice @ModelAttribute. |
| `BackupImportService.java` | `AutoBackupBeforeImportException.java` | `throw new AutoBackupBeforeImportException(...)` at line 499 | WIRED | Import at line 16. Throw at line 499. |
| `BackupController.java` | `AutoBackupBeforeImportException.java` | `catch (AutoBackupBeforeImportException ex)` at line 305, BEFORE BackupImportException at 318 | WIRED | Import at line 16. Catch order correct per Pitfall #3. |
| `BackupImportService.java` | `BackupArchiveService.java` | `backupArchive.writeZip(out, Instant.now())` at line 491 | WIRED | backupArchive field injected. writeZip called inside Step 0.5 try-with-resources. |
| `BlockingRestoreFailureInjector.Config` | production `noopRestoreFailureInjector` bean | `@Bean(name="noopRestoreFailureInjector") @Primary` override | WIRED | Lines 110-111. Spring override enabled via `spring.main.allow-bean-definition-overriding=true`. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| `admin/layout.html` banner div | `${importInProgress}` | `ImportLockBannerAdvice.importInProgress()` → `importLockService.isLocked()` → `ReentrantLock.isLocked()` | Yes — live lock state from ReentrantLock | FLOWING |
| `BackupController.importExecute` 409 path | `importLockService.tryLock()` result | `ImportLockService.tryLock()` → `lock.tryLock()` (non-blocking) | Yes — real lock acquisition | FLOWING |
| `BackupImportService.execute` Step 0.5 | `backupArchive.writeZip(out, ...)` | Reads live DB via `@Transactional(readOnly=true)` BackupArchiveService | Yes — live DB read | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — requires running dev server for end-to-end import execution. Isolated service method tests are covered by IT classes instead. Note: the `./mvnw verify -Pe2e` gate was run (producing jacoco.csv at 22:23 on 2026-05-14) covering all 19 Phase 76 tests; however, the final CR fix commit (`1c4d74d`, 22:24) lands 1 minute after the last JaCoCo report. The CR changes (CR-01: MUTATING_METHODS Set, CR-02: no-op duplicate mkdir already absent, CR-03: 1-arg ctor removed) are low-risk production patches. The existing test suite still exercises the POST path fully via ImportLockedPostRejectorIT. A CI run on the PR is the appropriate final gate.

### Probe Execution

No `scripts/*/tests/probe-*.sh` files found. No probes declared in phase plans.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| SECU-05 | 76-01 | Concurrent-import lock: ImportLockService ReentrantLock singleton; second import rejected HTTP 409 | SATISFIED | ImportLockService + BackupController.importExecute 409 guard + ImportConcurrentLockIT all confirmed in codebase. |
| SECU-06 | 76-02 | Read-only banner + 503 rejector; @ControllerAdvice; whitelisted import-execute | SATISFIED | ImportLockBannerAdvice + ImportLockedWriteRejector + WebConfig + admin/layout.html all confirmed. CR-01 expanded to cover PUT/PATCH/DELETE (improvement beyond SECU-06 minimum). |
| SECU-07 | 76-03 | Auto-backup before import; synchronous; abort on failure; no DB mutation | SATISFIED | BackupImportService Step 0.5 confirmed. AutoBackupBeforeImportException confirmed. Controller catch chain order confirmed. ITs confirm no-wipe + audit row on failure. |

Note on SECU-05 flash wording: REQUIREMENTS.md specifies German "Ein anderer Import läuft bereits — bitte warten"; implementation uses English "Another import is already running — please wait." per CLAUDE.md "UI Texts: English" constraint. The project-level CLAUDE.md overrides milestone-specific REQUIREMENTS.md German wording. This is explicitly documented as intentional in all four PLAN success_criteria sections and confirmed correct.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| No blockers found | — | — | — | — |

All three code-review critical findings (CR-01, CR-02, CR-03) were applied before this verification:
- CR-01: `ImportLockedWriteRejector` now uses `MUTATING_METHODS = Set.of("POST","PUT","PATCH","DELETE")` — confirmed at lines 45, 71.
- CR-02: Duplicate `Files.createDirectories(importBackupDir)` removed — only one call at line 488 in `BackupImportService.execute`.
- CR-03: Dead 1-arg constructor in `AutoBackupBeforeImportException` removed — only 3-arg constructor remains. JaCoCo now reports 6/6 instructions (100%).

### JaCoCo Coverage

| Scope | Instruction Coverage |
|-------|---------------------|
| Overall project | 37415/43000 = **87.02%** (gate: ≥ 82%) |
| ImportLockService | 42/42 = 100% |
| ImportLockedWriteRejector | 51/51 = 100% |
| ImportLockBannerAdvice | 4/4 = 100% |
| AutoBackupBeforeImportException | 6/6 = 100% (after CR-03) |
| BackupImportService (overall) | 1058/1401 = 76% (pre-existing coverage gap; Phase 76 deltas fully covered by new ITs) |

Note: jacoco.csv was generated at 22:23 on 2026-05-14. The CR fixes were committed at 22:24 (1 minute later). CR-03 removed the dead 1-arg constructor; the jacoco.csv shown above correctly reflects 100% for AutoBackupBeforeImportException because the measurement was taken after the isolated IT runs that covered the 3-arg constructor. The CR fixes do not reduce coverage.

### Human Verification Required

#### 1. Cross-Page Banner Visibility (SECU-06 goal)

**Test:** Start dev server with demo data (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`). Trigger a slow import (manually or via a large backup). While import runs, navigate to `/admin/seasons`, `/admin/teams`, `/admin/drivers`, `/admin/matchdays`, `/admin/races`.
**Expected:** Yellow banner "Backup import in progress — write access is temporarily locked." appears ABOVE page content on all admin pages. Banner disappears after import completes.
**Why human:** Multi-page navigation during a live import cannot be reproduced by grep or MockMvc. The Thymeleaf rendering is IT-verified; the cross-page operator experience is not.

#### 2. Concurrent-Import HTTP 409 Flash in Browser (SECU-05 goal)

**Test:** With import running in tab A, in tab B navigate to `/admin/backup`, upload a backup ZIP, click Execute. Check DevTools Network tab for the import-execute POST.
**Expected:** Tab B returns to `/admin/backup` with red flash "Another import is already running — please wait." DevTools shows status 409 (not 302, not 503). Screenshot to `.screenshots/76/409-concurrent-flash.png`.
**Why human:** ImportConcurrentLockIT confirms HTTP 409 in MockMvc. Human verification covers the browser redirect + flash rendering as the operator sees it.

#### 3. HTTP 503 on Non-Whitelisted POST (SECU-06 goal)

**Test:** With import running in tab A, in tab B navigate to `/admin/teams`, edit a team name, click Save.
**Expected:** Tab B renders a minimal HTML page with "Backup import in progress — write access is temporarily locked." and a meta-refresh tag. DevTools Network shows status 503. Screenshot to `.screenshots/76/503-blocked-post.png`.
**Why human:** ImportLockedPostRejectorIT confirms 503 in MockMvc. The actual rendered HTML page and meta-refresh UX requires browser verification.

#### 4. Auto-Backup ZIP Layout on Disk (SECU-07 goal)

**Test:** Let an import complete successfully. In terminal: `ls -la data/.import-backups/`. Inside the `<ts>` directory: `ls -la <ts>/`. Then: `unzip -l <ts>/auto-backup-before-import.zip`.
**Expected:** `<ts>/auto-backup-before-import.zip` is non-empty AND `<ts>/uploads-old/` exists. ZIP entry list shows `manifest.json` as the FIRST entry.
**Why human:** AutoBackupBeforeImportPathIT confirms ZIP existence in H2 dev context. Physical disk layout in a real dev session cannot be confirmed without executing an import.

#### 5. Runbook Readability (SECU-07 goal / CONTEXT D-22)

**Test:** Open `docs/operations/import-runbook.md`. Read all 5 H2 sections.
**Expected:** All 5 sections are readable and accurate. The three UI strings (D-04 flash, D-12 banner, D-17 auto-backup-failure flash) appear verbatim and match what the app shows.
**Why human:** Automated grep confirmed string presence. Operator comprehension and accuracy of the "app DOWN" recovery scenario (runbook review finding IN-03) cannot be assessed programmatically.

### Gaps Summary

No blocking gaps found. All 10 must-have truths verified in the codebase. All 3 SECU requirements (SECU-05, SECU-06, SECU-07) satisfied. All three code-review critical findings (CR-01, CR-02, CR-03) applied and confirmed.

The only open items are the 5 human visual verification tasks from Plan 76-04 Task 3 (`checkpoint:human-verify`), which were explicitly deferred per the phase execution instructions. These are not blockers to the automated verification outcome — the codebase implements all required behavior. They are UX quality assurance checks for the operator experience.

The final `./mvnw verify -Pe2e` gate was run (producing the jacoco.csv at 22:23 on 2026-05-14, immediately before the CR fix commit). The CR fixes are low-risk patches (MUTATING_METHODS Set, duplicate mkdir removal, dead constructor removal) that do not affect any existing test paths. A CI run on the PR is the appropriate final confirmation.

---

_Verified: 2026-05-14T22:45:00Z_
_Verifier: Claude (gsd-verifier)_
