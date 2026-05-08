---
phase: 66
plan: 02
slug: team-shortname-collision-fix
subsystem: dataimport
tags: [hotfix, resolver, dataimport, gap-closure, season-aware, tdd]
gap_closure: true
requirements: [GAP-66-01]
dependency_graph:
  requires:
    - 66-01-SUMMARY.md
    - 66-UAT.md
  provides:
    - season-aware-shortName-resolver
    - resolveTeamByShortName-with-SeasonPhase
  affects:
    - DriverSheetImportService
    - DriverSheetImportServiceTest
    - 66-CONTEXT.md (D-06 revised)
tech_stack:
  added: []
  patterns:
    - Season-aware multi-match resolution with PhaseTeam(REGULAR) preference
    - Parent-precedence as legacy fallback (preserved Phase 66 baseline)
    - EntityNotFoundException → null graceful fallback in execute() per-tab
key_files:
  created:
    - .planning/phases/66-team-shortname-collision-fix/66-02-SUMMARY.md
  modified:
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
    - .planning/phases/66-team-shortname-collision-fix/66-CONTEXT.md
decisions:
  - D-06 revised — season-aware step inserted before parent-precedence fallback
  - Resolver signature changed to resolveTeamByShortName(String, SeasonPhase)
  - 4 execute call sites resolve regularPhase once per tab via SeasonPhaseService.findRegularPhase with EntityNotFoundException → null graceful fallback
  - Tests #19 + #20 inverted in RED commit; tests #21 + #22 (regression-pinning for legacy fallback) added in GREEN commit
metrics:
  duration_minutes: 30
  task_count: 3
  file_count: 3
  tests_run: 1233
  tests_failures: 0
  tests_skipped: 4
  jacoco_bundle_line_ratio: 0.8560
  jacoco_gate: 0.82
  completed_date: 2026-05-08
---

# Phase 66 Plan 02: Season-Aware ShortName Resolver Summary

Season-aware resolver replaces the season-blind parent-precedence algorithm from Phase 66 plan 01: when multiple teams share a shortName, prefer the candidate that has a `PhaseTeam` in the target season's REGULAR phase; fall back to parent-precedence only when no candidate is rostered (legacy / no-REGULAR-phase data). Closes UAT Gap 1 — driver imports against sheets with parent+sub shortName collisions (MRL/P1R/ZFS) now route drivers to the correct sub-team and stop emitting `TEAM_NOT_IN_REGULAR_PHASE` warnings.

## What Changed

**Resolver (`DriverSheetImportService.java`):**

- Signature changed from `resolveTeamByShortName(String) → Optional<Team>` to `resolveTeamByShortName(String, SeasonPhase) → Optional<Team>`.
- New algorithm (D-06 revised, lines 405–447):
  1. `matches = teamRepository.findAllByShortName(shortName)`
  2. empty → `Optional.empty()`
  3. single → return it
  4. **NEW:** if `regularPhase != null`, iterate candidates and return first with `PhaseTeam` in that phase
  5. else parent (`parentTeam == null`) wins by fallback
  6. else log.warn + first match (multi-parent edge case, preserved D-07)
- Helper kept alongside `cellToString` per Phase 66 D-18.
- Javadoc rewritten to document new precedence + revision-history pointer to `66-CONTEXT.md` D-06.

**Call-site migration (5 sites):**

- Preview path (line 304 in `buildTabPreview`): passes `regularPhase` already cached at line 260.
- Execute path: 4 call sites (lines 143, 154, 174, 203) now receive `regularPhase` resolved once per tab via `seasonPhaseService.findRegularPhase(season.getId())`, with `EntityNotFoundException → null` graceful fallback (mirrors `buildTabPreview`'s pattern).

**Tests (`DriverSheetImportServiceTest.java`) — class total 27 → 29:**

- Test #19 inverted: `givenTeamsWithSameShortNameAndSubHasPhaseTeam_whenPreview_thenResolvesSubTeam` — sub-team with PhaseTeam wins, no warning, no error.
- Test #20 inverted: `givenTeamsWithSameShortNameAndNoCandidateHasPhaseTeam_whenPreview_thenFallsBackToParentPrecedence` — fallback path, legitimate `TEAM_NOT_IN_REGULAR_PHASE` warning, explicit `verify(phaseTeamRepository, ...)` interaction assertions on both candidates.
- Test #21 added (regression-pinning): `givenSeasonHasNoRegularPhase_whenPreviewWithCollision_thenFallsBackToParentPrecedence` — no-REGULAR-phase legacy path; `verifyNoInteractions(phaseTeamRepository)`.
- Test #22 added (regression-pinning, preserves Phase 66 D-12): `givenLegacyPath_whenTwoParentTeamsCollideWithoutRegularPhase_thenFirstParentWinsWithoutException` — defensive multi-parent edge.

**Documentation (`66-CONTEXT.md`):**

- Status line updated to flag revision by gap-closure plan 02.
- D-06 algorithm replaced with the 7-line season-aware version + assumption-failure rationale.
- Revision History footer added at the bottom of `<decisions>` documenting the D-06 revision.

## TDD Gate Compliance

| Gate | Commit | Marker |
|------|--------|--------|
| RED  | `a7a7994` | `test(66-02): add failing season-aware resolver tests` (`Tests run: 27, Failures: 2`) |
| GREEN | `9665d42` | `fix(66-02): season-aware shortName resolver — prefer team with PhaseTeam in REGULAR phase` (`Tests run: 29, Failures: 0`) |
| REFACTOR | — | not needed (no cleanup beyond GREEN) |

RED gate confirmed by exactly 2 failures: test #19 on `assertThat(tab.warnings()).isEmpty()` (today's parent-blind resolver routes to parent without PhaseTeam → warning fires); test #20 on `verify(phaseTeamRepository).findByPhaseIdAndTeamId(..., subZfs)` (today's resolver short-circuits on parent precedence and never consults phaseTeamRepository for the sub-team).

GREEN gate confirmed by `Tests run: 29, Failures: 0, Errors: 0` for the test class and `Tests run: 1233, Failures: 0, Errors: 0, Skipped: 4` full-suite.

## Verification

| Criterion | Result |
|-----------|--------|
| `./mvnw verify` | BUILD SUCCESS |
| Tests run / failures | 1233 / 0 (= 1231 Phase 66 baseline + 2 net new from #21/#22) |
| Class-level tests | 27 → 29 (#19/#20 inverted, #21/#22 added) |
| JaCoCo BUNDLE LINE ratio | 0.8560 (gate 0.82) ✅ |
| Phase 68 invariant — `sun.misc.Unsafe` / `lombok.permit` count in build log | 0 ✅ |
| Resolver signature changed | `resolveTeamByShortName(String, SeasonPhase)` is the only declaration ✅ |
| All 5 call sites pass `regularPhase` | grep gate empty (no single-arg or non-`regularPhase` call survives) ✅ |
| `grep -nE "resolveTeamByShortName\(" DriverSheetImportService.java` | 1 declaration + 5 call sites, all with `regularPhase` ✅ |
| 66-CONTEXT.md D-06 revised | Status line + algorithm block + Revision History note ✅ |

### Grep outputs

```text
$ grep -nE "resolveTeamByShortName\(" src/main/java/org/ctc/dataimport/DriverSheetImportService.java
143:                Team team = resolveTeamByShortName(row.teamShortName(), regularPhase)
154:                Team team = resolveTeamByShortName(row.teamShortName(), regularPhase)
174:                    Team newTeam = resolveTeamByShortName(row.sheetTeamShortName(), regularPhase)
203:                Team team = resolveTeamByShortName(row.teamShortName(), regularPhase)
304:            Optional<Team> teamOpt = resolveTeamByShortName(rawTeamCode, regularPhase);
428:    private Optional<Team> resolveTeamByShortName(String shortName, SeasonPhase regularPhase) {
```

```text
$ grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit" /tmp/phase-66-02-verify.log
0
```

## Coverage Delta

| Metric | Phase 66 baseline (plan 01) | After Phase 66 plan 02 | Delta |
|--------|-----------------------------|------------------------|-------|
| JaCoCo BUNDLE LINE | 0.8561 | 0.8560 | −0.0001 (within rounding) |
| Total tests | 1231 | 1233 | +2 (#21, #22) |
| `DriverSheetImportServiceTest` | 27 | 29 | +2 |

## Commits

| Task | Hash | Message |
|------|------|---------|
| 1 (RED) | `a7a7994` | `test(66-02): add failing season-aware resolver tests` |
| 2 (GREEN) | `9665d42` | `fix(66-02): season-aware shortName resolver — prefer team with PhaseTeam in REGULAR phase` |
| 3 (FINAL GATE) | — | `./mvnw verify` (no code commit; verification only) |
| Summary | (this commit) | committed by orchestrator post-merge |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Tightened test #20 phaseTeamRepository interaction assertion**

- **Found during:** Task 2 GREEN gate run
- **Issue:** After implementing the season-aware resolver, test #20 (`givenTeamsWithSameShortNameAndNoCandidateHasPhaseTeam_whenPreview_thenFallsBackToParentPrecedence`) failed because `phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase, parentZfs)` is now invoked TWICE: once by the resolver multi-match loop (returns empty) and once by `buildTabPreview`'s subsequent group-resolution lookup for the chosen team. Mockito's plain `verify(...)` defaults to exactly-once and rejected the second invocation.
- **Fix:** Changed the assertion for `parentZfs` from plain `verify` to `verify(..., atLeastOnce())` and added a clarifying comment that `subZfs` is consulted exactly once (resolver fallback only) while `parentZfs` is consulted twice (resolver + group-resolution). The plan's `<verify>` block (Task 3) explicitly anticipated this and recommended `atMost(2)` / `atLeastOnce()` over removal.
- **Files modified:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` (test #20 only)
- **Commit:** `9665d42` (bundled with Task 2 GREEN per plan D-10 spirit)

No architectural deviations. No rule-4 stops. Plan executed exactly as written.

## Decisions Made

| ID | Decision | Outcome |
|----|----------|---------|
| D-06 (revised) | Season-aware multi-match precedence | implemented per Step A in `DriverSheetImportService.resolveTeamByShortName` |
| TDD-RED purity | Only failing-today tests in RED commit | tests #19/#20 inverted in RED; tests #21/#22 (legacy fallback regression-pinning) deferred to GREEN |
| Per-tab `regularPhase` resolution | Mirror buildTabPreview pattern | execute() resolves once per tab, `EntityNotFoundException → null` graceful fallback |
| Test #20 interaction assertion | Use `atLeastOnce()` for parentZfs | accommodates legitimate dual-lookup (resolver + group-resolution) |

## Threat Model Outcomes

Per plan `<threat_model>`:

- **T-66-02-01 (Tampering, mitigate)** — `findAllByShortName` parameter binding via Spring Data; no new SQL surface added.
- **T-66-02-02 (Information Disclosure, accept)** — `log.warn` argument is bounded VARCHAR(50) public team shortName; no PII; matches Phase 66 baseline.
- **T-66-02-03 (DoS, mitigate)** — multi-match loop bound by `findAllByShortName` size (≤ 3 in practice); `phaseTeamRepository.findByPhaseIdAndTeamId` is indexed via UNIQUE(phase_id, team_id).
- **T-66-02-04 (Repudiation, mitigate)** — fallback path still emits `TEAM_NOT_IN_REGULAR_PHASE` warning when no candidate is in REGULAR phase; admin sees it in preview before execute.
- **T-66-02-05 (Elevation of Privilege, accept)** — sub-team-with-PhaseTeam preference is the user-confirmed correct semantic per UAT Gap 1.

## Manual UAT (deferred)

UAT Test 3 (driver import execute after preview with collision) remains blocked per `66-UAT.md` until both Gap 1 (this plan) and Gap 2 (plan 66-03 — layout-gated group warnings) are merged. Re-run on the user's actual sheet once both plans are integrated.

## Self-Check: PASSED

- `.planning/phases/66-team-shortname-collision-fix/66-02-SUMMARY.md` — created in this commit
- Commit `a7a7994` (RED) — FOUND in `git log`
- Commit `9665d42` (GREEN) — FOUND in `git log`
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — modified (resolver + 5 call sites + per-tab regularPhase resolution)
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — modified (29 tests; #19/#20 inverted; #21/#22 added)
- `.planning/phases/66-team-shortname-collision-fix/66-CONTEXT.md` — modified (Status + D-06 + Revision History)
- All success-criteria gates from plan met (BUILD SUCCESS, 1233 tests, JaCoCo 0.8560 ≥ 0.82, Phase 68 invariant clean, resolver migration grep gate empty)
