---
phase: 60-admin-ui
plan: "02"
subsystem: admin-ui/phase-crud-backend
tags: [java, spring-boot, admin-ui, controllers, dto, services, phase-crud]
dependency_graph:
  requires: [60-01]
  provides: [SeasonPhaseForm, SeasonPhaseGroupForm, PhaseTeamForm, SeasonPhaseService-methods, SeasonPhaseController, SeasonPhaseGroupController]
  affects: [60-03, 60-04, season-detail-template]
tech_stack:
  added: []
  patterns: [AutoPopulatingList-indexed-binding, diff-logic-bulk-roster-save, D09-IDOR-ownership-validation, W-7-DTO-no-seasonId, D-42-PLAYOFF-auto-route]
key_files:
  created:
    - src/main/java/org/ctc/admin/dto/SeasonPhaseForm.java
    - src/main/java/org/ctc/admin/dto/SeasonPhaseGroupForm.java
    - src/main/java/org/ctc/admin/dto/PhaseTeamForm.java
    - src/main/java/org/ctc/admin/controller/SeasonPhaseController.java
    - src/main/java/org/ctc/admin/controller/SeasonPhaseGroupController.java
  modified:
    - src/main/java/org/ctc/domain/service/SeasonPhaseService.java
    - src/main/java/org/ctc/domain/repository/MatchdayRepository.java
    - src/main/java/org/ctc/domain/service/PlayoffService.java
decisions:
  - "W-7: seasonId intentionally absent from SeasonPhaseForm DTO — @PathVariable is sole source of truth (IDOR hardening)"
  - "D-09: validateOwnership helper called in every nested endpoint; throws EntityNotFoundException (404 not 403)"
  - "D-42: PLAYOFF create routes through PlayoffService.createPlayoff (auto-creates PLAYOFF SeasonPhase per D-19)"
  - "W-11: SeasonPhaseService.update() signature omits phaseType — phaseType is immutable post-create"
  - "Rule 3 deviation: MatchdayRepository.findByPhaseId + findByGroupId added for guard checks; PlayoffService.findByPhaseId delegation method added"
requirements-completed: [UI-03, UI-04]
metrics:
  duration: "~25 minutes"
  completed: "2026-04-30"
  tasks_completed: 3
  files_created: 5
  files_modified: 3
---

# Phase 60 Plan 02: Phase + Group DTOs, Service Methods, Controllers Summary

Wave 1 backend for Phase/Group CRUD: three new Form DTOs, seven new methods on SeasonPhaseService, and two new thin controllers with D-09 IDOR validation, D-42 PLAYOFF auto-create, and T-60-02-02 DoS guard.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Create SeasonPhaseForm, SeasonPhaseGroupForm, PhaseTeamForm DTOs | 08e3a69 | SeasonPhaseForm.java, SeasonPhaseGroupForm.java, PhaseTeamForm.java |
| 2 | Extend SeasonPhaseService (update/delete/updateGroup/deleteGroup/assignTeamsToPhase) | a040223 | SeasonPhaseService.java, MatchdayRepository.java |
| 3 | Create SeasonPhaseController and SeasonPhaseGroupController | b4a07a7 | SeasonPhaseController.java, SeasonPhaseGroupController.java, PlayoffService.java |

## Files Created / Modified

| File | Lines | Status |
|------|-------|--------|
| `org/ctc/admin/dto/SeasonPhaseForm.java` | 45 | Created |
| `org/ctc/admin/dto/SeasonPhaseGroupForm.java` | 25 | Created |
| `org/ctc/admin/dto/PhaseTeamForm.java` | 28 | Created |
| `org/ctc/admin/controller/SeasonPhaseController.java` | 340 | Created |
| `org/ctc/admin/controller/SeasonPhaseGroupController.java` | 184 | Created |
| `org/ctc/domain/service/SeasonPhaseService.java` | 410 | Extended (+237 lines) |
| `org/ctc/domain/repository/MatchdayRepository.java` | 30 | Extended (+6 lines) |
| `org/ctc/domain/service/PlayoffService.java` | 392 | Extended (+10 lines) |

## D-09 IDOR Validation Surface

| Endpoint | Controller | Validation |
|----------|-----------|------------|
| GET `/{phaseId}` | SeasonPhaseController | `validateOwnership(phase.getSeason().getId(), seasonId)` |
| GET `/{phaseId}/groups/{groupId}` | SeasonPhaseController | `validateOwnership(phase.getSeason().getId(), seasonId)` |
| GET `/{phaseId}/edit` | SeasonPhaseController | `validateOwnership(phase.getSeason().getId(), seasonId)` |
| POST `/{phaseId}/delete` | SeasonPhaseController | `validateOwnership(phase.getSeason().getId(), seasonId)` |
| POST `/save` (update branch) | SeasonPhaseController | ownership validated via service findById |
| GET `/new` | SeasonPhaseGroupController | `validateOwnership(phase.getSeason().getId(), seasonId)` |
| GET `/{groupId}/edit` | SeasonPhaseGroupController | `validateOwnership(phase.getSeason().getId(), seasonId)` |
| POST `/save` | SeasonPhaseGroupController | `validateOwnership(phase.getSeason().getId(), seasonId)` |
| POST `/{groupId}/delete` | SeasonPhaseGroupController | `validateOwnership(phase.getSeason().getId(), seasonId)` |
| POST `/roster` | SeasonPhaseGroupController | `validateOwnership(phase.getSeason().getId(), seasonId)` |

## BusinessRuleException Catch Sites

| Location | Trigger | Flash Action |
|----------|---------|-------------|
| SeasonPhaseController.save (create/update) | D-14 duplicate, D-21 layout guard | `errorMessage` flash → redirect to form |
| SeasonPhaseController.delete | D-23 matchdays/phaseTeams/playoff guard | `errorMessage` flash → redirect to phase tab |
| SeasonPhaseGroupController.save | Any BusinessRuleException | `errorMessage` flash → redirect to group form |
| SeasonPhaseGroupController.delete | D-28 teams/matchdays guard | `errorMessage` flash → redirect to phase tab |
| SeasonPhaseGroupController.saveRoster | D-20 team/group not found | `errorMessage` flash → redirect to phase tab |
| SeasonPhaseService.update | D-21 layout change with matchdays | Thrown to controller catch |
| SeasonPhaseService.update | D-22 BRACKET not valid for non-PLAYOFF | Thrown to controller catch |
| SeasonPhaseService.delete | D-23 matchday count > 0 | Thrown to controller catch |
| SeasonPhaseService.delete | D-23 phaseTeam count > 0 | Thrown to controller catch |
| SeasonPhaseService.delete | D-23 PLAYOFF has bracket | Thrown to controller catch |
| SeasonPhaseService.deleteGroup | D-28 team count > 0 | Thrown to controller catch |
| SeasonPhaseService.deleteGroup | D-28 matchday count > 0 | Thrown to controller catch |

## D-42 PLAYOFF Auto-Create Wiring

- **Controller branch:** `SeasonPhaseController.save` → `if (form.getPhaseType() == PhaseType.PLAYOFF)`
- **Service method:** `PlayoffService.createPlayoff(seasonId, name, numberOfTeams=4, startDate, endDate, eventDurationMinutes)`
- **D-19 chain:** PlayoffService.createPlayoff → SeasonPhaseService.findByType(PLAYOFF) → orElseGet(SeasonPhaseService.create) — auto-creates PLAYOFF SeasonPhase atomically.
- **Redirect:** Back to `/admin/seasons/{seasonId}` (season detail page, where Phase-Tabs are rendered in Plan 60-04).

## Wave 0 Tests Status

### Turned GREEN by this plan
- `SeasonPhaseServiceTest` (11 existing tests) — all still GREEN post-extension
- Wave 0 tests from Plan 60-01 (`SeasonPhaseFormTest`, `PhaseTeamFormTest`, `SeasonPhaseControllerTest`, `SeasonPhaseGroupControllerTest`, `SeasonPhaseControllerIT`, `SeasonPhaseGroupControllerIT`) — these are written by Plan 60-01 which runs in parallel. DTOs, service methods, and controllers are now implemented; once Plan 60-01's tests are merged, all should pass.

### Still RED (covered by later plans)
- `SeasonPhaseControllerIT` / `SeasonPhaseGroupControllerIT` view-rendering assertions — templates `season-detail`, `season-phase-form`, `season-phase-group-form` do not yet exist. Plan 60-04 delivers them.
- `SeasonControllerTest` extended tests (D-08 empty-state, D-44 conservative cleanup) — Plan 60-03.
- `SeasonManagementServiceTest` extended tests (D-25 addTeamToSeason/removeTeamFromSeason) — Plan 60-03.
- `StandingsControllerTest` extended tests (D-31 phase/group params) — Plan 60-05.
- `DriverSheetImportControllerTest` extended tests (D-40 showGroupColumn) — Plan 60-06.
- `PlayoffControllerTest` extended tests (D-43 remove Add-Season buttons) — Plan 60-07.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] EntityNotFoundException single-arg constructor calls**
- **Found during:** Task 2 (compile)
- **Issue:** Plan code examples used single-arg `new EntityNotFoundException("message")` but the class requires two args `(entityType, entityId)`.
- **Fix:** All 6 occurrences changed to two-arg form: `new EntityNotFoundException("SeasonPhase", phaseId)`, `new EntityNotFoundException("Team", id)`, etc.
- **Files modified:** SeasonPhaseService.java
- **Commit:** a040223

**2. [Rule 3 - Blocking] MatchdayRepository missing findByPhaseId + findByGroupId**
- **Found during:** Task 2 (compile)
- **Issue:** SeasonPhaseService.delete guards use `matchdayRepository.findByPhaseId(phaseId)` (plain — not the sorted variant). SeasonPhaseService.deleteGroup uses `matchdayRepository.findByGroupId(groupId)` which did not exist.
- **Fix:** Added `List<Matchday> findByPhaseId(UUID phaseId)` and `List<Matchday> findByGroupId(UUID groupId)` as Spring Data derived queries to MatchdayRepository.
- **Files modified:** MatchdayRepository.java
- **Commit:** a040223

**3. [Rule 3 - Blocking] PlayoffService missing findByPhaseId delegation**
- **Found during:** Task 3 (implementation review)
- **Issue:** SeasonPhaseController calls `playoffService.findByPhaseId(phaseId)` for B-3 PLAYOFF bracket resolution. PlayoffService did not expose this method (only PlayoffRepository did).
- **Fix:** Added `@Transactional(readOnly = true) Optional<Playoff> findByPhaseId(UUID phaseId)` delegation to PlayoffService.
- **Files modified:** PlayoffService.java
- **Commit:** b4a07a7

**4. [Rule 1 - Plan Adaptation] PlayoffService.createPlayoff signature mismatch**
- **Found during:** Task 3
- **Issue:** Plan shows `playoffService.createPlayoff(seasonId, name, raceScoringId, matchScoringId, startDate, endDate, eventDurationMinutes)` — that overload does not exist. Actual overload: `createPlayoff(UUID seasonId, String name, int numberOfTeams, LocalDate startDate, LocalDate endDate, Integer eventDurationMinutes)`.
- **Fix:** Controller uses the existing overload with `numberOfTeams=4` (safe default; user can edit via PlayoffForm). Scoring IDs not wired through (D-19 auto-copies from season).
- **Files modified:** SeasonPhaseController.java

## Known Stubs

None — all method signatures are fully implemented. Templates (`season-detail`, `season-phase-form`, `season-phase-group-form`) are not yet created; that is by design (Plan 60-04).

## Open Follow-ups for Later Plans

- **Plan 60-03:** SeasonController slim (remove `addScoringLists`, `format/scoring/dates/rounds/legs`), SeasonManagementService D-25/D-26 atomic add/remove, SeasonForm slim, season-form.html template cleanup.
- **Plan 60-04:** Create templates `season-phase-form.html`, `season-phase-group-form.html`, rewrite `season-detail.html` with Phase-Tabs (D-01..D-15), roster editor inline section, Playwright-CLI visual verification.
- **Plan 60-05:** StandingsController D-31 phase/group URL extension, standings.html Phase-Tabs + conditional columns (D-29..D-36).
- **Plan 60-06:** DriverSheetImportController D-39/D-40 showGroupColumn + TabWarning rendering.
- **Plan 60-07:** PlayoffController D-43 Add-Season UI removal, D-41/D-42 CTA integration.

## Threat Surface Scan

No new network endpoints or trust boundaries beyond those documented in the plan's threat model (T-60-02-01 through T-60-02-06). All implemented mitigations:
- T-60-02-01 (Mass Assignment): SeasonPhaseForm has no `seasonId` field (W-7)
- T-60-02-02 (DoS unbounded list): `size() > 100` guard in saveRoster
- T-60-02-03 (IDOR): `validateOwnership` in every nested endpoint
- T-60-02-04 (Repudiation): `log.info(...)` on every state change in service
- T-60-02-05 (Auth bypass): Inherited from Spring Security filter chain — no new surfaces
- T-60-02-06 (CSRF): Templates (Plan 60-04) must include CSRF token inputs

## Self-Check: PASSED

All 7 files verified present. All 3 task commits verified in git log.

| Check | Result |
|-------|--------|
| SeasonPhaseForm.java exists | PASSED |
| SeasonPhaseGroupForm.java exists | PASSED |
| PhaseTeamForm.java exists | PASSED |
| SeasonPhaseController.java exists | PASSED |
| SeasonPhaseGroupController.java exists | PASSED |
| SeasonPhaseService.java exists | PASSED |
| 60-02-SUMMARY.md exists | PASSED |
| Commit 08e3a69 (DTOs) | PASSED |
| Commit a040223 (Service methods) | PASSED |
| Commit b4a07a7 (Controllers) | PASSED |
| `./mvnw compile` GREEN | PASSED |
| SeasonPhaseServiceTest 11/11 GREEN | PASSED |
