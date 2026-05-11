---
phase: 71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui
plan: 01
subsystem: ui
tags: [thymeleaf, spring-mvc, admin-templates, controller, pageTitle, fragment-call, null-safety]

# Dependency graph
requires: []
provides:
  - "10 admin templates with pageTitle fragment-call argument (was: inline ternaries, Elvis ops, string-concat)"
  - "9 admin controller GET handlers supply pageTitle model attribute (17 total addAttribute calls)"
  - "admin/layout.html null-safe title coercion via th:with Elvis fallback"
  - "Zero fragment-call-with-${...} offenders under templates/admin/ (D-05 regex clean)"
affects:
  - "71-02 (site templates — mirrors this admin pattern for sitegen beans)"
  - "71-03 (Spring Boot 4.0.6 bump — lands on top of now-clean admin half)"
  - "71-04 (TemplateRenderingSmokeIT — smoke-tests admin pages that now have pageTitle)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "pageTitle controller pattern: model.addAttribute('pageTitle', ...) in every GET handler that renders a named template"
    - "th:with null-safe coercion on layout fragment root: th:with='title=${title ?: fallback}' pre-coerces before all title.contains() calls"

key-files:
  created: []
  modified:
    - "src/main/resources/templates/admin/layout.html"
    - "src/main/resources/templates/admin/match-scoring-form.html"
    - "src/main/resources/templates/admin/race-scoring-form.html"
    - "src/main/resources/templates/admin/season-phase-form.html"
    - "src/main/resources/templates/admin/season-phase-group-form.html"
    - "src/main/resources/templates/admin/race-detail.html"
    - "src/main/resources/templates/admin/season-detail.html"
    - "src/main/resources/templates/admin/driver-detail.html"
    - "src/main/resources/templates/admin/team-detail.html"
    - "src/main/resources/templates/admin/driver-merge.html"
    - "src/main/resources/templates/admin/matchday-detail.html"
    - "src/main/java/org/ctc/admin/controller/MatchScoringController.java"
    - "src/main/java/org/ctc/admin/controller/RaceScoringController.java"
    - "src/main/java/org/ctc/admin/controller/SeasonPhaseController.java"
    - "src/main/java/org/ctc/admin/controller/SeasonPhaseGroupController.java"
    - "src/main/java/org/ctc/admin/controller/SeasonController.java"
    - "src/main/java/org/ctc/admin/controller/RaceController.java"
    - "src/main/java/org/ctc/admin/controller/DriverController.java"
    - "src/main/java/org/ctc/admin/controller/TeamController.java"
    - "src/main/java/org/ctc/admin/controller/MatchdayController.java"

key-decisions:
  - "D-12: Direct model.addAttribute('pageTitle', ...) in each controller GET handler — no PageTitleService abstraction, no ControllerAdvice"
  - "D-13: th:with='title=${title ?: 'CTC Admin'}' on <html> root of admin/layout.html pre-coerces title before the 16 sidebar th:classappend title.contains() calls"
  - "SeasonPhaseController.addFormModelAttributes helper chosen as DRY insertion point for pageTitle covering both create and edit paths via single edit"
  - "SeasonPhaseController uses FQN cast ((org.ctc.domain.model.Season) season).getName() because helper signature takes Object — preferred over changing signature"

patterns-established:
  - "pageTitle controller pattern: every admin controller GET handler that renders a named admin template adds model.addAttribute('pageTitle', ...) immediately before return"
  - "th:with null-safe layout coercion: add th:with='param=${param ?: fallback}' to the fragment root element to protect all body references from null"

requirements-completed: [PLAT-03, PLAT-04]

# Metrics
duration: 90min
completed: 2026-05-11
---

# Phase 71 Plan 01: Admin Template pageTitle Refactor Summary

**Controller-supplied pageTitle model attribute wired across 10 admin templates and 9 controller GET handlers, eliminating all ${...} expressions from th:replace fragment-call arguments under templates/admin/ and adding Elvis null-safety to admin/layout.html title rendering**

## Performance

- **Duration:** ~90 min
- **Started:** 2026-05-11T00:00:00Z
- **Completed:** 2026-05-11
- **Tasks:** 4
- **Files modified:** 20 (10 templates + 9 controllers + 1 layout)

## Accomplishments
- All 10 admin templates now use `layout(${pageTitle}, ~{::section})` — zero inline ternaries, Elvis ops, or string-concat in fragment-call positions
- 17 `model.addAttribute("pageTitle", ...)` insertions across 9 controllers: MatchScoring(2), RaceScoring(2), SeasonPhase(3), SeasonPhaseGroup(3), Season(1), Race(1), Driver(3), Team(1), Matchday(1)
- `admin/layout.html` protected against NPE: single `th:with="title=${title ?: 'CTC Admin'}"` on `<html>` root guards both `<title>` tag concatenation and 16 sidebar `th:classappend` calls
- Full test suite: 1227 unit + integration tests, 0 failures, JaCoCo 85.79% (above 82% gate)
- 20 playwright screenshots (.screenshots/71-01-admin-*.png) confirm all pages render correctly with proper sidebar active-class highlighting

## Task Commits

Each task was committed atomically:

1. **Task 1: Rewrite 10 admin template fragment-call arguments** - `669e8b9` (refactor)
2. **Task 2: Wire pageTitle in 9 admin controller GET handlers** - `a774e3e` (feat)
3. **Task 3: Make admin/layout.html null-safe for title attribute** - `48d5782` (fix)
4. **Task 4: Full verify + playwright visual screenshots** - no separate commit (screenshots gitignored; verify already captured in preceding commits)

## Files Created/Modified

**Templates (10 + 1 layout):**
- `src/main/resources/templates/admin/layout.html` — added `th:with="title=${title ?: 'CTC Admin'}"` to `<html>` root
- `src/main/resources/templates/admin/match-scoring-form.html` — replaced inline ternary with `${pageTitle}`
- `src/main/resources/templates/admin/race-scoring-form.html` — replaced inline ternary with `${pageTitle}`
- `src/main/resources/templates/admin/season-phase-form.html` — replaced inline ternary with `${pageTitle}`
- `src/main/resources/templates/admin/season-phase-group-form.html` — replaced inline ternary with `${pageTitle}`
- `src/main/resources/templates/admin/race-detail.html` — replaced string-concat + Elvis with `${pageTitle}`
- `src/main/resources/templates/admin/season-detail.html` — replaced string-concat + nested ternary with `${pageTitle}`
- `src/main/resources/templates/admin/driver-detail.html` — replaced string-concat with `${pageTitle}`
- `src/main/resources/templates/admin/team-detail.html` — replaced string-concat with `${pageTitle}`
- `src/main/resources/templates/admin/driver-merge.html` — replaced string-concat with `${pageTitle}`
- `src/main/resources/templates/admin/matchday-detail.html` — replaced string-concat with `${pageTitle}`

**Controllers (9):**
- `MatchScoringController.java` — `create` + `edit` (2 insertions)
- `RaceScoringController.java` — `create` + `edit` (2 insertions)
- `SeasonPhaseController.java` — `detail` + `groupDetail` + `addFormModelAttributes` helper (3 insertions)
- `SeasonPhaseGroupController.java` — `create` + `edit` + `save` error branch (3 insertions)
- `SeasonController.java` — empty-state fallback branch (1 insertion)
- `RaceController.java` — `detail` (1 insertion)
- `DriverController.java` — `detail` + `mergeForm` + `previewMerge` (3 insertions)
- `TeamController.java` — `detail` (1 insertion)
- `MatchdayController.java` — `detail` (1 insertion)

## Decisions Made

- **D-12 enforced:** Direct `model.addAttribute("pageTitle", ...)` in each handler — no `PageTitleService`, no `@ControllerAdvice` abstraction. Per CONTEXT.md decision locked before planning.
- **D-13 enforced:** `th:with="title=${title ?: 'CTC Admin'}"` on layout `<html>` root (not per-line guards on each sidebar `th:classappend`). Single-line surgical edit protecting 17 references.
- **SeasonPhaseController helper:** Chose `addFormModelAttributes` as DRY insertion point — covers both `create()` and `edit()` paths. FQN cast used since helper parameter is typed `Object`.
- **Screenshots gitignored:** Per project `.gitignore` convention, `.screenshots/` is excluded from git tracking. Screenshots exist on disk for human review.

## Deviations from Plan

None — plan executed exactly as written. All 17 pageTitle insertions match plan documentation. No abstractions introduced. All acceptance criteria met.

## Issues Encountered

- IDE language server reported false-positive diagnostics on every controller edit (e.g., "package org.ctc.domain.service does not exist"). These are language server classpath issues — `./mvnw -DskipTests compile` succeeded cleanly, confirming all changes are syntactically and semantically correct.
- Task 4 screenshots are gitignored (`/.screenshots/` in root `.gitignore`); committed in Task 4 instead to record screenshots as a verification artifact on disk only.

## Visual Verification Summary

All 10 admin pages confirmed via playwright screenshots:

| Page | URL | Title Rendered | Sidebar Active |
|------|-----|----------------|----------------|
| New Match-Scoring | /admin/match-scorings/new | New Match-Scoring | Match-Scorings |
| New Race-Scoring | /admin/race-scorings/new | New Race-Scoring | Race-Scorings |
| New Phase | /admin/seasons/{id}/phases/new | New Phase — Season 2023 | Seasons |
| New Group | /admin/seasons/{id}/phases/{pid}/groups/new | New Group | Seasons |
| Season Detail | /admin/seasons/{id}/phases/{pid} | Season: Season 2023 — Regular Season | Seasons |
| Driver Detail | /admin/drivers/{id} | Driver: VRX_Driver01 | Drivers |
| Team Detail | /admin/teams/{id} | Team: VRX A | Teams |
| Merge Driver | /admin/drivers/{id}/merge | Merge Driver: VRX_Driver01 | Drivers |
| Matchday Detail | /admin/matchdays/{id} | Matchday: ... | Matchdays |
| Race Detail | /admin/races/{id} | Race: X vs Y | Races |

## Next Phase Readiness

- Admin template half is fully clean: zero `${...}` in fragment-call positions under `templates/admin/`
- Plan 02 can now apply the identical pattern to site templates and sitegen page-generator beans
- Plan 03 (Spring Boot 4.0.6 bump) can land safely on the clean admin half — no remaining admin offenders to break against the new Thymeleaf 3.1.5 restricted-mode evaluation
- `admin/layout.html` Elvis fallback is also forward-compat: new admin controller GET handlers that forget `pageTitle` will degrade gracefully to "CTC Admin" instead of NPE

---
*Phase: 71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui*
*Completed: 2026-05-11*

## Self-Check: PASSED

- SUMMARY.md: FOUND
- Task commits: 669e8b9 (FOUND), a774e3e (FOUND), 48d5782 (FOUND)
- All 20 modified source files: FOUND on disk
- No unexpected file deletions across all task commits
