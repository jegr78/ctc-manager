---
gsd_state_version: 1.0
milestone: v1.9
milestone_name: "Season Phases & Groups"
status: roadmap_ready
last_updated: "2026-04-26T00:00:00.000Z"
last_activity: 2026-04-26
progress:
  total_phases: 6
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-26)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** v1.9 Season Phases & Groups — Saison als Klammer mit Phasen (Regular/Playoff/Placement) und optionalen Sub-Gruppen, Driver-Import wieder eindeutig.

## Current Position

Phase: 56 (Model & Schema Foundation) — not started
Plan: —
Status: Roadmap created, awaiting phase planning
Last activity: 2026-04-26 — Roadmap created for v1.9 (6 phases, 56-61, 33 requirements).

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

**Next action:** Run `/gsd-plan-phase 56` to create the implementation plan for Phase 56 (Model & Schema Foundation).

**Branch:** `master` (after v1.8 merge — feature branch deleted locally and remotely).
