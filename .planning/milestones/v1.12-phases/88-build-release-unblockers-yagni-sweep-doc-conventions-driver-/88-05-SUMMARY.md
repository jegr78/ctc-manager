---
phase: 88-build-release-unblockers-yagni-sweep-doc-conventions-driver
plan: 05
subsystem: api
tags: [driver-import, season-phase, groups-layout, phase-team, resolver, future-proofing]

requires:
  - phase: 88-04
    provides: DOCS-01 skill-naming convention landed; regression-fence grep returns 0
provides:
  - Season-aware `resolveTeamByShortName(String, SeasonPhase)` in `DriverSheetImportService` — sub-team-with-PhaseTeam wins over parent on multi-match collisions
  - All 5 resolver call sites (`execute()` × 4, `buildTabPreview()` × 1) plumbed with per-tab `regularPhase`
  - `TabPreview.usesGroups` boolean field computed once per tab from `regularPhase.getLayout() == PhaseLayout.GROUPS`
  - `DriverSheetImportController.preview()` page-wide `showGroupColumn = anyMatch(TabPreview::usesGroups)` aggregation as a future-proofing API surface
affects: [88-06, future-import-template-changes, future-PhaseTeam-aware-UI]

tech-stack:
  added: []
  patterns:
    - "Pattern: when a deferred-debug doc references surfaces that no longer exist in the current code, implement the defensive future-proofing API (new fields, aggregations, gates) without re-introducing the missing UI elements — the contract is enforced at the service/controller layer so future refactors cannot silently regress"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    - src/main/java/org/ctc/admin/controller/DriverSheetImportController.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java

key-decisions:
  - "DRIV-01 implemented per RESEARCH § 'Pattern 4: Season-Aware Resolver Algorithm' — multi-match precedence is now (1) candidate with PhaseTeam in target REGULAR phase, (2) parent-precedence, (3) first-match + WARN log. No BusinessRuleException thrown on missing parent (D-07 legacy semantics preserved)."
  - "DRIV-02 plan-drift surfaced: the deferred-debug doc `group-warnings-for-non-groups-seasons.md` (authored 2026-05-08) references three surfaces that no longer exist in v1.12 code — the `TEAM_NOT_IN_REGULAR_PHASE` warning in `DriverSheetImportService`, the `showGroupColumn` attribute in `DriverSheetImportController`, and the per-row Group cell in `driver-import-preview.html`. The bug those surfaces had cannot reproduce today. User-approved decision: Defensive Future-Proofing — add `TabPreview.usesGroups` + controller `showGroupColumn` aggregation as API stability, skip the template edit (no Group cell exists to gate). 2 new tests assert `usesGroups` is computed correctly under LEAGUE vs GROUPS layout."
  - "Plan Task 88-05-04 (playwright-cli visual verification) NOT executed: the Defensive Future-Proofing decision means the template was not modified, so there is no visual surface to verify. Re-introducing a playwright-cli check against an unchanged template would only produce a baseline that future template edits would have to maintain — adds maintenance burden without verifying anything new."
  - "Plan referenced 'tests #16/#17 inversion' but Mockito-mode test numbering is plan-drift (today tests #16/#17 are unrelated case-insensitive and cross-tab tests). The pre-existing tests #21-#23 still assert the legacy fallback path correctly under DRIV-01 because Mockito-default `Optional.empty()` for unstubbed `seasonPhaseService.findByType` and `phaseTeamRepository.findByPhaseIdAndTeamId` calls forces the resolver into the parent-precedence path."

patterns-established:
  - "Pattern: defensive future-proofing — when a planned fix would target a surface that no longer exists, implement the contract at one layer down (service+controller) without resurrecting the missing UI; the field/aggregation is then available for any future UI element that needs it"

requirements-completed:
  - DRIV-01
  - DRIV-02

duration: ~45min (2 service edits + 1 controller edit + 6 new tests + 1 verify -Pe2e cycle)
completed: 2026-05-19
---

# Phase 88-05: DRIV-01 + DRIV-02 — Driver-Import Gap-Closure

**Season-aware `resolveTeamByShortName` now picks the sub-team with a PhaseTeam in the target REGULAR phase over the parent; `TabPreview.usesGroups` flows from service → controller as defensive future-proofing for any subsequent Group-aware import-preview UI. JaCoCo LINE 89.06 % (+0.05 pp vs Plan-02 baseline), 1685 tests pass.**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-05-19T08:42:00Z
- **Completed:** 2026-05-19T09:24:00Z
- **Tasks:** 4 planned; 3 executed (visual playwright-cli check skipped — see Decisions)
- **Files modified:** 3

## Accomplishments
- DRIV-01: season-aware resolver in place; all 5 call sites passing `regularPhase`; per-tab `regularPhase` resolved once via `seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)` in both `buildTabPreview()` and `execute()`
- DRIV-01 edge cases: 4 new unit tests (#24 sub-with-PhaseTeam wins, #25 no-PhaseTeam → parent fallback, #26 no REGULAR phase → parent fallback, #27 no-PhaseTeam + no parent → first match + WARN, no BusinessRuleException)
- DRIV-02 defensive future-proofing: `TabPreview` gains `boolean usesGroups`; controller exposes `showGroupColumn` aggregation; 2 new unit tests (#28 LEAGUE → usesGroups=false, #29 GROUPS → usesGroups=true)
- 28 pre-existing unit tests stay green (record-construction site updated; Mockito-defaults preserve legacy fallback path for unstubbed tests)
- `DriverSheetImportServiceIT` (7 ITs) stay green
- Full `./mvnw clean verify -Pe2e` exit 0 in 10:19 min: 1685 tests pass, LINE coverage 89.06 % (+0.05 pp), SpotBugs `BugInstance` count == 0

## Task Commits

Each task was committed atomically:

1. **Task 88-05-01: DRIV-01 season-aware resolver + 4 edge tests** — `996934f3` (feat)
2. **Task 88-05-02: DRIV-02 GROUPS-layout gate + usesGroups field + controller aggregation + 2 tests** — `e73ab9c4` (feat)
3. **Task 88-05-03: Plan-05 final gate (./mvnw clean verify -Pe2e)** — no commit (verification-only)
4. **Task 88-05-04: playwright-cli visual verification** — SKIPPED per user-approved Defensive Future-Proofing scope (no template change to verify)

## Files Created/Modified
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — season-aware resolver, per-tab `regularPhase` + `usesGroups` computation, TabPreview record extended (+50/-12)
- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` — `showGroupColumn` aggregation (+3)
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — 4 DRIV-01 + 2 DRIV-02 new tests, 2 mock fields, 6 new imports (+183)

## Decisions Made

### 1. DRIV-02 Plan-Drift — Defensive Future-Proofing
The deferred-debug doc `.planning/debug/deferred/group-warnings-for-non-groups-seasons.md` (authored 2026-05-08) describes a bug at three surfaces that no longer exist at v1.12 HEAD:
- `TEAM_NOT_IN_REGULAR_PHASE` warning in `DriverSheetImportService` (lines 311-325 are EXACT match logic today)
- `showGroupColumn` attribute in `DriverSheetImportController` (only exists in Standings controllers)
- Per-row Group cell in `driver-import-preview.html` (template has no Group column today)

The bug those surfaces had cannot reproduce today. **User-approved decision: Defensive Future-Proofing** — add `TabPreview.usesGroups` boolean field + `DriverSheetImportController.showGroupColumn` aggregation as API surfaces so any future re-introduction of a Group cell or warning automatically inherits the GROUPS-layout gate. Template intentionally left untouched.

### 2. Task 88-05-04 (playwright-cli visual check) NOT executed
Reason: the Defensive Future-Proofing decision means the template was not modified. Running playwright-cli would only produce a baseline screenshot of unchanged output. No visual contract to verify.

### 3. Pre-existing tests #21-#23 NOT inverted
The plan said to "invert tests #16 and #17" but those test numbers refer to a historical state of the file. Today's tests #16/#17 are case-insensitive and cross-tab PSN tests, unrelated to the resolver. Tests #21-#23 (the existing collision tests) still assert the legacy-fallback path correctly under DRIV-01 because:
- Mockito-mode `seasonPhaseService.findByType(...)` returns `Optional.empty()` by default (no stub)
- The resolver therefore sees `regularPhase == null` and falls through to parent-precedence
- The pre-existing assertions ("parent wins") still hold

## Deviations from Plan

### Auto-fixed Issues

**1. [Plan-Drift / Missing Surfaces] DRIV-02 implemented as Defensive Future-Proofing**
- **Found during:** Task 88-05-02 reading of `driver-import-preview.html`, controller, and service code
- **Issue:** Three surfaces referenced by the plan (`TEAM_NOT_IN_REGULAR_PHASE` warning, `showGroupColumn` attribute, per-row Group cell) do not exist in v1.12 code
- **Fix:** User-approved Defensive Future-Proofing — service + controller API surfaces added; template skipped
- **Files modified:** see "Files Created/Modified"
- **Verification:** 30 unit tests + 7 ITs pass; coverage delta neutral-positive
- **Committed in:** `e73ab9c4`

**2. [Plan-Drift / Test Numbering] Pre-existing tests #21-#23 retained as legacy-fallback assertions**
- **Found during:** Task 88-05-01 reading of `DriverSheetImportServiceTest.java`
- **Issue:** Plan said "invert Test #16 and Test #17"; today's tests #16/#17 are unrelated to the resolver
- **Fix:** 4 new edge-case tests added at the end (Tests #24-#27); pre-existing tests stay green under Mockito-defaults
- **Files modified:** see "Files Created/Modified"
- **Verification:** 30/30 unit tests pass
- **Committed in:** `996934f3`

**3. [Task 88-05-04 Skipped] Visual playwright-cli check not applicable**
- **Found during:** Task 88-05-04 entry — no template change to verify
- **Issue:** Defensive Future-Proofing means no UI surface changed
- **Fix:** Documented decision; recorded as "skipped" rather than re-running the dev server for a no-op baseline
- **Files modified:** none
- **Verification:** N/A
- **Committed in:** N/A

---

**Total deviations:** 3 (1 scope clarification, 1 test-numbering correction, 1 skipped checkpoint)
**Impact on plan:** DRIV-01 acceptance fully met; DRIV-02 acceptance met for surfaces that exist (service + controller); template surface intentionally deferred to a future change that needs it. Test count delta and coverage delta both as expected (+6 tests, +0.05 pp LINE).

## Issues Encountered
- **First `./mvnw clean verify -Pe2e` run hit a transient build failure** (BUILD FAILURE without any `<<<FAILURE>>>` marker in surefire reports — exit non-zero, no test-level failure surfaced). Re-running the same command on the same HEAD produced `BUILD SUCCESS` in 10:19 min with all 1685 tests passing. Per [[no-flaky-dismissal]] this is NOT a flake to be dismissed — it indicates a transient infra issue (likely TestContainers reuse or process-level fork race). The first run produced no diagnostic artifacts (no surefire `txt` file with FAILED, no dump file) so the root cause cannot be pinpointed retroactively. **Action:** documented here; if it recurs in Wave 6 or in the v1.12 milestone PR CI, raise as a Phase 89 PERF-01 (per-fork backup-staging-dir) candidate or as a fresh debug session.

## Plan-05 Final Gate

`./mvnw clean verify -Pe2e` exit 0 (10:19 min, run at 2026-05-19T09:21:40+02:00)

| Metric | Plan-02 Baseline | Plan-05 Post | Delta |
| --- | --- | --- | --- |
| LINE coverage | 89.01 % | **89.06 %** | **+0.05 pp** |
| INSTRUCTION coverage | 88.06 % | 88.11 % | +0.05 pp |
| BRANCH coverage | 76.68 % | 76.83 % | +0.15 pp |
| Surefire tests | 1400 | 1406 | +6 (4 DRIV-01 + 2 DRIV-02) |
| Failsafe tests (incl. e2e) | 279 | 279 | 0 |
| Total tests | 1679 | 1685 | +6 |
| Build duration | 9:11 min | 10:19 min | +1:08 |
| SpotBugs BugInstance count | 0 | 0 | 0 |

All DRIV-01 contracts validated: 4 edge-case tests cover the 4 scenarios documented in `.planning/debug/deferred/shortname-resolver-picks-parent-without-phaseteam.md` § Resolution. Test #26 explicitly exercises the D-07 legacy fallback (no REGULAR phase → parent wins, no `BusinessRuleException`, `verifyNoInteractions(phaseTeamRepository)`).

## User Setup Required
None — service + controller + test changes only. No new dependencies, no environment changes, no migration. The new `seasonPhaseService` + `phaseTeamRepository` are already auto-wired beans existing in the Spring context.

## Next Phase Readiness
- Plan 88-06 (REL-02 retroactive-release runbook) is the last plan in Phase 88 and is pure documentation — no code-modifying surface
- v1.12 milestone PR squash-merge to master will trigger the hardened `release.yml` (REL-01) to produce `v1.12.0` automatically; the runbook from REL-02 documents the catch-up procedure for v1.10.0 + v1.11.0

---
*Phase: 88-build-release-unblockers-yagni-sweep-doc-conventions-driver*
*Completed: 2026-05-19*
