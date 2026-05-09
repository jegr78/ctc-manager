---
phase: 61-cleanup-quality-gate
fixed_at: 2026-05-01T20:55:00Z
review_path: .planning/phases/61-cleanup-quality-gate/61-REVIEW.md
iteration: 1
fix_scope: critical_warning
findings_in_scope: 8
fixed: 8
skipped: 0
status: all_fixed
---

# Phase 61: Code Review Fix Report

**Fixed at:** 2026-05-01T20:55:00Z
**Source review:** `.planning/phases/61-cleanup-quality-gate/61-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 8 (1 BLOCKER + 7 WARNINGs; 5 INFOs deferred per `fix_scope: critical_warning`)
- Fixed: 8
- Skipped: 0
- Final test gate: `./mvnw test` — 1172 tests run, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS

## Fixed Issues

### CR-01: REGULAR matchday creation poisons sortIndex when a PLAYOFF phase already has matchdays

**Files modified:** `src/main/java/org/ctc/domain/service/MatchdayService.java`,
`src/main/java/org/ctc/dataimport/CsvImportService.java`,
`src/test/java/org/ctc/domain/service/MatchdayServiceTest.java`,
`src/test/java/org/ctc/dataimport/CsvImportServiceTest.java`
**Commit:** `98bcfe2`
**Status:** fixed: requires human verification (logic-scope change — please run a manual playoff -> regular create flow in dev)
**Applied fix:**
- TDD-first: added two regression unit tests in `MatchdayServiceTest`
  (`givenSeasonWithPlayoffMatchdays_whenCreateInline_thenSortIndexScopedToRegularPhase`,
  `givenPlayoffMatchdayWithSameLabel_whenCreateInlineForRegular_thenAllowsCreation`).
  Confirmed RED before applying the fix.
- Switched 3 callsites from `findBySeasonIdOrderBySortIndexAsc` to
  `findByPhaseIdOrderBySortIndexAsc(regular.getId())`:
  `MatchdayService.createInline`, `CsvImportService.findOrCreateMatchday`, and
  `CsvImportService.checkDuplicate`. The `regular` SeasonPhase is resolved via
  `seasonPhaseService.findRegularPhase` and reused for both the existence
  lookup and the new Matchday's phase binding.
- Updated three pre-existing tests to use phase-scoped stubbing
  (`findByPhaseIdOrderBySortIndexAsc`).

### WR-01: SiteGeneratorService alltime-standings fallback path is dead and would crash if reached

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
**Commit:** `c329173` (combined with WR-05 since both touch the same lines)
**Applied fix:** Replaced the dead else-branch (`standingsService.calculateStandings(season.getId())`,
which would now throw via `findRegularPhase`) with `continue` for REGULAR-less seasons,
mirroring the loop-level skip on the outer `generate()` method. Updated the comment
to reflect the V6 reality (bridge gone, no fallback possible).

### WR-02: SeasonManagementService.getDetailData is dead code that crashes on legacy seasons

**Files modified:** `src/main/java/org/ctc/domain/service/SeasonManagementService.java`,
`src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java`
**Commit:** `622dd89`
**Applied fix:** Verified zero callers in `src/main/java`. Deleted the `getDetailData`
method, the `SeasonDetailData` record, and the corresponding unit test
(`givenExistingId_whenGetDetailData_thenReturnsSeasonWithPlayoffAndComputedFlags`).

### WR-03: V6 migration drops `playoff_seasons` without `IF EXISTS`

**Files modified:** `src/main/resources/db/migration/V6__cleanup_legacy_season_columns.sql`
**Commit:** `3859fba`
**Applied fix:** Added `IF EXISTS` to `DROP TABLE playoff_seasons` for consistency
with the rest of the file's portable/idempotent DDL pattern. V6 is editable
(not yet released).

### WR-04: Playoff/SeasonPhase data co-existence (start_date / end_date / event_duration_minutes)

**Files modified:** `src/main/java/org/ctc/domain/model/Playoff.java`
**Commit:** `bf27a1c` (docs-only)
**Status:** fixed: documentation-only mitigation; full V7 refactor deferred to Phase 62
**Applied fix:** Added a Javadoc block above `Playoff.startDate` documenting:
- The drift risk (Playoff form vs. Phase form write to different columns)
- Which path is currently authoritative for `RaceCalendarService.resolveEventDuration`
- That a follow-up V7 migration is planned to drop the duplicate columns

**Why not full fix here:** Option (a) — V7 migration + RaceCalendarService /
PlayoffService refactor — is genuine schema/code restructuring outside
Phase 61's "ship V6 + cascade" scope, and matches the briefing's
"architectural change beyond plan scope" guidance. The Phase 62 plan
should pick this up; the Javadoc gives the next maintainer a clean
starting point.

### WR-05: Stale `@SuppressWarnings("deprecation")` on a non-deprecated method

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
**Commit:** `c329173` (combined with WR-01)
**Applied fix:** Removed `@SuppressWarnings("deprecation")` along with the dead
fallback branch that motivated it. None of the `StandingsService.calculateStandings`
overloads carries `@Deprecated`.

### WR-06: RaceCalendarService NPEs on a corrupt playoff race (no matchday or no phase)

**Files modified:** `src/main/java/org/ctc/domain/service/RaceCalendarService.java`
**Commit:** `b6f8623`
**Applied fix:** Added null guards on `race.getMatchday()` and
`matchday.getPhase()` in `resolveEventDuration`. When the chain is incomplete,
returns `null`, which the caller maps to the meaningful
`IllegalStateException("Event duration not configured...")` — instead of an
opaque NPE.

### WR-07: TestDataService.seedPlayoffs computes 2024 standings via inline aggregation

**Files modified:** `src/main/java/org/ctc/admin/TestDataService.java`
**Commit:** `34a8f8a`
**Applied fix:** Replaced the hand-rolled team-score map (which double-counted
drivers in a Phase 56 D-04 succession case) with the same
`playoffService.createPlayoff` + `playoffSeedingService.autoSeedBracket` pattern
used by the 2023 branch. Net diff: -52 / +17 lines. Aligns 2024 dev-seed data
with the canonical D-15 Top-N-from-REGULAR-standings flow used in production.

## Skipped Issues

_None — all 8 in-scope findings were addressed._

## Out-of-scope (Info findings, deferred)

Per `fix_scope: critical_warning`, the following 5 INFO findings were not
processed by this fixer pass and remain documented in `61-REVIEW.md` for
follow-up:

- IN-01: GroupsSeasonE2ETest repository-level setup framing
- IN-02: V6MigrationTest as `@SpringBootTest` Surefire fork-cost (optimisation only)
- IN-03: SeasonRepository methods lack post-V6 javadoc
- IN-04: matchday-detail.html inline styles (pre-existing, non-`.btn`)
- IN-05: StandingsService.calculateBuchholzScoresForPhase unused `groupId` parameter

## Verification

After the final commit, the full Surefire gate was run:

```
./mvnw test
[INFO] Tests run: 1172, Failures: 0, Errors: 0, Skipped: 1
[INFO] BUILD SUCCESS
```

Two new regression tests cover CR-01 (sortIndex poisoning + cross-phase
duplicate-label false positive). All other tests (including
`V6MigrationTest`, `SiteGeneratorServiceTest`, `SeasonManagementServiceTest`,
`PlayoffServiceTest`, `RaceCalendarServiceTest`, `TestDataServiceIntegrationTest`,
`MatchdayServiceTest`, `CsvImportServiceTest`) were re-verified individually
during the per-finding fix loop.

The `-Pe2e` Failsafe profile was NOT re-run (per the briefing's
"test optimisation" rule — heavy gate, no template/UI changes in this fixer
pass). The orchestrator should run a final `./mvnw verify -Pe2e` before
proceeding to PR.

---

_Fixed: 2026-05-01T20:55:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
