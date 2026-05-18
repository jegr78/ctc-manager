---
phase: 81-static-analysis-gate-spotbugs-find-sec-bugs
fixed_at: 2026-05-16T17:10:00Z
review_path: .planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 2
skipped: 1
status: partial
---

# Phase 81: Code Review Fix Report

**Fixed at:** 2026-05-16T17:10:00Z
**Source review:** `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope (critical_warning): 3 (WR-01, WR-02, WR-03)
- Fixed: 2 (WR-01, WR-03)
- Already fixed: 1 (WR-02)
- Skipped: 0
- Out of scope: 2 (IN-01, IN-02)

| Finding | Title | Status | Notes |
|---------|-------|--------|-------|
| WR-01 | `givenMultiPhaseSeason_whenAggregateAcrossPhases` never exercises the regular-phase-priority branch | fixed | commit `58b8d798` |
| WR-02 | Stale line-number references in spotbugs-exclude.xml comments | already_fixed | commit `5579be6b` |
| WR-03 | Non-volatile mutable fields in a Spring singleton (`TemplatePreviewService`) | fixed | commit `e791cbc6` |
| IN-01 | `containsSpringElTypeAccess` skips only ASCII space, not all whitespace | out_of_scope | Info tier; critical_warning scope excludes Info |
| IN-02 | `ClassPathResource` import via fully-qualified name in two graphic services | out_of_scope | Info tier; critical_warning scope excludes Info |

## Fixed Issues

### WR-01: `givenMultiPhaseSeason_whenAggregateAcrossPhases` never exercises the regular-phase-priority branch

**Files modified:** `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java`
**Commit:** `58b8d798`
**Applied fix:** Added `when(phaseTeamRepository.findByPhaseId(regular.getId())).thenReturn(List.of(phaseTeamEntry))` stub before the `// when` block in `givenMultiPhaseSeason_whenAggregateAcrossPhases_thenRegularTeamGuardsAttribution`. The `PhaseTeam` entry links `regular` phase to `tnr` team, so `regularPhaseTeamIds` now contains `tnr.getId()`. The filter `rl -> regularPhaseTeamIds.contains(rl.getTeam().getId())` in `attributeTeamFromRegularOrLineup` now matches the `lineup` (which also uses `tnr`), proving the priority branch fires rather than the `lineups.get(0).getTeam()` fallback. All 16 `DriverRankingServiceTest` tests pass.

### WR-03: Non-volatile mutable fields in a Spring singleton (`TemplatePreviewService`)

**Files modified:** `src/main/java/org/ctc/admin/service/TemplatePreviewService.java`
**Commit:** `e791cbc6`
**Applied fix:** Added `volatile` keyword to all four lazy-init cache field declarations at lines 36-39:
- `private volatile String cachedFontBase64;`
- `private volatile String cachedLogoBase64;`
- `private volatile String cachedCommentatorBase64;`
- `private volatile String cachedVsBadgeBase64;`

Post-fix verification: `./mvnw spotbugs:check -DskipTests` reports `BugInstance size is 0` and `BUILD SUCCESS`. All 80 Template*-related tests pass.

## Already Fixed Issues

### WR-02: Stale line-number references in spotbugs-exclude.xml comments

**File:** `config/spotbugs-exclude.xml`
**Commit:** `5579be6b` (`docs(81): refresh stale line-number cross-references in spotbugs-exclude.xml`)
**Note:** Already fixed prior to this fix run. The `TeamCardService.java:104-106` and `BackupArchiveService.java:500-505` cross-references were updated in commit `5579be6b`. No further action required.

## Out of Scope Issues

### IN-01: `containsSpringElTypeAccess` skips only ASCII space, not all whitespace

**File:** `src/main/java/org/ctc/admin/service/TemplatePreviewService.java:362`
**Reason:** out_of_scope — Info-severity finding; `fix_scope: critical_warning` excludes Info findings. Functional safety net (`BLOCKED_TOKENS`) covers the practical attack surface.

### IN-02: `ClassPathResource` import via fully-qualified name in two graphic services

**File:** `src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java:134`, `src/main/java/org/ctc/admin/service/ResultsGraphicService.java:110`
**Reason:** out_of_scope — Info-severity finding; `fix_scope: critical_warning` excludes Info findings. Code-style inconsistency only; no behavioral impact.

## Validation Evidence

| Gate | Command | Result |
|------|---------|--------|
| WR-01: DriverRankingServiceTest (16/16) | `./mvnw test -Dtest=DriverRankingServiceTest` | PASS — 16 tests, 0 failures, 0 errors |
| WR-03: SpotBugs 0 BugInstance | `./mvnw spotbugs:check -DskipTests` | PASS — `BugInstance size is 0`, `No errors/warnings found` |
| WR-03: Template tests | `./mvnw test -Dtest='Template*'` | PASS — 80 tests, 0 failures |
| JaCoCo coverage >= 82% | Phase 81 VERIFY-PHASE.md | 88.13% (7449/8452 lines) — unchanged by these fixes |
| Pre-existing IT failures | `BackupSchemaExclusionIT`, `BackupImportRollbackIT` | Pre-existing; unrelated to phase 81 fixes (backup files not touched) |

---

_Fixed: 2026-05-16T17:10:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
