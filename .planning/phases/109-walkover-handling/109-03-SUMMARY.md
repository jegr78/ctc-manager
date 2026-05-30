---
phase: 109
plan: "03"
subsystem: admin-match
tags: [admin, controller, dto, validation, walkover]
requires: [109-01]
provides:
  - MatchForm.walkoverTeamId
  - MatchService.updateWalkover (validated)
  - walkover dropdown on match edit form
  - 4 controller integration tests
affects: [MatchController, MatchService, MatchForm, match-form-edit.html]
tech-stack:
  added: []
  patterns: [dto-binding, service-layer-validation, errorMessage-flash]
key-files:
  created: []
  modified:
    - src/main/java/org/ctc/admin/dto/MatchForm.java
    - src/main/java/org/ctc/domain/service/MatchService.java
    - src/main/java/org/ctc/admin/controller/MatchController.java
    - src/main/resources/templates/admin/match-form-edit.html
    - src/test/java/org/ctc/admin/controller/MatchControllerTest.java
key-decisions:
  - updateWalkover validates in order — null-clear early-return, then isBye, away-null, team ∉ {home,away} — all throwing BusinessRuleException BEFORE any save (no partial write)
  - Controller calls updateWalkover BEFORE updateDiscordFields inside try/catch; on BusinessRuleException reuses the existing applyErrorFlash helper and redirects without touching discord fields
  - Dropdown bound via th:field=*{walkoverTeamId} (DTO, not entity); hidden on bye matches (th:unless match.bye) since a bye cannot be a walkover
requirements-completed: [WO-04]
duration: ~25 min
completed: 2026-05-30
---

# Phase 109 Plan 03: Walkover Admin Marking Summary

Admins can mark/clear a walkover on the match edit form via a single dropdown (Kein Walkover / Home / Away). `MatchService.updateWalkover` validates (team must be one of the match's two teams, not a bye) and persists or clears `walkover_team_id`; invalid input surfaces an `errorMessage` flash with no DB write.

**Tasks:** 3 | **Files:** 5 modified

## What was built

- **Task 1 — DTO + service:** `MatchForm.walkoverTeamId` (UUID, no validation annotation); `@Transactional MatchService.updateWalkover(matchId, walkoverTeamId)` with the four gates (null-clear, isBye→throw, away-null→throw, team∉{home,away}→throw), loading the Team via `teamRepository.findById(...).orElseThrow(EntityNotFoundException)` and `matchRepository.save`. Parameterized logging.
- **Task 2 — controller + template:** `edit` GET pre-fills `walkoverTeamId` from `match.getWalkoverTeam()`; `saveEdit` POST calls `updateWalkover` in a try/catch on `BusinessRuleException` (→ `applyErrorFlash` + redirect, skipping `updateDiscordFields`) before the existing discord-field flow. `match-form-edit.html` got a `<select th:field="*{walkoverTeamId}">` form-group with the three option states, no inline styles.
- **Task 3 — 4 controller ITs:** `givenMatchEditForm_whenSaveEditWithWalkoverTeam_thenWalkoverPersisted`, `givenByeMatch_whenSaveEditWithWalkoverTeam_thenErrorFlash`, `givenWalkoverMatch_whenSaveEditWithNullWalkoverTeam_thenWalkoverCleared`, `givenMatchEditFormWithUnrelatedTeam_whenSaveEditWithWalkoverTeam_thenErrorFlash`. All green.

## Verification

- `./mvnw -Dtest=MatchControllerTest test` → Tests run: 9, Failures: 0 (5 pre-existing + 4 new walkover ITs).
- `./mvnw clean test-compile` succeeds after each task.
- Validation throws before save (persist test confirms set; bye + unrelated-team tests confirm errorMessage flash AND walkoverTeam stays null).

## Deviations from Plan

**[Rule 1 - Convention] Did not add @Tag("integration") to MatchControllerTest** — Found during: Task 3. The plan said "add @Tag("integration") if missing". The class is named `*Test` (not `*IT`) and already runs as an untagged `@SpringBootTest` under Surefire. Adding `@Tag("integration")` would have risked excluding it from Surefire without Failsafe picking it up (Failsafe runs `*IT`), so the four new ITs would not run at all. Kept the existing class convention (untagged `*Test`); the tests run green under Surefire. Verification: `./mvnw -Dtest=MatchControllerTest test` runs all 9.

**[Rule 1 - UX] Dropdown hidden on bye matches** — Added `th:unless="${match.bye}"` on the walkover form-group (not literally in the plan) so a walkover is not offered on a bye match (a bye cannot be a walkover; the service validation still rejects it defensively). Files: match-form-edit.html.

**Total deviations:** 2 (1 test-routing safeguard, 1 minor UX). **Impact:** none on requirements; both reduce risk.

## Self-Check: PASSED

WO-04 complete. Ready for 109-04 (w/o label in matchday-detail + site/standings + WalkoverE2ETest green).
