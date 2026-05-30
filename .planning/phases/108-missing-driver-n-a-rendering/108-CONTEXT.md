# Phase 108: Missing-Driver n/a Rendering - Context

**Gathered:** 2026-05-30
**Status:** Ready for planning

<domain>
## Phase Boundary

When a team fields fewer than 6 drivers, all three affected graphics — **Lineup**,
**Scorecard/Results**, and **Provisional-Scores** — render exactly 6 rows per side,
with missing slots shown as **"n/a"** (0 points), and the scoring engine treats
missing drivers consistently as 0 points / no position. The fix lives in the
**service/data layer**, never in Thymeleaf templates or controllers (CLAUDE.md
"No Fallback Calculations").

In scope: padding-to-6 in the three graphic services + their DTOs/templates,
introducing an "n/a" empty-slot convention, a single central team-size value.

Out of scope: persisting placeholder result rows, any Flyway/schema change, and
per-season configurable team size (deferred — see below).

</domain>

<decisions>
## Implementation Decisions

### Data / Scoring representation (LINEUP-04 / SC4)
- **D-01:** **Render-time padding in the graphic services** — each of the three
  services builds a fixed 6-row-per-side render DTO; missing slots get `"n/a"` + 0.
  **No persisted placeholder `RaceResult` rows, no Flyway migration.** Rationale:
  `RaceResult.position` is `@Min(1)` and NOT NULL; persisting placeholders would
  need a schema change + an audit of every standings/position/count query for
  pollution — disproportionate to a rendering fix.
- **D-02:** Scoring persistence is **unchanged**. An absent driver naturally
  contributes 0 points / no position to `aggregateMatchScores`. SC4 is satisfied
  because the *correct place* for the n/a/0 data is the graphic service that builds
  the render model — templates/controllers compute nothing. (Plan must still
  *verify* aggregation + standings are robust with <6 drivers — no null, no skew.)

### "Missing slot" definition (SC3)
- **D-03:** Padding is driven by the **count of real rows each graphic's own source
  yields**: Lineup → `RaceLineup` roster entries; Results/Provisional → `RaceResult`
  rows. Any slot beyond that count = `"n/a"`.
- **D-04:** A driver who is in the `RaceLineup` roster but has **no result yet**
  (e.g. mid-import provisional) is shown as **"n/a"**, not by name — per SC3
  ("if fewer than 6 drivers have results, the remaining rows appear with 'n/a'").
  Simple, uniform rule; no per-slot roster reconciliation.
- `RaceLineup` remains source of truth for team membership of the *existing* rows.

### Visual styling
- **D-05:** n/a rows get a **distinct de-emphasized treatment** (dedicated CSS
  class, e.g. `.empty-slot` — dimmed/greyed) so real drivers dominate visually.
  Points column for an n/a slot shows `"0"`. Applied **consistently across all
  three graphics**. All three switch to the literal `"n/a"` string (Lineup/Results
  currently emit empty string `""`). **Exact look is a visual checkpoint** — the
  user reviews screenshots during execution and gives free-typed feedback; do not
  finalize pixels from this CONTEXT alone. No inline styles — use `admin.css`
  classes (CLAUDE.md).

### Team size
- **D-06:** **Single central source of truth, no schema change** — one constant
  (default `TEAM_DRIVERS = 6`) used by all three services (and any scoring guard).
  Global, **not** per-season. Real per-season configurability is deferred to its
  own phase (no field exists today on `Season`/`SeasonPhase`).

### Claude's Discretion
- Exact home of the team-size value (a Java constant vs an `application.yml`
  property). Default to a **constant** — a property would imply runtime tunability
  that this phase does not deliver (real config is deferred).
- Exact `.empty-slot` class name and CSS values (subject to the visual checkpoint).
- Whether to add a defensive guard in `ScoringService` for the <6 case if research
  shows any null risk.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope & requirements
- `.planning/ROADMAP.md` § "Phase 108: Missing-Driver n/a Rendering" — goal + the
  four success criteria (locks "n/a", 6 rows, service-layer fix, no template fallback).
- `.planning/REQUIREMENTS.md` § "LINEUP — Missing-Driver Handling" — LINEUP-01..04.

### Project conventions (binding)
- `CLAUDE.md` § "Architectural Principles" → **No Fallback Calculations** (fix the
  root cause in the service, never in templates/controllers), **RaceLineup is
  Source of Truth**, **Score Aggregation on Result Save**.
- `CLAUDE.md` § "CSS Guidelines" / "No Inline Styles on Buttons" — use `admin.css`
  classes; if JS sets `element.className`, add the new class there too.

No external ADRs/specs — requirements fully captured above.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets / Integration Points (from codebase scout)
- `src/main/java/org/ctc/admin/service/LineupGraphicService.java` —
  `buildPairings(...)` (~line 131): replace `int maxSize = Math.max(home, away)`
  with the central team-size value. DTO `DriverPairing`. Template
  `src/main/resources/templates/admin/lineup-render.html`.
- `src/main/java/org/ctc/admin/service/ResultsGraphicService.java` —
  `buildResultRows(...)` (~line 164): same `Math.max` → team-size swap. DTO
  `DriverResultRow`. Template `templates/admin/results-render.html`.
- `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java` —
  `buildContext(...)` (~line 73): currently adds **only actual** rows (no padding
  at all → the SC3 bug). Add pad-to-6 loops for `homeRows`/`awayRows` with an
  `"n/a"` `ProvisionalRow`. Template `templates/admin/provisional-scores-render.html`.
- `src/main/java/org/ctc/domain/service/ScoringService.java` —
  `isDriverInTeam(...)` (RaceLineup-first, SeasonDriver fallback) and
  `aggregateMatchScores(...)`: no change expected under D-01/D-02; verify <6 safety.
- `src/main/java/org/ctc/domain/service/RaceService.java` — `saveResults(...)`:
  **no persistence change** under the render-padding decision.

### Established Patterns / Constraints
- Lineup/Results templates already use `th:if` guards on empty string (`!= ''`).
  Switching the placeholder to `"n/a"` means updating those guards — still pure
  presentation, no business logic added to templates.
- No existing `"n/a"` / empty-slot convention anywhere — this phase introduces it.
- **Cross-phase risk:** Phase 109 (Walkover) edits the **same** graphic templates.
  Phase 108 must complete + verify before Phase 109 starts template changes
  (clobber risk on shared files).

### Tests to update (Test-Impact)
- `LineupGraphicServiceTest` — uneven-teams test asserts `hasSize(3)` → must become 6.
- `ResultsGraphicServiceTest` — uneven-teams test asserts `hasSize(3)` → must become 6.
- `ProvisionalScoresGraphicServiceTest` — add assertions that home/away pad to 6
  with `"n/a"` rows.
- These graphic services are JaCoCo-excluded (Playwright runtime) — but the
  `build*`/padding helpers are plain logic and should be unit-tested directly.

</code_context>

<specifics>
## Specific Ideas

- The Provisional-Scores graphic currently renders **no row at all** for missing
  drivers (the visible inconsistency SC3 targets) — padding to 6 is the core fix there.
- The literal placeholder string is `"n/a"` (locked by the roadmap success criteria).

</specifics>

<deferred>
## Deferred Ideas

- **Per-season configurable team size** — a real `driverSlots` field on
  `SeasonPhase`/`Season` (default 6) + Flyway migration + admin-UI control, with all
  graphics/scoring reading the season value. The user wants this eventually, but it
  is a new capability beyond Phase 108's rendering scope and needs a schema change.
  Its own phase (v1.15 backlog or a later milestone). Phase 108 uses the central
  constant as the single source of truth so the later swap is localized.

</deferred>

---

*Phase: 108-missing-driver-n-a-rendering*
*Context gathered: 2026-05-30*
