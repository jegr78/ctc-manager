# Phase 62: Public Site Phase + Group Awareness - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 62-public-site-phases-groups
**Areas discussed:** URL/path scheme, Phase-tab UX in static HTML, Phase awareness scope, Legacy URL backward-compat, SiteGenerator refactoring shape, SC5 test fixture, Empty-state per phase, Visual + accessibility verification scope

---

## Area selection

| Option | Description | Selected |
|--------|-------------|----------|
| URL/path scheme | How to structure URLs/files for per-phase + per-group pages | ✓ |
| Phase-tab UX in static HTML | File-per-tab vs single page with anchor sections vs JS-toggle | ✓ |
| Phase awareness scope | Which other public pages need phase awareness beyond standings.html | ✓ |
| Legacy URL backward-compat | What `/season/{slug}/standings.html` renders post-Phase 62 | ✓ |

**User's choice:** All four selected.
**Notes:** All four areas are interconnected — URL scheme cascades into tab UX and back-compat decisions.

---

## URL/path scheme — Q1: per-phase + per-group page layout

| Option | Description | Selected |
|--------|-------------|----------|
| Phase-suffixed file | Flat: standings-{phaseSlug}[-group-{groupSlug}].html | ✓ |
| Phase-folder hierarchy | Nested: phase/{phaseSlug}/group/{groupSlug}/standings.html | |
| Single page + anchor tabs | One file with #regular / #playoff / etc. anchors | |

**User's choice:** Phase-suffixed file (Recommended).
**Notes:** Flat directory chosen — easier cross-link relativization than nested admin-style hierarchy; matches existing site's flat slugify convention.

---

## URL/path scheme — Q2: phaseSlug pattern

| Option | Description | Selected |
|--------|-------------|----------|
| PhaseType lowercased | regular / playoff / placement | ✓ |
| Slugified phase.label (with PhaseType fallback) | reg-season-spring-2025 / fallback to phaseType | |
| PhaseType + sortIndex | regular-0 / playoff-10 | |

**User's choice:** PhaseType lowercased (Recommended).
**Notes:** Stable across phase-label edits; UNIQUE phaseType per season (Phase 56 D-03) prevents collisions.

---

## URL/path scheme — Q3: groupSlug pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Slugified group.name | group-a / group-b | ✓ |
| group.sortIndex | group-1 / group-2 | |

**User's choice:** Slugified group.name (Recommended).
**Notes:** Matches existing site slugify convention; groups are user-renameable but typically stable.

---

## Legacy URL backward-compat — Q1: /season/{slug}/standings.html on multi-phase / GROUPS

| Option | Description | Selected |
|--------|-------------|----------|
| Render REGULAR-combined with phase tabs on top | Tab row visible only when ≥2 phases or GROUPS-layout | ✓ |
| Redirect via meta-refresh to per-phase URL | Stub HTML refreshes to standings-regular.html | |
| Keep legacy as REGULAR combined (no tabs) + tabs only on per-phase pages | Strict no-regression but multi-phase users land discoverability-blind | |

**User's choice:** Render REGULAR-combined with phase tabs on top (Recommended).
**Notes:** Discoverability for multi-phase users without breaking the SC4 invariant for single-REGULAR-LEAGUE seasons.

---

## Phase-tab UX — visual style

| Option | Description | Selected |
|--------|-------------|----------|
| Visual parity with admin's pill/underline tabs | Same tab UX shape, dark-theme adapted | |
| Public-site-native (subnav-style) | Reuse .subnav pattern from layout.html | ✓ |
| You decide | Planner picks based on existing patterns | |

**User's choice:** Public-site-native (subnav-style).
**Notes:** Tighter integration with current site dark theme; one less new CSS dialect; reuses existing .subnav + .entity-link conventions.

---

## Phase-tab UX — visibility logic

| Option | Description | Selected |
|--------|-------------|----------|
| Hide when redundant | No tabs for single-REGULAR-LEAGUE; group sub-tabs only on GROUPS | ✓ |
| Always render for consistency | 1 inert tab even on single-phase | |
| Tab row only on multi-phase OR GROUPS layout | Hides phase row on single-REGULAR-GROUPS | |

**User's choice:** Hide when redundant (Recommended).
**Notes:** Cleanest SC4 satisfaction; single-REGULAR-LEAGUE pages render byte-identical to today.

---

## PLAYOFF tab content

| Option | Description | Selected |
|--------|-------------|----------|
| PLAYOFF tab links to existing playoff.html | Tab href points at existing bracket page | ✓ |
| Renders standings-playoff.html with bracket inline | Bracket embedded in standings page | |
| standings-playoff.html for table + separate playoff.html for bracket | Two distinct artefacts, "View Bracket" button | |

**User's choice:** PLAYOFF tab links to existing playoff.html (Recommended).
**Notes:** Bracket page already exists; minimal new code; mirrors Phase 60 D-41 admin URL stability.

---

## Phase awareness scope — which other pages?

| Option | Description | Selected |
|--------|-------------|----------|
| matchdays.html (per-phase grouping) | Group matchdays by phase | ✓ |
| driver-ranking.html (per-phase tabs) | Aggregated default + per-phase tabs | ✓ |
| team-profile.html (per-phase points breakdown) | Phase breakdown section | ✓ |
| driver-profile.html (per-phase results section) | Sectioned results | ✓ |

**User's choice:** All four (multi-select).
**Notes:** Full phase awareness across the public site — not just standings.

---

## Phase awareness scope — matchdays + driver-ranking depth

| Option | Description | Selected |
|--------|-------------|----------|
| Tabs + suffixed files | matchdays-{phaseSlug}.html / driver-ranking-{phaseSlug}.html | ✓ |
| Section headings on existing files | One file with H2 sections per phase | |
| Hybrid: tabs for standings + driver-ranking, sections for matchdays | Different UX intent per page | |

**User's choice:** Tabs + suffixed files (Recommended).
**Notes:** One mental model across all list pages; consistent URL scheme.

---

## Phase awareness scope — team-profile + driver-profile depth

| Option | Description | Selected |
|--------|-------------|----------|
| Show only when ≥2 phases | Single-phase profile renders identically to today | ✓ |
| Always show phase section | Even single-phase profile shows "1 phase" section | |

**User's choice:** Show only when ≥2 phases (Recommended).
**Notes:** Clean SC4 default for current data shape; richer view appears organically when phases warrant it.

---

## driver-ranking legacy URL behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Aggregated stays default | /season/{slug}/driver-ranking.html stays cross-phase aggregated | ✓ |
| REGULAR becomes default; aggregated moves to suffix | driver-ranking-all.html for aggregated | |

**User's choice:** Aggregated stays default (Recommended).
**Notes:** SC4-clean; cross-phase is the most-used view; per-phase variants are drill-downs.

---

## Sub-nav (layout.html) phase awareness

| Option | Description | Selected |
|--------|-------------|----------|
| Keep sub-nav coarse | Sub-nav links stay at legacy URLs; currentPage matching unchanged | ✓ |
| Extend sub-nav with per-phase awareness | Sub-nav becomes phase-aware; possible dropdown | |

**User's choice:** Keep sub-nav coarse (Recommended).
**Notes:** Phase navigation lives within the page, not in shared layout. Reduces shared-layout churn.

---

## GROUPS-layout default view

| Option | Description | Selected |
|--------|-------------|----------|
| Combined-View | Per-phase landing = combined (all teams flat with Group column) | ✓ |
| First group as default | Per-phase landing redirects to group-a | |

**User's choice:** Combined-View (Recommended).
**Notes:** Mirrors admin Phase 60 D-30; visual + URL parity.

---

## SC5 regression test framework

| Option | Description | Selected |
|--------|-------------|----------|
| Surefire integration test | @SpringBootTest with H2 + Jsoup parsing | ✓ |
| Playwright E2E (Failsafe -Pe2e) | Browser-driven full-render verification | |
| Both — Surefire IT for structure + playwright-cli for visual | Hybrid manual + automated | |

**User's choice:** Surefire integration test (Recommended).
**Notes:** Mirrors v1.6 sitegen test pattern + Phase 61 D-09 V6MigrationTest "Surefire not Failsafe" choice. No Playwright dependency for sitegen tests.

---

## Alltime aggregation

| Option | Description | Selected |
|--------|-------------|----------|
| Keep REGULAR-only | Today's behavior, no change | |
| Expand to cross-phase aggregate per season | Sum all phases per season | ✓ |

**User's choice:** Expand to cross-phase aggregate per season.
**Notes:** **TRACKED BEHAVIOR CHANGE.** Public-site-visible alltime numbers will recompute and may shift for any season with a PLAYOFF / PLACEMENT phase. Must be called out in PR + release notes (mirror Phase 61 D-23 pattern). Captured as D-19 in CONTEXT.md.

---

## team-profile standings panel on GROUPS-layout

| Option | Description | Selected |
|--------|-------------|----------|
| Combined-view standing for that team | calculateStandings(REGULAR, group=null) | ✓ |
| Per-group standing | Team's position within their group | |

**User's choice:** Combined-view standing for that team (Recommended).
**Notes:** Matches legacy default page; per-group data lives in the breakdown section (when ≥2 phases).

---

## Cross-link strategy from per-phase pages

| Option | Description | Selected |
|--------|-------------|----------|
| Single team-profile.html / driver-profile.html | All standings variants link to one canonical profile per entity | ✓ |
| Per-phase team-profile variants | team-profile-regular.html / team-profile-playoff.html | |

**User's choice:** Single team-profile.html (Recommended).
**Notes:** Avoids combinatorial explosion of files; per-phase data lives in the breakdown section internally.

---

## SiteGenerator refactoring shape

| Option | Description | Selected |
|--------|-------------|----------|
| Phase-aware sub-methods, same outer loop | Inline expansion in current SiteGeneratorService | |
| Extract per-page generators into helper classes | StandingsPageGenerator / etc. as separate beans | ✓ |
| Inline expansion + refactor opportunistically | Pragmatic, planner judges per-method | |

**User's choice:** Extract per-page generators into helper classes.
**Notes:** Today's SiteGeneratorService is ~870 LOC; per-phase variants would balloon. Per-page Spring beans mirror the admin layer's service decomposition pattern. Easier per-page-type unit testing.

---

## SC5 test fixture composition

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse Phase 59 D-09 GROUPS-2023 fixture | TestDataService.create2023GroupsSeason | ✓ |
| Dedicated SiteGeneratorPhaseAwareIntegrationTest fixture | Self-contained inside test class | |
| @Sql script with minimal SQL inserts | Pre-insert raw rows | |

**User's choice:** Reuse Phase 59 D-09 GROUPS-2023 fixture (Recommended).
**Notes:** Single source of truth for GROUPS test data; battle-tested in admin-side tests; less duplication.

---

## Empty-state per phase

| Option | Description | Selected |
|--------|-------------|----------|
| Render with 0-point roster + banner | Mirror Phase 60 D-36 admin empty-state | ✓ |
| Skip page generation when phase has 0 results | Hide tab in nav | |
| Render empty page with banner only | No table | |

**User's choice:** Render with 0-point roster + banner (Recommended).
**Notes:** Predictable structure; users see who's competing even pre-results; mirrors admin D-36.

---

## Visual + accessibility verification scope

| Option | Description | Selected |
|--------|-------------|----------|
| All 4 phase-aware page types + a11y on tabs | playwright-cli Desktop + Mobile + ARIA tablist + keyboard nav | ✓ |
| Standings + matchdays only; basic a11y | Lighter scope | |
| All phase-aware pages, a11y by Claude's discretion | Full visual; planner picks a11y level | |

**User's choice:** All 4 phase-aware page types + a11y on tabs (Recommended).
**Notes:** v1.6 polish bar; CLAUDE.md mandate; tab role="tablist" + aria-selected + aria-controls; native Tab-key + Enter activation (no JS arrow handler).

---

## Wrap-up

| Option | Description | Selected |
|--------|-------------|----------|
| Write CONTEXT.md | All major gray areas covered; ready for /gsd-plan-phase 62 | ✓ |
| One more area | Something still to lock down | |

**User's choice:** Write CONTEXT.md.

---

## Claude's Discretion

User left these to Claude / Planner:

- Exact CSS class names for phase-tab row + group-sub-tab row in `static/site/css/style.css`
- Helper-class boundaries (whether MatchdayPageGenerator + MatchdayIndexPageGenerator are one class or two; whether TeamProfilePageGenerator includes alltime team-profile logic)
- Active-tab visual treatment (underline accent vs. background fill vs. bold-only)
- Empty-phase-banner exact wording
- Whether `generatePlayoffBracket` extracts to its own helper class
- Number of plan files (rough decomposition is ~6-8)
- TDD sequence within each plan (refactor first vs. test first)
- Whether `PhaseSlug / GroupSlug` slug helpers live in `SiteGeneratorService.slugify` or new dedicated methods

## Deferred Ideas

Captured in CONTEXT.md `<deferred>` section. Highlights:

- Per-phase team-profile / driver-profile URL forks
- Sub-nav extension with per-phase awareness
- JS-driven tab toggle / arrow-key keyboard nav
- Standalone `standings-playoff.html` for PLAYOFF-phase final standings table
- Per-phase RSS / sitemap entries
- Cross-season phase-aware navigation
- Worktree parallelization for Phase 62 plan-tasks
- Automated Playwright E2E for SC5 (chose Surefire IT)
- Mobile-Dropdown navigation as alternative to horizontal-scroll tabs
