---
phase: 71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui
plan: "02"
subsystem: sitegen
tags:
  - thymeleaf
  - site-templates
  - sitegen
  - pageTitle
dependency_graph:
  requires:
    - "71-01 (admin-side pageTitle + layout fix, wave-1 parallel)"
  provides:
    - "Site template fragment-call arguments clean of ${...} per D-05 corrected regex"
    - "pageTitle context variable wired in all 6 sitegen page-generator beans"
    - "site/layout.html null-safe title rendering with Elvis fallback 'CTC'"
    - "matchCardBody fragment body inlined into site/matchday.html (D-03b option a)"
  affects:
    - "71-03 (POM bump to 4.0.6 — lands on top of clean template baseline)"
    - "71-05 (build-guard regex will see zero offenders by construction)"
tech_stack:
  added: []
  patterns:
    - "sitegen Context.setVariable(\"pageTitle\", ...) adjacent to breadcrumbCurrent"
    - "th:with Elvis fallback on layout <html> element for null-safe title"
    - "Inline fragment body instead of parameterized fragment call (D-03b option a)"
key_files:
  created: []
  modified:
    - src/main/resources/templates/site/layout.html
    - src/main/resources/templates/site/driver-profile.html
    - src/main/resources/templates/site/team-profile.html
    - src/main/resources/templates/site/matchday.html
    - src/main/resources/templates/site/driver-ranking.html
    - src/main/resources/templates/site/standings.html
    - src/main/resources/templates/site/playoff-bracket.html
    - src/main/resources/templates/site/matchdays.html
    - src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java
    - src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java
    - src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java
    - src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java
    - src/main/java/org/ctc/sitegen/StandingsPageGenerator.java
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
decisions:
  - "D-03b option (a) locked: matchCardBody fragment body inlined into site/matchday.html — no fragment call remains"
  - "D-13: site/layout.html uses th:with Elvis fallback 'CTC' (not 'CTC Admin' — that is admin layout)"
  - "D-14: each sitegen bean adds setVariable(\"pageTitle\") next to breadcrumbCurrent — no shared abstraction"
metrics:
  duration: "~10 minutes"
  completed: "2026-05-11"
  tasks_completed: 3
  files_modified: 14
  tests_run: 1227
  tests_failed: 0
  coverage: "85.58% (>= 82% minimum)"
---

# Phase 71 Plan 02: Site Template pageTitle Fix + matchCardBody Inline Summary

**One-liner:** Site Thymeleaf 3.1.5 fragment-call audit — rewired 7 site templates to `${pageTitle}` from sitegen beans, inlined `matchCardBody` fragment body (D-03b), and added Elvis-safe `'CTC'` fallback to `site/layout.html`.

## What Was Built

This plan mirrors the admin-side fix (Plan 01) for the public-site templates. All 7 site templates had `${...}` expressions in fragment-call argument positions — either plain `${var}` (allowed but fixed preemptively per D-01) or string-concatenation (restricted under Thymeleaf 3.1.5 restricted mode). Every template now passes `${pageTitle}` as the layout fragment title argument, populated by the corresponding sitegen page-generator bean.

The `site/matchday.html` had an additional offender on line 10: `<th:block th:insert="~{site/fragments/match-card :: matchCardBody(${race})}">`. Per D-03b option (a) lock, the `matchCardBody` fragment body was inlined directly into the `th:each` loop — the `race` variable is already bound by the parent loop scope so all `${race.*}` references resolve identically. No fragment call remains; the orphaned `matchCardBody` fragment in `match-card.html` is preserved for v1.11+ cleanup.

## Files Modified

14 files total (matches frontmatter `files_modified` list):

**Templates (8):**
- `site/layout.html` — added `th:with="title=${title ?: 'CTC'}"` (D-13 Elvis fallback)
- `site/driver-profile.html` — `${driver.psnId}` → `${pageTitle}`
- `site/team-profile.html` — `${team.name}` → `${pageTitle}`
- `site/matchday.html` — `${matchday.label}` → `${pageTitle}` + matchCardBody body inlined
- `site/driver-ranking.html` — `'Driver Ranking ' + ${season.displayLabel}` → `${pageTitle}`
- `site/standings.html` — `'Standings ' + ${season.displayLabel}` → `${pageTitle}`
- `site/playoff-bracket.html` — `'Playoffs ' + ${season.displayLabel}` → `${pageTitle}`
- `site/matchdays.html` — `'Matchdays — ' + ${season.displayLabel}` → `${pageTitle}`

**Sitegen generators (6):**
- `DriverProfilePageGenerator` — `context.setVariable("pageTitle", driver.getPsnId())`
- `TeamProfilePageGenerator` — `context.setVariable("pageTitle", team.getName())`
- `MatchdaysPageGenerator.generateDetails` — `context.setVariable("pageTitle", matchday.getLabel())`
- `MatchdaysPageGenerator.writeIndexVariant` — `tplCtx.setVariable("pageTitle", "Matchdays — " + season.getDisplayLabel())`
- `DriverRankingPageGenerator.writeRankingVariant` — `tplCtx.setVariable("pageTitle", "Driver Ranking " + season.getDisplayLabel())`
- `StandingsPageGenerator.writeStandingsFile` — `tplCtx.setVariable("pageTitle", "Standings " + season.getDisplayLabel())`
- `SiteGeneratorService.generatePlayoffBracket` — `ctx.setVariable("pageTitle", "Playoffs " + season.getDisplayLabel())`

Total: 7 `setVariable("pageTitle", ...)` insertions across 6 files (MatchdaysPageGenerator contributes 2).

## Test Baseline

- **1227 unit + integration tests**, 0 failures, 4 skipped
- **JaCoCo line coverage: 85.58%** (minimum: 82%)
- SiteGenerator ITs (`SiteGeneratorPhaseAwarenessIT`, `SiteGeneratorServiceIT`) green — generated pages have correct `<title>CTC - ...</title>` tags
- Build: `./mvnw verify` BUILD SUCCESS on Spring Boot 4.0.5 baseline

## Visual Verification

14 playwright screenshots captured locally in `.screenshots/` (gitignored per project convention):

| Screenshot | Title confirmed | Match cards |
|---|---|---|
| `71-02-site-driver-profile.png` | `CTC - PWR_Driver06` | n/a |
| `71-02-site-team-profile.png` | `CTC - Nitro Fuel Racing` | n/a |
| `71-02-site-matchday.png` | `CTC - Group B — Matchday 1` | 6 match-teams divs rendered correctly |
| `71-02-site-driver-ranking.png` | `CTC - Driver Ranking 2023 \| #1 \| Season 2023` | n/a |
| `71-02-site-standings.png` | `CTC - Standings 2023 \| #1 \| Season 2023` | n/a |
| `71-02-site-playoff-bracket.png` | `CTC - Playoffs 2023 \| #1 \| Season 2023` | n/a |
| `71-02-site-matchdays.png` | `CTC - Matchdays — 2023 \| #1 \| Season 2023` | n/a |

Mobile equivalents (375x667) captured for all 7 pages.

**Key visual confirmation:** `site/matchday.html` renders match cards correctly via the inlined `matchCardBody` body — identical HTML output to the pre-inline fragment call. No `TemplateProcessingException` on any page.

## Carry-Forward Notes

- **Plans 01 + 02 combined:** Zero `${...}` in any fragment-call argument anywhere under `src/main/resources/templates/` per the corrected D-05 regex. Plan 03's POM bump to Spring Boot 4.0.6 / Thymeleaf 3.1.5 lands on this clean baseline.
- **Build-guard regex (Plan 05):** Will stay clean by construction — no exclusions, no narrowing needed.
- **Orphaned `matchCardBody` fragment** in `src/main/resources/templates/site/fragments/match-card.html`: deliberately preserved, zero call sites after this plan. Deferred to v1.11+ cleanup per D-03b lock.
- **`site/matchday.html` inner `<h1 th:text="...">` body expression** (line 7: `${matchday.label + ' — ' + season.displayLabel}`) is NOT a fragment-call arg → not changed; Thymeleaf 3.1.5 restricted mode does not apply there.

## Deviations from Plan

None — plan executed exactly as written. All D-03b, D-13, D-14 decisions honored. No additional abstractions introduced.

## Threat Flags

No new security-relevant surface introduced. All `pageTitle` values come from trusted internal entity fields; Thymeleaf 3.1.5 escapes them in `<title>` by default. (Covered by T-71-02-01 in plan threat model.)

## Commits

| Task | Commit | Description |
|---|---|---|
| Task 1 | de2cb7a | refactor(71-02): rewrite 7 site template fragment-call args to ${pageTitle} + inline matchCardBody |
| Task 2 | 67faece | feat(71-02): wire pageTitle context variable in 6 sitegen page-generator beans |
| Task 3 | 768f629 | feat(71-02): add Elvis-safe title fallback to site/layout.html (D-13) |

## Self-Check: PASSED

All created/modified files verified:
- `site/layout.html`: th:with present, title line unchanged
- All 7 site templates: `layout(${pageTitle}` present
- `site/matchday.html`: 0 matchCardBody refs, match-teams div present
- All 6 generator files: setVariable("pageTitle") present (7 total insertions)
- `./mvnw verify` BUILD SUCCESS, 1227/1227 tests pass, 85.58% coverage
- 14 screenshots captured in `.screenshots/71-02-site-*.png`
