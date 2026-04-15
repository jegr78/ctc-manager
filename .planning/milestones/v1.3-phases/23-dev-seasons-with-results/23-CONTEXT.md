# Phase 23: Dev Seasons with Results - Context

**Gathered:** 2026-04-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Seed the dev profile with three fully played-out seasons — one per format (League, Swiss, Round Robin) — each with real matchdays, race results, and points calculated by the existing scoring system. Reuse and reconfigure existing seasons rather than creating new ones.

</domain>

<decisions>
## Implementation Decisions

### Season Format Assignment
- **D-01:** Reuse existing seasons: S1 2023 Group A + Group B → `ROUND_ROBIN`, S2 2024 → `SWISS`, S4 2026 → `LEAGUE` (active)
- **D-02:** S3a/S3b (2025) remain as older seasons without matchdays/results — no changes needed
- **D-03:** Round Robin uses 2 separate Season entities (Group A + Group B) as the 2 groups — matches existing data model

### Matchday Depth
- **D-04:** League (S4 2026): 5 matchdays, 1 race per match
- **D-05:** Swiss (S2 2024): 5 matchdays, 2 races per match
- **D-06:** Round Robin (S1 2023): 3 matchdays per group, 2 races per match
- **D-07:** 6 drivers per team per race (12 total per race) — uses full position spectrum 1-12

### Team Distribution
- **D-08:** Round Robin (S1 2023): Adjust season-teams to ~5-6 teams per group, mix of parent-teams and sub-teams. Current all-10-parents-per-group must be restructured.
- **D-09:** Swiss (S2 2024): Keep existing 10 parent-teams only, no sub-teams
- **D-10:** League (S4 2026): 14 teams — 7 standalone parents (ADR, ICL, SVT, NFR, EGP, HMS, PWR) + 7 sub-teams (VRX A, VRX B, SGM B, SGM S, TBR R, TBR B, TBR G). The 3 parent-teams with subs (VRX, SGM, TBR) do NOT participate as match teams.

### Result Generation
- **D-11:** Deterministic (hard-coded) positions per race — 100% reproducible, predictable standings
- **D-12:** Simple rotation of positions between matchdays — not monotonous but predictable. Fastest lap rotates between different drivers.
- **D-13:** Use `ScoringService.calculatePoints()` to compute points from positions (not hard-coded point values). Call `aggregateMatchScores()` after saving results. This validates DATA-07.

### Claude's Discretion
- Specific match pairings per matchday (who plays whom) — as long as all season-teams play
- Exact position assignments per race — as long as rotation creates varied standings
- Which 6 of 10 drivers per team play each race — can vary between matchdays
- RaceLineup creation for all races (must exist before results)
- SeasonDriver assignments for S1 and S2 (currently only S4 has them)
- RaceSettings values for seeded races

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Domain Model
- `src/main/java/org/ctc/domain/model/Season.java` — Season entity with format field, addTeam(), findSeasonTeam()
- `src/main/java/org/ctc/domain/model/SeasonFormat.java` — LEAGUE, SWISS, ROUND_ROBIN enum
- `src/main/java/org/ctc/domain/model/Matchday.java` — Matchday entity (season, name, orderIndex)
- `src/main/java/org/ctc/domain/model/Match.java` — Match entity (matchday, homeTeam, awayTeam, homeScore, awayScore)
- `src/main/java/org/ctc/domain/model/Race.java` — Race entity (matchday, match, settings, results)
- `src/main/java/org/ctc/domain/model/RaceResult.java` — RaceResult(race, driver, position, qualiPosition, fastestLap) — points calculated by ScoringService
- `src/main/java/org/ctc/domain/model/RaceLineup.java` — RaceLineup(race, driver, team) — must exist before results
- `src/main/java/org/ctc/domain/model/RaceSettings.java` — Race configuration (laps, tyres, weather etc.)
- `src/main/java/org/ctc/domain/model/RaceScoring.java` — Scoring preset: "20,17,14,12,10,8,7,6,5,4,3,2" race, "3,2,1" quali, 2 FL points
- `src/main/java/org/ctc/domain/model/MatchScoring.java` — Match scoring: 3-1-0 (win-draw-loss)

### Services
- `src/main/java/org/ctc/domain/service/ScoringService.java` — calculatePoints(result, scoring) + aggregateMatchScores(race)
- `src/main/java/org/ctc/admin/TestDataService.java` — Current seed service, must be extended with matchday/result seeding

### Project Specs
- `CLAUDE.md` — Project conventions, test data isolation rules, TDD approach
- `docs/superpowers/specs/2026-03-29-scoring-legs-design.md` — Scoring and legs design specification

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TestDataService` — Full seeding infrastructure, already has seasons/teams/drivers/lineups
- `ScoringService.calculatePoints()` — Computes race/quali/FL points from positions
- `ScoringService.aggregateMatchScores()` — Updates Match homeScore/awayScore from race results
- `createTestSettings()` — Already creates RaceSettings for test races
- `RaceResult(race, driver, position, qualiPosition, fastestLap)` constructor
- `RaceLineup(race, driver, team)` constructor
- `Match(matchday, homeTeam, awayTeam)` constructor

### Established Patterns
- Test data section in `seedRaceLineups()` shows the full pattern: create Match → Race → RaceSettings → RaceLineup
- Missing from test data: RaceResult creation + ScoringService calls
- Idempotent seeding via `seasonRepository.count() > 0` check
- ScoringService must be injected into TestDataService (currently not injected)

### Integration Points
- `TestDataService.seed()` — extend with new methods: `seedMatchdays()`, `seedResults()`
- Season format must be set: `season.setFormat(SeasonFormat.ROUND_ROBIN)` etc.
- After results: `scoringService.calculatePoints(results, raceScoring)` then `scoringService.aggregateMatchScores(race)`
- SeasonDriver records needed for S1/S2 to enable driver-team lookups in standings
- Standings pages (`/admin/seasons/{id}/standings`) will display the scored data

</code_context>

<specifics>
## Specific Ideas

- S1 2023 Round Robin groups need restructured season-teams: currently all 10 parents in both groups → reduce to ~5-6 per group with sub-team mix
- S4 2026 League needs season-teams adjusted: remove VRX, SGM, TBR as match participants, keep only their sub-teams + standalone parents (14 total)
- Rotation pattern: vary positions across matchdays so standings aren't monotonous (e.g., team that's 1st in MD1 might be 3rd in MD2)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 23-dev-seasons-with-results*
*Context gathered: 2026-04-09*
