---
phase: 38-season-content-data-filtering
fixed_at: 2026-04-16T11:15:00Z
review_path: .planning/phases/38-season-content-data-filtering/38-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 38: Code Review Fix Report

**Fixed at:** 2026-04-16T11:15:00Z
**Source review:** .planning/phases/38-season-content-data-filtering/38-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5
- Fixed: 5
- Skipped: 0

## Fixed Issues

### WR-01: German locale causes comma decimal separator in driver ranking

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
**Commit:** b2c7f3c
**Applied fix:** Changed all 8 `new Context(Locale.GERMAN)` constructors to `new Context(Locale.ENGLISH)` across all generate methods (generateIndex, generateStandings, generateDriverRanking, generateMatchdays, generateTeamProfiles, generateDriverProfiles, generatePlayoffBracket, generateArchive). This ensures decimal numbers use dot separator (1.5) instead of comma (1,5), consistent with CLAUDE.md English UI mandate.

### WR-02: NPE in driver-profile template when race has no home team

**Files modified:** `src/main/resources/templates/site/driver-profile.html`
**Commit:** dea4176
**Applied fix:** Added null-safe navigation operator (`?.`) for `result.race.homeTeam.id` and `result.race.homeTeam.shortName` in the opponent column. Also changed `&&` to `and` (Thymeleaf's preferred boolean operator) in the awayTeam condition. This prevents NullPointerException when rendering driver profiles for bye races where homeTeam may be null.

### WR-03: Unfiltered allSeasons passed to index template context

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
**Commit:** c9c184b
**Applied fix:** Changed the `generateIndex` call from `allSeasons` to `productionSeasons` as argument. The index template now receives only production seasons (excluding Test seasons), consistent with all other page generators and the CONT-06 filtering requirement.

### WR-04: Away score captures unknown team results

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
**Commit:** c51c3bd
**Applied fix:** Changed the `awayTotal` stream filter from negation `!r.teamShortName().equals(homeShortName)` to positive match `r.teamShortName().equals(awayShortName)`. The `awayShortName` variable was already resolved on the preceding line. This prevents drivers with unresolved team ("?") from having their points incorrectly counted toward the away total.

### WR-05: Test slugify diverges from production implementation

**Files modified:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java`
**Commit:** d3af112
**Applied fix:** Added German umlaut substitutions (ae/oe/ue/ss) to the test's `slugify` helper, matching the full production implementation in SiteGeneratorService. The test helper now produces identical slugs for season names containing umlauts (e.g., "Fruehjahr" instead of dropping the characters).

---

_Fixed: 2026-04-16T11:15:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
