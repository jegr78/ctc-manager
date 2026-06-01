# Phase 115: Guest Marking & Visibility — Research

**Researched:** 2026-06-01
**Domain:** Thymeleaf SSR, CSS theming, graphic DTOs, DriverRankingService, DriverProfilePageGenerator
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01 (Treatment locked):** Icon + accent color, direction locked.
- **D-02 (Identical everywhere):** Same icon glyph and accent color across ALL surfaces. Accent held as central CSS variable mirrored into `admin.css` + graphic templates + site CSS.
- **D-03 (Glyph/color = planner discretion, constrained):** Exact glyph and color chosen by planner against rendered reference. Icon is PRIMARY meaning carrier. Color is reinforcement only. Color-blind-safe.
- **D-04 (Binary flag everywhere; fielding team only on profile):** Binary "was a guest" marker only. Fielding team shown ONLY on public driver-profile. Graphics and rankings carry no team name.
- **D-05 (Ranking-row semantics, MARK-05):** Marked as soon as the row contains ≥1 guest appearance. Covers pure guests AND dual-role. `hasGuestAppearance` boolean computed in `DriverRankingService`, not template SpEL.
- **D-06 (Audit-driven, comprehensive scope):** Researcher/planner audits ALL `*-render` templates. Goal: no graphic posts alongside others with unmarked guests.
- **D-07 (Shared marker mechanism):** One shared mechanism — shared Thymeleaf fragment + `isGuest` flag per-row in the graphic data model/DTO. Guest flag computed in graphic data services, never via template SpEL.
- **D-08 (Admin may be richer):** `race-detail.html` + `matchday-detail.html` get the same icon+accent marker. Admin MAY additionally surface the fielding team. No inline styles.
- **D-09 (No legend):** Graphics get no legend/caption.
- **D-10 (Inline sub-label):** On `driver-profile.html`, each guest race row gets icon + inline sub-label "as guest for <Team>". No new table column.
- **D-11 (Show actual sub-team name):** Actual sub-team name from `RaceLineup.team`, not parent. Display-only; points attribution stays parent-rollup.
- **D-12 (Central CSS variable):** Accent color `--guest: #f59e0b` (amber). Mirrored across `admin.css` + graphic render templates + site CSS.

### Claude's Discretion
- Exact icon glyph + concrete accent-color value against rendered reference (constraints in D-03/D-12).
- Shape of the shared marker fragment and exactly where each graphic data service computes the per-row `isGuest` flag.
- Whether one "is this row a guest" resolver is shared across graphics + admin + ranking, or computed per surface.
- Exact admin fielding-team presentation (tooltip vs. small column) (D-08).
- Where `hasGuestAppearance` lives on the `DriverRankingService` ranking row/accumulator.

### Deferred Ideas (OUT OF SCOPE)
None. Phase 115 is the final phase of milestone v1.17.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MARK-01 | Guest drivers are marked on the Scorecard (race scoring) graphic | `results-render.html` + `ResultsGraphicService.DriverResultRow` — add `homeIsGuest`/`awayIsGuest` booleans |
| MARK-02 | Guest drivers are marked on the Provisional Scores graphic | `provisional-scores-render.html` + `ProvisionalScoresGraphicService.ProvisionalRow` — add `isGuest` boolean |
| MARK-03 | Guest drivers are marked on the further matchday graphics (lineup) | `lineup-render.html` + `LineupGraphicService.DriverPairing` — add `homeIsGuest`/`awayIsGuest` booleans; matchday-results + match-results + overlay + power-rankings confirmed team-only (excluded) |
| MARK-04 | Guest assignments are marked in admin matchday/race detail | `race-detail.html` result rows: augment `driverTeamMap` with guest flag (new `Map<UUID, Boolean> guestDriverMap` from `RaceService`); `matchday-detail.html` lineup chips: `RaceLineup.guest` is directly accessible on the `lu` object (OSIV lazy) |
| MARK-05 | Season driver-ranking (admin + public) marks the guest appearance | `DriverRanking` accumulator: add `hasGuestAppearance` boolean; `addResult()` sets it when lineup resolves `isGuest=true`; both `StandingsViewService` and `DriverRankingPageGenerator` consume the same `List<DriverRanking>` |
| MARK-06 | Public driver-profile shows guest race as marked entry with fielding team | `DriverProfilePageGenerator`: introduce `DriverProfileRow` record (wraps `RaceResult` + `isGuest` boolean + `fieldingTeamName` String); replace raw `results` / `resultsByPhase` model variables |

</phase_requirements>

---

## Summary

Phase 115 is a pure presentation phase. All data (the `RaceLineup.guest` flag, rankings, profile pages) was introduced in Phases 113–114. The work here is to thread the per-row `isGuest` boolean and (on the profile) the `fieldingTeamName` from `RaceLineup` into each rendering surface's data model, then render the `★` glyph in amber `#f59e0b` using a shared Thymeleaf fragment.

Three groups of surfaces need changes:

**Graphic services (MARK-01/02/03):** `ResultsGraphicService`, `ProvisionalScoresGraphicService`, and `LineupGraphicService` each build per-row record types that are rendered inline by Playwright. Adding an `isGuest` boolean to `DriverResultRow`, `ProvisionalRow`, and `DriverPairing` — and populating it from `RaceLineupRepository.findByRaceIdAndDriverId` at row-build time — is sufficient. The nine other `*-render` templates are confirmed team-only (verified against source); they are excluded. The graphic templates use inline CSS (no `admin.css` load), so `--guest: #f59e0b` must be hardcoded inline.

**Admin detail (MARK-04):** `race-detail.html` iterates `race.results` directly (each `RaceResult` has no `isGuest` field). The `RaceService.getRaceDetailData` already calls `raceLineupRepository.findByRaceIdAndDriverId` once per result to build `driverTeamMap`. A parallel `Map<UUID, Boolean> guestDriverMap` can be built in the same loop. The `RaceService.RaceDetailData` record needs one new field. `matchday-detail.html` iterates `lineupsByTeam` whose values are `List<RaceLineup>` — `RaceLineup.guest` is directly accessible on each `lu` object via OSIV, requiring only a template change.

**Ranking + profile (MARK-05/06):** `DriverRankingService.DriverRanking` inner class needs a `hasGuestAppearance` boolean field. `addResult()` cannot set it alone (it only receives a `RaceResult`, not a `RaceLineup`). The flag must be set separately by the callers in `calculateRankingForPhase` / `aggregateAcrossPhases` / `calculateAlltimeRanking` after looking up the lineup for each result. `DriverProfilePageGenerator` currently passes raw `List<RaceResult>` to the template; it needs to be augmented with per-result `isGuest` + `fieldingTeamName` via a new `DriverProfileRow` record, built by joining each result against `raceLineupRepository.findByRaceIdAndDriverId`.

**Primary recommendation:** Introduce `isGuest` at the data-service layer (graphic services, `RaceService`, `DriverRankingService`, `DriverProfilePageGenerator`), expose it via DTOs/records, render with the shared fragment + CSS variable. No SpEL in templates. No new persistence.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `isGuest` resolution per race row | Service (data) | — | `RaceLineup.guest` is the Source of Truth; business logic belongs in service layer |
| `hasGuestAppearance` on ranking row | Service (`DriverRankingService`) | — | CLAUDE.md "Keep Templates Lean" — no SpEL accumulation in templates |
| `fieldingTeamName` on profile row | Sitegen (`DriverProfilePageGenerator`) | Repository | Reads `RaceLineup.team.shortName`; prepared at generation time |
| Guest marker rendering (HTML/CSS) | Template + Thymeleaf fragment | `admin.css`, `style.css` | Fragment DRY; CSS holds the color token |
| Graphic inline CSS | Graphic render template `<style>` block | — | Templates do not load `admin.css` (file:// rendering) |

---

## Standard Stack

### Core (no new dependencies — presentation-only phase)

| Technology | Version | Purpose | Why Standard |
|------------|---------|---------|--------------|
| Thymeleaf | 3.x (Spring Boot 4.x managed) | Server-side template rendering + fragments | Established stack |
| Plain CSS | n/a | Guest marker styling via CSS variable | No frontend build tool in this project |
| Unicode glyph `&#x2605;` | n/a | `★` BLACK STAR — language-neutral guest icon | Established pattern in existing templates (see `&#x25CF;` in `season-detail.html`) |

**No new packages.** This phase adds no new Maven dependencies. The `--guest: #f59e0b` CSS variable is the only new artifact outside Java/HTML.

---

## Package Legitimacy Audit

> Not applicable — no external packages are installed in this phase.

---

## Architecture Patterns

### System Architecture Diagram

```
RaceLineup.guest (DB column, Phase 113 Source of Truth)
    │
    ├── Graphic services (JaCoCo-excluded)
    │     ├── ResultsGraphicService.buildResultRows()
    │     │     └── DriverResultRow + homeIsGuest / awayIsGuest
    │     ├── ProvisionalScoresGraphicService.buildContext()
    │     │     └── ProvisionalRow + isGuest
    │     └── LineupGraphicService.buildPairings()
    │           └── DriverPairing + homeIsGuest / awayIsGuest
    │
    ├── Admin detail
    │     ├── RaceService.getRaceDetailData()
    │     │     └── guestDriverMap (Map<UUID,Boolean>) → RaceDetailData record
    │     │           → race-detail.html result rows
    │     └── matchday-detail.html (OSIV: lu.guest directly)
    │
    ├── DriverRankingService (JaCoCo-covered)
    │     ├── DriverRanking.hasGuestAppearance (new field)
    │     ├── setGuestAppearance() called by calculateRankingForPhase /
    │     │   aggregateAcrossPhases / calculateAlltimeRanking
    │     └── Consumed by:
    │           ├── StandingsViewService → standings.html (admin)
    │           └── DriverRankingPageGenerator → driver-ranking.html,
    │               alltime-driver-ranking.html (public site)
    │
    └── DriverProfilePageGenerator (JaCoCo-covered)
          ├── DriverProfileRow record (new): RaceResult + isGuest + fieldingTeamName
          ├── Built from raceLineupRepository.findByRaceIdAndDriverId per result
          └── Passed as `profileRows` / `profileRowsByPhase` to driver-profile.html
```

```
CSS token flow:
  admin.css  :root { --guest: #f59e0b }     → admin templates (standings.html, race-detail.html, matchday-detail.html)
  style.css  :root { --guest: #f59e0b }     → site templates (driver-ranking.html, alltime-driver-ranking.html, driver-profile.html)
  Inline <style>  .guest-marker { color: #f59e0b }  → graphic render templates (no CSS file load)

Thymeleaf fragment:
  templates/admin/fragments/guest-marker.html
      th:fragment="guestMarker(isGuest)"
      → th:replace used in all three graphic render templates
      → th:replace used in race-detail.html, matchday-detail.html
      → th:replace used in site templates (or fragment replicated in site/fragments/)
```

### Recommended Project Structure (changes only)

```
src/main/
├── java/org/ctc/
│   ├── admin/service/
│   │   ├── ResultsGraphicService.java        # DriverResultRow: + homeIsGuest, awayIsGuest
│   │   ├── ProvisionalScoresGraphicService.java  # ProvisionalRow: + isGuest
│   │   └── LineupGraphicService.java         # DriverPairing: + homeIsGuest, awayIsGuest
│   ├── domain/service/
│   │   ├── DriverRankingService.java         # DriverRanking: + hasGuestAppearance
│   │   └── RaceService.java                  # RaceDetailData: + guestDriverMap
│   └── sitegen/
│       └── DriverProfilePageGenerator.java   # new DriverProfileRow record
└── resources/
    ├── static/admin/css/admin.css             # APPEND --guest + .guest-marker + .guest-label
    ├── static/site/css/style.css              # APPEND --guest + .guest-marker + .guest-label
    └── templates/
        ├── admin/
        │   ├── fragments/
        │   │   └── guest-marker.html          # NEW shared fragment
        │   ├── results-render.html            # INLINE .guest-marker CSS + fragment use
        │   ├── provisional-scores-render.html # INLINE .guest-marker CSS + fragment use
        │   ├── lineup-render.html             # INLINE .guest-marker CSS + fragment use
        │   ├── race-detail.html               # guestDriverMap lookup + fragment use
        │   └── matchday-detail.html           # lu.guest + fragment use
        └── site/
            ├── driver-ranking.html            # r.hasGuestAppearance + fragment use
            ├── alltime-driver-ranking.html    # r.hasGuestAppearance + fragment use
            └── driver-profile.html            # row.guest + row.fieldingTeamName + fragment use
```

---

## Graphic Template Audit (D-06) — VERIFIED

[VERIFIED: codebase source] — all templates inspected directly.

| Template | Renders Individual Driver Names? | Service | Mark? |
|----------|----------------------------------|---------|-------|
| `results-render.html` | YES — `row.homeDriver` / `row.awayDriver` per `DriverResultRow` | `ResultsGraphicService` | **YES (MARK-01)** |
| `provisional-scores-render.html` | YES — `row.driverName` per `ProvisionalRow` in `homeRows`/`awayRows` | `ProvisionalScoresGraphicService` | **YES (MARK-02)** |
| `lineup-render.html` | YES — `p.homeDriver` / `p.awayDriver` per `DriverPairing` | `LineupGraphicService` | **YES (MARK-03)** |
| `matchday-results-render.html` | NO — `m.homeTeamName` / `m.awayTeamName` only | `MatchdayResultsGraphicService` | **EXCLUDED** |
| `match-results-render.html` | NO — team-level header + season name | `MatchResultsGraphicService` | **EXCLUDED** |
| `overlay-render.html` | NO — team names only | `OverlayGraphicService` | **EXCLUDED** |
| `power-rankings-render.html` | NO — team names only | `PowerRankingsGraphicService` | **EXCLUDED** |
| `standings-render.html` | NO — team names only | `StandingsGraphicService` | **EXCLUDED** |
| `matchday-pairings-render.html` | NO — team-level pairings | — | **EXCLUDED** |
| `matchday-overview-render.html` | NO — team names only | — | **EXCLUDED** |
| `matchday-schedule-render.html` | NO — team names only | — | **EXCLUDED** |
| `playoff-round-overview-render.html` | NO — bracket/team only | — | **EXCLUDED** |
| `playoff-round-results-render.html` | NO — team-level | — | **EXCLUDED** |
| `playoff-round-schedule-render.html` | NO — team-level | — | **EXCLUDED** |

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Guest flag resolution | Recomputing from `SeasonDriver` absence | `RaceLineup.guest` boolean directly | CLAUDE.md "RaceLineup is Source of Truth" |
| CSS color management | Inline styles on elements | CSS variable `--guest` + class `.guest-marker` | CLAUDE.md "No Inline Styles on Buttons" |
| Repeated `★` glyph markup | Copy-pasted `<span class="guest-marker">&#x2605;</span>` in every template | Thymeleaf fragment `admin/fragments/guest-marker.html` | D-07 — single source for consistency |
| Sub-team name look-up in template | SpEL navigation `${raceLineupRepo.findBy...}` in template | Pre-computed `fieldingTeamName` on `DriverProfileRow` DTO | CLAUDE.md "Keep Thymeleaf Templates Lean" |

---

## Key Technical Findings

### 1. ResultsGraphicService — Scorecard (MARK-01)

[VERIFIED: codebase source]

`buildResultRows(Race race)` builds `DriverResultRow` records from `Race.getResults()`. It already calls `scoringService.isDriverInTeam(r, raceId, homeTeamId)` to split home vs. away results. **Critical:** `isDriverInTeam` calls `raceLineupRepository.findByRaceIdAndDriverId` internally, so the lineup is already fetched per result. To avoid a second lookup, the simplest approach is to call `raceLineupRepository.findByRaceIdAndDriverId(race.getId(), result.getDriver().getId())` once per result in `buildResultRows` and use the result for both team side (already done via `scoringService`) and guest flag.

`DriverResultRow` is a Java record: `record DriverResultRow(String homeDriver, String homeNickname, int homePoints, int awayPoints, String awayDriver, String awayNickname)`. Adding `boolean homeIsGuest, boolean awayIsGuest` requires updating the record definition and the one construction call in `buildResultRows`.

The `results-render.html` template renders:
- Driver name: `<span class="driver-name" th:text="${row.homeDriver}">` and `th:text="${row.awayDriver}"`
- The marker should be inserted **before** the `.driver-name` span using `th:replace="~{admin/fragments/guest-marker :: guestMarker(${row.homeIsGuest})}"`.

`ResultsGraphicService` also supports a custom template loaded from the upload directory. The inline `<style>` block is in the default template only; a custom template uploaded by the admin would not automatically gain the `.guest-marker` style. The default template must include the inline CSS; custom templates are user-owned and are not in scope for automatic migration.

### 2. ProvisionalScoresGraphicService — Provisional Scores (MARK-02)

[VERIFIED: codebase source]

`buildContext(Race race, int raceIndex, Team homeTeam, Team awayTeam)` builds `ProvisionalRow` records. The service holds a `ScoringService` dependency but no `RaceLineupRepository`. To resolve the guest flag, one of:
- Inject `RaceLineupRepository` into `ProvisionalScoresGraphicService` (or)
- Pass a `Map<UUID, Boolean> guestMap` pre-computed by the caller.

The first approach is simpler and consistent with `ResultsGraphicService`. The `RaceLineupRepository` is already a Spring bean; it can be injected.

`ProvisionalRow` is a record: `record ProvisionalRow(String driverName, int position, int qualiPosition, boolean fastestLap, int ptsRace, int ptsQuali, int ptsFl, int total)`. Adding `boolean isGuest` changes the record and the `toRow()` call. The empty-row factory `emptyRow()` returns `isGuest=false`.

The template iterates `homeRows` and `awayRows` in `<td class="col-driver">`. The marker goes before `th:text="${row.driverName}"`.

### 3. LineupGraphicService — Lineup (MARK-03)

[VERIFIED: codebase source]

`buildPairings(List<RaceLineup> lineups, Team homeTeam, Team awayTeam)` receives the full `List<RaceLineup>` already (fetched by `raceLineupRepository.findByRaceId(race.getId())`). **The `RaceLineup.guest` boolean is directly available on each `RaceLineup` object** — no additional query needed.

`DriverPairing` is a record: `record DriverPairing(String homeDriver, String homeNickname, String awayDriver, String awayNickname)`. Adding `boolean homeIsGuest, boolean awayIsGuest` is straightforward. The pairing builds entries from `homeEntries` / `awayEntries` (filtered `RaceLineup.getDriver()` lists) — the filtering already separates home vs. away. Map from index `i` to the originating `RaceLineup` to extract the guest flag.

The template (`lineup-render.html`) renders `p.homeDriver` in `.driver-info.home` and `p.awayDriver` in `.driver-info.away`.

### 4. RaceService.getRaceDetailData — Admin race-detail (MARK-04)

[VERIFIED: codebase source]

`RaceService.getRaceDetailData(UUID raceId)` already builds `Map<UUID, String> driverTeamMap` per result by calling `raceLineupRepository.findByRaceIdAndDriverId(race.getId(), result.getDriver().getId())` for each result. The returned `Optional<RaceLineup>` already contains the `guest` flag.

**Minimal change:** In the same loop, build a parallel `Map<UUID, Boolean> guestDriverMap` keyed by `result.getDriver().getId()`, setting the value to `rl.isGuest()` when the lineup is present.

`RaceDetailData` is a large record with 20+ fields. The new field `Map<UUID, Boolean> guestDriverMap` is added at the end (or alongside `driverTeamMap`). All constructor calls in `RaceService` must be updated; there is only one (`return new RaceDetailData(race, homeTotal, awayTotal, driverTeamMap, ...)` at line 142).

The `race-detail.html` template iterates `race.results` directly. The model has `driverTeamMap` as `Map<UUID, String>`. The new `guestDriverMap` (passed as a model attribute by `RaceController`) lets the template do: `th:replace="~{admin/fragments/guest-marker :: guestMarker(${guestDriverMap != null ? guestDriverMap[result.driver.id] : false})}"`.

### 5. MatchdayService / matchday-detail.html — Admin matchday-detail (MARK-04)

[VERIFIED: codebase source]

`MatchdayService.getMatchdayDetail` builds `lineupsByTeam` as `Map<String, List<RaceLineup>>` using `raceLineupRepository.findByRaceMatchdayId(matchdayId)` (which has `@EntityGraph(attributePaths = {"driver", "team", "race"})`). The full `RaceLineup` object (including `guest` field) is in the map values.

`matchday-detail.html` iterates `${lineupsByTeam}` (outer `th:each="entry : ${lineupsByTeam}"`) and then `<span th:each="lu : ${entry.value}" class="chip"><span th:text="${lu.driver.psnId}">`. The `lu` variable is a `RaceLineup` — `lu.guest` is directly accessible via OSIV (Hibernate session open through request). **No service change needed for matchday-detail**: just add the fragment call `th:replace="~{admin/fragments/guest-marker :: guestMarker(${lu.guest})}"` inside the chip.

For admin D-08 (optional richer display with sub-team name), the `lu.team.shortName` is also available on the same `RaceLineup` object. Adding `<span class="guest-label" th:if="${lu.guest}" th:text="${lu.team.shortName}"></span>` after the fragment suffices.

### 6. DriverRankingService.DriverRanking — hasGuestAppearance (MARK-05)

[VERIFIED: codebase source]

`DriverRankingService.DriverRanking` is a public inner class (not a record) with mutable state. Current fields: `driver`, `team`, `totalPoints`, `totalRacePoints`, `totalQualiPoints`, `totalFlPoints`, `racesCount`, `bestPosition`, `raceResults`.

`addResult(RaceResult result)` is the existing mutation method — but it only receives a `RaceResult`, which has no `guest` field. The `RaceLineup` is the source of truth. Two options:

**Option A (preferred):** Add `public void markGuestAppearance() { this.hasGuestAppearance = true; }` and call it from `calculateRankingForPhase` / `aggregateAcrossPhases` / `calculateAlltimeRanking` after building each ranking entry, using the `RaceLineup` already fetched by `resolveAttributedTeam` (or via a second lookup if needed).

The `resolveAttributedTeam` method already calls `raceLineupRepository.findByRaceIdAndDriverId(raceId, driver.getId())` for guests (when `raceId != null`). For rostered drivers it calls `seasonDriverRepository.findBySeasonIdAndDriverId`. The guest flag only matters when `lineup.isGuest()` is true — meaning: when the resolved lineup has `guest=true`, call `ranking.markGuestAppearance()`.

For `calculateRankingForPhase`: in the per-result accumulation loop, after `rankingMap.computeIfAbsent(driverId, ...)`, check the lineup for each result and call `markGuestAppearance()` if `isGuest`. The lineup is already needed by `resolveAttributedTeam`; the call can be combined or the `resolveAttributedTeam` can return an enriched result.

For `calculateAlltimeRanking`: the private helper method already resolves the lineup for pure guests (lines ~148-150). For dual-role guests a second lookup by `raceId` can resolve the flag.

**Important:** The `hasGuestAppearance` setter must not break the `DriverRanking` immutability contract; the field is a mutable flag, consistent with `bestPosition` and `racesCount` which are also mutated post-construction.

The `DriverRanking` class must add a public getter `public boolean isHasGuestAppearance()` (Lombok-consistent — though `DriverRanking` is not annotated with Lombok; it uses manual getters. Add `public boolean isHasGuestAppearance()` to match the Java boolean convention).

### 7. DriverRankingPageGenerator + StandingsViewService — Ranking consumers (MARK-05)

[VERIFIED: codebase source]

Both consumers receive `List<DriverRanking>` from the same service:
- `StandingsViewService.buildView()` calls `driverRankingService.calculateRankingForPhase(...)` or `calculateAlltimeRanking()` and puts the list into the `StandingsView` record's `driverRanking` field.
- `DriverRankingPageGenerator.generate()` calls `driverRankingService.calculateRankingForPhase(p.getId())` and `aggregateAcrossPhases(phaseIds, season.getId())`.
- `SiteGeneratorService.generateAlltimeDriverRanking()` calls `driverRankingService.calculateAlltimeRanking(seasonIds)`.

In all three paths the template accesses `r.hasGuestAppearance` (or `ranking.hasGuestAppearance`). No service-level change to the consumers — only the `DriverRanking` accumulator changes.

**Admin standings template** (`standings.html`) renders driver names at line 130: `<td><strong th:text="${ranking.driver.psnId}">`. The marker is added before the psnId text.

**Site driver-ranking.html** renders at line 30: `<a class="entity-link" th:href="..." th:text="${r.driver.psnId}">`. The marker is added before this anchor.

**Site alltime-driver-ranking.html** renders at line 22: `<a class="entity-link" th:href="..." th:text="${r.driver.psnId}">`. Same pattern. This template already has access to `r` (a `DriverRanking`).

### 8. DriverProfilePageGenerator — Profile rows (MARK-06)

[VERIFIED: codebase source]

`writeDriverProfile(...)` currently passes:
- `"results"` → `List<RaceResult>` (filtered by season)
- `"resultsByPhase"` → `LinkedHashMap<PhaseType, List<RaceResult>>`

The template `driver-profile.html` iterates `${results}` (flat) and `${entry.value}` (phase-grouped) with `th:each="result : ${...}"`. Neither `RaceResult` nor the template have guest information.

**Required change:** Introduce `DriverProfileRow` record:
```java
public record DriverProfileRow(RaceResult result, boolean guest, String fieldingTeamName) {}
```

Build `List<DriverProfileRow>` instead of `List<RaceResult>`:
- For each `result` in `results`, call `raceLineupRepository.findByRaceIdAndDriverId(result.getRace().getId(), driver.getId())`.
- If present and `rl.isGuest()`: `guest=true`, `fieldingTeamName=rl.getTeam().getShortName()` (actual sub-team, per D-11).
- If not present or not guest: `guest=false`, `fieldingTeamName=null`.

**Efficiency:** `DriverProfilePageGenerator` already injects `RaceLineupRepository`. The `seasonLineups` fetched in the guest-pass (`raceLineupRepository.findByRaceMatchdaySeasonId(season.getId())`) contains all season lineups. A `Map<UUID, RaceLineup> lineupByRaceAndDriver` can be pre-built from this collection to avoid N per-result queries.

The template then accesses `row.result.position`, `row.result.pointsTotal` etc., and `row.guest` / `row.fieldingTeamName` for the marker + sub-label. The model variables must be renamed to `profileRows` / `profileRowsByPhase` (or the template rewritten to use `row.result.*` — both are breaking changes to the template, which is expected since the phase owns it).

**Key challenge:** The `resultsByPhase` grouping uses `List<RaceResult>`. This must become `Map<PhaseType, List<DriverProfileRow>>`.

### 9. CSS Implementation

[VERIFIED: codebase source]

`admin.css` is at `src/main/resources/static/admin/css/admin.css` (2114 lines). The `:root` block starts at line 12. The `.badge` rules are at lines 377–412. Append the new variable to `:root` and the new classes after `.badge` rules (or at the end).

`style.css` is at `src/main/resources/static/site/css/style.css` (1089 lines). The `:root` block is at line 15. Same append pattern.

**CLAUDE.md subagent rule:** "APPEND your new content after existing content — do NOT rewrite or replace existing sections."

New CSS additions for `admin.css` and `style.css`:
```css
/* append to :root */
--guest: #f59e0b;

/* append after .badge rules */
.guest-marker {
    color: var(--guest);
    font-size: 12px;
    margin-right: 4px;
    vertical-align: middle;
    line-height: 1;
}

.guest-label {
    font-size: 12px;
    font-weight: 400;
    color: var(--text-dim);
    margin-left: 4px;
}
```

For graphic templates (inline `<style>` block — no `admin.css` load):
```css
.guest-marker { color: #f59e0b; font-size: 0.85em; margin-right: 4px; vertical-align: middle; }
```

### 10. Shared Thymeleaf Fragment

[VERIFIED: codebase source — pattern confirmed from `templates/site/fragments/match-card.html`]

The `templates/admin/fragments/` directory does not yet exist. It must be created. The fragment:

```html
<!-- templates/admin/fragments/guest-marker.html -->
<th:block xmlns:th="http://www.thymeleaf.org" th:fragment="guestMarker(isGuest)">
    <span class="guest-marker" th:if="${isGuest}" aria-label="Guest driver">&#x2605;</span>
</th:block>
```

Usage in admin/graphic templates:
```html
<th:replace="~{admin/fragments/guest-marker :: guestMarker(${row.homeIsGuest})}"></th:replace>
```

Usage in site templates requires the fragment to be accessible. Since both admin and site templates are resolved by the same `TemplateEngine`, the fragment path `admin/fragments/guest-marker` works from site templates too. This is confirmed by Thymeleaf's classpath-based template resolver — all templates under `templates/` are reachable by their relative path.

**Alternative for site:** Create a mirror at `templates/site/fragments/guest-marker.html` for clarity. Either works technically.

### 11. RaceLineupRepository — Queries Available

[VERIFIED: codebase source]

For guest resolution, the relevant existing methods are:
- `findByRaceIdAndDriverId(UUID raceId, UUID driverId)` → `Optional<RaceLineup>` — no `@EntityGraph` (lazy driver/team); but the `guest` boolean field is on the entity itself (not lazy-loaded relation) — accessible without join. However `fieldingTeamName` requires `team.shortName` (lazy). OSIV covers template rendering, but service-layer code outside a transaction needs either a join or the field is already loaded.
- `findByRaceId(UUID raceId)` → `List<RaceLineup>` with `@EntityGraph(attributePaths = {"driver", "team"})` — team eager-loaded.
- `findByRaceMatchdaySeasonId(UUID seasonId)` → `List<RaceLineup>` with `@EntityGraph({"driver", "team"})`.

For `DriverProfilePageGenerator`, building a `Map<UUID /* race+driver key */, RaceLineup>` from the existing `seasonLineups` fetch (which already uses the season-level eager-loaded query) avoids N+1. Key: `raceId + driverId` composite (a `String raceId + ":" + driverId` or a custom record).

For graphic services and `RaceService`, the per-result `findByRaceIdAndDriverId` call is already made (in `ScoringService.isDriverInTeam` and in `RaceService.getRaceDetailData`). The graphic services need access to the lineup. Since `ResultsGraphicService` and `ProvisionalScoresGraphicService` don't currently hold a `RaceLineupRepository`, they need injection.

`LineupGraphicService` already has `raceLineupRepository` injected and calls `findByRaceId(race.getId())` — the lineup is available as the input to `buildPairings`.

---

## Common Pitfalls

### Pitfall 1: Graphic Template Custom Override
**What goes wrong:** `ResultsGraphicService` and `ProvisionalScoresGraphicService` support custom templates loaded from `uploadDir`. The default template's inline `<style>` block will have the `.guest-marker` class, but a previously saved custom template will not.
**Why it happens:** Custom templates are user-uploaded strings and are not updated by a code deploy.
**How to avoid:** Document in the plan that custom templates are out of scope; the default template is the only one modified. Admin users with custom templates must manually add the CSS if they want the marker in their custom graphic.
**Warning signs:** Guest ★ appears in dev/test but not in production if the admin has a custom template saved.

### Pitfall 2: Record Expansion Breaks All Callers
**What goes wrong:** Adding a field to a Java record (`DriverResultRow`, `ProvisionalRow`, `DriverPairing`, `RaceDetailData`) causes compilation failures at every construction site and every destructuring use.
**Why it happens:** Java records require all fields in the canonical constructor.
**How to avoid:** Grep all construction and usage of each record before modifying. `RaceDetailData` has a long parameter list (20+ fields) with exactly one construction call in `RaceService`. `DriverResultRow` and `ProvisionalRow` are each constructed in one place (their service's build method). `DriverPairing` is constructed in `LineupGraphicService.buildPairings`. One test each. Use `grep -rn "new DriverResultRow\|new ProvisionalRow\|new DriverPairing\|new RaceDetailData"` before editing.
**Warning signs:** `mvn clean test-compile` fails immediately.

### Pitfall 3: N+1 in DriverProfilePageGenerator
**What goes wrong:** Calling `raceLineupRepository.findByRaceIdAndDriverId(...)` inside a loop over `results` generates N queries per driver profile.
**Why it happens:** `results` can have 10–20 entries per driver.
**How to avoid:** Pre-build a lookup map from `seasonLineups` (already fetched). Map key: `UUID raceId + UUID driverId` composite. Single pass.
**Warning signs:** Slow site generation in production; many identical SQL SELECT statements in debug log.

### Pitfall 4: DriverRanking.hasGuestAppearance Not Set for Dual-Role Drivers
**What goes wrong:** `hasGuestAppearance` is only set for pure guests (those resolved via `RaceLineup`), not for rostered drivers (those resolved via `SeasonDriver`) who also guested in the same season.
**Why it happens:** `resolveAttributedTeam` returns early when `SeasonDriver` is found, before checking `RaceLineup.guest`.
**How to avoid:** Set `hasGuestAppearance` based on whether the *specific result's* lineup has `guest=true`, NOT based on whether the driver is a pure guest. The flag should be set per-result in the accumulation loop, by looking up the lineup for that result's `race.getId()`.
**Warning signs:** Pure guests are marked; dual-role (own-team + guest) drivers are not — but D-05 says both must be marked.

### Pitfall 5: Thymeleaf Fragment in Graphic Templates (String Template Mode)
**What goes wrong:** Graphic templates loaded from the file system as custom uploads are processed via `SpringTemplateEngine` with `StringTemplateResolver`. String template mode does NOT resolve `th:replace` classpath fragments.
**Why it happens:** The `processStringTemplate` method creates a new `SpringTemplateEngine` with only a `StringTemplateResolver`, which cannot resolve classpath-relative paths.
**How to avoid:** Inline the `<span class="guest-marker">` markup directly in the graphic render templates rather than using `th:replace`. The shared fragment is for admin/site templates (processed by the classpath resolver); graphic templates (both default and custom) use the inline pattern. The default graphic templates are classpath-resolved via `templateEngine.process("admin/results-render", ctx)` (not string mode) — so `th:replace` would work in the default path. Custom uploaded templates use string mode and cannot use fragments. **Use inline markup in graphic templates** (not the fragment) to avoid the custom-template breakage risk.
**Warning signs:** `Exception processing template` error on graphic generation when `th:replace` path is unresolvable in string mode.

### Pitfall 6: CSS Variable Not Available in Graphic Templates
**What goes wrong:** Graphic templates have inline `<style>` that doesn't include `--guest` in a `:root` block.
**Why it happens:** Graphics don't load `admin.css`; they render as standalone HTML files via `file://` in Playwright.
**How to avoid:** Hardcode `#f59e0b` directly in the inline `.guest-marker { color: #f59e0b; }` rule. No CSS variable. This is already the pattern for other graphic colors (e.g., `#f5c542` for gold accents hardcoded in every template).
**Warning signs:** Marker glyph renders in black/default browser color instead of amber.

---

## Code Examples

### DriverResultRow with guest flags (MARK-01)
```java
// ResultsGraphicService.java
public record DriverResultRow(String homeDriver, String homeNickname, int homePoints,
                              int awayPoints, String awayDriver, String awayNickname,
                              boolean homeIsGuest, boolean awayIsGuest) {
}
```

### ProvisionalRow with guest flag (MARK-02)
```java
// ProvisionalScoresGraphicService.java
public record ProvisionalRow(String driverName, int position, int qualiPosition, boolean fastestLap,
                              int ptsRace, int ptsQuali, int ptsFl, int total, boolean isGuest) {
}
```

### DriverPairing with guest flags (MARK-03)
```java
// LineupGraphicService.java
public record DriverPairing(String homeDriver, String homeNickname, String awayDriver, String awayNickname,
                            boolean homeIsGuest, boolean awayIsGuest) {
}
```

### DriverRanking.hasGuestAppearance (MARK-05)
```java
// DriverRankingService.DriverRanking inner class
private boolean hasGuestAppearance;

public void markGuestAppearance() {
    this.hasGuestAppearance = true;
}

public boolean isHasGuestAppearance() {
    return hasGuestAppearance;
}
```

### DriverProfileRow (MARK-06)
```java
// DriverProfilePageGenerator.java (new inner record or top-level)
public record DriverProfileRow(RaceResult result, boolean guest, String fieldingTeamName) {}
```

### Guest marker fragment
```html
<!-- templates/admin/fragments/guest-marker.html -->
<th:block xmlns:th="http://www.thymeleaf.org" th:fragment="guestMarker(isGuest)">
    <span class="guest-marker" th:if="${isGuest}" aria-label="Guest driver">&#x2605;</span>
</th:block>
```

### Inline guest marker for graphic templates (no fragment; avoids string-template pitfall)
```html
<!-- Inside results-render.html, before .driver-name span -->
<span class="guest-marker" th:if="${row.homeIsGuest}" aria-label="Guest driver">&#x2605;</span>
```

### Admin matchday-detail.html chip (MARK-04 — direct OSIV access)
```html
<span th:each="lu : ${entry.value}" class="chip">
    <span class="guest-marker" th:if="${lu.guest}" aria-label="Guest driver">&#x2605;</span>
    <span th:text="${lu.driver.psnId}"></span>
    <span class="guest-label" th:if="${lu.guest}" th:text="${lu.team.shortName}"></span>
</span>
```

### Driver-profile sub-label (MARK-06)
```html
<!-- driver-profile.html — per-row, using DriverProfileRow -->
<span class="guest-marker" th:if="${row.guest}" aria-label="Guest driver">&#x2605;</span>
<span th:text="${row.result.race.matchday.label}"></span>
<!-- ... other columns ... -->
<span class="guest-label" th:if="${row.guest}">
    as guest for <span th:text="${row.fieldingTeamName}"></span>
</span>
```

---

## Runtime State Inventory

> This is a greenfield presentation phase — no rename/refactor/migration.

Not applicable. No stored data, live service configs, OS-registered state, secrets, or build artifacts reference a string that is being renamed. The `is_guest` column (Phase 113) and all existing data remain unchanged. This section is explicitly omitted (greenfield phase).

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Custom-uploaded graphic templates (saved in `uploadDir`) do NOT support `th:replace` classpath fragments when processed via string mode | Common Pitfalls #5 | If string template mode did support classpath fragments, the shared fragment could also be used in graphic templates (minor — inline markup is always safe) |
| A2 | The `admin/fragments/` classpath path is reachable from site templates under the same `TemplateEngine` | Key Technical Findings #10 | If path is wrong, site templates would need their own fragment copy in `site/fragments/` |

**All other key claims verified against codebase source.**

---

## Open Questions

1. **Fragment vs. inline for graphic templates**
   - What we know: String template mode (used by custom uploaded templates) cannot resolve classpath fragments. Default templates use classpath resolver and CAN use `th:replace`.
   - What's unclear: Should the default graphic templates use `th:replace` (fragment-based, DRY) or inline markup (simpler, robust for custom-template users)?
   - Recommendation: Use **inline markup** in graphic render templates to be consistent regardless of whether the operator has a custom template. The admin/site templates use the shared fragment normally.

2. **DriverRanking.hasGuestAppearance lookup strategy for `calculateAlltimeRanking`**
   - What we know: The alltime helper already looks up `RaceLineup` for pure guests (line 148-150). For rostered drivers, no lineup lookup happens.
   - What's unclear: Should the alltime ranking also mark `hasGuestAppearance` for dual-role drivers whose alltime row aggregates both home and guest races?
   - Recommendation: Yes — D-05 says "≥1 guest appearance in the row" regardless of whether the driver is rostered. Requires a per-result lineup lookup in the alltime accumulation loop; can use the existing `guestRaceByDriver` map structure as a template.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Temurin) | Build | ✓ (per CLAUDE.md) | 25 | — |
| Maven wrapper (`./mvnw`) | Build/Test | ✓ | per pom | — |
| Playwright Chromium | Graphic visual verification | ✓ (per CLAUDE.md install command) | managed by Playwright | `mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"` |
| H2 (dev profile) | Unit/IT tests | ✓ | in-memory | — |
| `./scripts/app.sh` | Dev server for playwright-cli | ✓ (per MEMORY.md) | — | — |

**No missing dependencies.**

---

## Validation Architecture

Nyquist validation is enabled (`workflow.nyquist_validation: true`).

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring Boot Test + AssertJ |
| Config file | `pom.xml` (Surefire + Failsafe, JaCoCo) |
| Quick run command | `./mvnw clean test-compile` (compile gate) |
| Full suite command | `./mvnw clean verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| MARK-01 | `ResultsGraphicService.buildResultRows()` sets `homeIsGuest`/`awayIsGuest` correctly from `RaceLineup.guest` | unit | `./mvnw clean test -Dtest=ResultsGraphicServiceTest` | ✅ (extend existing) |
| MARK-02 | `ProvisionalScoresGraphicService.buildContext()` sets `isGuest` on `ProvisionalRow` correctly | unit | `./mvnw clean test -Dtest=ProvisionalScoresGraphicServiceTest` | ✅ (extend existing) |
| MARK-03 | `LineupGraphicService.buildPairings()` sets `homeIsGuest`/`awayIsGuest` from `RaceLineup.guest` | unit | `./mvnw clean test -Dtest=LineupGraphicServiceTest` | ✅ (extend existing) |
| MARK-04 | `RaceService.getRaceDetailData()` returns `guestDriverMap` with correct boolean per driver | unit | `./mvnw clean test -Dtest=RaceServiceTest` | ✅ (extend existing) |
| MARK-05 | `DriverRankingService.calculateRankingForPhase()` sets `hasGuestAppearance=true` when driver has ≥1 guest race; false otherwise. Covers dual-role driver. | integration | `./mvnw clean verify -Dit.test=DriverRankingServiceGuestIT -DfailIfNoTests=false` | ✅ (extend existing) |
| MARK-05 | `DriverRankingService.calculateAlltimeRanking()` sets `hasGuestAppearance=true` for guest drivers | integration | `./mvnw clean verify -Dit.test=DriverRankingServiceGuestIT -DfailIfNoTests=false` | ✅ (extend existing) |
| MARK-06 | `DriverProfilePageGenerator` builds `profileRows` with `guest=true` and correct `fieldingTeamName` for guest races | integration | `./mvnw clean verify -Dit.test=DriverProfilePageGeneratorIT -DfailIfNoTests=false` | ✅ (extend existing) |
| MARK-06 | Generated `driver-profile.html` HTML contains `★` glyph for guest races and the "as guest for" sub-label text | integration | `./mvnw clean verify -Dit.test=DriverProfilePageGeneratorIT -DfailIfNoTests=false` | ✅ (extend existing) |
| MARK-04 (matchday) | `matchday-detail.html` template receives `RaceLineup` with `guest=true` and `lu.guest` accessible | manual/UAT (OSIV template rendering) | playwright-cli screenshot | — |
| Visual approval | `★` in amber precedes guest driver names on all marked surfaces | playwright-cli | `./scripts/app.sh start dev` + playwright-cli screenshots | — |

**Coverage note:** Graphic services (`ResultsGraphicService`, `ProvisionalScoresGraphicService`, `LineupGraphicService`) are JaCoCo-excluded per CLAUDE.md. Their unit tests cover the data-building methods (`buildResultRows`, `buildContext`, `buildPairings`) only (no Playwright execution in tests). The guest-flag logic lives in these methods → covered by extending the existing service unit tests with a guest-driver fixture. The templates themselves are visual-only (no new Java logic).

`DriverRankingService` and `DriverProfilePageGenerator` ARE in coverage scope. The existing `DriverRankingServiceGuestIT` and `DriverProfilePageGeneratorIT` use `TestDataService` fixtures with `Test_Guest_1` and `Test_DualRole_1` — both guest fixtures already exist; tests need only assert the new `hasGuestAppearance` flag and the new `profileRows` structure.

### Sampling Rate

- **Per task commit:** `./mvnw clean test -Dtest=<affected-test-class>` (targeted TDD loop)
- **Per wave merge:** `./mvnw clean verify` (no e2e — graphic-only phase can skip Playwright in IT waves)
- **Phase gate:** `./mvnw clean verify -Pe2e` before `/gsd-verify-work`

### Wave 0 Gaps

None — existing test infrastructure covers all phase requirements. No new test files or framework config needed. Wave 0 is "add guest assertions to existing tests", not "create new test infrastructure".

---

## Project Constraints (from CLAUDE.md)

Key directives that apply to this phase:

| Directive | Impact on Phase 115 |
|-----------|---------------------|
| "RaceLineup is Source of Truth" | Every guest flag resolved from `RaceLineup.guest`; never from `SeasonDriver` absence |
| "Keep Thymeleaf Templates Lean" | No SpEL guest-flag computation in templates; all flags prepared in service/generator |
| "No Inline Styles on Buttons" | CSS classes `.guest-marker` / `.guest-label` only; `style="..."` forbidden on any element |
| "Excluded from coverage" — graphic services | `ResultsGraphicService`, `ProvisionalScoresGraphicService`, `LineupGraphicService` not counted in JaCoCo. Guest-flag logic lives in their build methods → tested via unit tests without Playwright |
| "Visual Verification with playwright-cli" + `.screenshots/` | All screenshot paths must be under `.screenshots/`; never repo root |
| "Append, never rewrite CSS" | `admin.css` and `style.css` get new rules appended; no section replacement |
| Min 82% line coverage | New service logic (non-graphic) must be covered; existing tests extended |
| TDD (Red → Green → Refactor) | Unit tests for `buildResultRows` / `buildContext` / `buildPairings` / `getRaceDetailData` with guest fixture BEFORE implementation |
| "No Comment Pollution" | No phase/wave/plan markers in Java source; no Javadoc on obvious getters |
| Checkstyle (UnusedImports gate) | No unused imports in new/modified Java files |
| SpotBugs gate | `@SuppressFBWarnings` required only if `EI_EXPOSE_REP` triggers; `--guest-marker` fragment and record fields are primitives/Strings — no issue expected |

---

## Security Domain

> `security_enforcement` is not explicitly set to `false` in config — treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Auth handled at Spring Security layer; no new endpoints |
| V3 Session Management | No | No new session state |
| V4 Access Control | No | All changes are to existing admin-protected or public-site routes |
| V5 Input Validation | No | No new user input; all data reads from existing DB columns |
| V6 Cryptography | No | No new cryptographic operations |

### Known Threat Patterns for This Phase

| Pattern | STRIDE | Assessment |
|---------|--------|------------|
| XSS via driver name in `★ <driverName>` | Tampering | `th:text` with Thymeleaf always HTML-escapes; `&#x2605;` is a literal entity, not user input. No risk. |
| Template injection via custom graphic template | Tampering | Pre-existing risk in `processStringTemplate`; this phase does not change the custom-template upload mechanism. |
| Log injection via guest `fieldingTeamName` | Spoofing | `fieldingTeamName` is `rl.getTeam().getShortName()` — a DB value. If logged, apply `LogSanitizer.sanitize()` per MEMORY.md feedback. Unlikely to be logged in display-only code, but check any new `log.info` calls. |

---

## Sources

### Primary (HIGH confidence)
- Source code, `src/main/java/org/ctc/admin/service/ResultsGraphicService.java` — `buildResultRows`, `DriverResultRow` record
- Source code, `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java` — `buildContext`, `ProvisionalRow` record
- Source code, `src/main/java/org/ctc/admin/service/LineupGraphicService.java` — `buildPairings`, `DriverPairing` record
- Source code, `src/main/java/org/ctc/domain/service/DriverRankingService.java` — `DriverRanking` inner class, all three ranking paths
- Source code, `src/main/java/org/ctc/domain/service/RaceService.java` — `getRaceDetailData`, `RaceDetailData` record
- Source code, `src/main/java/org/ctc/domain/service/MatchdayService.java` — `getMatchdayDetail`, `lineupsByTeam`
- Source code, `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java` — `writeDriverProfile`, raw `results` model
- Source code, `src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java` — `writeRankingVariant`
- Source code, `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — `generateAlltimeDriverRanking`
- Source code, `src/main/java/org/ctc/domain/model/RaceLineup.java` — `guest` field confirmed
- Source code, `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` — all methods, @EntityGraph annotations
- Source code, `src/main/resources/templates/admin/*.html` — all render templates audited
- Source code, `src/main/resources/static/admin/css/admin.css` — `:root` at line 12, `.badge` at 377, existing `.guest-section/.guest-row` at 2084
- Source code, `src/main/resources/static/site/css/style.css` — `:root` at line 15
- Source code, `src/main/resources/templates/admin/standings.html` — admin ranking row rendering
- Source code, `src/main/resources/templates/site/driver-ranking.html` — public ranking row
- Source code, `src/main/resources/templates/site/alltime-driver-ranking.html` — alltime ranking row
- Source code, `src/main/resources/templates/site/driver-profile.html` — profile result rows
- Source code, `src/main/java/org/ctc/admin/TestDataService.java` — `Test_Guest_1` and `Test_DualRole_1` fixtures confirmed
- Test file, `src/test/java/org/ctc/domain/service/DriverRankingServiceGuestIT.java` — existing guest ranking IT
- Test file, `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorIT.java` — existing profile IT

### Secondary (MEDIUM confidence)
- `.planning/phases/115-guest-marking-visibility/115-CONTEXT.md` — locked decisions D-01 through D-12
- `.planning/phases/115-guest-marking-visibility/115-UI-SPEC.md` — glyph ★ U+2605, `--guest: #f59e0b`, fragment path, CSS class spec
- `.planning/REQUIREMENTS.md` — MARK-01..06

---

## Metadata

**Confidence breakdown:**
- Graphic service DTO changes: HIGH — record fields and build methods read directly
- DriverRankingService.hasGuestAppearance: HIGH — inner class structure fully read; D-05 constraint understood
- DriverProfilePageGenerator DriverProfileRow: HIGH — current flow read; N+1 risk identified and mitigated
- Admin template changes (race-detail, matchday-detail): HIGH — model attributes and template rendering confirmed
- CSS/fragment approach: HIGH — existing admin.css structure, fragment pattern, UI-SPEC confirmed

**Research date:** 2026-06-01
**Valid until:** 2026-07-01 (stable stack — Spring Boot 4.x / Thymeleaf)
