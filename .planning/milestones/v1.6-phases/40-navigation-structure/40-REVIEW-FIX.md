---
phase: 40-navigation-structure
fixed_at: 2026-04-16T14:38:15Z
review_path: .planning/phases/40-navigation-structure/40-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 40: Code Review Fix Report

**Fixed at:** 2026-04-16T14:38:15Z
**Source review:** .planning/phases/40-navigation-structure/40-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (WR-01, WR-02, WR-03; IN-* excluded per fix_scope=critical_warning)
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: Breadcrumb renders broken link when `seasonSlug` is null

**Files modified:** `src/main/resources/templates/site/layout.html`
**Commit:** ded1b43
**Applied fix:** Added `th:if="${seasonSlug != null and !#strings.isEmpty(seasonSlug)}"` to the Season breadcrumb `<a>` element. The link now only renders when `seasonSlug` is non-null and non-empty, eliminating the latent risk of Thymeleaf rendering the literal string `"null"` into the href.

---

### WR-02: Per-result database query inside `toRaceView` (query-per-row pattern)

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
**Commit:** dff40b0
**Applied fix:** Added `import org.ctc.domain.model.RaceLineup`. Changed `toRaceView` signature from `(Race, Season, String)` to `(Race, Season, String, List<RaceLineup> seasonLineups)`. Replaced the in-lambda `raceLineupRepository.findByRaceIdAndDriverId(...)` call with an in-memory stream filter over `seasonLineups`. Updated both callers — `generateIndex` and `generateMatchdays` — to pre-fetch all lineups for the season via `raceLineupRepository.findByRaceMatchdaySeasonId(season.getId())` and pass the result into `toRaceView`. This matches the pattern already used in `generateTeamProfiles` and eliminates N per-row repository queries.

---

### WR-03: Subnav link count assertion is fragile — will break for seasons with playoff data

**Files modified:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java`
**Commit:** f44089b
**Applied fix:** Replaced `assertEquals(4, doc.select(".subnav-link").size(), ...)` with three targeted `assertEquals(1, ...)` assertions checking for the presence of specific link hrefs (`standings.html`, `matchdays.html`, `driver-ranking.html`) plus a general non-empty assertion. The test now validates observable behavior (the links that matter exist) rather than an implementation detail (total link count), and is resilient to the Playoff link being conditionally added or removed in future layout changes.

---

_Fixed: 2026-04-16T14:38:15Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
