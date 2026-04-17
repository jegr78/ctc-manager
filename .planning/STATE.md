---
gsd_state_version: 1.0
milestone: v1.6
milestone_name: Static Site Quality
status: executing
last_updated: "2026-04-17T17:12:54.047Z"
last_activity: 2026-04-17
progress:
  total_phases: 14
  completed_phases: 12
  total_plans: 23
  completed_plans: 23
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-16)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.
**Current focus:** Phase 50 — Site Generator Test Robustness

## Current Position

Phase: 50
Plan: Not started
Status: Executing Phase 50
Last activity: 2026-04-17

## Progress Bar

```
v1.6: [ ] Phase 37  [ ] Phase 38  [ ] Phase 39  [ ] Phase 40  [ ] Phase 41
        0/5 complete
```

## Completed Milestones

- v1.0 Technical Debt Cleanup (5 phases, 12 plans) — shipped 2026-04-04
- v1.1 Codebase Concerns Cleanup (10 phases, 20 plans) — shipped 2026-04-07
- v1.3 English Test Data (8 phases) — shipped 2026-04-10
- v1.5 Code Review Fixes (9 phases, 14 plans) — shipped 2026-04-15

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md Key Decisions table.

- [Phase 44]: cleanOutputDirectory uses Files.walkFileTree with SimpleFileVisitor for bottom-up deletion; root dir preserved via !dir.equals guard; IOException propagates to generate() outer catch
- [Phase 48]: Adapted hero test to assert .hero h1 contains COMMUNITY TEAM CUP instead of .hero-label year (D-09)
- [Phase 48]: Regex pattern uses {11,} for videoId matching (safe chars only); dev profile fallback videoId set for test iframe visibility
- [Phase 49]: Used @TempDir as @BeforeAll parameter injection (not instance field) for reliable PER_CLASS lifecycle
- [Phase 49]: Cross-test isolation via Test_ prefix rename on pre-existing seasons to exclude from productionSeasons filter

### Phase Structure (v1.6)

| Phase | Name | Requirements | Focus |
|-------|------|--------------|-------|
| 37 | Critical Link Fixes | LINK-01..04 | Broken archive slugs, driver ranking 404, absolute paths, team logos |
| 38 | Season Content & Data Filtering | CONT-01, CONT-06, CONT-07 | Season year/number display, filter test seasons, hide empty match-meta |
| 39 | Entity Cross-Linking | CONT-02..04, CONT-08 | Links: standings→teams, ranking→drivers, matchday→drivers, team→drivers |
| 40 | Navigation & Structure | CONT-05, UX-02, UX-03 | Season subnav, active nav state, breadcrumbs |
| 41 | UX Polish & Accessibility | UX-01, UX-04..09, QUAL-01 | Skip link, winner highlight, scroll indicator, footer, aria, transitions |

### Key Technical Context

- All changes target: `SiteGeneratorService.java`, `templates/site/`, `static/site/css/style.css`
- No database changes needed for this milestone
- Season model: `getDisplayLabel()` returns "year | #number | name"
- Site pages: index, archive, standings, matchday, team-profile, driver-profile, driver-ranking, playoff-bracket
- Shared nav/footer lives in `layout.html`

### Blockers/Concerns

None.
