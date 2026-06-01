# Phase 114: Scoring & Personal Crediting - Context

**Gathered:** 2026-06-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Ensure a guest driver's race points are credited correctly in two ways: (a) toward the **fielding team's** match score and standings (SCORE-01), and (b) **additionally credited to the guest personally** in the season's driver-ranking — including drivers with no `SeasonDriver` roster entry in that season (SCORE-02) — recomputed idempotently on every result save (SCORE-03).

**Key scouting finding (defines the phase character):** Guest crediting already *largely emerges* from the existing aggregation, because both scoring paths iterate `RaceResult` rows and resolve team via `RaceLineup` (Source of Truth), not via the `SeasonDriver` roster:
- **SCORE-01** — `ScoringService.isDriverInTeam(result, raceId, teamId)` uses `RaceLineup` (incl. sub-team→parent rollup); a guest result already flows into the fielding team's `Match.homeScore`/`awayScore`. → verification + regression test.
- **SCORE-02** — `DriverRankingService.calculateRankingForPhase` / `aggregateAcrossPhases` iterate `RaceResult` keyed by `driverId`; a guest *without* a `SeasonDriver` already appears in the season ranking. → works in principle; edge gaps closed below.
- **SCORE-03** — the driver-ranking is computed **live on read** (no persisted personal-points store); match scores are recomputed from *all* legs on each save → structurally idempotent.

**Therefore Phase 114 = verify + harden edge cases + lock regression tests**, NOT a new scoring system.

**In scope:**
- Unified team-attribution policy for guests across the three ranking paths (per-phase, season-aggregate, alltime).
- Close the alltime `team = null` gap for pure guests.
- Extend `DriverProfilePageGenerator` so a pure guest (no `SeasonDriver`) gets a profile page for that season (data hook only).
- Regression test suite pinning SCORE-01/02/03 + alltime/profile.
- Guest scenario in the shared test fixture + a guest example in the `dev,demo` seed.

**Out of scope (other phases):**
- Visual guest marking (`*`/badge) across graphics, admin detail, ranking, profile → **Phase 115** (MARK-01..06).
- Ad-hoc/free-text guests, guest-specific scoring rules, reassigning points away from the fielding team → **out of scope for v1.17** (REQUIREMENTS.md "Out of Scope").

</domain>

<decisions>
## Implementation Decisions

### Team Attribution in the Driver-Ranking
- **D-01 (Doppelrolle):** A driver who is a roster member of Team A but guests for Team B is attributed in their **personal** ranking row to their **home team** — the `SeasonDriver` team for that season. The home-team preference only changes behavior for guests; normal roster drivers are unaffected (their lineup team *is* their home team).
- **D-02 (Pure guest):** A driver with **no `SeasonDriver`** in the season is attributed to the **fielding team via `RaceLineup`** (sub-team→parent rollup as in `isDriverInTeam`). There is always a concrete team — no null/empty attribution.
- **D-03 (Unified policy):** The attribution rule is **home-first, fallback fielding** and applies **uniformly across all ranking surfaces** — `calculateRankingForPhase` (admin per-phase), `aggregateAcrossPhases` (site season), and `calculateAlltimeRanking` (admin + site alltime). One shared attribution helper is preferred over three divergent implementations. Accepted edge: a per-phase view may show a guest's (possibly phase-foreign) home team — the user chose the simpler uniform model over context-specific attribution.
  - **⚠ Planner note:** Today the three paths use *different* logic — `calculateRankingForPhase` uses lineup-only (`resolveTeamFromLineup`); `aggregateAcrossPhases` uses `attributeTeamFromRegularOrLineup`; `calculateAlltimeRanking` uses SeasonDriver-most-recent. Unifying them is the core refactor. Grep all callers (CLAUDE.md "Grep All Usages Before Refactor"); the per-phase change alters admin standings attribution for guests (no regression today — no guests exist yet).

### Alltime Ranking & Public Driver-Profile
- **D-04 (Alltime):** A guest race **counts toward the cross-season alltime ranking**. This requires closing the `team = null` gap in `calculateAlltimeRanking` (today the team map is built only from `SeasonDriver`) — fall back to `RaceLineup`-resolved team per D-02/D-03.
- **D-05 (Profile page existence):** A **pure guest** (no `SeasonDriver` in the season) must get a public **driver-profile page** for that season so the guest race is visible. `DriverProfilePageGenerator` today iterates `seasonDriverRepository.findBySeasonId(...)` only — extend the iteration to include drivers who appear solely as guests in that season. Reuse the existing `generatedDriverIds` dedup set. **114 delivers the data/page-existence hook only**; the visual guest marking on the profile is **MARK-06 / Phase 115** against a rendered reference.

### Additive Crediting & Stat Semantics
- **D-06 (Additive, single row):** A driver who races for their own team *and* guests in the same season is summed into **one** personal ranking row (additive) — already the behavior of `computeIfAbsent` + `addResult` over all the driver's results. Locked by SCORE-02.
- **D-07 (Stat semantics):** A guest race counts **fully** as a driven race — it increments `racesCount` and contributes to average-points and best-position, not just `totalPoints`. Avoids points-without-a-race distortion (consistent with "No Fallback Calculations").
- **D-08 (Counting threshold):** A guest race counts in the personal ranking **as soon as a `RaceResult` row exists** — including 0-point / last-place / DNF-with-position results. A pure "n.a." (missing-driver, no `RaceResult` row, per v1.15 LINEUP behavior) produces no result row and therefore does not appear. This is already how the `RaceResult`-iterating aggregation behaves.
- **D-09 (Phase edge):** The season ranking merges a driver's races across **all season phases** (REGULAR + PLAYOFF + PLACEMENT) regardless of which phase the driver is rostered in — a guest race in a phase the driver isn't rostered in still counts. This is the existing `aggregateAcrossPhases` behavior; keep it.

### Crediting Architecture
- **D-10 (Live read-model — LOCKED):** Keep the existing **live, on-read** computation for the driver-ranking. **No new persisted personal-points table / `DriverSeasonScore` aggregate, no recompute trigger.** Only the match score (`Match.homeScore`/`awayScore`) stays persisted (recomputed from all legs on each save, as today). This makes SCORE-03 idempotent by construction — no double-count is possible. The planner must NOT introduce a persistence layer for personal points.

### Test & Demo Fixtures
- **D-11 (Test fixture):** Extend the shared fixture (`TestDataService` / `TestHelper.createFullSeasonFixture`) with a guest scenario covering **both** a doppelrollen guest (roster Team A + guest Team B) and a **pure guest** (no `SeasonDriver`). Reusable for 114 + 115. Use the mandated test-prefix isolation (`T-…`, `Test_…`, `Test-Season …`).
- **D-12 (Demo seed):** Add a guest example to the **`dev,demo`** seed so `/gsd-auto-uat` (114) and the visual reference (115) have real guest data to render. The seeder stays `@Profile("dev")` only — **never** `local`/`prod`/`docker` (CLAUDE.md / memory "local darf KEINE Dev-Daten").

### Regression Test Scope (all four MUST be pinned)
- **D-13:** SCORE-01 — guest result flows into the fielding team's `Match.homeScore`/`awayScore` (incl. sub-team→parent via `isDriverInTeam`).
- **D-14:** SCORE-02 — pure guest (no `SeasonDriver`) appears in the season ranking with correct points + fielding team; doppelrollen guest sums additively under the home team.
- **D-15:** SCORE-03 — repeated `saveResults`/`aggregateMatchScores` yields identical scores (no double-count); removing a guest (Phase 113 D-11 cascade-delete of `RaceResult` + re-aggregation) makes both the personal credit and the team score disappear cleanly.
- **D-16:** Alltime + profile — guest race counts in the alltime ranking (team ≠ null); a pure guest gets a profile page containing the guest race.

### Claude's Discretion
- Exact shape/signature of the unified attribution helper (where it lives, whether it takes season + driver + race or resolves internally).
- Whether the alltime team map and the profile-generator iteration share one "drivers who participated in season X" query or compute independently.
- Concrete fixture wiring (entities, IDs, which existing season fixture to extend).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & Roadmap
- `.planning/REQUIREMENTS.md` §"SCORE — Scoring & Personal Crediting" — SCORE-01..03, the three requirements this phase satisfies; also the "Out of Scope" table.
- `.planning/ROADMAP.md` §"Phase 114: Scoring & Personal Crediting" — goal statement.
- `.planning/phases/113-guest-assignment-foundation/113-CONTEXT.md` — prior phase; `is_guest` flag on `RaceLineup` (D-01), guest cascade-delete of `RaceResult` + re-aggregation (D-11), `(race_id, driver_id)` uniqueness.

### Scoring & ranking code (primary change surface)
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` — `calculateRankingForPhase`, `aggregateAcrossPhases`, `calculateAlltimeRanking`, the `resolveTeamFromLineup` / `attributeTeamFromRegularOrLineup` helpers, and the inner `DriverRanking` accumulator (`addResult`, `racesCount`, `averagePoints`, `bestPosition`). The unify target for D-01..D-04.
- `src/main/java/org/ctc/domain/service/ScoringService.java` — `aggregateMatchScores`, `recomputeMatchScoresFromAllLegs`, `isDriverInTeam` (RaceLineup source of truth + sub-team→parent). SCORE-01 / SCORE-03 verification surface.
- `src/main/java/org/ctc/domain/service/StandingsViewService.java` — admin standings consumer (`calculateRankingForPhase` per-phase, `calculateAlltimeRanking` for alltime).
- `src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java` — public site ranking (`aggregateAcrossPhases` season + per-phase, `calculateAlltimeRanking`).
- `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java` — **iterates `seasonDriverRepository.findBySeasonId(...)`** (the D-05 gap); per-season per-phase results via `findByDriverIdAndRaceMatchdaySeasonId`.
- `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` — `findByRaceIdAndDriverId`, `findByDriverIdAndRaceMatchdaySeasonId` (team resolution for guests).

### Test & seed
- `TestDataService` / `TestHelper.createFullSeasonFixture()` — shared fixture to extend (D-11).
- `DevDataSeeder` (`@Profile("dev")`) — demo seed for the guest example (D-12).

### Project conventions
- `CLAUDE.md` §"RaceLineup is Source of Truth", §"Score Aggregation on Result Save", §"No Fallback Calculations", §"Grep All Usages Before Refactor", §"Test naming / TDD", §"Isolate Test Data Completely", §"Build & Test Discipline".

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ScoringService.isDriverInTeam`** — the canonical RaceLineup-based team resolver (incl. sub-team→parent). The unified guest attribution helper should reuse/mirror its lineup-resolution logic.
- **`DriverRanking` inner accumulator** — already sums points and counts races per driver; D-06/D-07/D-08 are satisfied by its current `addResult` over all results. No change needed to the accumulator itself, only to team attribution.
- **`generatedDriverIds` dedup set** in `DriverProfilePageGenerator` — reuse when extending iteration to pure guests (D-05) so doppelrollen drivers aren't double-generated.

### Established Patterns
- **Live, read-time ranking computation** (`@Transactional(readOnly = true)`) — no persisted personal score; the basis for D-10's idempotency lock.
- **Match-score recompute from all legs** on each save (`aggregateMatchScores`) — the persisted-side idempotency mechanism.
- **Three divergent team-attribution helpers** today — the central tech-debt this phase unifies (D-03).

### Integration Points
- Guest `is_guest`/`RaceLineup` from Phase 113 → consumed here purely via the existing `RaceLineup` join; no new column needed for scoring.
- Match score (`Match.homeScore`/`awayScore`) is the only persisted scoring artifact touched.
- Site generation (`DriverRankingPageGenerator`, `DriverProfilePageGenerator`) re-runs the same services → fixes propagate to the public site for free.

</code_context>

<specifics>
## Specific Ideas

- The phase is explicitly framed as **verification + edge-case hardening + tests**, not a new scoring engine — the user confirmed the "live read-model, no new table" lock (D-10).
- Uniform, simple mental model preferred over per-view precision for team attribution (D-03): "a guest shows under their home team, a pure guest under whoever fielded them — everywhere."

</specifics>

<deferred>
## Deferred Ideas

- **Visual guest marking** across Scorecard / Provisional Scores / matchday graphics, admin race & matchday detail, driver-ranking (admin + public), public driver-profile — **Phase 115** (MARK-01..06); concrete visual treatment decided against a rendered reference via `playwright-cli`. Note: Phase 114 only guarantees the underlying *data/page existence* for pure guests (D-05); the *mark* itself is 115.

None outside the milestone — discussion stayed within v1.17 scope (Phase 115 is the explicit next phase of the same milestone, not scope creep).

</deferred>

---

*Phase: 114-Scoring & Personal Crediting*
*Context gathered: 2026-06-01*
