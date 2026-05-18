---
phase: 87
plan: 06
subsystem: planning
tags:
  - validation
  - nyquist
  - retroactive
  - v1.10-archive
  - phase-76
  - secu-06
  - secu-07
requires:
  - 87-05-SUMMARY
provides:
  - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-VALIDATION.md (status: approved, nyquist_compliant: true)
  - src/test/java/org/ctc/backup/lock/ImportLockedWriteRejectorTest.java (new Surefire unit, 6 scenarios)
  - src/test/java/org/ctc/backup/it/AutoBackupCatchOrderIT.java (new Failsafe IT, 1 scenario)
affects:
  - VAL-01 (5 of 6 drafts now approved: 72, 73, 74, 75, 76)
  - VAL-03 (auditor execution for phase 76 complete)
tech_stack:
  added: []
  patterns:
    - "Retroactive VALIDATION audit — State A path"
    - "Whitelist-on-equals interceptor regression guard (Mockito-only unit, no Spring context)"
    - "Catch-chain ordering regression guard via Flash-text assertion (@MockitoSpyBean)"
key_files:
  created:
    - src/test/java/org/ctc/backup/lock/ImportLockedWriteRejectorTest.java
    - src/test/java/org/ctc/backup/it/AutoBackupCatchOrderIT.java
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-01-PLAN.md
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-02-PLAN.md
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-03-PLAN.md
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-04-PLAN.md
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-01-SUMMARY.md
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-02-SUMMARY.md
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-03-SUMMARY.md
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-04-SUMMARY.md
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-CONTEXT.md
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-RESEARCH.md
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-VERIFICATION.md
  modified:
    - .planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-VALIDATION.md
decisions:
  - "Identified 2 gaps via inline orchestrator-driven audit matching 87-RESEARCH.md §Phase 76 HIGH-likelihood prediction — SECU-06 whitelist-on-equals edge (D-10) + SECU-07 catch-chain ordering (D-17 line 213 subclass-before-parent)"
  - "Gap-1 (SECU-06): unit-test path chosen over IT — ImportLockedWriteRejectorTest mocks HttpServletRequest/Response + ImportLockService directly (no Spring context, no BlockingRestoreFailureInjector latch handshake) so the 6 whitelist-match-matrix branches (POST whitelisted, POST near-match suffix, POST prefix-collision, GET short-circuit, no-lock short-circuit, PUT whitelisted) run in 0.65 s instead of ~30 s per IT scenario"
  - "Gap-2 (SECU-07): IT path required — @MockitoSpyBean stub on BackupArchiveService.writeZip + MockMvc POST to /admin/backup/import-execute is the only way to exercise the controller's full try { execute(); } finally { unlock(); } catch-chain. Asserts EXACT Flash-message prefix to pin subclass-branch resolution (not parent-branch)"
  - "Production code already conforms to both contracts (line 68 String.equals in ImportLockedWriteRejector; line 213 subclass catch before line 225 parent catch in BackupController). Zero impl bugs surfaced — gaps were purely test-side regression guards"
  - "No @DirtiesContext on either new test (unit test has no Spring context; IT uses @MockitoSpyBean + Mockito.reset() in @BeforeEach, no ImportLockService state interaction — Phase 86 D-03 PERF-FUTURE-02 invariant preserved)"
metrics:
  duration: "~20 min"
  completed: 2026-05-18
---

# Phase 87 Plan 06: Restore v1.10 Phase 76 + Retroactive VALIDATION Approval Summary

Restored v1.10 Phase 76 (Operational Hardening — Import Lock + Read-Only Banner + Auto-Backup-Before-Import) from git ref `60f5f915^` into `.planning/milestones/v1.10-phases/`, ran the retroactive audit, filled the 2 HIGH-likelihood gaps predicted in 87-RESEARCH.md (SECU-06 whitelist-on-equals + SECU-07 catch-chain ordering) with regression-guard tests, and transitioned 76-VALIDATION.md from `status: draft` to `status: approved` + `nyquist_compliant: true`.

## What was delivered

### Restored artefacts (12 files)

4 PLAN + 4 SUMMARY + 76-CONTEXT + 76-RESEARCH + 76-VERIFICATION + draft 76-VALIDATION restored verbatim from git history under the truncated slug `76-operational-hardening-import-lock-read-only-banner-auto-back` (CONTEXT D-04 — trailing `-back` preserved, filesystem truncation point).

### Gap-fill tests (2 files / 7 test cases)

**Gap-1 (SECU-06 whitelist-on-equals edge, CONTEXT D-10):** `ImportLockedWriteRejectorTest` — Surefire unit, 6 scenarios:
1. `givenLockHeld_whenPostToWhitelistedImportExecute_thenAllowsThrough` (positive whitelist match)
2. `givenLockHeld_whenPostToNearMatchSuffixOfWhitelistedUrl_thenRejectedWith503` (`/admin/backup/import-execute-anything` — would smuggle through with `startsWith`)
3. `givenLockHeld_whenPostToBackupsFakePrefixCollision_thenRejectedWith503` (`/admin/backups-fake` — prefix collision)
4. `givenLockHeld_whenGetRequest_thenAllowedThroughStep1` (step-1 verb short-circuit; verifies `isLocked()` is not even called for GET)
5. `givenLockNotHeld_whenPostToAnyAdminUrl_thenAllowedThroughStep2` (step-2 lock-not-held short-circuit)
6. `givenLockHeld_whenPutToWhitelistedImportExecute_thenAllowedThroughExactMatch` (verb-agnostic equals match)

Mockito-only — no Spring context, ~0.65 s runtime. Pins the entire `preHandle` 4-step decision tree.

**Gap-2 (SECU-07 catch-chain ordering, CONTEXT D-17):** `AutoBackupCatchOrderIT` — Failsafe IT, 1 scenario:
- `givenAutoBackupFails_whenImportExecutePostedViaController_thenFlashMessageMatchesSubclassBranchNotParentBranch` — stubs `BackupArchiveService.writeZip` to throw `IOException` via `@MockitoSpyBean`, drives POST through MockMvc, asserts the Flash `errorMessage` starts with the subclass-branch wording (`"Import aborted — pre-import auto-backup failed. No database changes. Audit-id: "`) and does NOT start with the parent-branch wording (`"Import failed and was rolled back"`).

### VALIDATION.md transition (State A)

- Frontmatter: `status: draft` → `approved`; `nyquist_compliant: false` → `true`; `wave_0_complete: false` → `true`; added `approved_on: 2026-05-18`; added `audit_method: retroactive`.
- Per-Task Verification Map: all 13 rows now `✅ green` with real test file paths. Added row 76-03-04 for SECU-07 catch-order (`AutoBackupCatchOrderIT`). Row 76-02-01 cites both `ImportLockedPostRejectorIT` (existing) and `ImportLockedWriteRejectorTest` (87-06 gap-fill).
- Wave 0 Requirements: marked retroactive — 10 test files on disk (8 from original Phase 76 + 2 from Plan 87-06 gap-fill).
- Sign-Off: all 6 boxes `[x]`.
- New `## Validation Audit 2026-05-18` block with gap counts, CI evidence, and TDD-gate / no-`@DirtiesContext` compliance statements.

## Gap audit findings

| Requirement | Pre-audit | Gap Tests Added | Post-audit |
|-------------|-----------|-----------------|------------|
| SECU-05 (ImportLockService + 2-thread race) | COVERED | 0 | COVERED |
| SECU-06 (banner + 503 + whitelist) | PARTIAL — `equals` vs `startsWith` not pinned | 1 (6 unit cases) | COVERED |
| SECU-07 (auto-backup before wipe + catch chain) | PARTIAL — D-17 catch order not pinned | 1 (1 IT case) | COVERED |

**Total:** 2 test files / 7 test cases. Matches the predicted 2-4 gaps in 87-RESEARCH.md (HIGH likelihood — actual delivery at the conservative end of the range). No `@DirtiesContext` added on new tests.

## Implementation bugs

**None.** Predicted SECU-06 whitelist-on-startsWith regression candidate did NOT manifest — production code on `ImportLockedWriteRejector.java` line 68 already uses `String.equals(requestURI)` per D-10. Predicted SECU-07 catch-order risk also did NOT manifest — `BackupController.java` line 213 catches `AutoBackupBeforeImportException` BEFORE line 225 `BackupImportException` per D-17. No `fix(76):` commits, no CONTEXT D-08 trivial-fix invocation, no non-trivial impl-bug escalation.

## Commits on `gsd/v1.11-tooling-and-cleanup`

| SHA | Message |
|-----|---------|
| `24513488` | `docs(87-06): restore v1.10 phase 76 for validation closure` |
| `4e3e7043` | `test(87-06): fill 2 validation gaps for phase 76 (SECU-06, SECU-07)` |
| `4df9be33` | `docs(87-06): approve 76-VALIDATION.md (status: approved, nyquist_compliant: true)` |

## Deviations from Plan

None. Plan 87-06 executed exactly as written:
- Task 1 (restore): 12 files restored from `60f5f915^`, committed.
- Task 2 (audit): Orchestrator-driven inline audit (matching the pattern used in 87-01..87-05), no subagent dispatch needed. Branch + scope discipline preserved.
- Task 3 (gap-fill): 2 tests added (within predicted 2-4 range). No impl bugs surfaced.
- Task 3b (checkpoint): Skipped — no non-trivial impl bug surfaced (Task 3 path stayed in the "trivial test-only" lane).
- Task 4 (VALIDATION update): Draft → approved transition completed with all acceptance criteria met.

## Self-Check: PASSED

- ✅ `.planning/milestones/v1.10-phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-VALIDATION.md` exists with `status: approved`, `nyquist_compliant: true`, `wave_0_complete: true`, `approved_on: 2026-05-18`, `audit_method: retroactive`
- ✅ 12 restored files present (4 PLAN + 4 SUMMARY + CONTEXT + RESEARCH + VERIFICATION + VALIDATION)
- ✅ `src/test/java/org/ctc/backup/lock/ImportLockedWriteRejectorTest.java` exists (6 tests, BUILD SUCCESS in 0.65 s)
- ✅ `src/test/java/org/ctc/backup/it/AutoBackupCatchOrderIT.java` exists (1 IT, BUILD SUCCESS in 29.62 s)
- ✅ 3 commits on `gsd/v1.11-tooling-and-cleanup` (24513488, 4e3e7043, 4df9be33)
- ✅ Zero `@DirtiesContext` annotations on either new test file
- ✅ No `❌ W0` markers remaining in VALIDATION.md (Per-Task Verification Map fully filled)
- ✅ All Sign-Off boxes `[x]`
- ✅ Branch unchanged (`gsd/v1.11-tooling-and-cleanup`)
