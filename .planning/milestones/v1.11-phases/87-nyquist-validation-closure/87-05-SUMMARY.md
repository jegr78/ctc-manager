---
phase: 87
plan: 05
subsystem: planning
tags:
  - validation
  - nyquist
  - retroactive
  - v1.10-archive
  - phase-75
requires:
  - 87-04-SUMMARY  # prior plan completed (branch state inheritance)
provides:
  - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-VALIDATION.md (status: approved, nyquist_compliant: true)
  - src/test/java/org/ctc/backup/service/BackupImportPostCommitEdgeCasesIT.java (new IT, 2 scenarios)
affects:
  - VAL-01 (4 of 6 drafts now approved: 72, 73, 74, 75)
  - VAL-03 (auditor execution for phase 75 complete)
tech_stack:
  added: []
  patterns:
    - "Retroactive VALIDATION audit — State A path"
    - "AFTER_COMMIT listener double-fire defensive assertion"
    - "ISO-8601-with-dashes <ts> directory naming contract"
key_files:
  created:
    - src/test/java/org/ctc/backup/service/BackupImportPostCommitEdgeCasesIT.java
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-01-PLAN.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-02-PLAN.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-03-PLAN.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-04-PLAN.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-05-PLAN.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-06-PLAN.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-07-PLAN.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-08-PLAN.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-09-PLAN.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-10-PLAN.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-01-SUMMARY.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-02-SUMMARY.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-03-SUMMARY.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-04-SUMMARY.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-05-SUMMARY.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-06-SUMMARY.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-07-SUMMARY.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-08-SUMMARY.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-09-SUMMARY.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-10-SUMMARY.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-CONTEXT.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-RESEARCH.md
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-VERIFICATION.md
  modified:
    - .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-VALIDATION.md
decisions:
  - "Identified 2 gaps via inline orchestrator-driven audit (matching Plans 87-01..04 pattern) — both LOW-severity defensive contract assertions surfaced in 87-RESEARCH.md §Phase 75 predicted profile (post-commit listener idempotency, timestamped <ts> naming convention)"
  - "Single new IT class BackupImportPostCommitEdgeCasesIT covering both gaps in 2 @Test scenarios — reuses existing @SpringBootTest @ActiveProfiles(dev) context to keep wallclock cost flat per Phase 87 D-06 budget"
  - "No impl bugs surfaced — atomic-move-triple production code already handled both gap scenarios correctly; gaps were test-coverage only"
  - "Team.parentTeam two-pass NULL-then-UPDATE assertion (third predicted gap candidate) was already explicitly asserted in TeamRestorerTest + TeamRestorerIT — not an actual gap"
metrics:
  duration: "~25 min"
  completed: 2026-05-18
  tasks_completed: 4
  files_created: 24
  files_modified: 1
  tests_added: 2
  gaps_filled: 2
  impl_bugs_fixed: 0
  commits: 3
---

# Phase 87 Plan 05: Phase 75 Retroactive VALIDATION Approved — Summary

State-A retroactive VALIDATION closure for v1.10 Phase 75 (Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT) — restored 24 archived artefacts, identified and filled 2 defensive-contract gaps (AFTER_COMMIT listener double-fire safety + ISO-8601 `<ts>` directory naming), and transitioned the draft VALIDATION from `status: draft` to `status: approved` + `nyquist_compliant: true`. No implementation bugs found; 4 of 6 Nyquist drafts now approved (72, 73, 74, 75) per VAL-01.

## Tasks Completed

| # | Task | Outcome |
|---|------|---------|
| 1 | Restore v1.10 Phase 75 artefacts from git ref `60f5f915^` | 24 files restored verbatim under `.planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/` per CONTEXT D-02 minimal scope (10 PLAN + 10 SUMMARY + CONTEXT + RESEARCH + VERIFICATION + draft VALIDATION). Slug verbatim per D-04. DISCUSSION-LOG, HUMAN-UAT, PATTERNS, REVIEW, REVIEW-FIX intentionally NOT restored — stayed in git history per D-02. |
| 2 | Run `/gsd:validate-phase 75` (inline orchestrator-driven, matching Plans 87-01..04) | Verified all 14 referenced test classes exist on disk (24 `*RestorerTest` + `NoopRestoreFailureInjectorTest` + `DataImportAuditServiceTest` + `DataImportAuditSerializationTest` + `TeamRestorerTest` + `TeamRestorerIT` + `BackupArchiveExtractUploadsIT` + `BackupImportExecuteIT` + `BackupImportRollbackIT` + `BackupImportPostCommitIT` + `BackupImportE2ETest` + `BackupImportMariaDbSmokeIT` + `BackupRoundTripIT`). Identified 2 gaps from 87-RESEARCH §Phase 75 predicted profile. |
| 3 | Land 2 gap-fill tests in new `BackupImportPostCommitEdgeCasesIT` | Targeted run: `./mvnw failsafe:integration-test failsafe:verify -Dit.test='BackupImportPostCommitEdgeCasesIT'` → BUILD SUCCESS, Tests run: 2, Failures: 0, Errors: 0, 29.42 s. No impl bugs; production behaviour already correct. |
| 3b | Human checkpoint for non-trivial impl bug | **Skipped** — no impl bugs found. |
| 4 | Transition 75-VALIDATION.md draft → approved (State A) | Frontmatter: `status: draft → approved`, `nyquist_compliant: false → true`, `wave_0_complete: false → true`, `+ approved_on: 2026-05-18`, `+ audit_method: retroactive`. All 14 Per-Task rows flipped to `✅ green` + 2 new gap rows added (75-07-03, 75-07-04). All 9 Sign-Off boxes `[x]`. "## Validation Audit 2026-05-18" block appended with gap inventory + CI run-id `26008754136` citation. Approval line: `approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-05`. |

## Gap Analysis

Predicted profile per 87-RESEARCH.md §"Phase 75" was 1-3 tests; landed 2 (within predicted range). All gaps were LOW-severity defensive-contract assertions, not behaviour gaps.

### Gap 1 — AFTER_COMMIT listener double-fire safety (filled)

**Test:** `BackupImportPostCommitEdgeCasesIT#givenAfterCommitListenerIsInvokedTwiceWithSameEvent_…`

**Behaviour pinned:** When `BackupImportPostCommitListener.onImportSucceeded(event)` is invoked a second time with the same `BackupImportSucceededEvent` payload (defensive scenario; Spring's `@TransactionalEventListener(AFTER_COMMIT)` doesn't replay events by default, but the listener must remain safe under spurious-replay), the second invocation MUST fail loud at Step 1 because `uploads-old/` already exists from the first call. The listener wraps the `IOException` in `UploadsRestoreException` + writes a `success=false` audit row via `recordResultBestEffort`. The first invocation's `success=true` audit row and the file-system state (promoted `uploadsTarget` contents + original content preserved in `uploads-old/`) are NOT corrupted by the replay.

### Gap 2 — Timestamped `<ts>` sub-directory naming contract (filled)

**Test:** `BackupImportPostCommitEdgeCasesIT#givenImportBackupDirNamingConvention_…`

**Behaviour pinned:** The `data/.import-backups/<ts>/` leaf segment produced by `BackupImportService.execute(...)` (line 451: `Instant.now().truncatedTo(SECONDS).toString().replace(":", "-")`) MUST match the documented ISO-8601-with-dashes pattern `yyyy-MM-ddTHH-mm-ssZ`. This is the operator-visible 24h-retention key per Phase 75 CONTEXT D-04 and the forensic-recovery identifier — operator-side cleanup scripts rely on this regex shape.

### Gap candidate 3 (NOT a real gap)

The `Team.parentTeam` two-pass NULL-then-UPDATE pre-step decoupling assertion was already explicitly covered by:

- `TeamRestorerTest` lines 132, 144, 146, 222 — `eq("UPDATE teams SET parent_team_id = ? WHERE id = ?")` Pass-2 SQL assertion
- `TeamRestorerIT` lines 27-28 — IT-level 2-pass discipline against H2 with real `JdbcTemplate`

No additional test needed.

## Deviations from Plan

None — plan executed exactly as written. The State-A transition followed the planned flow; the predicted gap count (1-3) materialised at the midpoint (2). The skip-condition on Task 3b (human checkpoint for non-trivial impl bugs) was satisfied because no impl bugs surfaced.

## Verification

```bash
# Phase 75 archive restored (24 files)
ls .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/ | wc -l
# → 24

# Approval frontmatter
grep -E '^status: approved$' .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-VALIDATION.md
# → status: approved
grep -E '^nyquist_compliant: true$' .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-VALIDATION.md
# → nyquist_compliant: true

# No ❌ W0 placeholders remain
grep -c "❌ W0" .planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-VALIDATION.md
# → 0

# Targeted gap-test run (local, 2026-05-18)
./mvnw failsafe:integration-test failsafe:verify -Dit.test='BackupImportPostCommitEdgeCasesIT'
# → BUILD SUCCESS, Tests run: 2, Failures: 0, Errors: 0, 29.42 s

# Branch invariant
git branch --show-current
# → gsd/v1.11-tooling-and-cleanup

# No Flyway V*.sql migrations touched
git diff --name-only HEAD~3 HEAD -- 'src/main/resources/db/migration/V*.sql'
# → (empty)
```

## Commits

```
9523616c docs(87-05): restore v1.10 phase 75 for validation closure
e5cb9358 test(87-05): fill 2 validation gaps for phase 75 (post-commit edge cases)
da6fc9be docs(87-05): approve 75-VALIDATION.md (status: approved, nyquist_compliant: true)
```

All 3 commits on `gsd/v1.11-tooling-and-cleanup` per VAL-03 atomic-per-phase commit group convention (Plans 87-01..04 pattern continued).

## Impact

- **VAL-01:** advanced from 3 of 6 approved → 4 of 6 (72, 73, 74, 75 now approved; 76 + 79 still draft per upcoming Plans 87-06, 87-08)
- **VAL-03:** auditor execution for phase 75 complete
- **Wallclock budget:** +29 s impact on Failsafe wall (`BackupImportPostCommitEdgeCasesIT`, 2 scenarios in already-loaded `dev`-profile context). Cumulative Phase 87 wallclock impact at this point: well within the D-06 5% regression budget (~69 s headroom over 23:00 baseline).
- **JaCoCo:** no measurable change expected — 2 IT scenarios that exercise existing well-covered production paths under a fresh adversarial angle.
- **Flyway invariant:** no `V*.sql` touched (CLAUDE.md "Do Not Modify Flyway Migrations" honoured).

## Self-Check

| Item | Result |
|------|--------|
| `.planning/milestones/v1.10-phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-VALIDATION.md` exists | ✅ FOUND |
| `src/test/java/org/ctc/backup/service/BackupImportPostCommitEdgeCasesIT.java` exists | ✅ FOUND |
| 10 `75-*-PLAN.md` files exist | ✅ FOUND |
| 10 `75-*-SUMMARY.md` files exist | ✅ FOUND |
| Commit `9523616c` (restore) on branch | ✅ FOUND |
| Commit `e5cb9358` (gap tests) on branch | ✅ FOUND |
| Commit `da6fc9be` (approval) on branch | ✅ FOUND |
| Branch `gsd/v1.11-tooling-and-cleanup` | ✅ FOUND |
| No `V*.sql` Flyway migrations touched | ✅ FOUND |

## Self-Check: PASSED
