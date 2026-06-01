# Phase 114: Scoring & Personal Crediting — Research

**Researched:** 2026-06-01
**Domain:** DriverRankingService unification, alltime/profile edge-case hardening, regression test suite
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 (Doppelrolle):** A driver on roster Team A who guests for Team B is attributed in their personal ranking row to their **home team** (SeasonDriver team for that season). Normal roster drivers are unaffected.
- **D-02 (Pure guest):** A driver with no SeasonDriver in the season is attributed to the **fielding team via RaceLineup** (sub-team→parent rollup per `isDriverInTeam`). Never null.
- **D-03 (Unified policy):** Home-first, fallback fielding — applied **uniformly** across all three ranking surfaces (`calculateRankingForPhase`, `aggregateAcrossPhases`, `calculateAlltimeRanking`). One shared attribution helper.
- **D-04 (Alltime):** Guest race counts toward the cross-season alltime ranking. Close `team = null` gap in `calculateAlltimeRanking` by falling back to RaceLineup-resolved team for pure guests.
- **D-05 (Profile page existence):** A pure guest (no SeasonDriver in the season) gets a public driver-profile page for that season. Extend `DriverProfilePageGenerator` iteration; reuse `generatedDriverIds` dedup set. Data/page-existence hook only — visual marking is Phase 115.
- **D-06 (Additive, single row):** One personal ranking row per driver per season — already the behavior of `computeIfAbsent + addResult`.
- **D-07 (Stat semantics):** Guest race increments `racesCount` and contributes to average-points and best-position.
- **D-08 (Counting threshold):** Any RaceResult row triggers credit — including 0-point / DNF-with-position. No-RaceResult means no credit.
- **D-09 (Phase edge):** Season ranking merges races across all season phases — existing `aggregateAcrossPhases` behavior; keep it.
- **D-10 (Live read-model — LOCKED):** No new persisted personal-points table. Live on-read computation only. Match scores (`Match.homeScore`/`awayScore`) remain the only persisted scoring artifact.
- **D-11 (Test fixture):** Extend `TestDataService` / `TestHelper.createFullSeasonFixture` with doppelrollen guest + pure guest scenarios. Test-prefix isolation (`T-…`, `Test_…`, `Test-Season …`).
- **D-12 (Demo seed):** Add guest example to `dev,demo` seed inside `TestDataService`/`DevDataSeeder`. `@Profile("dev")` only — never `local`/`prod`/`docker`.
- **D-13:** SCORE-01 regression — guest result flows into fielding team's match score.
- **D-14:** SCORE-02 regression — pure guest in season ranking with correct points + team; doppelrollen guest summed under home team.
- **D-15:** SCORE-03 regression — repeated saves idempotent; guest removal → clean disappearance.
- **D-16:** Alltime + profile regression — guest in alltime ranking (team ≠ null); pure guest gets profile page.

### Claude's Discretion

- Exact shape/signature of the unified attribution helper.
- Whether the alltime team map and the profile-generator iteration share one query or compute independently.
- Concrete fixture wiring (entities, IDs, which existing season fixture to extend).

### Deferred Ideas (OUT OF SCOPE)

- Visual guest marking (`*`/badge) across graphics, admin detail, ranking, profile → **Phase 115** (MARK-01..06).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SCORE-01 | Guest driver's race points count toward the **fielding team's** match score and standings via `ScoringService.aggregateMatchScores(race)` — no separate points model | Already works via `isDriverInTeam` → RaceLineup; needs regression pin (D-13) |
| SCORE-02 | Guest driver's earned points credited personally in the season's driver-ranking, including drivers with no SeasonDriver in that season | Works in principle; attribution unification (D-01..D-03) + alltime (D-04) + profile (D-05) close remaining gaps |
| SCORE-03 | Idempotent recompute on every result save — no double-counting | Structural property of live read-model + recompute-from-all-legs; needs idempotency regression (D-15) |
</phase_requirements>

---

## Summary

Phase 114 is **verification + edge-case hardening + regression tests**, not a new scoring engine. The core scoring behavior for guests is already structurally correct because both the team score (`ScoringService.isDriverInTeam`) and the driver ranking (result-iteration via `RaceResult` keyed by `driverId`) operate against `RaceLineup` as the source of truth and accumulate points per driver regardless of SeasonDriver membership.

**What is genuinely broken (three gaps to close):**

1. **Three divergent attribution helpers in `DriverRankingService`** — `calculateRankingForPhase` uses `resolveTeamFromLineup` (lineup-only, correct for a single race but misses the home-first rule for doppelrollen guests); `aggregateAcrossPhases` uses `attributeTeamFromRegularOrLineup` (partially correct — prefers REGULAR-phase team, but does not apply the home-first D-01 rule); `calculateAlltimeRanking` uses a SeasonDriver-only `driverTeamMap` — pure guests are absent from this map, so `driverTeamMap.get(id)` returns `null` and a pure guest's `DriverRanking` is created with `team = null`. These three paths need to be unified under one helper implementing D-01/D-02/D-03.

2. **`calculateAlltimeRanking` team = null gap (D-04)** — when a driver appears in `results` but has no `SeasonDriver`, `driverTeamMap.get(driverId)` returns `null` (via `orElse(null)`), causing `new DriverRanking(driver, null)`. The fix is to augment the team map with a RaceLineup-based fallback for drivers missing from SeasonDriver.

3. **`DriverProfilePageGenerator` misses pure guests (D-05)** — the outer loop iterates only `seasonDriverRepository.findBySeasonId(season.getId())`. A driver who appears only as a guest has no SeasonDriver row and therefore never gets a profile page for that season. The fix is to add a second pass over lineup-only drivers for the season, deduped via the existing `generatedDriverIds` set.

**Primary recommendation:** Introduce one private method `resolveAttributedTeam(Driver, UUID seasonId, UUID raceId)` in `DriverRankingService` that implements home-first / fallback-fielding uniformly, and swap all three existing helpers to call it. [ASSUMED]

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Team score aggregation (SCORE-01) | API / Backend (`ScoringService`) | — | Already RaceLineup-correct; no tier change needed |
| Personal driver-ranking attribution (SCORE-02) | API / Backend (`DriverRankingService`) | — | Read-model service; all three paths live here |
| Idempotency (SCORE-03) | API / Backend (`ScoringService` recompute-from-all-legs) | — | Structural property; no new code needed |
| Alltime gap (D-04) | API / Backend (`DriverRankingService.calculateAlltimeRanking`) | Database / Storage (RaceLineupRepository fallback query) | `driverTeamMap` augmented with lineup fallback |
| Profile-page existence (D-05) | Frontend Server (`DriverProfilePageGenerator`) | Database / Storage (new lineup-driver query) | Static site generator iterates lineup-only drivers |
| Test fixture (D-11) | API / Backend (`TestDataService`) | — | `@Profile("dev")` seeder |
| Demo seed (D-12) | API / Backend (`TestDataService`) | — | `@Profile("dev")` seeder |

---

## Standard Stack

This phase is an internal refactor + test hardening in an existing Spring Boot 4 / Java 25 application. No new third-party libraries are introduced. All tools are already on the classpath.

### Core (already present)
| Library | Purpose | Notes |
|---------|---------|-------|
| JUnit 5 (Jupiter) | Unit + IT test framework | Plain unit tests: `@ExtendWith(MockitoExtension.class)`, no Spring context |
| Mockito 4.x | Mocking in unit tests | `@Mock` / `@InjectMocks` pattern |
| Spring Boot Test | `@SpringBootTest` + `@CtcDevSpringBootContext` for ITs | ITs use shared TCF cache context |
| AssertJ | Fluent assertions | `assertThat(...)` pattern |
| H2 (dev profile) | In-memory DB for integration tests | Flyway migrations run on H2 |
| Jsoup | HTML parsing in sitegen ITs | Already used in `DriverProfilePageGeneratorTest` |

### No New Packages Required

No `npm install` or `pip install` — this is a pure Java/Spring phase. Package Legitimacy Audit: N/A.

---

## Architecture Patterns

### System Architecture Diagram

```
Admin: saveResults()
    │
    ▼
RaceService.saveResults(race)
    ├── ScoringService.calculatePoints(results, scoring)     [RaceResult points written]
    ├── raceRepository.save(race)
    └── scoringService.aggregateMatchScores(race)
              │
              ├── raceRepository.findByMatchId(match.id)     [all legs loaded from DB]
              │
              └── for each leg, for each result:
                    isDriverInTeam(result, raceId, homeTeamId)
                              │
                              └── RaceLineupRepository.findByRaceIdAndDriverId(raceId, driverId)
                                    ├── found → team match + parent rollup   ← SCORE-01 correct path
                                    └── not found → SeasonDriver fallback    ← legacy-data fallback


Driver Ranking Read Path (on-demand, read-only):
    Admin Standings (/admin/standings?phase=...) → StandingsViewService
        └── DriverRankingService.calculateRankingForPhase(phaseId)    ← admin per-phase
                 │
                 └── resolveTeamFromLineup(driverId, race)  [today: lineup-only, no home-first]
                          └── RaceLineupRepository.findByRaceIdAndDriverId(...)

    Public Site (/season/{slug}/driver-ranking.html) → DriverRankingPageGenerator
        ├── aggregateAcrossPhases(phaseIds, seasonId)       ← season aggregate
        │        └── attributeTeamFromRegularOrLineup(...)  [today: prefers REGULAR team]
        └── calculateRankingForPhase(phaseId)               ← per-phase variants

    Admin/Public Alltime → StandingsViewService / SiteGeneratorService
        └── calculateAlltimeRanking([seasonIds])
                 └── driverTeamMap built from SeasonDriver.findAll/findBySeasonIdIn
                          [GAP: pure guests absent → team = null]

    Public Site Profile → DriverProfilePageGenerator
        └── seasonDriverRepository.findBySeasonId(season.id)
                 [GAP: pure guests absent from this list → no profile page generated]
```

### Recommended Project Structure (no new directories needed)

```
src/main/java/org/ctc/domain/service/
└── DriverRankingService.java            # New private: resolveAttributedTeam(); remove 3 divergent helpers
src/main/java/org/ctc/sitegen/
└── DriverProfilePageGenerator.java     # Extend generate() with pure-guest second pass
src/main/java/org/ctc/admin/
└── TestDataService.java                # Add guest fixture to seedRaceLineups()
src/test/java/org/ctc/domain/service/
├── DriverRankingServiceTest.java        # New unit tests for D-13/D-14/D-15 (unit layer)
└── DriverRankingServiceGuestIT.java     # New @Tag("integration") IT for full guest round-trip
src/test/java/org/ctc/sitegen/
└── DriverProfilePageGeneratorTest.java  # New test: pure guest gets profile page
```

---

## Current-State Map: The Three Attribution Paths

This section documents exactly what each path does today for three driver types.

### Path 1: `calculateRankingForPhase(phaseId)`

**Used by:** `StandingsViewService.buildView(...)` (admin per-phase standings), `DriverRankingPageGenerator.generate(...)` (per-phase site pages), `aggregateAcrossPhases` (internally for each phase).

**Mechanism:** Iterates `RaceResult` rows for the phase. On the *first* result for each driver, calls:
```java
// [CITED: DriverRankingService.java:54]
Team team = resolveTeamFromLineup(driverId, result.getRace());
return new DriverRanking(result.getDriver(), team);
```

`resolveTeamFromLineup` does:
```java
// [CITED: DriverRankingService.java:199-202]
return raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driverId)
    .map(RaceLineup::getTeam)
    .orElse(null);
```

**Behavior for each driver type:**
| Driver Type | Today | After D-03 |
|-------------|-------|------------|
| Normal roster (Team A races for Team A) | Team A (from lineup) | Team A (unchanged) |
| Doppelrollen (roster Team A, guesting Team B) | **Team B** (fielding team from lineup — wrong per D-01) | **Team A** (home team from SeasonDriver — correct) |
| Pure guest (no SeasonDriver) | Fielding team from lineup (correct per D-02, but no parent rollup today) | Fielding team via `getParentOrSelf()` |

**Key issue:** `resolveTeamFromLineup` returns the raw lineup team without parent rollup AND without checking SeasonDriver for the home-first rule (D-01). For a doppelrollen guest, the per-phase ranking will show the *fielding* team, not the home team.

---

### Path 2: `aggregateAcrossPhases(phaseIds, seasonId)`

**Used by:** `DriverRankingPageGenerator` (legacy aggregated season ranking page).

**Mechanism:** Calls `calculateRankingForPhase` for each phase, then for each *new* driver (not yet in `rankingMap`), calls:
```java
// [CITED: DriverRankingService.java:86-88]
Team team = attributeTeamFromRegularOrLineup(
    regularPhaseTeamIds, driverId, seasonId, phaseRanking.getDriver());
return new DriverRanking(phaseRanking.getDriver(), team);
```

`attributeTeamFromRegularOrLineup` does:
```java
// [CITED: DriverRankingService.java:174-185]
List<RaceLineup> lineups = raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(driverId, seasonId);
if (lineups.isEmpty()) { return null; }
return lineups.stream()
    .filter(rl -> regularPhaseTeamIds.contains(rl.getTeam().getId()))
    .findFirst()
    .map(RaceLineup::getTeam)
    .orElseGet(() -> lineups.get(0).getTeam());
```

**Behavior for each driver type:**
| Driver Type | Today | After D-03 |
|-------------|-------|------------|
| Normal roster | REGULAR-phase lineup team (correct) | Unchanged |
| Doppelrollen (roster Team A, guesting Team B) | **Team B** if only guest lineup exists, or **Team A** if regular-phase lineup team matches regularPhaseTeamIds (partially correct depending on context) | **Team A** (SeasonDriver home team, per D-01) |
| Pure guest (no SeasonDriver) | `lineups.get(0).getTeam()` — raw team, no parent rollup | Fielding team via `getParentOrSelf()` |

**Key issue for doppelrollen:** `regularPhaseTeamIds` is built from `PhaseTeam` entries (teams enrolled in the REGULAR phase). A doppelrollen driver's regular roster team is in `regularPhaseTeamIds`; their guest team for the other team is also in `regularPhaseTeamIds`. The filter `regularPhaseTeamIds.contains(rl.getTeam().getId())` will match the *first* lineup for either team in REGULAR phase — this is not deterministic if the driver has both a roster lineup (Team A) and a guest lineup (Team B) in REGULAR. The home-first rule (check SeasonDriver explicitly) is not implemented.

---

### Path 3: `calculateAlltimeRanking(results, allSeasonDrivers)` — PRIVATE

**Used by:** both `calculateAlltimeRanking()` (public, uses `findAll`) and `calculateAlltimeRanking(seasonIds)` (public, uses `findBySeasonIdIn`).

**Mechanism:**
```java
// [CITED: DriverRankingService.java:137-152]
Map<UUID, Team> driverTeamMap = allSeasonDrivers.stream()
    .collect(Collectors.groupingBy(sd -> sd.getDriver().getId()))
    .entrySet().stream()
    .collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> e.getValue().stream()
            .max(Comparator.comparing(sd -> sd.getSeason().getName()))
            .map(sd -> sd.getTeam().getParentOrSelf())
            .orElse(null)));

for (RaceResult result : results) {
    UUID driverId = result.getDriver().getId();
    DriverRanking ranking = rankingMap.computeIfAbsent(driverId,
        id -> new DriverRanking(result.getDriver(), driverTeamMap.get(id)));
    ranking.addResult(result);
}
```

**Behavior for each driver type:**
| Driver Type | Today | After D-04 |
|-------------|-------|------------|
| Normal roster | Most recent SeasonDriver team, `getParentOrSelf()` — correct | Unchanged |
| Doppelrollen | Most recent SeasonDriver team (home team) — actually correct for alltime by accident, since SeasonDriver always records the home team | Unchanged (already correct) |
| **Pure guest** (no SeasonDriver anywhere) | **`driverTeamMap.get(id)` returns `null`** → `new DriverRanking(driver, null)` | Fallback to RaceLineup-resolved team (most recent lineup's parent) |

**The D-04 gap in precise code terms:** `driverTeamMap.get(id)` is `null` for any `driverId` not present in `allSeasonDrivers`. When the loop encounters such a driver's result, `computeIfAbsent` creates `new DriverRanking(driver, null)`. This null team propagates to the admin alltime standings and the public alltime page.

---

## Unified Attribution Helper — Recommendation

**Signature options:**

Option A (recommended, per D-03): A private method in `DriverRankingService` that takes `Driver`, `seasonId`, and `raceId`, and implements home-first / fallback-fielding:

```java
// [ASSUMED] — recommended signature
private Team resolveAttributedTeam(Driver driver, UUID seasonId, UUID raceId) {
    // D-01: prefer home team (SeasonDriver) if driver is rostered in this season
    Optional<SeasonDriver> sd = seasonDriverRepository.findBySeasonIdAndDriverId(seasonId, driver.getId());
    if (sd.isPresent()) {
        return sd.get().getTeam().getParentOrSelf();
    }
    // D-02: pure guest — resolve via RaceLineup (fieldling team, sub-team→parent)
    return raceLineupRepository.findByRaceIdAndDriverId(raceId, driver.getId())
        .map(rl -> rl.getTeam().getParentOrSelf())
        .orElseGet(() ->
            raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(driver.getId(), seasonId)
                .stream().findFirst()
                .map(rl -> rl.getTeam().getParentOrSelf())
                .orElse(null));
}
```

**Note on `per-phase` vs `aggregate` use:** In `calculateRankingForPhase`, `raceId` is the specific race whose result triggered the `computeIfAbsent` — pass `result.getRace().getId()`. In `aggregateAcrossPhases`, team attribution is done once per driver for the entire season; pass any lineup race (or rely on the season-scoped fallback). For `calculateAlltimeRanking`, team is built once per driver across all seasons — use the season-scoped fallback (no single raceId available).

**Alternative for alltime only (Option B):** Augment the existing `driverTeamMap` after it is built, by also querying `raceLineupRepository.findByRaceMatchdaySeasonIdIn(...)` to find drivers missing from SeasonDriver and adding them with their lineup-resolved team. This separates the alltime fix from the per-phase/aggregate fix. [ASSUMED]

**Recommendation:** Option A for per-phase and aggregate, Option B (augment map) for alltime — because `calculateAlltimeRanking` operates over all seasons at once and a per-driver `seasonDriverRepository.findBySeasonIdAndDriverId` call would be an N+1. The alltime-specific augmentation can query lineups for the same season scope as results in a single batch, then merge. [ASSUMED]

**All callers that must switch:**

| Current caller | Current helper | Change needed |
|----------------|----------------|---------------|
| `calculateRankingForPhase` line 54 | `resolveTeamFromLineup(driverId, result.getRace())` | Switch to `resolveAttributedTeam(driver, seasonId, raceId)` |
| `aggregateAcrossPhases` line 86-88 | `attributeTeamFromRegularOrLineup(...)` | Switch to `resolveAttributedTeam(driver, seasonId, firstRaceId)` or season-scoped variant |
| `calculateAlltimeRanking` private line 152 | `driverTeamMap.get(id)` | Augment `driverTeamMap` with lineup fallback before the result loop |

Note: `calculateRankingForPhase` currently does not have access to `seasonId`. Adding it: either pass it in (signature change) or derive it from `regularPhaseTeamIds` resolution. The cleaner approach is to derive `seasonId` from the `SeasonPhase` loaded at line 40 (`seasonPhaseService.findById(phaseId)`). [ASSUMED]

---

## Alltime Gap (D-04) — Detailed Fix Plan

**Current code (lines 137–145):**
```java
Map<UUID, Team> driverTeamMap = allSeasonDrivers.stream()
    .collect(Collectors.groupingBy(sd -> sd.getDriver().getId()))
    .entrySet().stream()
    .collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> e.getValue().stream()
            .max(Comparator.comparing(sd -> sd.getSeason().getName()))
            .map(sd -> sd.getTeam().getParentOrSelf())
            .orElse(null)));
```
[CITED: DriverRankingService.java:137-145]

**Gap:** Any driver with no SeasonDriver entry is absent from `allSeasonDrivers`, therefore absent from `driverTeamMap`, therefore `driverTeamMap.get(id)` returns `null` at line 152.

**Fix approach:** After building `driverTeamMap`, identify result-drivers not in the map, and for each, resolve their team from RaceLineup:

```java
// [ASSUMED] — augment driverTeamMap with lineup fallback for pure guests
Set<UUID> missingDriverIds = results.stream()
    .map(r -> r.getDriver().getId())
    .filter(id -> !driverTeamMap.containsKey(id))
    .collect(Collectors.toSet());

if (!missingDriverIds.isEmpty()) {
    // Use existing findByRaceMatchdaySeasonId-equivalent scoped to the same results
    // For each missing driver, find their most recent lineup entry and get parent team
    for (UUID id : missingDriverIds) {
        raceLineupRepository.findByDriverId(id).stream()
            .findFirst()  // any lineup; sub-team→parent
            .ifPresent(rl -> driverTeamMap.put(id, rl.getTeam().getParentOrSelf()));
    }
}
```

**Important:** `findByDriverId` has no @EntityGraph for `team` — it may lazy-load `team` inside the transaction. Since `calculateAlltimeRanking` is `@Transactional(readOnly = true)`, OSIV is active and lazy-load is safe. However, for correctness, a scoped `findByDriverIdAndRaceMatchdaySeasonId`-style query limited to the seasons in scope is preferable for the `calculateAlltimeRanking(List<UUID> seasonIds)` overload. The full-alltime overload can use `findByDriverId`. [ASSUMED]

---

## Profile-Page Gap (D-05) — Detailed Fix Plan

**Current code (DriverProfilePageGenerator.java lines 53–54):**
```java
var seasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());
var generatedDriverIds = new java.util.HashSet<java.util.UUID>();
```
[CITED: DriverProfilePageGenerator.java:53-54]

The loop iterates `seasonDrivers` only. A pure guest has no SeasonDriver row → no profile page generated.

**Fix:** After the existing `seasonDrivers` loop completes, add a second pass over lineup-only drivers:

```java
// [ASSUMED] — second pass for pure guests
var guestDrivers = raceLineupRepository.findByRaceMatchdaySeasonId(season.getId()).stream()
    .filter(rl -> generatedDriverIds.add(rl.getDriver().getId()))  // dedup via existing set
    .map(RaceLineup::getDriver)
    .distinct()
    .toList();

for (var driver : guestDrivers) {
    // team = fielding team from lineup (sub-team→parent)
    var team = raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(driver.getId(), season.getId())
        .stream().findFirst()
        .map(rl -> rl.getTeam().getParentOrSelf())
        .orElse(null);
    // ... render profile page with results = raceResultRepository.findByDriverId(driver.getId())
    //     filtered to this season, same as the existing loop body
}
```

**Key design notes:**
- Reuse the `generatedDriverIds` set — if `generatedDriverIds.add(driver.getId())` returns `false`, the driver already had a page from the SeasonDriver pass; skip.
- The existing SeasonDriver loop already sets `team = sd.getTeam()`. For pure guests, `team` is resolved from lineup. The template may receive `null` team if no lineup is found — guard this.
- `raceLineupRepository.findByRaceMatchdaySeasonId(seasonId)` already has an `@EntityGraph(attributePaths = {"driver", "team"})` — eager-fetch safe. [CITED: RaceLineupRepository.java:29-31]
- Phase 115 will add guest marking to the rendered profile — this phase only ensures the page exists.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Team-from-RaceLineup resolution | Custom SQL / JPQL | Existing `RaceLineupRepository.findByRaceIdAndDriverId` + `findByDriverIdAndRaceMatchdaySeasonId` | Already @EntityGraph-annotated; used in `ScoringService.isDriverInTeam` |
| Sub-team→parent rollup | Custom tree traversal | `team.getParentOrSelf()` | Already implemented on `Team` entity |
| Season scope for alltime driver query | New custom query | Augment existing `driverTeamMap` with `findByDriverId` or scoped lineup query | Less code; no new repository method needed |
| Idempotency mechanism | Persisted personal-points table (D-10 LOCKED) | Live read-model + recompute-from-all-legs | Already structural property; D-10 explicitly locks this out |
| Guest iteration for profile | New repository method returning `List<Driver>` for season | `findByRaceMatchdaySeasonId` + stream `.map(RaceLineup::getDriver).distinct()` | Existing query with @EntityGraph |

---

## Common Pitfalls

### Pitfall 1: N+1 in the unified attribution helper for per-phase ranking
**What goes wrong:** If `resolveAttributedTeam` is called once per driver *per result* (not per driver *per phase*), and each call makes two repository lookups (`findBySeasonIdAndDriverId` + `findByRaceIdAndDriverId`), a phase with 60 results over 12 drivers does 12 × 2 = 24 queries — acceptable. But if called N times per driver (once per result rather than via `computeIfAbsent`), it's 60 × 2 = 120 queries.
**Why it happens:** The existing `computeIfAbsent` pattern already ensures the helper is called only once per driver per phase — do not refactor that pattern.
**How to avoid:** Keep `computeIfAbsent` as the guard. The new helper is called only inside the `computeIfAbsent` lambda.

### Pitfall 2: `resolveTeamFromLineup` returns raw sub-team (no `getParentOrSelf()`)
**What goes wrong:** A guest fielded for sub-team `T-BRV 1` gets attributed to `T-BRV 1` in the ranking instead of parent `T-BRV`. The same driver in `ScoringService.isDriverInTeam` is correctly rolled up to the parent via `getTeam().getParentTeam()`.
**Why it happens:** `resolveTeamFromLineup` (line 200) calls `.map(RaceLineup::getTeam)` without `getParentOrSelf()`.
**How to avoid:** The new unified helper must call `rl.getTeam().getParentOrSelf()` (not `rl.getTeam()`).

### Pitfall 3: `aggregateAcrossPhases` creates a `DriverRanking` using the phase-level team, then overwrites it with the season-level team
**What goes wrong:** The current flow calls `calculateRankingForPhase(phaseId)` which creates `DriverRanking` objects with phase-level team attribution. Then `aggregateAcrossPhases` discards those objects entirely and creates a new `DriverRanking` with season-level team. This is correct intentionally — the season-level aggregation uses `attributeTeamFromRegularOrLineup` for team, not the per-phase team. After unification, the season-level `DriverRanking` will use `resolveAttributedTeam(driver, seasonId, ...)`.
**How to avoid:** Ensure the new helper is called in the `computeIfAbsent` lambda of `aggregateAcrossPhases`, not in `calculateRankingForPhase`.

### Pitfall 4: Double-profile-page for doppelrollen driver
**What goes wrong:** A doppelrollen driver is in `seasonDrivers` (has a SeasonDriver) and also in the lineup-only pass. Without dedup, their profile page is generated twice — the second write clobbers the first.
**How to avoid:** The `generatedDriverIds.add(driver.getId())` check in the second pass returns `false` for drivers already processed in the first pass. This is the existing dedup mechanism — reuse it exactly as specified in D-05.

### Pitfall 5: Alltime overload disambiguation
**What goes wrong:** `calculateAlltimeRanking()` uses `raceResultRepository.findByRacePlayoffMatchupIsNull()` (legacy, full table) and `seasonDriverRepository.findAll()`. After the fix, the lineup fallback for missing drivers must also be full-table-scoped. The `calculateAlltimeRanking(seasonIds)` overload scopes both results and season-drivers to `seasonIds` — the lineup fallback must also be season-scoped.
**How to avoid:** The private `calculateAlltimeRanking(List<RaceResult>, List<SeasonDriver>)` method receives both lists as parameters. The lineup fallback must be added consistently for both callers — pass the season scope (either `null` for full-alltime or `List<UUID> seasonIds` for scoped). [ASSUMED]

### Pitfall 6: `DriverProfilePageGenerator` second pass — `team` is null for lineup-only drivers
**What goes wrong:** If `raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId` returns empty (edge case: driver in `findByRaceMatchdaySeasonId` but deleted lineup), `team` is `null`. The template will NPE on `team.getShortName()` or similar.
**How to avoid:** Guard `team = null` in the second pass — skip the driver or use a null-safe approach consistent with Phase 115's visual-marking scope. Since pure guests always have a lineup row (they are identified from the lineup query), this edge case should not arise in practice.

### Pitfall 7: `@Transactional(readOnly = true)` and lazy-load for augmented alltime map
**What goes wrong:** `findByDriverId` for the lineup fallback has no `@EntityGraph(attributePaths = {"team"})` — `team` is loaded lazily. If called outside a transaction context, this fails.
**Why it doesn't apply here:** `calculateAlltimeRanking` is already `@Transactional(readOnly = true)`, and OSIV is enabled — lazy-loads inside the ranking computation are safe. No new `@EntityGraph` is strictly required, but adding one to any new query is preferred for clarity.

---

## Idempotency Proof for SCORE-03

**Claim:** Calling `saveResults(race)` / `aggregateMatchScores(race)` multiple times produces identical `Match.homeScore` / `Match.awayScore` and the live driver-ranking returns identical rows.

**Why it is structural:**

1. **`aggregateMatchScores(race)`** reloads all legs from the database (`raceRepository.findByMatchId(match.getId())`) and recomputes from scratch on every call. It calls `match.setHomeScore(matchHome)` then `matchRepository.save(match)` — it does not add to existing scores, it replaces them. [CITED: ScoringService.java:148-168]

2. **`aggregateMatchScores` guard:** If `race.getResults().isEmpty()`, it returns immediately without touching the match scores. If the operator clears results, `recomputeMatchScoresFromAllLegs` is called, which again reloads all legs and recomputes from scratch. [CITED: ScoringService.java:63-124]

3. **Driver ranking** has no persisted personal-points store (D-10 LOCKED). Every read of `calculateRankingForPhase` / `aggregateAcrossPhases` iterates the same `RaceResult` rows from the DB. Adding the same guest result row a second time is prevented by the `UNIQUE(race_id, driver_id)` constraint on `race_results`. [CITED: RaceResult.java:15]

4. **Guest removal idempotency (Phase 113 D-11):** When a guest is removed from the lineup, `saveLineup` cascade-deletes the guest's `RaceResult` for that race, then calls `scoringService.aggregateMatchScores(race)`. After deletion, the guest's `RaceResult` row is gone — the ranking no longer sees it, and the match score is recomputed without it. Calling `saveLineup` again without the guest is idempotent — there is nothing to delete, and `aggregateMatchScores` recomputes the same value. [CITED: RaceLineupService.java:143-149]

**What the regression test must demonstrate (D-15):**
```
// Scenario:
// 1. Race has home-driver (15pts) + guest (10pts) for home team
// 2. Save results → homeScore = 25
// 3. Save results again (identical) → homeScore = 25 (not 50)
// 4. Remove guest → save lineup → homeScore = 15
// 5. Driver ranking contains guest; after removal, guest absent
```

---

## Regression Test Strategy

### Unit Tests (no Spring context — plain Mockito)

**File:** `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java`

Add the following test cases to the existing file:

| Test ID | Behavior (D-ref) | Mock setup | Assertion |
|---------|-----------------|------------|-----------|
| D-01/D-03 doppelrollen per-phase | `calculateRankingForPhase` attributes doppelrollen to SeasonDriver team | lineup has guest=true for Team B; SeasonDriver has Team A | ranking.getTeam() = Team A |
| D-02/D-03 pure guest per-phase | `calculateRankingForPhase` attributes pure guest (no SeasonDriver) to fielding team | lineup has guest=true for Team B; no SeasonDriver | ranking.getTeam() = Team B (parent) |
| D-03 doppelrollen aggregate | `aggregateAcrossPhases` attributes doppelrollen to home team across phases | lineup for guest race + SeasonDriver for home team | single DriverRanking row, team = home team |
| D-04 alltime pure guest non-null | `calculateAlltimeRanking` — pure guest team ≠ null | results include pure guest; SeasonDrivers list does not include guest | ranking.getTeam() != null |
| D-06 additive single row | doppelrollen: one row, points summed | two RaceResult rows for same driver, different races | one DriverRanking, racesCount = 2 |

### Integration Tests (Spring context — @CtcDevSpringBootContext + @Tag("integration"))

**New file:** `src/test/java/org/ctc/domain/service/DriverRankingServiceGuestIT.java`

Purpose: Full DB round-trip with real H2 schema + guest fixture from `TestDataService`.

| Test ID | Behavior (D-ref) | IT approach |
|---------|-----------------|-------------|
| D-13 SCORE-01 | Guest result flows into fielding team's homeScore/awayScore | Create race with guest lineup + result, call aggregateMatchScores, assert match.homeScore includes guest points |
| D-14 SCORE-02 | Pure guest appears in season ranking | Guest has no SeasonDriver; call aggregateAcrossPhases, assert guest present with correct team and points |
| D-15 SCORE-03 idempotency | Double-save yields same scores | Save results twice, assert scores equal; remove guest + re-aggregate, assert guest absent |
| D-16 alltime non-null team | Guest in alltime ranking, team ≠ null | `calculateAlltimeRanking(seasonIds)` includes season with pure guest; assert team != null |

**Note on @Tag:** `DriverRankingServiceGuestIT.java` is a `*IT.java` file — must have `@Tag("integration")` per CLAUDE.md "Tag Tests by Category". [CITED: CLAUDE.md §"Tag Tests by Category"]

### Integration Test (sitegen — extends existing DriverProfilePageGeneratorTest)

**File:** `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java`

Add test:
```java
@Test
void givenPureGuestDriver_whenGenerate_thenProfilePageExists() { ... }
```
The test requires a pure-guest driver in the test fixture for at least one season. This means `TestDataService.seedRaceLineups()` must be extended with a guest fixture.

**Note:** `DriverProfilePageGeneratorTest.java` does NOT follow the `*IT.java` naming convention but IS a `@SpringBootTest` test. Looking at existing behavior: Failsafe `<includedGroups>` is `integration` and the file has no `@Tag("integration")`. This means it currently runs as a plain Surefire test (no Spring context exclusion because Surefire excludes `integration,e2e,flaky` groups — the file has no group so it is included). **Risk:** The `@SpringBootTest` context may cause issues running under Surefire's fork config. However, this is pre-existing behavior for `DriverProfilePageGeneratorTest` and `DriverRankingPageGeneratorTest` — the existing tests pass, so the forking works (likely because `@DynamicPropertySource` causes a separate context). Leave the naming convention as-is to match existing sitegen test files. [ASSUMED — pattern inferred from existing tests]

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test 4.x |
| Config file | `pom.xml` (Surefire/Failsafe) |
| Quick run command | `./mvnw -Dtest=DriverRankingServiceTest test` |
| Full suite command | `./mvnw clean verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SCORE-01 | Guest points counted in fielding team's match score | @SpringBootTest IT | `./mvnw -Dit.test=DriverRankingServiceGuestIT -DfailIfNoTests=false verify` | ❌ Wave 0 |
| SCORE-02 | Pure guest in season driver-ranking with correct team | @SpringBootTest IT | same | ❌ Wave 0 |
| SCORE-02 | Doppelrollen guest summed under home team | Unit + IT | `./mvnw -Dtest=DriverRankingServiceTest test` | ❌ new test methods needed |
| SCORE-03 | Idempotent double-save, no double-count | @SpringBootTest IT | same | ❌ Wave 0 |
| SCORE-03 | Guest removal → clean score disappearance | @SpringBootTest IT | same | ❌ Wave 0 |
| D-04 | Alltime ranking: pure guest team ≠ null | Unit + IT | `./mvnw -Dtest=DriverRankingServiceTest test` | ❌ new test methods needed |
| D-05 | Pure guest gets driver-profile page | @SpringBootTest sitegen | `./mvnw -Dtest=DriverProfilePageGeneratorTest test` | ❌ new test method needed |
| D-11 | Test fixture has doppelrollen + pure guest | Verified by above ITs passing | — | ❌ Wave 0 (TestDataService extension) |

### Sampling Rate

- **Per task commit:** `./mvnw -Dtest=DriverRankingServiceTest test` (unit, fast)
- **Per wave merge:** `./mvnw clean verify`
- **Phase gate:** `./mvnw clean verify -Pe2e` before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/domain/service/DriverRankingServiceGuestIT.java` — covers SCORE-01/02/03 + D-04/D-16; create empty class with `@CtcDevSpringBootContext` + `@Tag("integration")` + four `@Test` skeletons
- [ ] New test methods in `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java` — unit coverage for D-01/D-02/D-03 doppelrollen + pure guest
- [ ] New test method in `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` — covers D-05/D-16 profile page existence
- [ ] `TestDataService.seedRaceLineups()` must gain guest fixture (doppelrollen + pure guest in `Test-Season 2026`) — prerequisite for all ITs above

---

## Environment Availability

Step 2.6: SKIPPED — no external dependencies. This phase is code/config changes to existing Spring Boot services and their tests. H2 (already used for dev/test profile) is the only runtime dependency.

---

## Security Domain

No new external inputs, no new endpoints, no authentication changes. SCORE-01/02/03 are internal service-layer computations over already-persisted data. ASVS V5 (Input Validation) does not apply — no new user-facing inputs. CodeQL and SpotBugs are already gated on every `./mvnw verify`; no new patterns are introduced that would trigger findings.

---

## Project Constraints (from CLAUDE.md)

The following CLAUDE.md directives constrain this phase directly:

| Directive | Impact on Phase 114 |
|-----------|---------------------|
| **RaceLineup is Source of Truth** | All team attribution helpers must resolve via `RaceLineupRepository`, not SeasonDriver alone |
| **Score Aggregation on Result Save** | `scoringService.aggregateMatchScores(race)` must be called after every result mutation — already satisfied; regression test must pin it |
| **No Fallback Calculations** | Team = null must not be patched in templates or controllers; fix in service (`calculateAlltimeRanking` fix) |
| **Grep All Usages Before Refactor** | Before changing `resolveTeamFromLineup` / `attributeTeamFromRegularOrLineup`, grep all callers — currently only internal to `DriverRankingService` (confirmed by grep: no external callers) |
| **No Persisted Personal Points (D-10)** | Planner MUST NOT introduce a `DriverSeasonScore` or similar persistence layer |
| **TDD / Feature Sequence** | Unit Tests → Implementation → Integration Tests; tests must be green before commit |
| **Test Naming (Given-When-Then)** | Method names: `givenContext_whenAction_thenExpectedResult()` |
| **Tag Tests by Category** | New `*IT.java` file MUST have `@Tag("integration")` |
| **Isolate Test Data Completely** | Guest fixture in `TestDataService` must use `T-…` / `Test_…` / `Test-Season …` prefix |
| **No Comment Pollution** | Do not add phase/plan/wave references as comments |
| **Build & Test Discipline** | Always `./mvnw clean verify -Pe2e` at phase end; no `-DskipTests` |
| **Checkstyle Unused-Import Gate** | No unused imports in new/modified files; `validate` phase fails if violated |
| **Flyway: no V18 changes** | V18 is already complete (Phase 113); no new migration needed for Phase 114 |

---

## Open Questions (RESOLVED)

1. **`calculateRankingForPhase` does not have `seasonId` in scope** — **RESOLVED**
   - What we know: the method takes only `phaseId`; seasonId must be derived.
   - Resolution: derive from the already-loaded phase entity at line 40 (`seasonPhaseService.findById(phaseId).getSeason().getId()`, already called for validation) — no signature change, no new query.

2. **`DriverProfilePageGenerator` second-pass page rendering for pure guests** — **RESOLVED**
   - What we know: the existing SeasonDriver loop sets `team = sd.getTeam()` and renders a full profile page including `context.setVariable("team", team)`. For a pure guest, team comes from lineup.
   - Resolution: per D-05 ("data/page-existence hook only") Phase 114 sets NO `isGuest` flag on the template context. Keep the second-pass page identical in structure to the first-pass page; just pass the lineup-resolved team. Phase 115 (MARK-06) adds the guest marker.

3. **Fixture placement: extend `seedRaceLineups` or add new method?** — **RESOLVED**
   - What we know: `seedRaceLineups()` already creates `Test-Season 2026` with `T-ALF` vs `T-BRV 1` and `T-ALF` vs `T-BRV 2`. The guest fixture must use the same test teams.
   - Resolution: inline the guest rows into `seedRaceLineups` for simplicity — the guest lineup rows are just `new RaceLineup(race, driver, team, true)` for existing test drivers/races. (The `dev,demo` seed example for D-12 is a separate edit in `DevDataSeeder`, not in this test fixture.)

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Recommended signature for `resolveAttributedTeam`: takes `Driver`, `seasonId`, `raceId` | Unified Attribution Helper | May require signature refactor if seasonId derivation is cleaner another way |
| A2 | Inline guest rows into `seedRaceLineups` for D-11 fixture | Open Questions | Low risk — alternative is a new `seedGuestFixtures()` method |
| A3 | Alltime gap fix: augment `driverTeamMap` after initial build, using `findByDriverId` for pure guests | Alltime Gap (D-04) | Minor query-scope risk for `calculateAlltimeRanking(seasonIds)` overload — may need season-scoped query instead of full-table |
| A4 | Pure guest second pass in `DriverProfilePageGenerator` uses `findByRaceMatchdaySeasonId` stream | Profile-Page Gap (D-05) | Fine as long as the @EntityGraph fetches driver+team eagerly — confirmed: it does |
| A5 | `DriverProfilePageGeneratorTest` sitegen tests run under Surefire (not Failsafe) without @Tag | Regression Test Strategy | Pre-existing behavior; if this breaks, add `@Tag("integration")` and rename to `*IT.java` |
| A6 | `resolveAttributedTeam` called only once per driver via `computeIfAbsent` (not per result) | Common Pitfalls #1 | If called per result, N+1 query pattern; keep existing guard |

---

## Sources

### Primary (HIGH confidence — code read directly)

- [CITED: DriverRankingService.java] — full source read; three attribution paths documented precisely
- [CITED: ScoringService.java] — `aggregateMatchScores`, `recomputeMatchScoresFromAllLegs`, `isDriverInTeam` read directly
- [CITED: DriverProfilePageGenerator.java] — SeasonDriver-only iteration confirmed at line 53
- [CITED: RaceLineupRepository.java] — all queries and @EntityGraph annotations confirmed
- [CITED: RaceResultRepository.java] — UNIQUE constraint confirmed; `findByRaceMatchdaySeasonIdIn` confirmed
- [CITED: RaceLineup.java] — `is_guest` column + boolean `guest` field + 4-arg constructor confirmed (Phase 113 deliverable)
- [CITED: RaceResult.java] — no team column confirmed; UNIQUE(race_id, driver_id) confirmed
- [CITED: TestDataService.java] — `seedRaceLineups()` structure confirmed; no guest rows yet
- [CITED: DriverRankingServiceTest.java] — existing unit test coverage mapped
- [CITED: ScoringServiceTest.java] — isDriverInTeam + aggregateMatchScores test coverage mapped
- [CITED: DriverProfilePageGeneratorTest.java] — existing sitegen IT coverage mapped
- [CITED: TestHelper.java] — `createFullSeasonFixture` structure confirmed
- [CITED: StandingsViewService.java] — `calculateRankingForPhase` + `calculateAlltimeRanking` callsites confirmed
- [CITED: CLAUDE.md] — all project constraints read and applied

### Secondary (MEDIUM confidence)

- [CITED: .planning/phases/114-scoring-personal-crediting/114-CONTEXT.md] — locked decisions D-01..D-16 used directly
- [CITED: .planning/phases/113-guest-assignment-foundation/113-CONTEXT.md] — Phase 113 `is_guest` flag, cascade-delete, uniqueness constraint confirmed

### Tertiary

None — all claims are from direct code inspection.

---

## Metadata

**Confidence breakdown:**
- Current-state code map: HIGH — source files read directly
- Unified helper recommendation: ASSUMED — specific signature is Claude's discretion per D-03; logic is correct
- Alltime gap fix: HIGH for diagnosis, ASSUMED for specific implementation approach
- Profile-page gap fix: HIGH for diagnosis, ASSUMED for implementation approach
- Regression test strategy: HIGH — test infrastructure confirmed by reading existing test files

**Research date:** 2026-06-01
**Valid until:** 2026-07-01 (stable codebase, no fast-moving dependencies)
