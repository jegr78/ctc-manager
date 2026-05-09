---
plan_id: 61-gap-05
phase: 61-cleanup-quality-gate
status: complete
completed: 2026-05-01T23:40:00Z
gap_closure: true
---

# 61-gap-05 — Javadoc audit: admin.controller + admin.service

## What changed

Targeted Javadoc additions on admin endpoints with non-obvious URL/redirect contracts and
on the matching service-layer entry points. Followed CLAUDE.md "Keep Controllers Thin" —
the audit confirmed most controller methods are self-evident from the @GetMapping/@PostMapping
annotation plus method name; only three methods needed explicit documentation.

## Scope correction

The plan's `<files_modified>` section listed admin services that do not exist in this
codebase (`SeasonFormService`, `PlayoffFormService`, etc.). The actual `admin.service`
package contains only graphics-rendering services
(`*GraphicService`, `TeamCardService`, `TemplatePreviewService`); these are
self-evident from class+method names and do not warrant new Javadoc.

The Phase 60 form-orchestration that the plan attributed to a "SeasonFormService" lives
on `domain.service.SeasonManagementService.save` instead. That method received the
auto-bootstrap Javadoc the plan called for.

## Javadoc added

- `StandingsController.standings` — documents the 4-tier query-param resolution priority
  (`alltime` → `phase` → legacy `seasonId` → active-season fallback) and clarifies that
  `phase` takes precedence over `seasonId`.
- `SeasonPhaseController.save` — documents PLAYOFF auto-routing through
  `PlayoffService.createPlayoff` and notes that `phaseType` is immutable post-create.
- `SeasonManagementService.save` — documents the atomic REGULAR-phase bootstrap on
  create and the explicit non-update of phase-owned fields.

## Commits

- `1549702 docs(61-gap-05): document non-obvious admin endpoints + SeasonManagementService.save`

## Files touched

3 files:
- `src/main/java/org/ctc/admin/controller/StandingsController.java`
- `src/main/java/org/ctc/admin/controller/SeasonPhaseController.java`
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java`

## Diff size

3 files, 33 insertions, 5 deletions.

## Test gate

`./mvnw test -Dtest='StandingsControllerTest,SeasonPhaseControllerTest,SeasonManagementServiceTest'`

→ `Tests run: 63, Failures: 0, Errors: 0, Skipped: 1` — BUILD SUCCESS

## Acceptance criteria

- [x] `StandingsController` has Javadoc describing the legacy URL auto-resolve contract
- [x] No boilerplate Javadoc added on obvious methods
- [x] Test count UNCHANGED (Javadoc-only changes)
- [x] Targeted Surefire suite GREEN

## Self-Check: PASSED

The three non-obvious admin entry points are now documented. The plan's reference to
non-existent admin services has been resolved by mapping the requirement onto the actual
service-layer method (`SeasonManagementService.save`).
