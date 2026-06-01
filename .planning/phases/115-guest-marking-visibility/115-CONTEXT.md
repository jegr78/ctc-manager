# Phase 115: Guest Marking & Visibility - Context

**Gathered:** 2026-06-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Visually mark guest drivers on **every surface that already renders driver names** so operators and viewers can identify guest appearances at a glance (MARK-01..06). This is the final phase of milestone v1.17 (Guest Drivers).

**Character of the phase: presentation only.** The data already exists from prior phases — there is NO new scoring, no new data model, no new persistence:
- **Phase 113** added `RaceLineup.guest` (`is_guest` column, `RaceLineup.java:38-39`) — the per-race, per-driver guest flag (Source of Truth via `RaceLineup`).
- **Phase 114** ensured pure guests (no `SeasonDriver`) already appear in the season/alltime driver-ranking and get a public driver-profile page (D-05). The *mark* itself was explicitly deferred to this phase.

**Surfaces in scope (each must surface a per-row guest flag for display):**
- **Graphics** (Playwright-rendered, excluded from JaCoCo): Scorecard (`results-render.html` / `ResultsGraphicService`), Provisional Scores (`provisional-scores-render.html`), matchday-results (`matchday-results-render.html`), match-results (`match-results-render.html`), and — audit-driven — every other `*-render` graphic that renders driver names (MARK-01/02/03).
- **Admin detail**: `race-detail.html` + `matchday-detail.html`, lineup rows AND result rows (MARK-04).
- **Driver-ranking**: admin standings + public site (`site/driver-ranking.html`, `site/alltime-driver-ranking.html`) (MARK-05).
- **Public driver-profile**: `site/driver-profile.html`, per guest race (MARK-06).

**Out of scope (do not introduce here):**
- Any new scoring rule, points model, or persisted personal-points store (locked in Phase 114 D-10).
- Ad-hoc/free-text guests, guest-specific scoring, reassigning points away from the fielding team (REQUIREMENTS.md "Out of Scope", v1.17).
- New capabilities beyond marking — this phase only adds a visual marker + (on the profile) the fielding-team name.

</domain>

<decisions>
## Implementation Decisions

### Marker Treatment (MARK-01..06, common)
- **D-01 (Treatment locked = Icon + accent color):** The guest marker is an **icon plus an accent color**. The direction is locked NOW (not a candidate comparison). The `playwright-cli` visual approval against a rendered reference (ROADMAP SC-1, CLAUDE.md "Visual Verification") only **confirms and fine-tunes** the chosen treatment — it does not re-open the treatment type.
- **D-02 (Identical everywhere):** The **same icon glyph** and the **same accent color** are used across ALL surfaces (graphics, admin, public site). Maximum recognizability. The accent color is held as a **central CSS variable/constant**, mirrored into `admin.css` (graphics render templates inline this) + the site CSS so all three render paths share one value.
- **D-03 (Glyph/color = planner discretion, constrained):** The exact icon glyph and the concrete color value are chosen by the planner **against the rendered reference**. Hard constraints: (a) the **icon is the PRIMARY carrier of meaning** — color alone is insufficient (WCAG); (b) color is reinforcement only; (c) **color-blind-safe**; (d) the glyph must be **intuitive** because there is no explanatory text (see D-09).

### What the Marker Conveys
- **D-04 (Binary flag everywhere; fielding team only on profile):** Every surface carries a **binary "was a guest"** marker only. The **fielding/origin team is shown ONLY on the public driver-profile** (MARK-06 requires it). Graphics and rankings carry no team. Admin MAY add the fielding team as a richer extra (D-08).
- **D-05 (Ranking-row semantics, MARK-05):** A season ranking row is marked **as soon as it contains ≥ 1 guest appearance** — covering **both** pure guests (no `SeasonDriver`) **and** dual-role drivers (own-team rows + guest rows). **Identical** in admin standings and public site ranking. Requires a `hasGuestAppearance` (boolean) flag **computed in `DriverRankingService`** (service-side data prep — NO template SpEL logic, per CLAUDE.md "Keep Thymeleaf Templates Lean").

### Graphic Coverage (MARK-03)
- **D-06 (Audit-driven, comprehensive scope):** The researcher/planner **audits ALL `*-render` templates** and marks **every graphic that renders driver names** — not only the three named (Scorecard/`results-render`, Provisional Scores, matchday-results). Goal (ROADMAP SC-2): **no graphic posts alongside others with unmarked guests.** Candidates to audit: `results-render`, `provisional-scores-render`, `matchday-results-render`, `match-results-render`, `lineup-render`, `overlay-render`, `power-rankings-render`. Team-only graphics (`standings-render`, `matchday-pairings/overview/schedule-render`, `playoff-round-*`) are excluded **only if** they render no driver names — verify per template, do not assume.
- **D-07 (Shared marker mechanism):** Use **one shared mechanism** — a shared Thymeleaf fragment + an `isGuest` flag carried **per-row in the graphic data model/DTO** — so all graphics stay consistent. The guest flag is computed in the graphic **data services** (the Playwright-excluded layer), never via template SpEL. *(Architecture detail with low blast-radius — shape is planner discretion, but a single source for the marker is the intent of MARK-03.)*

### Admin Detail (MARK-04)
- **D-08 (Same marker, admin may be richer):** `race-detail.html` + `matchday-detail.html` — both **lineup rows and result rows** — get the same icon+accent marker as the graphics. Admin is **not** a posted screenshot, so it MAY additionally surface the fielding team (tooltip or small column). Reuse existing **`admin.css` classes (no inline styles**, CLAUDE.md); anchor on the **Phase-113 guest UI** (`race-lineup.html` Add-Guest block) for styling consistency.

### Legend / Self-Evidence
- **D-09 (No legend — icon self-explanatory):** Graphics get **no legend/caption**. The icon is treated as self-explanatory. **Accepted tradeoff:** external viewers must infer the meaning → reinforces the D-03 constraint that the glyph must be intuitive.

### Profile Fielding-Team Display (MARK-06)
- **D-10 (Inline sub-label):** On `driver-profile.html`, each guest race row gets the icon+accent **plus an inline sub-label "as guest for <Team>"** naming the fielding team. **No new table column** — inline annotation on the existing per-race row.

### Sub-team → Parent Display
- **D-11 (Show actual sub-team name):** Where the fielding team is displayed (profile sub-label D-10, admin extra D-08), show the **actual sub-team name** from `RaceLineup` (the real lineup), NOT the parent. **Display-only** choice — points attribution stays **parent-rollup** per Phase 114 D-02. No conflict: display ≠ attribution.

### Accent Color Sourcing
- **D-12 (Central CSS variable, chosen against reference):** The accent color is a **central CSS variable**; the concrete value is chosen by the planner against the rendered reference and mirrored across `admin.css` + graphic render templates + site CSS. WCAG contrast verified; icon remains the primary meaning carrier (D-03); color-blind-safe.

### Claude's Discretion
- Exact icon glyph + concrete accent-color value (against the rendered reference; constraints in D-03/D-12).
- Shape of the shared marker fragment and exactly where each graphic data service computes the per-row `isGuest` flag (D-07).
- Whether one "is this row a guest" resolver is shared across graphics + admin + ranking, or computed per surface (must reuse `RaceLineup.guest` as Source of Truth either way).
- Exact admin fielding-team presentation (tooltip vs. small column) (D-08).
- Where `hasGuestAppearance` lives on the `DriverRankingService` ranking row/accumulator (D-05).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & Roadmap & Prior Phases
- `.planning/REQUIREMENTS.md` §"MARK — Marking & Visibility" — MARK-01..06 (the six requirements this phase satisfies) + the visual-treatment deferral note + "Out of Scope" table.
- `.planning/ROADMAP.md` §"Phase 115: Guest Marking & Visibility" — goal + the five success criteria (SC-1 rendered-reference visual approval; SC-2 no-unmarked-graphic).
- `.planning/phases/113-guest-assignment-foundation/113-CONTEXT.md` — `RaceLineup.guest` data model, `(race_id, driver_id)` uniqueness, guest CRUD UI (`race-lineup.html` Add-Guest block) = the styling anchor for admin marking (MARK-04).
- `.planning/phases/114-scoring-personal-crediting/114-CONTEXT.md` — guest data already flows into rankings/profile; D-02 parent-rollup attribution (relevant to D-11 display-vs-attribution distinction); D-05 pure-guest profile page exists (the row this phase marks).

### Graphics — render templates (mark surfaces, MARK-01/02/03)
- `src/main/resources/templates/admin/results-render.html` — **Scorecard** (race scoring) graphic (MARK-01). *Researcher: confirm Scorecard = `ResultsGraphicService`/`results-render`.*
- `src/main/resources/templates/admin/provisional-scores-render.html` — Provisional Scores (MARK-02).
- `src/main/resources/templates/admin/matchday-results-render.html` + `match-results-render.html` — matchday/match results (MARK-03).
- `src/main/resources/templates/admin/lineup-render.html`, `overlay-render.html`, `power-rankings-render.html` — **audit for driver names** (D-06).
- Team-only candidates to verify-and-likely-exclude: `standings-render.html`, `matchday-pairings-render.html`, `matchday-overview-render.html`, `matchday-schedule-render.html`, `playoff-round-*-render.html`.

### Graphics — data services (where the per-row `isGuest` flag is computed; JaCoCo-excluded)
- `src/main/java/org/ctc/admin/service/ResultsGraphicService.java`, `ProvisionalScoresGraphicService.java`, `MatchdayResultsGraphicService.java`, `MatchResultsGraphicService.java`, `LineupGraphicService.java`, `OverlayGraphicService.java`, `PowerRankingsGraphicService.java` — and their abstract bases `AbstractGraphicService.java`, `AbstractMatchdayGraphicService.java`.
- `src/main/java/org/ctc/admin/dto/MatchdayGraphicData.java`, `PowerRankingsGraphicData.java` — graphic data DTOs (candidate carriers for the `isGuest` per-row flag, D-07).

### Admin detail (MARK-04)
- `src/main/resources/templates/admin/race-detail.html`, `matchday-detail.html` — lineup + result rows to mark.
- `src/main/resources/templates/admin/race-lineup.html` — Phase-113 guest UI / Add-Guest block = styling anchor.

### Ranking + Profile (MARK-05, MARK-06)
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` — add `hasGuestAppearance` to the ranking row/accumulator (D-05); `calculateRankingForPhase` (admin per-phase), `aggregateAcrossPhases` (site season), `calculateAlltimeRanking`.
- `src/main/java/org/ctc/domain/service/StandingsViewService.java` — admin standings consumer.
- `src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java` — public ranking pages.
- `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java` — public profile; per-race rows + the D-10 "as guest for <Team>" sub-label.
- `src/main/resources/templates/site/driver-ranking.html`, `alltime-driver-ranking.html`, `driver-profile.html` — site templates to mark.
- `src/main/java/org/ctc/domain/model/RaceLineup.java` (`guest` field, `is_guest`) — Source of Truth for the per-race guest flag.
- `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` — `findByRaceIdAndDriverId`, `findByDriverIdAndRaceMatchdaySeasonId` (guest resolution).

### Styling & Conventions
- `src/main/resources/static/css/admin.css` — central accent-color CSS variable + marker classes (D-02/D-12); NO inline styles on buttons/elements.
- `CLAUDE.md` §"Keep Thymeleaf Templates Lean" (no SpEL/logic — guest flag from service), §"No Inline Styles on Buttons", §"Visual Verification with `playwright-cli`", §"RaceLineup is Source of Truth", §"Excluded from coverage" (graphics services), §"gsd-auto-uat for UI-Heavy Verification".

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`RaceLineup.guest`** (Phase 113) — the single source for "is this driver a guest in this race". Every surface resolves the marker from it (or from a row flag derived from it).
- **Phase-113 guest UI** (`race-lineup.html` Add-Guest block) — existing admin guest styling to anchor the MARK-04 indicator on.
- **`DriverRanking` accumulator** in `DriverRankingService` (from Phase 114 unification) — already iterates per-driver results; add a `hasGuestAppearance` boolean here (D-05) rather than recomputing in templates.
- **Shared graphic abstractions** (`AbstractGraphicService`, `AbstractMatchdayGraphicService`) + the `*GraphicData` DTOs — natural home for a shared `isGuest` per-row flag (D-07).

### Established Patterns
- **Graphics are Playwright-rendered and JaCoCo-excluded** — guest-flag logic must live in the data services / DTOs, not in template SpEL, and graphic services won't move coverage either way.
- **Site generation re-runs the same services** — fixes to `DriverRankingService` / `DriverProfilePageGenerator` propagate to the public site automatically.
- **Server-side rendering, no frontend build** — marker is plain CSS + a shared Thymeleaf fragment; accent color as a CSS variable.

### Integration Points
- Guest data (113) + ranking/profile presence (114) → consumed here purely for display; no new column, no new query beyond surfacing the existing `RaceLineup.guest`.
- One central accent-color CSS variable mirrored into `admin.css` + graphic templates + site CSS (D-02/D-12) — touches a shared file: APPEND marker classes, do not rewrite existing CSS (CLAUDE.md subagent rule).

</code_context>

<specifics>
## Specific Ideas

- **Treatment = Icon + accent color, identical everywhere** (D-01/D-02), direction locked in discussion — the rendered-reference step confirms the glyph/color, it does not re-pick the treatment type.
- **No legend on graphics** (D-09) — the user explicitly accepts that the icon must stand on its own; the glyph choice must therefore be intuitive.
- **Binary marker is the default; the fielding team is "extra info" surfaced only where there is room** — profile (required, MARK-06), admin (optional richer). Graphics + rankings stay clean (D-04).
- **Display shows the actual sub-team**, even though points roll up to the parent (D-11) — the user wants the real lineup reflected visually.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. Phase 115 is the final phase of milestone v1.17; after verification + review + validation the milestone closes (`/gsd-complete-milestone`). No new capabilities were raised that would belong to a future milestone.

</deferred>

---

*Phase: 115-Guest Marking & Visibility*
*Context gathered: 2026-06-01*
