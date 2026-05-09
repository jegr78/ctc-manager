---
gsd_state_version: 1.0
milestone: v1.9
milestone_name: Season Phases & Groups
status: executing
stopped_at: Completed Plan 70-03 + final `./mvnw verify -Pe2e` gate (BUILD SUCCESS, 1226 unit + 31 E2E tests, JaCoCo line 0.8718). Phase 70 is complete and ready for `/gsd-verify-work 70`.
last_updated: "2026-05-09T15:20:29.973Z"
last_activity: 2026-05-09 -- Phase 70 execution started
progress:
  total_phases: 2
  completed_phases: 1
  total_plans: 8
  completed_plans: 7
  percent: 88
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-26)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** Phase 70 — driver-import-parent-only-team-resolution

## Current Position

Phase: 70 (driver-import-parent-only-team-resolution) — EXECUTING
Plan: 1 of 4
Status: Executing Phase 70
Last activity: 2026-05-09 -- Phase 70 execution started

## Completed Milestones

- v1.0 Technical Debt Cleanup (5 phases, 12 plans) — shipped 2026-04-04
- v1.1 Codebase Concerns Cleanup (10 phases, 20 plans) — shipped 2026-04-07
- v1.2 Driver Merge (4 phases, 5 plans) — shipped 2026-04-07
- v1.3 English Test Data (8 phases) — shipped 2026-04-10
- v1.5 Code Review Fixes (9 phases, 14 plans) — shipped 2026-04-15
- v1.6 Static Site Quality (17 phases, 56 requirements) — shipped 2026-04-18
- v1.8 Bulk Driver Import from Google Sheets (2 phases, 4 plans, +52 tests) — shipped 2026-04-25

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md Key Decisions table.

- [v1.8 start]: Foundation document is `docs/superpowers/specs/2026-04-24-bulk-driver-import-design.md` — authored via `/gsd-explore` brainstorming; approved as canonical design before milestone kickoff.
- [v1.8 start]: Reuse `GoogleSheetsService`, `DriverMatchingService` (4-stage fuzzy), and `CsvImportService` preview-state pattern — no parallel infrastructure.
- [v1.8 start]: Missing Seasons/Teams are errors, never auto-created — consistent with "No Fallback Calculations" principle.
- [v1.8 start]: E2E tests deferred (Playwright x Google Sheets mocking is fragile); Unit + Integration tests must meet 82% coverage gate.
- [v1.8 roadmap]: Two-phase structure chosen over three-phase split. Rationale: Controller+templates+execute form a single cohesive deliverable — splitting preview-controller from execute-controller would ship a non-verifiable intermediate (preview form with no execute path). Phase 54 delivers a fully unit-tested service; Phase 55 delivers the end-to-end admin flow with integration coverage.
- [v1.9 roadmap]: Six-phase structure chosen. Rationale: schema (56) must precede data migration (57) which must precede services (58); MIGR-06 (cleanup drop) is deferred to Phase 61 as a safety gate — old bridge columns remain intact until all services and UI are fully migrated off them. Phase 59 (import + test data) and Phase 60 (UI) both depend on services (58) but are independent of each other and can be planned/executed in parallel if desired.
- [v1.9 roadmap]: Phase 60 (Admin UI) depends on Phase 58 (Service Layer), not Phase 59 (Import & Test Data) — UI does not depend on the seeder rebuild. Both 59 and 60 unblock Phase 61.
- [phase 56 discuss]: Entity Java-side scope = **parallel additive**. New entities + new bidirectional fields (`Season.phases`, `Matchday.phase`, `Playoff.phase`) added alongside the old `Season` fields and `season_id` FKs. ROADMAP-Phase-56-SC3 wording reinterpreted: old Season fields stay until Phase 61. Service-layer rewrite stays in Phase 58.
- [phase 56 discuss]: `matchdays.phase_id` and `playoffs.phase_id` columns are **NULLABLE** in Phase 56's V3 migration; Phase 57's data migration backfills values and flips both to NOT NULL in the same step.
- [phase 56 discuss]: DB-level uniqueness via `UNIQUE (season_id, phase_type)` on `season_phases` (max 1× per type per season) + `UNIQUE (phase_id, team_id)` on `phase_teams`. No CHECK constraints — `@Enumerated(EnumType.STRING)` plus typed enums cover value validation.
- [phase 56 discuss]: Existing `SeasonFormat` enum is **reused** for `SeasonPhase.format` (no rename to PhaseFormat). New top-level enums `PhaseType` (REGULAR/PLAYOFF/PLACEMENT) and `PhaseLayout` (LEAGUE/GROUPS/BRACKET) in `org.ctc.domain.model`.
- [phase 56 discuss]: New repositories ship with default Spring Data CRUD only — no custom finders in Phase 56 (deferred to Phase 58 when services need them).
- [Phase ?]: Bridge uses findByType (Optional) instead of findRegularPhase to avoid transaction rollback-only poisoning; legacy fallback for pre-V4 seasons
- [Plan 58-05]: PlayoffService.createPlayoff atomically writes PLAYOFF SeasonPhase + Playoff in single @Transactional boundary (D-19, Pitfall 2 mitigation). Duplicate-playoff exception type swapped from IllegalArgumentException to BusinessRuleException for D-03 consistency.
- [Plan 58-05]: PlayoffSeedingService.autoSeedBracket dual-flow — manual PlayoffSeed rows have priority (legacy admin workflow); D-15 REGULAR-phase Top-N is the fallback when no manual seeds exist. PhaseTeam roster on PLAYOFF phase populated as side-effect of D-15 seeding (D-20).
- [Plan 58-05]: Pitfall 4 mitigated — PlayoffService.addRaceToMatchup writes matchday.phase=playoff.getPhase() so playoff race results attribute correctly to PLAYOFF phase in DriverRankingService.
- [Phase ?]: [Plan 70-01]: Inverted Phase 66 D-04 sub-team resolver to parent-precedence; removed group-resolution branch in DriverSheetImportService. Production compile clean.
- [Phase 70]: [Plan 70-02]: UX decommission — DriverSheetImportController no longer computes showGroupColumn (3 imports + seasonPhaseService field + 10-line GROUPS-detection block deleted); driver-import-preview.html renders no Group column / no warning box across 5 buckets; DriverSheetImportControllerTest @Test count 21 -> 19 (two GROUPS-/null-resolved-group tests deleted, dead PhaseLayout import + SeasonPhaseRepository field removed). Production compile clean (./mvnw clean compile -> BUILD SUCCESS, 182 source files). Service-side test compile remains intentionally RED until Plan 70-03 (Wave 2).
- [Phase 70]: [Plan 70-03]: Test reconciliation + Phase-66 doc addendum + final verify gate. DriverSheetImportServiceTest reconciled (8 superseded tests deleted, 2 multi-match regression fences preserved, 16 stale findRegularPhase stubs removed, dead-accessor lines dropped from #21, +1 new D-13 parent-always test using T-MRL fixtures with execute-path ArgumentCaptor proving SeasonDriver.team == parent). DriverSheetImportServiceIT reconciled (3 group-resolution IT tests deleted, Test #8 warnings assertion replaced with positive newDrivers assertion, unused dataRows helper removed). 66-CONTEXT.md D-06..D-09 carry inline supersede annotations; 66-VERIFICATION.md gets `## Phase-70 Re-Open Addendum (2026-05-09)` section + frontmatter `re_verification` Phase-70 entry (single-object schema preserved, May-8 entry archived under `previous_re_verification:` sibling). Final `./mvnw verify -Pe2e` PASSED: 1226 unit tests, 31 E2E tests, JaCoCo line ratio 0.8718 (gate 0.82).

### Phase Numbering

Continuing from v1.8 (last phase: 55). v1.9 phases start at **Phase 56**.

- Phase 56: Model & Schema Foundation (MODEL-01..08, MIGR-01, MIGR-07)
- Phase 57: Data Migration (MIGR-02, MIGR-03, MIGR-04, MIGR-05)
- Phase 58: Service Layer (SVC-01..05)
- Phase 59: Import & Test Data (IMPORT-01..04, DATA-01, DATA-02)
- Phase 60: Admin UI (UI-01..07)
- Phase 61: Cleanup & Quality Gate (MIGR-06, QUAL-01..03)
- Phase 62: Public Site Phase + Group Awareness (TBD — to be derived during /gsd-discuss-phase)

### Roadmap Evolution

- 2026-05-02: Phase 62 (Public Site Phase + Group Awareness) added at end of v1.9 milestone. Discovered during Phase 61 UAT — admin-side phase/group model is fully wired but invisible on the public static site. Without Phase 62 the v1.9 feature ships externally invisible.
- 2026-05-07: Phase 66 (Team ShortName Collision Fix — Driver Import) added at end of v1.9 milestone. Discovered during v1.9 UAT — `DriverSheetImportService` crashes with `IncorrectResultSizeDataAccessException` when parent + sub-team share the same `shortName` (e.g. ZFS parent + ZFS sub). 5 call sites in the import service share the same `findByShortName`-on-non-unique-column bug. Resolution policy: prefer parent (`parentTeam IS NULL`) on multi-match.
- 2026-05-07: Phase 67 (Comment Cleanup Re-Sweep) added — UAT review found WHAT-style / narrative comments re-introduced across the v1.9 cluster (Phases 56–66). Same CLAUDE.md policy as Phases 20-21/53/61 but enforcement regressed. Scope extended on user request to include `src/test/java` (test files re-accumulated narrative comments too); BDD `// given` / `// when` / `// then` markers explicitly preserved. Sweep production + test code + ideally lock the rule with a CI / pre-commit guard.
- 2026-05-07: Phase 68 (Lombok Unsafe Deprecation Warning Fix) added — JDK 24+/25 emits terminally-deprecated warnings from `lombok.permit.Permit` calling `sun.misc.Unsafe::objectFieldOffset`. Currently shipping Lombok 1.18.44; upstream fix is in newer point releases that switched `Permit` to `MethodHandles.privateLookupIn`. Plan: verify the resolved version under the Spring Boot starter parent, pin a property override in `pom.xml` if needed, and confirm warnings are gone in `./mvnw verify` + `./mvnw spring-boot:run`.
- 2026-05-09: Phase 70 (Driver Import: Parent-Only Team Resolution) added — Live-UAT against local MariaDB (Saison 2023, parent MRL + Subs MRL 1/MRL 2 in different Groups) revealed that Phase 66 D-04 default ("sub-team-with-PhaseTeam wins over parent") violates the user's domain model. User clarification 2026-05-09: SeasonDriver.team is always the parent; sub-team assignment happens per-match via RaceLineup, never per-phase. Phase 70 inverts the Phase-66 resolver default (parent always wins), removes the entire Group-resolution UX from the import preview (`resolvedGroupName`, `usesGroups`, `showGroupColumn`, `TEAM_NOT_IN_REGULAR_PHASE` warning), and adds a Phase-66-VERIFICATION.md addendum marking superseded truths. No schema changes, no SeasonDriver data migration needed.

### Key Technical Context

- Foundation document: `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` (architecture plan from brainstorming session).
- New entities: `SeasonPhase` (season_phases), `SeasonPhaseGroup` (season_phase_groups), `PhaseTeam` (phase_teams).
- FK migrations: `Matchday.season_id` -> `Matchday.phase_id`; `Playoff.season_id` -> `Playoff.phase_id`; M:N `playoff_seasons` table dropped in MIGR-06.
- Fields migrating from `Season` to `SeasonPhase`: format, totalRounds, legs, eventDurationMinutes, startDate, endDate, raceScoring_id, matchScoring_id.
- Fields staying in `Season`: id, year, number, name, description, active, audit fields, cars/tracks (season-wide assets).
- Driver import fix: `findByYearAndNumber(int, int)` replaces `findByYear(int)`; tab pattern extended to `^\d{4}_S\d+$`.
- Group membership for imported drivers: resolved implicitly via `PhaseTeam` of the REGULAR phase — no per-driver override in sheet.
- MIGR-06 must be the last migration executed — only safe after all Java code references have been removed from old columns.
- Constraint: UNIQUE on `(phase_id, team_id)` in `phase_teams`; max 1 REGULAR + max 1 PLAYOFF + max 1 PLACEMENT per season.
- Critical files to modify (~25-30): Season, Matchday, Playoff entities; SeasonRepository, MatchdayRepository, PlayoffRepository; SeasonManagementService, StandingsService, DriverRankingService, MatchdayGeneratorService, PlayoffService, PlayoffSeedingService, DriverSheetImportService; SeasonController, StandingsController, DriverSheetImportController, MatchdayController, PlayoffController; SeasonForm; 6 Thymeleaf templates; TestDataService + DevDataSeeder.
- Critical files to create (~12-15): SeasonPhase, SeasonPhaseGroup, PhaseTeam entities; SeasonPhaseRepository, SeasonPhaseGroupRepository, PhaseTeamRepository; SeasonPhaseService; SeasonPhaseController, SeasonPhaseGroupController; SeasonPhaseForm, SeasonPhaseGroupForm, PhaseTeamForm; 6 Flyway migration files; 3 Thymeleaf templates (season-phase-form, season-phase-detail, season-phase-group-form).

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-05-09T13:57:09Z

**Plan 70-03 commits:**

- `722e40c` test(70-03): reconcile DriverSheetImportServiceTest with parent-only resolver — delete tests #15-#20/#23/#24, preserve #21/#22, drop stale stubs (D-11, D-12)
- `1855eb6` test(70-03): reconcile DriverSheetImportServiceIT — delete group-resolution IT tests, adjust Test #8 (D-09)
- `5b86482` test(70-03): add parent-always regression test for sub-team-collision in GROUPS phase (D-13)
- `b863c80` docs(70-03): Phase-66 re-open addendum + superseded inline notes (D-15, D-16, D-17)

**Stopped at:** Completed Plan 70-03 + final `./mvnw verify -Pe2e` gate (BUILD SUCCESS, 1226 unit + 31 E2E tests, JaCoCo line 0.8718). Phase 70 is complete and ready for `/gsd-verify-work 70`.

**Next action:** Run `/gsd-verify-work 70` to confirm phase verification, then proceed with milestone v1.9 closure.

**Branch:** `gsd/v1.9-season-phases-groups` (4 plan-70-03 commits + Plan 70-03 metadata commit on top of `17e2225`).
