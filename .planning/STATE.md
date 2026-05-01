---
gsd_state_version: 1.0
milestone: v1.9
milestone_name: Season Phases & Groups
status: executing
last_updated: "2026-05-01T10:12:23.668Z"
last_activity: 2026-05-01 -- Phase 61 planning complete
progress:
  total_phases: 6
  completed_phases: 5
  total_plans: 31
  completed_plans: 26
  percent: 84
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-26)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** Phase 60 — admin-ui

## Current Position

Phase: 61
Plan: Not started
Status: Ready to execute
Last activity: 2026-05-01 -- Phase 61 planning complete

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

### Phase Numbering

Continuing from v1.8 (last phase: 55). v1.9 phases start at **Phase 56**.

- Phase 56: Model & Schema Foundation (MODEL-01..08, MIGR-01, MIGR-07)
- Phase 57: Data Migration (MIGR-02, MIGR-03, MIGR-04, MIGR-05)
- Phase 58: Service Layer (SVC-01..05)
- Phase 59: Import & Test Data (IMPORT-01..04, DATA-01, DATA-02)
- Phase 60: Admin UI (UI-01..07)
- Phase 61: Cleanup & Quality Gate (MIGR-06, QUAL-01..03)

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

Last session: 2026-05-01T07:39:06.394Z

**Plan 58-06 commits:**

- `4b2cb3d` test(58-06): add failing tests for D-18 delete-guard, D-25 auto-sync, D-26 dual-API, D-23 phase-aware sitegen
- `7067f31` feat(58-06): SeasonManagementService delete-guard + REGULAR-phase auto-sync + MatchdayService dual-API + SiteGenerator phase-aware (D-18, D-25, D-26, D-23 — 2 BEHAVIOR CHANGES)
- `fe4e5c2` docs(58-06): complete SeasonManagementService + MatchdayService + SiteGenerator phase-aware plan — SVC-01 closure

**Behavior changes shipped (must call out at PR time):**

- D-18 strict-delete-guard — `SeasonManagementService.delete` now throws `BusinessRuleException` when matchdays / playoffs / phase-teams reference any phase of the season (previously: blind cascade).
- D-25 REGULAR-phase auto-sync — `SeasonManagementService.save` now find-or-creates the REGULAR `SeasonPhase` and write-throughs format / totalRounds / legs / eventDurationMinutes / start+end dates / raceScoring / matchScoring on every save (atomic with the Season insert via single `@Transactional`).

**Next action:** `/gsd-verify-work 58` (UAT validation) → then proceed to Phase 59 (Import & Test Data) or Phase 60 (Admin UI) per ROADMAP — both depend on Phase 58 and are independent of each other.

**Branch:** `gsd/v1.9-season-phases-groups` (3 plan-58-06 commits ahead of last checkpoint; working tree has staged STATE.md / ROADMAP.md awaiting tracking-update commit).
