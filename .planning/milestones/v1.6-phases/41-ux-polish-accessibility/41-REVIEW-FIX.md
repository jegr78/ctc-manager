---
phase: 41-ux-polish-accessibility
fixed_at: 2026-04-16T17:01:52Z
review_path: .planning/phases/41-ux-polish-accessibility/41-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 41: Code Review Fix Report

**Fixed at:** 2026-04-16T17:01:52Z
**Source review:** .planning/phases/41-ux-polish-accessibility/41-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: Dangling breadcrumb separator when seasonSlug is absent

**Files modified:** `src/main/resources/templates/site/layout.html`
**Commit:** a4aabae
**Applied fix:** Wrapped the first breadcrumb separator and the season `<a>` link together in a `<th:block th:if="${seasonSlug != null and !#strings.isEmpty(seasonSlug)}">` block. This ensures the separator between "Home" and the season link is only rendered when the season link itself is rendered, preventing a dangling ` > > ` when a page has `breadcrumbCurrent` but no `seasonSlug`.

### WR-02: Driver profile "Opponent" column can show both teams at once

**Files modified:** `src/main/resources/templates/site/driver-profile.html`
**Commit:** 03760f2
**Applied fix:** Introduced a `<th:block th:with="driverTeamId=${team != null ? team.id : null}">` local variable to capture the driver's team ID once. Both `homeTeam` and `awayTeam` spans now check `!= driverTeamId` with an explicit null guard on the team entity itself (`result.race.homeTeam != null`). This prevents both spans from rendering simultaneously when `team` is null, and eliminates the unguarded `homeTeam` null-dereference for bye races.

### WR-03: `averagePoints` division without empty-results guard in `generateDriverProfiles`

**Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
**Commit:** 3e36083
**Applied fix:** Extracted `int total = results.stream().mapToInt(r -> r.getPointsTotal()).sum()` as a local variable before setting context variables. `totalPoints` and `averagePoints` now both reference `total`, reducing the list traversal from three passes to two and making the empty-results guard (`results.isEmpty() ? 0.0 : (double) total / results.size()`) unambiguous.

---

_Fixed: 2026-04-16T17:01:52Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
