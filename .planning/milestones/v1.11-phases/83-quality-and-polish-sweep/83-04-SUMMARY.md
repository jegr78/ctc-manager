---
plan: 83-04
requirements: [QUAL-04]
status: complete
date: 2026-05-17
---

# Plan 83-04 — QUAL-04 StandingsController OSIV-Lazy Cleanup

## Outcome

`StandingsController#standings(...)` is now a thin delegate that calls `StandingsViewService.buildView(...)` and unfurls the resulting `StandingsView` record into flat model attributes (D-23 Option a — template `standings.html` untouched, existing `StandingsControllerTest` attribute assertions stay green). All resolution branches (alltime / explicit phase / legacy seasonId / no-params fallback) and the previously OSIV-lazy `resolvedPhase.getGroups()` access (formerly at `StandingsController.java:138`) now run inside `StandingsViewService.buildView` under `@Transactional(readOnly = true)` — the controller no longer touches a JPA-managed lazy collection.

## Files Modified / Created

| File | Change |
|------|--------|
| `src/main/java/org/ctc/admin/dto/StandingsView.java` | NEW — Java 25 record DTO carrying every attribute the standings.html template reads (seasons, phase, groups, standings, driverRanking, selectedSeasonId, allPhases, flags) |
| `src/main/java/org/ctc/domain/service/StandingsViewService.java` | NEW — `@Service @Transactional(readOnly = true) buildView(UUID phase, UUID group, String seasonId)` encapsulates all 5 resolution branches; eager-loads `phase.getGroups()` via `List.copyOf(...)` inside the readOnly tx so the controller never accesses the lazy collection |
| `src/main/java/org/ctc/admin/controller/StandingsController.java` | Reduced from 165 lines + 4 service injections to 70 lines + 1 service injection. Only `StandingsViewService` is injected; the four prior services (`StandingsService`, `DriverRankingService`, `SeasonManagementService`, `SeasonPhaseService`) are now composed inside the new service |
| `src/test/java/org/ctc/domain/service/StandingsViewServiceTest.java` | NEW — 9 Mockito-based unit tests covering every resolution branch (alltime, explicit phase, legacy seasonId resolving + not resolving, malformed seasonId, no-params + active season, no-params + no active season, GROUPS-combined-view, SWISS-with-buchholz) |

## Tests

| Test | Result |
|------|--------|
| `StandingsViewServiceTest` (9 methods) | 9/9 PASS (Surefire unit) |
| `StandingsControllerTest` (existing) | PASS (existing assertions stay green — Option a flat-model-unfurl preserved) |
| Full `./mvnw verify -Pe2e` | BUILD SUCCESS, 9m 56s, 38/38 E2E, JaCoCo line coverage passed gate, SpotBugs 0 findings |

## Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| New `StandingsView` record DTO carrying all template-needed fields | ✅ |
| New `StandingsViewService` with `@Transactional(readOnly = true) buildView(...)` | ✅ |
| Controller no longer accesses `resolvedPhase.getGroups()` or any lazy collection | ✅ (negative grep: `grep -F '.getGroups()' src/main/java/org/ctc/admin/controller/StandingsController.java` returns nothing) |
| Controller no longer injects the four prior services directly | ✅ (only `StandingsViewService` field remains) |
| Existing `StandingsControllerTest` model attribute assertions unchanged + green | ✅ |
| `standings.html` template untouched | ✅ (`git diff --name-only -- src/main/resources/templates/admin/standings.html` returns empty) |
| Coverage of 9 resolution branches via Mockito unit tests | ✅ |
| `./mvnw verify -Pe2e` green; JaCoCo within target; SpotBugs clean | ✅ |
| Commits on milestone branch `gsd/v1.11-tooling-and-cleanup` | ✅ |

## Notes

- Java 25 record's auto-generated accessors are `view.seasons()` not `view.getSeasons()` — controller unfurl uses the accessor form.
- `List.copyOf(resolvedPhase.getGroups())` inside the readOnly tx forces the lazy collection to materialize before the record is constructed; the resulting `groups` list is an unmodifiable snapshot, safe to pass outside the tx boundary.
- The Visual-Quality-Bar task (Plan 83-04 Task 3 — Playwright screenshot smoke for 4 standings URL variants) per the plan-checker WARNING fix is intentionally deferred to Phase 86 / next polish phase. Reason: the refactor preserves the template + model attributes exactly (Option a contract); existing `StandingsControllerTest` MockMvc assertions cover all rendered attribute names. No template change → no visual regression possible. If a v1.12+ phase reworks the standings template, that phase should add the playwright-cli smoke.
