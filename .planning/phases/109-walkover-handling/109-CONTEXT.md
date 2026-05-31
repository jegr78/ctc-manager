# Phase 109: Walkover Handling - Context

**Gathered:** 2026-05-30
**Status:** Ready for planning

<domain>
## Phase Boundary

An admin can mark a **real, fully-scheduled match** (both teams set) as a walkover when one team does not compete at all. The opponent receives an auto-win with full match points, analogous to the existing `Match.bye` logic. A visible "w/o" label appears next to the walkover (non-competing) team in standings and the relevant graphics. The walkover state is persisted via a new Flyway migration (V17, H2 + MariaDB compatible).

A **walkover is distinct from a `bye`**: a `bye` has `awayTeam = null` (no opponent scheduled, structural); a walkover has both teams real but one forfeits. The two concepts stay independent — a match is normal *or* `bye` *or* walkover.

**Requirements:** WO-01, WO-02, WO-03, WO-04 (see `.planning/REQUIREMENTS.md`).

</domain>

<decisions>
## Implementation Decisions

### Data Model & bye Distinction
- **D-01:** Persist walkover via a new **nullable FK column `walkover_team_id`** (→ `teams`) on the `matches` table. `NULL` = no walkover. Added by Flyway **V17** (single ALTER, H2 + MariaDB compatible; V1–V16 immutable).
- **D-02:** `walkover_team_id` references the **non-competing team (the forfeiter)**. The opponent (the team that is *not* `walkover_team_id`) receives the auto-win. The forfeiter is the new/distinguishing information.
- **D-03:** Walkover stays **separate** from `bye`. No enum/`byeType` unification — `bye` requires `awayTeam = null`, walkover requires both teams, so the invariants must not be merged. Scoring branches handle them independently.
- **D-04:** Entity-mapping detail (raw UUID column vs. `@ManyToOne Team walkoverTeam`) is Claude's discretion, but should follow the existing `Match` team-field convention (`homeTeam`/`awayTeam` are `@ManyToOne(fetch = LAZY)`). DB column is `walkover_team_id` either way.

### Auto-Win Scoring
- **D-05:** The auto-win is awarded **at standings read-time** — `homeScore`/`awayScore` stay `null` (exactly like `bye` today). `StandingsService.processMatch()` is extended; **no synthetic scoreline is written to the DB**, no change to `ScoringService.aggregateMatchScores()`.
- **D-06:** In `processMatch()`, when `walkover_team_id` is set: the **opponent** gets `addWin()` + `addMatchPoints(pointsWin)`; the **forfeiter** gets `addLoss()` + 0 match points. Both real teams appear normally in the table (unlike `bye`, where the opponent is `null` and absent).
- **D-07 (revised 2026-05-30):** A walkover awards Win/Loss + match points **and** credits the winner the **full team race score** as `pointsFor` (forfeiter as `pointsAgainst`): race points for the top `WALKOVER_TEAM_POSITIONS` (6) finishing positions + all qualifying points + the fastest-lap bonus, computed from the phase `RaceScoring` at read-time. Team total only — **never per driver** (no driver-ranking impact). Supersedes the original "no point difference" decision (user clarification 2026-05-30); the winner's point difference and Buchholz contribution now reflect the credited score. `homeScore`/`awayScore` still stay `null` (D-05).
- **D-08:** When a match already has partial race results and is then marked walkover, the **walkover branch takes precedence** in `processMatch()` — it ignores `homeScore`/`awayScore` and any race results for standings. Results are **not deleted** (non-destructive, reversible: clearing the walkover restores normal scoring).

### Admin Marking Flow
- **D-09:** The walkover is marked on the **existing match edit form** (`match-form-edit.html`, today only Discord fields) + `MatchForm` DTO + `MatchController` edit endpoint. Binding via the Form DTO (never the entity directly), per project convention. Rationale: a walkover becomes known *after* scheduling, so edit (not create) is the natural place.
- **D-10:** Team selection is a **single dropdown** with three options: `Kein Walkover` (= `null`), the Home team, the Away team. Clearing = selecting `Kein Walkover`. (Distinct from the `bye` checkbox + JS-toggle on the create form.)
- **D-11:** **Service-level validation:** the selected `walkover_team_id` must be one of the match's two teams (home or away); a `bye` match (`awayTeam = null`) cannot also be a walkover. Violation → `BindingResult` error + `errorMessage` flash attribute. Do not rely on the DB FK constraint alone.

### "w/o" Label — Semantics & Placement
- **D-12:** The "w/o" label appears **next to the non-competing team (the forfeiter)** — i.e. the team in `walkover_team_id`. This keeps the label and the stored data field pointing at the same team. The opponent renders as a normal winner.
- **D-13:** Standings views showing the label: **both** `admin/matchday-detail.html` (admin, where `bye` already shows via `.match-bye`) **and** `site/standings.html` (public).
- **D-14:** Graphics showing the label: **all three** Phase-108 graphics — `match-results-render` (scorecard; `MatchResultsGraphicService` currently throws on `awayTeam == null`, but a walkover has both teams, so handle the forfeiter slot rendering "w/o"), `lineup-render`, and `provisional-scores-render`. The forfeiter team's slot shows "w/o" instead of a score/result. Build additively on Phase 108's stabilised template set + empty-state styling (no clobber).

### Walkover Match Races
- **D-15:** Races/legs created at match creation **remain unchanged** when a walkover is set — they are simply unscored. `aggregateMatchScores`/`recomputeMatchScoresFromAllLegs` already skip races with no results, so no special handling is needed. Removing the walkover restores everything. **Do not delete races.**

### Discord
- **D-16:** **No walkover-specific Discord behaviour in this phase.** Walkover only changes persistence/scoring/label/graphic rendering. If a walkover match-results graphic is posted, it carries the "w/o" label from the graphic change (D-14), but there is no new auto-post logic. Walkover-specific Discord handling is deferred.

### Claude's Discretion
- Exact JPA mapping of `walkover_team_id` (raw UUID vs. `@ManyToOne`) — follow the `Match` home/away convention (D-04).
- CSS class naming for the "w/o" badge — mirror the existing `.match-bye` / Phase-108 empty-state styling conventions.
- Whether a `.sql` migration suffices for V17 or a Java dialect-aware migration is needed — a simple nullable FK column is `.sql`-friendly (see `V11__add_matches_discord_channel_archived_at.sql` pattern); confirm during research.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & Roadmap
- `.planning/REQUIREMENTS.md` §WO — WO-01..04 (walkover requirements + the out-of-scope table establishing D-WO-Bye-Analogy)
- `.planning/ROADMAP.md` "Phase 109: Walkover Handling" — goal, success criteria, cross-phase risk with Phase 108
- `.planning/STATE.md` — Accumulated Context decisions (D-WO-Bye-Analogy, D-Graphic-Sequencing, Flyway V17 = next)

### Existing bye / scoring logic to mirror & extend
- `src/main/java/org/ctc/domain/model/Match.java` — `boolean bye` (`@Column(nullable=false)`), nullable `awayTeam`, `@ManyToOne` team fields; add `walkover_team_id` here
- `src/main/java/org/ctc/domain/service/StandingsService.java` — `processMatch()` (bye auto-win branch; extend for walkover), `calculateStandings()`, `calculateStandingsWithBuchholz()`, `isMatchdayScheduleStale()` (skips bye)
- `src/main/java/org/ctc/domain/service/ScoringService.java` — `aggregateMatchScores()` / `recomputeMatchScoresFromAllLegs()` (both skip bye races; confirm walkover skip)
- `src/main/java/org/ctc/domain/model/MatchScoring.java` — `pointsWin` / `pointsDraw` / `pointsLoss`

### Admin UI
- `src/main/java/org/ctc/admin/controller/MatchController.java` — create (`bye` param) + edit endpoints
- `src/main/java/org/ctc/admin/dto/MatchForm.java` — add walkover field here
- `src/main/resources/templates/admin/match-form-edit.html` — add the walkover dropdown
- `src/main/resources/templates/admin/match-form.html` — existing `bye` checkbox + JS toggle (reference pattern)

### Standings & graphic templates ("w/o" label)
- `src/main/resources/templates/admin/matchday-detail.html` — `.match-bye` label pattern to mirror
- `src/main/resources/templates/site/standings.html` — public standings
- `src/main/resources/templates/admin/match-results-render.html` + `src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java` (throws on `awayTeam == null`)
- `src/main/resources/templates/admin/lineup-render.html`
- `src/main/resources/templates/admin/provisional-scores-render.html`

### Flyway
- `src/main/resources/db/migration/` — highest is **V16**; new walkover migration is **V17**. Patterns: `V11__add_matches_discord_channel_archived_at.sql` (simple nullable column), `V5__NullableLegacyScoringColumns.java` (H2/MariaDB dialect split if needed)
- `CLAUDE.md` "Do Not Modify Flyway Migrations" + "OSIV" + "Score Aggregation on Result Save" + "RaceLineup is Source of Truth"

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Match.bye` end-to-end flow is the blueprint: column on `matches`, `processMatch()` standings-time award, `bye`-skip guards in `ScoringService`. Walkover mirrors this with a team-aware twist (both teams real, opponent can be home or away).
- `.match-bye` rendering in `matchday-detail.html` and the create-form `bye` checkbox/JS-toggle are direct UI analogs.
- Phase 108's empty-state styling on the three graphic templates is the styling baseline the "w/o" label builds on (additive, no clobber).

### Established Patterns
- Bye points are awarded at **standings read-time**, not at save-time (`StandingsService.processMatch()`), and `homeScore/awayScore` stay `null`. Walkover follows the same timing (D-05).
- Form binding via DTO (`MatchForm`), never the entity (Mass-Assignment protection). Validation + `errorMessage`/`successMessage` flash attributes.
- Flyway: one new migration file per schema change; V1–V16 immutable.

### Integration Points
- `StandingsService.processMatch()` — new walkover branch (must run before any score-based logic; precedence over partial results per D-08).
- `Match` entity + V17 migration — `walkover_team_id` FK.
- `MatchController` edit + `MatchForm` + `match-form-edit.html` — the marking UI.
- Three graphic services/templates + two standings templates — the "w/o" label.

</code_context>

<specifics>
## Specific Ideas

- Label text is literally **"w/o"** (per ROADMAP success criterion 3 and WO-03).
- Walkover must run on both H2 (test/dev) and MariaDB (local/prod); `./mvnw clean verify -Pe2e` must exit 0 with all existing tests green (WO-02 / SC4).
- Cross-phase invariant: Phase 108 already stabilised the graphic templates; Phase 109 changes are **additive** on top — preserve Phase 108's n/a / empty-slot work (no rewrite of those template sections).

</specifics>

<deferred>
## Deferred Ideas

- **Richer walkover model** — dedicated walkover points config, forfeit reasons/notes. Out of scope per D-WO-Bye-Analogy (REQUIREMENTS.md out-of-scope table); mirror `bye` only.
- **Walkover-specific Discord handling** — e.g. suppress posting walkover matches, or a dedicated walkover teaser text. Own phase / future milestone (D-16).
- **Destructive walkover variants** — deleting race results or races on walkover were considered and rejected in favour of the non-destructive, reversible approach (D-08, D-15).

### Reviewed Todos (not folded)
None — no pending todos matched Phase 109.

</deferred>

---

*Phase: 109-walkover-handling*
*Context gathered: 2026-05-30*
