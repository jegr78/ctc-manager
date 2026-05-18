---
phase: 87
plan: 04
subsystem: nyquist-validation-closure
tags: [validation, retroactive-audit, v1.10-archive, secu-04, backup-import]
requires:
  - 87-03 (Phase 73 retroactive VALIDATION approved)
provides:
  - 74-VALIDATION.md status: approved (3 of 6 drafts approved: 72, 73, 74)
  - .planning/milestones/v1.10-phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/ archive directory (24 files)
  - BackupUploadExceptionHandlerScopeIT (SECU-04 structural invariant lock-in)
affects:
  - VAL-01 (3 of 6 drafts approved)
  - VAL-03 (auditor execution complete for Phase 74)
tech_stack:
  added: []
  patterns:
    - "Reflective @ControllerAdvice scope assertion (structural invariant test)"
    - "Mixed-return-type binding-ambiguity prevention (locked via test)"
key_files:
  created:
    - src/test/java/org/ctc/backup/exception/BackupUploadExceptionHandlerScopeIT.java
    - .planning/milestones/v1.10-phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/ (24 restored files)
  modified:
    - .planning/milestones/v1.10-phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md (draft → approved)
decisions:
  - "SECU-04 structural-invariant gap filled with reflective IT (3 assertions: class @ControllerAdvice, method @ExceptionHandler+String-return, GlobalExceptionHandler-non-registration)"
  - "Original draft test name BackupImportSchemaVersionMismatchIT reconciled to on-disk BackupImportSchemaMismatchIT (in-flight rename)"
  - "All 12 Per-Task Verification Map rows now ✅ green; Wave 0 retroactively satisfied"
metrics:
  duration_minutes: ~12
  tasks_completed: 4
  files_created: 25
  files_modified: 1
  gaps_found: 1
  gaps_resolved: 1
  impl_bugs_fixed: 0
  impl_bugs_deferred: 0
completed_on: 2026-05-18
---

# Phase 87 Plan 04: Restore v1.10 Phase 74 + Retroactive VALIDATION Approval Summary

Restored 24 archived artefacts of v1.10 Phase 74 (Backup Import Preview + ZIP Hardening + Multipart Config + Schema Gate) from git ref `60f5f915^`, ran the equivalent of `/gsd:validate-phase 74` State A against the restored directory, generated 1 structural gap-fill IT for the SECU-04 advice-scope invariant, and transitioned the existing `74-VALIDATION.md` draft to `status: approved` with `nyquist_compliant: true` + `audit_method: retroactive`.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Restore v1.10 Phase 74 artefacts from `60f5f915^` (24 files, NO `74-` prefix on PLAN/SUMMARY per R-03 verbatim D-04) | `db41a1d6` | `.planning/milestones/v1.10-phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/` (10 PLAN + 10 SUMMARY + 74-CONTEXT + 74-RESEARCH + 74-VERIFICATION + 74-VALIDATION) |
| 2 | Inline auditor pass — verified every test class in the draft map exists on disk; identified 1 gap (V10 SECU-04 structural invariant) | (no separate commit — folded into Task 3) | — |
| 3 | Land gap-fill test: `BackupUploadExceptionHandlerScopeIT` (3 reflective assertions); Failsafe targeted run green (28.91 s) | `fb68a87e` | `src/test/java/org/ctc/backup/exception/BackupUploadExceptionHandlerScopeIT.java` |
| 4 | Update 74-VALIDATION.md draft → approved (State A): frontmatter flips, Per-Task Map reconciled, Wave 0 retroactively satisfied, all Sign-Off boxes `[x]`, "## Validation Audit 2026-05-18" block appended | `7c58e121` | `.planning/milestones/v1.10-phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md` |

## Per-Requirement Coverage (IMPORT-01..04, SECU-01..04)

| REQ-ID | Verdict | Anchoring Tests |
|--------|---------|-----------------|
| IMPORT-01 | COVERED | `BackupImportServiceIT`, `BackupArchiveServiceReadIT`, `BackupArchiveServiceIT`, `BackupStagingCleanupIT` |
| IMPORT-02 | COVERED | `BackupImportSchemaMismatchIT` |
| IMPORT-03 | COVERED | `BackupImportPreviewTest`, `EntityRowCountTest`, `BackupImportConfirmFormValidationIT` |
| IMPORT-04 | COVERED | `BackupImportConfirmFormValidationTest`, `BackupImportConfirmFormValidationIT` |
| SECU-01 | COVERED | `BackupImportZipSlipIT`, `PathTraversalGuardTest` |
| SECU-02 | COVERED | `BackupImportZipBombIT`, `BackupImportLimitsTest`, `LimitedInputStreamTest` |
| SECU-03 | COVERED | `BackupImportMultipartLimitIT`, `BackupArchiveExceptionTest` |
| SECU-04 | COVERED (after gap-fill) | `BackupImportControllerSecurityIT` (behaviour), `BackupImportMultipartLimitIT` (runtime), **`BackupUploadExceptionHandlerScopeIT` (structural — NEW)** |

## Gap-Fill Detail (V10 — SECU-04 Structural Invariant)

**Found:** The original Per-Task Map covered SECU-04 behaviour (real Tomcat 100 MB limit → Flash redirect) but not the structural invariant — that `MaxUploadSizeExceededException` is bound to a **dedicated** `@ControllerAdvice` (`BackupUploadExceptionHandler`) and explicitly NOT to `GlobalExceptionHandler`. Phase 74 D-02 rationale: the two advices return different types (`String "redirect:..."` vs `ModelAndView`); merging them produces Spring binding ambiguity. A future PR could silently merge them — only a structural test catches that regression.

**Filled with:** `BackupUploadExceptionHandlerScopeIT` (105 lines, `@Tag("integration")`, `@SpringBootTest` on `dev` profile) with 3 reflective assertions:

1. `BackupUploadExceptionHandler.class.isAnnotationPresent(ControllerAdvice.class) == true` + bean registration check.
2. `BackupUploadExceptionHandler#handleMaxUploadSizeExceeded` declares `@ExceptionHandler(MaxUploadSizeExceededException.class)` and returns `String.class`.
3. `GlobalExceptionHandler` declares NO `@ExceptionHandler` whose `value()` is assignable from `MaxUploadSizeExceededException` (mixed-return-type guard).

**Test result:** 3 tests, 0 failures, 0 errors, 0 skipped, 28.91 s wallclock (Failsafe `failsafe:integration-test failsafe:verify -Dit.test='BackupUploadExceptionHandlerScopeIT'`).

## Deviations from Plan

**None — plan executed exactly as written.**

Per CONTEXT D-08 the plan reserved `fix(74): ...` for trivial impl bugs; none surfaced. The single auditor-identified gap was a structural test, not an impl bug. No checkpoint paths triggered (no architectural change, no non-trivial bug, no auth gate).

## Commits

| Hash | Type | Subject |
|------|------|---------|
| `db41a1d6` | docs | `docs(87-04): restore v1.10 phase 74 for validation closure` (24 files, 7111 insertions) |
| `fb68a87e` | test | `test(87-04): fill 1 validation gap for phase 74 (SECU-04 advice scope)` (105 lines, BackupUploadExceptionHandlerScopeIT) |
| `7c58e121` | docs | `docs(87-04): approve 74-VALIDATION.md (status: approved, nyquist_compliant: true)` (93 insertions, 33 deletions) |

3-commit shape per CONTEXT D-13 + 87-RESEARCH.md §"Sample Per-Plan Commit Sequence" (restore + test + approve; no `fix(74):` needed).

## CI Evidence

Re-uses Plan 87-03's CI evidence baseline: workflow run-id **`26008754136`** on `gsd/v1.11-tooling-and-cleanup` @ `b7f20b53`, conclusion: **success**, dispatched 2026-05-18T01:30:27Z, e2e step wallclock 23:00. The Phase 74 backup-import test suite (`BackupImportServiceIT`, `BackupImportSchemaMismatchIT`, `BackupImportZipSlipIT`, `BackupImportZipBombIT`, `BackupImportMultipartLimitIT`, `BackupImportControllerSecurityIT`, `BackupImportConfirmFormValidationIT`, `BackupStagingCleanupIT`, `BackupArchiveServiceReadIT`, `BackupImportE2ETest`, et al.) was green in that run.

The single new gap-fill IT (`BackupUploadExceptionHandlerScopeIT`) was verified locally (28.91 s wallclock); next CI run will absorb it. Wallclock headroom analysis per 87-RESEARCH.md §"Wallclock Baseline": 1 new `@SpringBootTest` IT cold-start = ~29 s, well within the 69 s budget toward the 5 % regression threshold (24:09).

## Acceptance Criteria — All Met

- [x] Restored 24 files at `.planning/milestones/v1.10-phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/` (PLAN filenames verbatim with NO `74-` prefix per R-03)
- [x] `/gsd:validate-phase 74` equivalent ran end-to-end against restored directory; returned 1 gap (resolved inside this plan)
- [x] Gap test (`BackupUploadExceptionHandlerScopeIT`) green (3 tests, 0 failures, 28.91 s)
- [x] `74-VALIDATION.md` frontmatter: `status: approved`, `nyquist_compliant: true`, `wave_0_complete: true`, `approved_on: 2026-05-18`, `audit_method: retroactive`
- [x] Per-Task Verification Map: 12 of 12 rows `✅ green`; no `❌ W0` markers remaining
- [x] Wave 0 Requirements: 3 of 3 boxes `[x]`
- [x] Validation Sign-Off: 6 of 6 boxes `[x]`
- [x] "## Validation Audit 2026-05-18" block appended with gap counts + CI run-id citation `26008754136`
- [x] Approval line: `**Approval:** approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-04`
- [x] All 3 commits land on `gsd/v1.11-tooling-and-cleanup` (branch unchanged throughout)

## VAL Anchor Progress

- **VAL-01** (drafts → approved): **3 of 6 complete** (Phase 72 ✅, Phase 73 ✅, Phase 74 ✅; remaining: Phase 75, Phase 76, Phase 79)
- **VAL-02** (new VALIDATION.md from scratch): still pending — Phase 71 done (Plan 87-01), Phase 78 pending (Plan 87-07)
- **VAL-03** (auditor execution): **4 of 8 plans complete** (87-01, 87-02, 87-03, 87-04)
- **VAL-04** (clear STATE.md Nyquist row): pending — final closure in Plan 87-08

## Self-Check: PASSED

- File `.planning/milestones/v1.10-phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md` exists with `status: approved` ✅
- File `src/test/java/org/ctc/backup/exception/BackupUploadExceptionHandlerScopeIT.java` exists ✅
- 10 PLAN.md files + 10 SUMMARY.md files restored (counted) ✅
- Commits `db41a1d6`, `fb68a87e`, `7c58e121` all in `git log` ✅
- Branch `gsd/v1.11-tooling-and-cleanup` unchanged ✅

## Next

Plan **87-05** — Phase 75 (Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT) retroactive VALIDATION. Predicted gap profile per 87-RESEARCH.md: 1-3 gap tests (post-commit-listener idempotency + timestamped-directory assertion + `Team.parentTeam` pre-step). State A path (existing draft present).
