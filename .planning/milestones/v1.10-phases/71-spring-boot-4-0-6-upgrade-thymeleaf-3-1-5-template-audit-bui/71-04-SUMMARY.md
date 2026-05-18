---
phase: 71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui
plan: 04
status: complete
started: 2026-05-11
completed: 2026-05-11
requirements: [PLAT-06]
---

# Plan 71-04 Summary — TemplateRenderingSmokeIT

## What Shipped

PLAT-06 regression net for the Wave-1 fragment-call refactor: a dynamic-test factory IT that introspects `RequestMappingHandlerMapping` for every `/admin/**` GET handler and asserts each renders without a `TemplateProcessingException`.

Beyond the test class itself, this plan **discovered and closed a long-standing harness gap**: Maven Failsafe was bound only to the `e2e` profile, so the existing `*IT.java` integration tests in the codebase (5 of them) had been silently inactive. The plan landed a default-profile Failsafe execution that activates them on every `./mvnw verify` — a substantial uplift in baseline coverage with no new code under test.

## Verification Results

| Check | Result |
|-------|--------|
| `./mvnw verify` | **BUILD SUCCESS** in 8:08 min |
| Surefire (unit + integration) | 1227 run, 0 failures, 0 errors, 4 skipped |
| Failsafe (`*IT.java`, non-e2e) | **112 run, 0 failures, 0 errors, 1 skipped** |
| ↳ TemplateRenderingSmokeIT | 64 dynamic GET routes — all `< 500`, none match `\bTemplateProcessingException\b` |
| JaCoCo LINE coverage | **89.44 %** (Plan-03 baseline 87.51 %, gate 82 %) |
| JaCoCo INSTRUCTION coverage | 87.15 % (was 84.86 %) |

## Files Modified / Created

| File | Change |
|------|--------|
| `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` | New — 64-route dynamic-test factory + `@RequestParam` UUID auto-supply |
| `src/test/resources/sql/template-rendering-smoke-fixture.sql` | New — 15-table deterministic-UUID seed for smoke IT |
| `pom.xml` | Default-profile maven-failsafe-plugin binding; Surefire excludes `**/*IT.java`; e2e profile uses execution id `e2e-it` to keep filters independent |

## Plan Deviations (Intentional)

### 1. Failsafe binding (pom.xml edit)

The plan stated `files_modified: [src/test/...]` only. But the IT could never actually run on `./mvnw verify` because Failsafe was only wired in the `e2e` profile (whose include pattern `**/e2e/**/*Test.java` excluded our IT location). Two paths existed: rename to `*Test.java` so Surefire picks it up (violates filename must-have), or wire Failsafe in the default lifecycle (small pom edit). User chose the latter — clean Maven convention, additive scope.

### 2. Dynamic `@RequestParam` resolution

Plan D-10 covered path-variable substitution only. Four admin GET routes use `@RequestParam UUID …` instead of `@PathVariable`, and without query params those routes returned 500 (`MissingServletRequestParameterException`) — false-positive for the smoke contract. Solution: extend the IT to walk handler-method parameters, detect `@RequestParam` UUIDs, and supply values from the same `PATH_VARS` map (with new `homeTeamId`/`awayTeamId`/`seasonTeamId` aliases). Stays within the spirit of D-08 "no hardcoded route list".

### 3. `@Qualifier("requestMappingHandlerMapping")`

Spring Boot Actuator exposes a second `RequestMappingHandlerMapping` bean (`controllerEndpointHandlerMapping`), so the plain `@Autowired` fails with `expected single matching bean but found 2`. Standard Spring solution — qualify by the canonical bean name.

## Recovery Note

First agent attempt (`a6263a064f9b3d087`) stalled on the stream watchdog mid-Edit (had committed the SQL fixture, left the test class uncommitted). Orchestrator recovery:

1. Pulled the SQL-fixture commit from the worktree via `git merge --no-ff`.
2. Copied the uncommitted test file to main, removed an unused `Collectors` import (caught by the IDE during the agent's last words).
3. Discovered the Failsafe-binding gap when targeted IT runs returned `Tests run: 0`.
4. Asked the user how to wire the IT into the lifecycle (3 options: surefire include, rename, failsafe binding) — user chose "Failsafe binding for non-e2e".
5. Refactored pom.xml to use execution-level config on both profiles for clean independence.
6. Fixed bean-ambiguity (`@Qualifier`) and added `@RequestParam` UUID auto-resolution.
7. Targeted re-run: 64 routes, 0 failures.
8. Full `./mvnw verify`: 1227 surefire + 112 failsafe, all green, coverage ↑ 1.93 pp.

## Bonus: Orphaned ITs Activated

The Failsafe default-binding activates 5 previously-inactive integration tests:

| IT | Test count |
|----|------------|
| `AdminDropdownRenderingIT` | 5 |
| `SeasonPhaseControllerIT` | 3 |
| `SeasonPhaseGroupControllerIT` | 3 |
| `DriverSheetImportServiceTransactionIT` | 2 |
| `DriverSheetImportServiceIT` | (in 112 total) |
| `SiteGeneratorPhaseAwarenessIT` | 9 |
| `SiteGeneratorServiceIT` | 1 |
| `PhaseTeamRepositoryIT` | 4 |
| `SeasonPhaseGroupRepositoryIT` | 2 |
| `SeasonPhaseRepositoryIT` | 3 |
| `V4MigrateSeasonsToPhasesIT` | 6 |
| `V4MigrationSmokeIT` | 2 |

12 IT classes wired in, all green, contributing to the LINE-coverage jump from 87.51 % → 89.44 %.

## Self-Check: PASSED

- ✅ `TemplateRenderingSmokeIT.java` created at the documented path.
- ✅ Dynamic route discovery via `RequestMappingHandlerMapping` (D-08).
- ✅ Minimal-complete SQL fixture in the `0071` UUID range with `T-SMOKE-` / `Test-Smoke` prefix (D-09).
- ✅ Path-variable substitution from in-IT map (D-10) + dynamic `@RequestParam` UUID supply.
- ✅ Status `< 500` + word-boundary `\bTemplateProcessingException\b` assertion (D-11a).
- ✅ Runs on every `./mvnw verify` (was the underlying must-have).
- ✅ JaCoCo line coverage ≥ 82 % held (89.44 %).
