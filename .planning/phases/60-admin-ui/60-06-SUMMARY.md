---
plan: 60-06
phase: 60-admin-ui
status: complete
self_check: PASSED
requirements: [UI-07]
files_modified:
  - src/main/resources/templates/admin/playoff-bracket.html
  - src/main/java/org/ctc/admin/controller/PlayoffController.java
  - src/main/java/org/ctc/admin/controller/SeasonController.java
  - src/main/java/org/ctc/domain/service/PlayoffService.java
metrics:
  tasks_completed: 4
  commits: 2
  tests_green: 37
---

# Phase 60 Plan 06: Playoff UI + D-44 SeasonController Cleanup Summary

## Commits

| SHA | Subject |
|-----|---------|
| `adf6772` | feat(60-06): remove Add-Season UI from playoff bracket + add /admin/playoffs/{id} detail endpoint (UI-07, D-41, D-43) |
| `e2f664b` | refactor(60-06): SeasonController.swissRounds/generate use phaseId-canonical service methods (D-44) |

## What was delivered

### Task 1 — D-43 Add-Season UI removed from playoff-bracket.html
The complete "Linked Seasons (Team Sources)" card block (h2, intro paragraph, chip-list of linked seasons with Remove buttons, and the Add-Season `<select>` form) was deleted from the template. The backend POST endpoints `/admin/playoffs/{id}/add-season` and `/admin/playoffs/{id}/remove-season` on `PlayoffController` are preserved — direct API callers continue to work. The existing test `givenPlayoffAndOtherSeason_whenAddAndRemoveSeasonFromPlayoff_thenBothSucceed` verifies that.

### Task 2 — D-41 New `GET /admin/playoffs/{id}` detail endpoint
Wave 0 RED test `givenPlayoff_whenGetBracket_thenAddSeasonButtonNotPresent` expected this URL to render the bracket page, but no such endpoint existed (only `GET /admin/playoffs?seasonId=...`). Added:
- `PlayoffController.detail(@PathVariable UUID id, Model model)` — loads playoff, populates `seasons / selectedSeasonId / playoff / bracket` model attrs, renders `admin/playoff-bracket`.
- `PlayoffService.getPlayoffDetailData(UUID playoffId)` — small service helper that resolves the season via `playoff.getPhase().getSeason()` (post-Phase-58 D-19 phase FK) with fallback to `playoff.getSeason()` (pre-migration playoffs). Throws `EntityNotFoundException` if the playoff does not exist.

**Scope deviation note:** `PlayoffService.java` is technically out of the plan's `files_modified` list, but a service helper is required because the controller cannot reach `PlayoffRepository` directly (CLAUDE.md: "no direct repository access in controllers"). The helper is a single read-only method that mirrors the existing `getPlayoffListData` pattern.

### Task 3 — D-44 SeasonController phaseId-canonical refactor
Refactored three endpoints to internally resolve the REGULAR phase and call phase-canonical service overloads with `groupId=null`:
- `swissRounds` — uses `swissPairingService.getCurrentRound(phaseId, null)` and `.isCurrentRoundComplete(phaseId, null)`
- `generateSwissRound` — uses `swissPairingService.generateNextRound(phaseId, null)`
- `generate` — uses `matchdayGeneratorService.generate(phaseId, null, numberOfRounds, homeAndAway)`

Saison-zentric URLs unchanged: `/admin/seasons/{id}/swiss`, `/{id}/swiss/generate`, `/{id}/generate`. After this refactor, the deprecated `seasonId` overloads on `SwissPairingService` and `MatchdayGeneratorService` have **no production callers** — Plan 60-07 can remove them safely.

### Task 4 — Visual verification (playwright-cli)
- `60-06-playoff-bracket-no-add-season-desktop.png` — `/admin/playoffs/{id}` renders bracket + Round Configuration without the Linked Seasons card.
- `60-06-playoff-bracket-mobile.png` — 375px viewport renders cleanly.

User explicitly approved the visual checkpoint on 2026-04-30.

## Test results

`./mvnw test -Dtest='PlayoffControllerTest,SeasonControllerTest'` — 37/37 GREEN
- PlayoffControllerTest: 20/20 (existing 19 + Wave 0 `givenPlayoff_whenGetBracket_thenAddSeasonButtonNotPresent`)
- SeasonControllerTest: 17/17 (slim form + swiss/generate phaseId resolution)

## Open follow-ups

- Plan 60-07: conservative removal of `@Deprecated` `seasonId` overloads on SwissPairingService, MatchdayGeneratorService, MatchdayService, StandingsService, DriverRankingService — and run the full verification gate (`./mvnw verify -Pe2e`, JaCoCo, regression coverage).

## Self-Check: PASSED

- [x] Add-Season UI removed from playoff-bracket.html (D-43 verified by Wave-0 RED test + visual)
- [x] Backend POST /add-season + /remove-season endpoints preserved (existing test still GREEN)
- [x] `GET /admin/playoffs/{id}` detail endpoint added (D-41)
- [x] SeasonController.swissRounds/generate/generateSwissRound use phaseId-canonical service methods (D-44 prep complete)
- [x] All 37 tests GREEN, no regressions
- [x] Visual checkpoint approved by user
- [x] Documented scope deviation: PlayoffService.getPlayoffDetailData added (1 read-only helper)
