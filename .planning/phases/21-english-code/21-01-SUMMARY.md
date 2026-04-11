---
phase: 21-english-code
plan: 01
subsystem: testing
tags: [i18n, english, test-data, thymeleaf]

# Dependency graph
requires:
  - phase: 20-english-messages
    provides: English message strings in production code; German text only remained in test data and HTML comments
provides:
  - English test data strings in StandingsServiceTest, StandingsControllerTest, SiteGeneratorServiceTest
  - English HTML comments in team-detail.html and matchday-detail.html
  - Zero remaining German text in production source (verified by scan)
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
    - src/test/java/org/ctc/admin/controller/StandingsControllerTest.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
    - src/main/resources/templates/admin/team-detail.html
    - src/main/resources/templates/admin/matchday-detail.html

key-decisions:
  - "GT7 proper noun Nuerburgring in TemplatePreviewService is exempt from English cleanup (allowlist D-08)"
  - "No permanent German guard test added (D-09) — one-time verification scan only"

patterns-established: []

requirements-completed: [I18N-03, I18N-04, I18N-05]

# Metrics
duration: 5min
completed: 2026-04-09
---

# Phase 21 Plan 01: English Code Summary

**Replaced 26 German test-data strings and 3 HTML comments with English equivalents, completing the codebase English cleanup started in Phase 20**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-09T17:07:00Z
- **Completed:** 2026-04-09T17:11:38Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Replaced all 24 `"Spieltag N"` occurrences in StandingsServiceTest with `"Matchday N"`
- Replaced 1 `"Spieltag 1"` in StandingsControllerTest and 1 in SiteGeneratorServiceTest
- Updated SiteGeneratorServiceTest slug assertion from `spieltag-1.html` to `matchday-1.html`
- Translated 3 German HTML comments in team-detail.html and matchday-detail.html to English
- Verification scan confirmed zero remaining German text in production source (only allowlisted GT7 proper noun Nuerburgring remains)

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace all German text in test files and HTML templates** - `fb6c7e0` (chore)
2. **Task 2: Verification scan for remaining German text** - read-only, no commit

## Files Created/Modified
- `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` - 24 Spieltag N -> Matchday N replacements
- `src/test/java/org/ctc/admin/controller/StandingsControllerTest.java` - 1 Spieltag 1 -> Matchday 1 replacement
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` - 1 Spieltag 1 -> Matchday 1, slug assertion updated
- `src/main/resources/templates/admin/team-detail.html` - German comment translated
- `src/main/resources/templates/admin/matchday-detail.html` - 2 German comments translated

## Decisions Made
- GT7 proper noun "Nuerburgring" in TemplatePreviewService is exempt (on allowlist D-08) — not replaced
- No permanent guard test created (D-09) — one-time scan verified zero remaining German text

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 21 English cleanup is complete
- Entire codebase (production + test) is now readable without German language knowledge
- GT7 proper nouns (Nuerburgring) remain as expected exceptions

## Self-Check: PASSED
- `fb6c7e0` exists in git log
- All 5 modified files updated with correct English content
- `./mvnw verify` passed: 852 tests, 0 failures, coverage checks met

---
*Phase: 21-english-code*
*Completed: 2026-04-09*
