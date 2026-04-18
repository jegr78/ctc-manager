---
phase: 39-entity-cross-linking
fixed_at: 2026-04-16T13:21:25Z
review_path: .planning/phases/39-entity-cross-linking/39-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 39: Code Review Fix Report

**Fixed at:** 2026-04-16T13:21:25Z
**Source review:** .planning/phases/39-entity-cross-linking/39-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4
- Fixed: 4
- Skipped: 0

## Fixed Issues

### WR-01: Point aggregation uses parent-team short name but results may carry sub-team short name

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`, `src/main/java/org/ctc/sitegen/model/RaceView.java`
**Commit:** dc3cd0a
**Applied fix:** Added `scoringTeamShortName` field to `RaceView.ResultView` record. In `toRaceView`, `lineupOpt` is resolved once per driver and used to populate both `teamShortName` (sub-team short name, for display) and `scoringTeamShortName` (parent-resolved via `getParentOrSelf().getShortName()`, for scoring). The `homeTotal`/`awayTotal` aggregation filters now use `scoringTeamShortName` instead of `teamShortName`, so sub-team drivers are correctly attributed to their parent team's score total.
**Status:** fixed: requires human verification (logic fix — correct parent-resolution behavior should be confirmed with a season that has active sub-teams)

---

### WR-02: Team profile Drivers section uses SeasonDriver instead of RaceLineup

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`, `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java`
**Commit:** ebe60e8
**Applied fix:** Added `findByRaceMatchdaySeasonId(UUID seasonId)` to `RaceLineupRepository` (with `@EntityGraph` on driver and team). In `generateTeamProfiles`, all lineup entries and season drivers for the season are now pre-fetched once before the team loop. For each team, drivers are gathered from lineup entries where the lineup team matches the team directly or is a sub-team of it (`getParentTeam().getId().equals(team.getId())`). Falls back to `SeasonDriver` only when no lineup entries exist for the season — satisfying the CLAUDE.md "RaceLineup is Source of Truth" rule.

---

### WR-03: Inline `style=` attributes in `archive.html` violate the no-inline-styles convention

**Files modified:** `src/main/resources/templates/site/archive.html`, `src/main/resources/static/site/css/style.css`
**Commit:** 4b754d8
**Applied fix:** Replaced `style="color:#4fc3f7;"` on the Active span with `class="text-accent"`. Replaced `style="color:var(--accent); text-decoration:none;"` on the Standings anchor with `class="entity-link"` (which already provides those exact styles). Added `.text-accent { color: var(--accent); }` utility class to `style.css`.

---

### WR-04: `slugify` is duplicated verbatim between service and test

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`, `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java`
**Commit:** 17d8b5b
**Applied fix:** Changed `slugify` access modifier from `private` to package-private in `SiteGeneratorService`. Replaced the verbatim copy in `SiteGeneratorServiceTest` with a delegation wrapper `return siteGeneratorService.slugify(input)`, so tests always exercise the production implementation.

---

_Fixed: 2026-04-16T13:21:25Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
