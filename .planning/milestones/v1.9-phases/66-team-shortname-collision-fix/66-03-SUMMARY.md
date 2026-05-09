---
phase: 66
plan: 03
slug: team-shortname-collision-fix
subsystem: dataimport
tags: [hotfix, dataimport, gap-closure, layout-aware, template, tdd]
gap_closure: true
requirements: [GAP-66-02]
dependency_graph:
  requires:
    - 66-02-SUMMARY.md
    - 66-UAT.md (Gap 2)
  provides:
    - layout-gated-group-warnings
    - tab-uses-groups-flag
  affects:
    - DriverSheetImportService
    - driver-import-preview.html
    - DriverSheetImportServiceTest
tech_stack:
  added: []
  patterns:
    - Layout-gated warning emission (PhaseLayout.GROUPS check)
    - Per-tab usesGroups boolean on TabPreview record (separate from page-wide showGroupColumn)
    - Per-row Thymeleaf gate combining showGroupColumn (header) with tab.usesGroups() (content)
key_files:
  created:
    - .planning/phases/66-team-shortname-collision-fix/66-03-SUMMARY.md
  modified:
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    - src/main/resources/templates/admin/driver-import-preview.html
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
decisions:
  - Layout gate uses regularPhase.getLayout() == PhaseLayout.GROUPS (Plan-required exact string)
  - usesGroups computed once per tab and stored on TabPreview record (avoids re-evaluation in template)
  - Page-wide showGroupColumn (controller) preserved as column-header gate; per-row gate is purely additive
  - Test #20 fixture also flipped to GROUPS (Rule-3 deviation — Plan only listed #16/#17 in Step C, but #20 collides with the new layout-gated contract identically)
metrics:
  duration_minutes: 24
  task_count: 3
  file_count: 3
  tests_run: 1235
  tests_failures: 0
  tests_skipped: 4
  jacoco_bundle_line_ratio: 0.8561
  jacoco_gate: 0.82
  completed_date: 2026-05-08
---

# Phase 66 Plan 03: Layout-Gated Group Warnings Summary

Layout-gated warning emission closes UAT Gap 2 (`group-warnings-for-non-groups-seasons`): for seasons whose REGULAR phase has LEAGUE layout (e.g. season 2024) the driver-import preview no longer surfaces the spurious `TEAM_NOT_IN_REGULAR_PHASE` warning for every team. Per-row `<td>` rendering of the "No group" badge is suppressed via a new `tab.usesGroups()` per-tab gate while the page-wide `showGroupColumn` column-header gate is preserved unchanged. GROUPS-layout phases continue to emit warnings for genuinely missing teams (test #16, #17, #20 — all flipped to `groupsRegularPhase` fixture).

## What Changed

**Service (`DriverSheetImportService.java`):**

- New import: `org.ctc.domain.model.PhaseLayout` (line 10).
- New per-tab boolean `usesGroups` computed once after `regularPhase` is resolved (line 269): `regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS`.
- Group-resolution branch (line 328) now gated on `regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS` — LEAGUE-layout phases short-circuit before the `phaseTeamRepository` lookup, so neither the warning nor a `verify(phaseTeamRepository)` interaction fires.
- `TabPreview` record extended from 12 to 13 fields with `boolean usesGroups`; the single canonical constructor call at the bottom of `buildTabPreview` updated to pass it.

**Template (`driver-import-preview.html`):**

- All 5 buckets (New Drivers, New Assignments, Conflicts, Fuzzy Suggestions, Unchanged) now gate the per-row Group cell on `tab.usesGroups() and ...`. When `tab.usesGroups()` is false, an em-dash (`&mdash;`) renders instead of either the resolved name or the "⚠ No group" badge.
- The page-wide `${showGroupColumn}` column-header gate is preserved unchanged — non-GROUPS tabs in mixed multi-tab previews still get a column header (because at least one tab is GROUPS), but their row content is the placeholder.

**Tests (`DriverSheetImportServiceTest.java`) — class total 29 → 31:**

- Test #16 renamed `givenGroupsLayoutAndTeamMissingFromRegularPhase_whenPreview_thenWarningEmitted`; fixture flipped from `regularPhase` (LEAGUE) to `groupsRegularPhase` (GROUPS) with two groups; new assertion `tab.usesGroups()=true` added.
- Test #17 renamed `givenGroupsLayoutAndTwoRowsSameMissingTeam_whenPreview_thenSingleWarningEmitted`; fixture flipped to `groupsRegularPhase` with one group.
- Test #20 (`givenTeamsWithSameShortNameAndNoCandidateHasPhaseTeam_whenPreview_thenFallsBackToParentPrecedence`) — fixture also flipped to `groupsRegularPhase`. Plan Step C only listed #16/#17, but #20 carries the same legitimate-warning contract under the same conditions; without the flip the test would fail because the Layout-gate now suppresses the warning emission for LEAGUE phases. Resolver-side `verify(phaseTeamRepository).findByPhaseIdAndTeamId(...)` assertions for both candidates remain valid because the resolver's PhaseTeam lookup is NOT layout-gated (only `buildTabPreview`'s subsequent group-resolution branch is). Documented as Rule-3 deviation below.
- Test #23 added (`givenLeagueLayoutRegularPhaseAndTeamWithoutGroup_whenPreview_thenNoWarningAndUsesGroupsFalse`): LEAGUE-layout no-warning contract — `tab.warnings()` empty, `tab.usesGroups()=false`, `tab.newDrivers().get(0).resolvedGroupName()=null`, `verifyNoInteractions(phaseTeamRepository)`.
- Test #24 added (`givenGroupsLayoutRegularPhase_whenPreview_thenUsesGroupsTrue`): GROUPS-layout positive contract — `tab.usesGroups()=true`, `tab.warnings()` empty, `resolvedGroupName="Group A"` preserved.

## TDD Gate Compliance

| Gate | Commit | Marker |
|------|--------|--------|
| RED  | `48dcfeb` | `test(66-03): add layout-gate contract tests (compile-error RED gate)` — `./mvnw test-compile` failed with `cannot find symbol: method usesGroups()` at lines 886 and 913 (intended RED signal per Phase 66 plan-01 SUMMARY pattern). |
| GREEN | `3b66e77` | `fix(66-03): suppress group warnings for non-GROUPS layout phases` — `Tests run: 31, Failures: 0, Errors: 0` for the test class; `Tests run: 1235, Failures: 0, Errors: 0, Skipped: 4` for the full suite. |
| REFACTOR | — | not needed (no cleanup beyond GREEN). |

## Verification

| Criterion | Result |
|-----------|--------|
| `./mvnw verify` | BUILD SUCCESS |
| Tests run / failures | 1235 / 0 (= 1233 from end of Plan 66-02 + 2 net new from #23/#24) |
| Class-level tests | 29 → 31 (#23 + #24 added; #16/#17/#20 fixture-flipped) |
| JaCoCo BUNDLE LINE ratio | 0.8561 (gate 0.82) — same as end of Plan 66-02 |
| Phase 68 invariant (`sun.misc.Unsafe` / `lombok.permit` count in build log) | 0 |
| Service grep `regularPhase.getLayout() == PhaseLayout.GROUPS` | 2 hits (line 269 `usesGroups` computation + line 328 group-resolution gate) |
| Template structural gate (BLOCKING — every "No group" badge must be conjoined with `tab.usesGroups()`) | empty (PASS — no ungated badge) |
| Template `tab.usesGroups()` count | 15 (3 occurrences per bucket × 5 buckets, exactly per plan) |
| Template `&#x26A0; No group` count | 5 (one per bucket — unchanged from before, now gated) |

### Grep outputs

```text
$ grep -n "regularPhase.getLayout() == PhaseLayout.GROUPS" src/main/java/org/ctc/dataimport/DriverSheetImportService.java
269:        boolean usesGroups = regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS;
328:            if (regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS) {
```

```text
$ grep -nE '<span th:if="\$\{row\.resolvedGroupName\(\) == null\}" class="badge badge-warning">' \
      src/main/resources/templates/admin/driver-import-preview.html
(empty — every "No group" badge is gated by tab.usesGroups())
```

```text
$ grep -c "tab.usesGroups()" src/main/resources/templates/admin/driver-import-preview.html
15
$ grep -c "&#x26A0; No group" src/main/resources/templates/admin/driver-import-preview.html
5
```

```text
$ grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit" /tmp/phase-66-03-verify.log
0
```

```text
$ grep -rn "new TabPreview(" src/main src/test
src/main/java/org/ctc/dataimport/DriverSheetImportService.java:397:        return new TabPreview(...)
(exactly 1 hit — single canonical constructor call)
```

## Coverage Delta

| Metric | After Plan 66-02 | After Plan 66-03 | Delta |
|--------|------------------|------------------|-------|
| JaCoCo BUNDLE LINE | 0.8560 | 0.8561 | +0.0001 (rounding) |
| Total tests | 1233 | 1235 | +2 (#23, #24) |
| `DriverSheetImportServiceTest` | 29 | 31 | +2 |

Coverage delta is essentially neutral — the new layout gate adds one branch but the new tests #23/#24 cover both layouts, so net branch coverage is preserved.

## Commits

| Task | Hash | Message |
|------|------|---------|
| 1 (RED) | `48dcfeb` | `test(66-03): add layout-gate contract tests (compile-error RED gate)` |
| 2 (GREEN) | `3b66e77` | `fix(66-03): suppress group warnings for non-GROUPS layout phases` |
| 3 (FINAL GATE) | — | `./mvnw verify` (no code commit; verification only) |
| Summary | (this commit) | Worktree-local SUMMARY commit; orchestrator publishes after merge |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Test #20 fixture flipped from `regularPhase` to `groupsRegularPhase`**

- **Found during:** Task 2 GREEN gate run (`./mvnw test -Dtest=DriverSheetImportServiceTest`)
- **Issue:** Plan Task 2 Step C only listed tests #16 and #17 for fixture flip. After the production layout gate landed, test #20 (`givenTeamsWithSameShortNameAndNoCandidateHasPhaseTeam_whenPreview_thenFallsBackToParentPrecedence`) failed with `Expected size: 1 but was: 0` on `assertThat(tab.warnings()).hasSize(1)` because its fixture is `regularPhase(...)` (LEAGUE) and the new layout gate suppresses the warning emission identically to #16/#17.
- **Fix:** Changed test #20's fixture from `PhaseTestFixtures.regularPhase(season2024, rs, ms)` to `PhaseTestFixtures.groupsRegularPhase(season2024, rs, ms, "Group A")`. The test's existing `verify(phaseTeamRepository, atLeastOnce())` and `verify(phaseTeamRepository).findByPhaseIdAndTeamId(...)` assertions on both candidates remain valid because the **resolver**'s PhaseTeam lookup (lines 437-441) is NOT layout-gated — only `buildTabPreview`'s subsequent group-resolution branch (line 328) is.
- **Files modified:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` (test #20 only)
- **Commit:** `3b66e77` (bundled with Task 2 GREEN per plan D-10 spirit and Plan 66-02 precedent — ad-hoc test correction during GREEN run is the established Phase 66 pattern)

No architectural deviations. No Rule-4 stops. Plan otherwise executed exactly as written.

### Plan-spec correction (cosmetic, no code impact)

The Plan Task 3 verification grep formula

```bash
awk -F, 'NR>1 {covered+=$8; missed+=$9} END {printf "%.4f\n", covered/(covered+missed)}' target/site/jacoco/jacoco.csv
```

is column-swapped (column 8 of `jacoco.csv` is `LINE_MISSED`, column 9 is `LINE_COVERED`). The corrected invocation is:

```bash
awk -F, 'NR>1 {missed+=$8; covered+=$9} END {printf "%.4f\n", covered/(covered+missed)}'
```

Both forms yield the same gate decision (1 - 0.1439 = 0.8561 ≥ 0.82), but the correct semantic value is reported in this SUMMARY.

## Decisions Made

| ID | Decision | Outcome |
|----|----------|---------|
| Layout-gate exact-string | `regularPhase.getLayout() == PhaseLayout.GROUPS` literally at both gate sites | implemented at lines 269 + 328 of DriverSheetImportService.java |
| TDD-RED purity | Compile-error counts as RED per Phase 66 plan-01 SUMMARY pattern | tests #23/#24 with `usesGroups()` reference produced 2 compile errors before GREEN landed |
| Test #20 fixture flip | Bundle with Step C as Rule-3 deviation rather than separate commit | follows D-10 single-bundled-commit spirit; documented inline |
| Single Step-A `usesGroups` boolean | Compute once after `regularPhase` resolve, consume both at gate AND at TabPreview construction | maintains `regularPhase.getLayout() == ...` literal at the gate site (Plan-required) while still avoiding redundant evaluation at TabPreview construction |

## Threat Model Outcomes

Per plan `<threat_model>`:

- **T-66-03-01 (Information Disclosure, accept)** — Suppressing warnings for LEAGUE phases is the user-confirmed correct behavior (UAT Gap 2). No operational signal lost: LEAGUE phases never have groups, so the warning was 100% noise.
- **T-66-03-02 (Tampering, mitigate)** — `usesGroups` is server-side computed from `SeasonPhase.layout` enum; client cannot influence it (validated via `@Enumerated(EnumType.STRING)` + `@Column(nullable=false)`).
- **T-66-03-03 (XSS, accept)** — `&mdash;` is a static character entity, no user-input interpolation in the new template branch.
- **T-66-03-04 (Repudiation, mitigate)** — Genuine missing-team warnings for GROUPS layout still emit (covered by tests #16, #17, #20 — all flipped to GROUPS fixture).

## Manual UAT (deferred)

UAT Test 3 (driver import execute after preview with collision) — was blocked per `66-UAT.md` until both Gap 1 (plan 66-02) and Gap 2 (this plan) merge. Re-run on the user's actual sheet once both plans are integrated and 66-UAT.md re-runs.

UAT Test 1 follow-up (Gap 2 reproduction): user can now open the driver-import preview against season 2024 (LEAGUE) — should see no "⚠ No group" badges (em-dash placeholder when column header still appears for mixed multi-tab previews) and no `TEAM_NOT_IN_REGULAR_PHASE` warnings in the alert panel. GROUPS-layout seasons (e.g. 2025) continue to emit warnings for genuinely missing teams.

## Self-Check: PASSED

- `.planning/phases/66-team-shortname-collision-fix/66-03-SUMMARY.md` — created in this commit
- Commit `48dcfeb` (RED) — FOUND in `git log`
- Commit `3b66e77` (GREEN) — FOUND in `git log`
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — modified (PhaseLayout import + usesGroups + 2 layout-gate sites + TabPreview field + canonical constructor update)
- `src/main/resources/templates/admin/driver-import-preview.html` — modified (5 buckets, all gated on `tab.usesGroups()`)
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — modified (31 tests; #16/#17/#20 fixture-flipped to GROUPS; #23/#24 added)
- All success-criteria gates from plan met (BUILD SUCCESS, 1235 tests, JaCoCo 0.8561 ≥ 0.82, Phase 68 invariant clean, structural template gate empty, sanity grep 15, badge count 5, service-side gate ≥ 2 hits)
