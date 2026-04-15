# Phase 31: Null Safety and Transaction Fix - Research

**Researched:** 2026-04-13
**Domain:** Spring Transaction Management, Java Null Safety, JPA Entity Navigation
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Transaction Atomicity (DATA-01)**
- D-01: Validate-then-import strategy: `executeImport()` first validates ALL team pairs and rows (teams resolvable, drivers assignable, duplicates checked), then imports only if all validations pass
- D-02: If any validation error exists, no import happens — all errors are returned to the user in one response
- D-03: Duplicate check (match already exists on matchday) is part of the validation phase — when overwrite=false and a match exists, it counts as a validation error that aborts the entire import
- D-04: The existing `@Transactional` on `executeImport()` remains as safety net — if an unexpected exception occurs during the import phase, everything rolls back

**Null Safety for Bye Matches (DATA-03)**
- D-05: Fix only the identified NPE paths: `RaceFormDataService.getRaceFormData()`, `RaceFormDataService.getResultsFormData()`, `ScoringService.aggregateMatchScores()`
- D-06: Safe defaults strategy: methods return empty/neutral values when encountering bye matches or unlinked races (empty result lists, skip populateDrivers for null teams, score 0)
- D-07: No error logging for bye matches — byes are normal operation, not exceptional situations
- D-08: Existing null-safe code (RaceCalendarService, StandingsService with `isBye()` checks, stream filters with `!r.isBye()`) remains unchanged

**Season-Filtered Driver Fallback (DATA-04)**
- D-09: Fix only `ScoringService.isDriverInTeam()` — the only fallback that affects scoring results
- D-10: Season ID derived from race internally: `raceId → Race → Matchday → Season.getId()`. No change to the `isDriverInTeam(RaceResult, UUID raceId, UUID teamId)` method signature
- D-11: Fallback becomes: `result.getDriver().getSeasonDrivers().stream().filter(sd -> sd.getSeason().getId().equals(seasonId)).anyMatch(sd -> sd.getTeam().getId().equals(teamId))`

### Claude's Discretion
- Internal structure of the validation phase in `executeImport()` (private method extraction, error collection pattern)
- Whether to load Race entity in `isDriverInTeam()` via repository lookup or pass through existing context
- Test structure and naming for new validation and null-safety tests

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-01 | Multi-race CSV import runs within a single transaction (all-or-nothing) | Validate-then-import pattern in `executeImport()` — collect all errors before any writes |
| DATA-03 | Race services handle null home/away teams without NPE (bye matches, unlinked races) | `isBye()` check before team access; null guard before `populateDrivers`; skip awayTeam scoring when null |
| DATA-04 | Driver-team fallback check filters by current season to prevent cross-season misattribution | `raceId → Race → Matchday → Season.getId()` navigation in `isDriverInTeam()` — requires `RaceRepository.findById()` |

</phase_requirements>

## Summary

Phase 31 fixes three independent defects in CTC Manager, all of which are surgical changes to existing methods with no schema changes and no new dependencies. The codebase already has the infrastructure needed: `Race.isBye()`, `ValidationException`, `ImportResult.addError()`, and `RaceRepository.findById()`.

The largest change is `executeImport()` in `CsvImportService`: the current code interleaves validation and persistence within the same loop, so a team-not-found error on row 5 still allows rows 1–4 to write. The fix restructures the loop into a two-phase approach (validate all, then import all). The `@Transactional` annotation already present acts as a safety net for unexpected runtime exceptions but cannot compensate for the current design of continuing past validation errors.

The null safety fixes in `RaceFormDataService` and `ScoringService` are single-condition guards following the `RaceCalendarService` reference pattern that already exists in the codebase. The season filter fix in `isDriverInTeam()` requires one repository lookup to resolve `raceId → Season`, which is the cleanest path given the method signature is not changing.

**Primary recommendation:** Three isolated, test-first changes. Each change has a clear before/after and existing tests that must keep passing.

## Standard Stack

No new libraries required. [VERIFIED: codebase grep — all required APIs already present]

### Core (Already in Use)
| API | Purpose | Where Used |
|-----|---------|-----------|
| `@Transactional` (Spring) | Transaction boundary on `executeImport()` | `CsvImportService` L117 |
| `ImportResult.addError()` | Error collection | `CsvImportService` throughout |
| `Race.isBye()` | Bye-match detection | `RaceFormDataService`, `StandingsService`, stream filters |
| `RaceRepository.findById()` | Race entity lookup | `RaceFormDataService.getRaceFormData()`, `ScoringService` (to add) |
| `RaceLineupRepository` | Source of truth for driver-team | `ScoringService.isDriverInTeam()` |
| `ValidationException` | Validation error (400) | `CsvImportService` already uses it |

## Architecture Patterns

### Validate-Then-Import Pattern (DATA-01)

The existing `executeImport()` loops over `byTeamPair.entrySet()`, and both validates and persists within the same iteration. When a validation error occurs (team not found, duplicate match) it adds to `result.addError()` and `continue`s — meaning partial import has already happened for earlier entries.

**Current structure (problem):**
```java
for (var entry : byTeamPair.entrySet()) {
    // validate team resolution...
    if (homeTeam == null) { result.addError(...); continue; }   // <-- other entries already persisted
    // persist match, race, results...
}
```

**Required structure (fix):**
```java
// Phase 1: Validate ALL team pairs
List<String> validationErrors = validateAllTeamPairs(byTeamPair, seasonTeams, matchday, overwriteExisting);
if (!validationErrors.isEmpty()) {
    validationErrors.forEach(result::addError);
    return result;  // abort — no writes
}

// Phase 2: Import (only reached if Phase 1 has no errors)
for (var entry : byTeamPair.entrySet()) {
    // persist match, race, results...
}
```
[VERIFIED: code inspection of `CsvImportService.java` L117-221]

**Key constraint:** `groupByTeamPair()` produces a single entry for single-race CSV (2-team import), so for the common case this is equivalent to the existing behaviour. Multi-race CSVs (future: multiple team pairs in one import) require the two-phase approach. The validation phase must mirror every check in the current import loop:
- Team resolution (`findTeamFlexible`) for home and away
- Duplicate check (`matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId`)
- When `overwriteExisting=false` and duplicate exists: validation error

**What the validation phase does NOT check:**
- Driver resolution (fuzzy/new driver decisions are already made at `parseAndPreview` time and confirmed by the user before `executeImport` is called)
- Individual row errors from `resolveDriver()` — these remain in-loop `continue` paths (a row that can't assign a driver does not abort the whole import, it just skips that row)

**Discretion note:** A private method `validateTeamPairs(...)` extracting Phase 1 logic keeps `executeImport()` readable and makes Phase 1 independently testable.

### Null Safety Pattern for Bye Matches (DATA-03)

**Reference pattern** (already in codebase, `RaceCalendarService.java` L43):
```java
// Source: RaceCalendarService.java L43
if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
    throw new IllegalStateException("Race has no teams assigned");
}
```
For bye match services the strategy is `return` (not throw) per D-06.

**Fix 1 — `RaceFormDataService.getRaceFormData()` L63:**

Current code (NPE when `race.getHomeTeam()` is null):
```java
// L63-64
getUsedCarIds(season.getId(), race.getHomeTeam().getId(), race.getId()),
getUsedTrackIds(season.getId(), race.getHomeTeam().getId(), race.getId())
```

The `getUsedCarIds` / `getUsedTrackIds` methods already filter `!r.isBye()` in their stream, but the NPE fires before entering the stream — at the `.getId()` call on the null team reference in the outer method.

Fix: guard before calling with team ID:
```java
// Source: decision D-06 + RaceCalendarService reference pattern
UUID homeTeamId = race.getHomeTeam() != null ? race.getHomeTeam().getId() : null;
return new RaceService.RaceFormData(data, matchdayRepository.findAll(), teamRepository.findAll(),
        season.getCars(), season.getTracks(),
        homeTeamId != null ? getUsedCarIds(season.getId(), homeTeamId, race.getId()) : Set.of(),
        homeTeamId != null ? getUsedTrackIds(season.getId(), homeTeamId, race.getId()) : Set.of());
```

**Fix 2 — `RaceFormDataService.getResultsFormData()` L76-77:**

Current code (NPE when `race.getHomeTeam()` or `race.getAwayTeam()` is null):
```java
// L76-77
populateDrivers(results, raceId, seasonId, race.getHomeTeam());
populateDrivers(results, raceId, seasonId, race.getAwayTeam());
```

Fix: null-guard each call:
```java
// Source: decision D-06
if (race.getHomeTeam() != null) populateDrivers(results, raceId, seasonId, race.getHomeTeam());
if (race.getAwayTeam() != null) populateDrivers(results, raceId, seasonId, race.getAwayTeam());
```

**Fix 3 — `ScoringService.aggregateMatchScores()` L62-86:**

Current code already guards `match.getHomeTeam() != null` (L62) but does NOT guard `match.getAwayTeam()`. The log statement at L84 already handles null awayTeam via the ternary `match.getAwayTeam() != null ? ... : "?"`, so the NPE surface is limited to the `isDriverInTeam()` call for awayTeam points (L79), which internally accesses `result.getDriver().getSeasonDrivers()` without issue. However, the real risk is that a Race with `match.isBye() == true` still enters the legs loop — the existing guard at L62 checks `match.getHomeTeam() != null` but `isBye()` is a cleaner semantic check.

The conservative fix per D-06: add `if (race.isBye()) return;` guard at the top of `aggregateMatchScores()`, or equivalently guard the awayTeam dereference in the log statement is already done but verify no other awayTeam access exists without null check. Current code at L86:
```java
match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "?"
```
That is already null-safe. The only unguarded awayTeam usage in this method is that points assigned via `!isDriverInTeam(r, leg.getId(), hId)` — this is correct for a bye (all results go to awayTeam bucket) but scoring a bye is arguably meaningless. Per D-06, silent skip is the right approach. Add `if (race.isBye()) return;` at top.

### Season-Filtered Driver Fallback Pattern (DATA-04)

**Current problem in `isDriverInTeam()` L143-144:**
```java
// Fallback for legacy data without RaceLineup
return result.getDriver().getSeasonDrivers().stream()
        .anyMatch(sd -> sd.getTeam().getId().equals(teamId));
```
This matches the team across ALL seasons, so a driver who drove for TeamA in Season 1 but TeamB in Season 2 will still match TeamA during Season 2 scoring.

**Fix per D-11:**
```java
// Fallback for legacy data without RaceLineup
var race = raceRepository.findById(raceId).orElse(null);
if (race == null) return false;
var seasonId = race.getMatchday().getSeason().getId();
return result.getDriver().getSeasonDrivers().stream()
        .filter(sd -> sd.getSeason().getId().equals(seasonId))
        .anyMatch(sd -> sd.getTeam().getId().equals(teamId));
```

**Dependency injection:** `ScoringService` currently only injects `RaceLineupRepository`. This fix requires adding `RaceRepository` as an additional dependency. This is straightforward given `@RequiredArgsConstructor` pattern — add `private final RaceRepository raceRepository;`.

**Repository lookup cost:** `isDriverInTeam()` is called per-result per-leg in `aggregateMatchScores()`. The extra `raceRepository.findById()` only executes when RaceLineup is absent (legacy data path). Acceptable performance cost for correctness. [ASSUMED — no benchmark, but given H2/MariaDB PK lookup cost this is negligible]

**Existing tests to verify:** `ScoringServiceTest.AggregateMatchScoresTest.givenNoRaceLineup_whenAggregateMatchScores_thenFallsBackToSeasonDriver()` — this test currently sets up SeasonDriver without season filtering. After the fix, this test will need to supply a properly linked Season via `sd.setSeason(season)` AND the `raceRepository` mock must return the race with a matchday that links to the season. The test already sets `homeSd.setSeason(season)` at L259 and `awaySd.setSeason(season)` at L263 — but the mock for `raceRepository.findById(race.getId())` will need to be added.

### Recommended Project Structure

No structural changes. All edits are within existing classes:
```
src/main/java/org/ctc/
├── dataimport/
│   └── CsvImportService.java       # DATA-01: two-phase validate-then-import
├── domain/service/
│   ├── RaceFormDataService.java    # DATA-03: null guards in getRaceFormData, getResultsFormData
│   └── ScoringService.java         # DATA-03: isBye guard; DATA-04: season-scoped fallback + RaceRepository dep
```

### Anti-Patterns to Avoid

- **Continue-past-validation:** Do not add only an early-return at the top of `executeImport()` without restructuring the loop. The existing `continue` statements after `result.addError()` are the core problem — they allow partial persistence.
- **Throwing on bye match:** Per D-07, bye matches are normal operation. Do not throw `IllegalStateException` (that is the RaceCalendarService pattern for calendar — a different context where a bye has no calendar meaning).
- **Changing `isDriverInTeam()` signature:** D-10 explicitly locks the signature. The season ID must be derived internally.
- **Logging byes at warn/error:** Per D-07 and CLAUDE.md conventions, business rule violations log at warn. Byes are not violations — no logging at all.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Transaction rollback on failure | Manual delete/compensate logic | `@Transactional` already on method + two-phase validation (no writes until all validated) | Spring rolls back automatically on unchecked exceptions; two-phase prevents partial writes |
| Null-safe team access | Custom `Optional<Team>` wrappers | Simple `if (x != null)` guard | Consistent with existing `RaceCalendarService` pattern already in codebase |
| Season lookup | Manual join query | `race.getMatchday().getSeason()` (OSIV enabled, lazy load works in service context) | OSIV is deliberately enabled; no extra fetch needed |

**Key insight:** Every tool needed exists in the codebase. No new utilities, wrappers, or libraries are required.

## Common Pitfalls

### Pitfall 1: Validation Phase Misses the Duplicate Check
**What goes wrong:** The validation phase checks team resolution but not duplicates. Result: a duplicate match triggers a runtime error during the import phase (after some rows may have been written to DB) — this is still a partial import scenario.
**Why it happens:** The duplicate check involves a repository call, making it easy to skip from the validation phase.
**How to avoid:** The validation phase must include `matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId()` for each team pair (per D-03).
**Warning signs:** Test for "duplicate with overwrite=false and multiple team pairs" fails to roll back.

### Pitfall 2: Existing ScoringServiceTest Breaks After RaceRepository Added
**What goes wrong:** `ScoringServiceTest.givenNoRaceLineup_whenAggregateMatchScores_thenFallsBackToSeasonDriver()` calls `aggregateMatchScores()` which calls `isDriverInTeam()` which now calls `raceRepository.findById()`. Without a mock for the repository, Mockito strict stubs will throw `UnnecessaryStubbingException` or NPE.
**Why it happens:** Adding a new dependency to `ScoringService` requires updating existing tests.
**How to avoid:** Add `@Mock private RaceRepository raceRepository;` to `ScoringServiceTest` and stub `when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race))` in the relevant tests. The race in the test already has `matchday` set (L332 in test: `race.setMatchday(match.getMatchday())`), so the season navigation will work if the matchday has a season.
**Warning signs:** Build fails with `NullPointerException` in `ScoringServiceTest` after adding `RaceRepository`.

### Pitfall 3: toRaceData() NPE Not Covered by isBye() Guard
**What goes wrong:** `RaceFormDataService.toRaceData()` at L164-165 calls:
```java
race.getHomeTeam().getId(), race.getAwayTeam().getId()
```
This is called from both `getRaceFormData()` and `getResultsFormData()` — adding null guards in the calling methods does not protect the `toRaceData()` call itself.
**Why it happens:** The NPE is inside the shared private `toRaceData()` method, not in the public method that was patched.
**How to avoid:** `toRaceData()` itself must handle null teams. Use `race.getHomeTeam() != null ? race.getHomeTeam().getId() : null` for both team ID accesses in `toRaceData()`.
**Warning signs:** `getRaceFormData()` test with bye race still throws NPE even after the public method fix.

### Pitfall 4: Single-Race Import Accidentally Becomes Non-Atomic
**What goes wrong:** The two-phase refactor accidentally removes atomicity for the import phase (e.g., by wrapping Phase 2 in a try-catch that swallows exceptions without re-throwing).
**Why it happens:** The validate-then-return pattern can lead to removing `@Transactional` or adding catch-all exception handling.
**How to avoid:** D-04 — keep `@Transactional` on `executeImport()`. Do not add a catch block that returns a result with errors from the import phase; let exceptions propagate to trigger rollback.

### Pitfall 5: Season Navigation Null in isDriverInTeam() Fallback
**What goes wrong:** `race.getMatchday().getSeason()` throws NPE if `matchday` or `season` is null for the race.
**Why it happens:** `raceRepository.findById()` always fetches a full Race but the matchday and season are lazy-loaded. In the transaction context of `aggregateMatchScores()` (@Transactional), OSIV is active so lazy access works — but in standalone unit tests there is no Hibernate session.
**How to avoid:** Add null check: `if (race == null || race.getMatchday() == null) return false;` and ensure unit tests provide a fully constructed race with matchday and season.

## Code Examples

### Two-Phase executeImport() Structure
```java
// Source: design decision D-01 to D-04, CsvImportService.java analysis

@Transactional
public ImportResult executeImport(ImportPreview preview, Map<String, UUID> confirmedMatches,
                                  Set<String> createNewDrivers, boolean overwriteExisting) {
    var result = new ImportResult();
    var metadata = preview.getMetadata();

    var season = seasonRepository.findById(metadata.seasonId()).orElseThrow(
            () -> new ValidationException("Season not found: " + metadata.seasonId()));
    var matchday = findOrCreateMatchday(season, metadata);
    var seasonTeams = season.getTeams();
    var byTeamPair = groupByTeamPair(preview.getRows());

    // Phase 1: Validate all team pairs (no writes)
    for (var entry : byTeamPair.entrySet()) {
        var teamParts = entry.getKey().split("\\|");
        var homeTeam = findTeamFlexible(teamParts[0], seasonTeams);
        var awayTeam = teamParts.length > 1 ? findTeamFlexible(teamParts[1], seasonTeams) : null;

        if (homeTeam == null) {
            result.addError("Team not found: " + teamParts[0]);
        }
        if (awayTeam == null && teamParts.length > 1) {
            result.addError("Team not found: " + teamParts[1]);
        }
        if (homeTeam != null) {
            var effectiveAwayTeam = awayTeam != null ? awayTeam : homeTeam;
            if (!overwriteExisting && matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(
                    matchday.getId(), homeTeam.getId(), effectiveAwayTeam.getId())) {
                result.addError("Match already exists: " + homeTeam.getShortName() +
                        " vs " + effectiveAwayTeam.getShortName() + " on " + matchday.getLabel());
            }
        }
    }

    if (result.hasErrors()) {
        return result;  // abort — @Transactional will not roll back (no writes happened)
    }

    // Phase 2: Import (only reached when validation passes)
    // ... existing import loop without validation checks ...
    return result;
}
```

### Null-Safe populateDrivers Call
```java
// Source: RaceFormDataService.java L76-77, decision D-06
if (data.results().isEmpty()) {
    var seasonId = race.getMatchday().getSeason().getId();
    var results = new ArrayList<RaceService.RaceResultData>();
    if (race.getHomeTeam() != null) populateDrivers(results, raceId, seasonId, race.getHomeTeam());
    if (race.getAwayTeam() != null) populateDrivers(results, raceId, seasonId, race.getAwayTeam());
    // ... rebuild data record ...
}
```

### Season-Scoped SeasonDriver Fallback
```java
// Source: ScoringService.java L143-144, decision D-11
// Fallback for legacy data without RaceLineup
var race = raceRepository.findById(raceId).orElse(null);
if (race == null || race.getMatchday() == null) return false;
var seasonId = race.getMatchday().getSeason().getId();
return result.getDriver().getSeasonDrivers().stream()
        .filter(sd -> sd.getSeason().getId().equals(seasonId))
        .anyMatch(sd -> sd.getTeam().getId().equals(teamId));
```

## State of the Art

No technology changes. This phase is entirely within existing Spring + JPA patterns.

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| interleaved validate+persist loop | two-phase validate-then-import | Phase 31 | atomicity for multi-race CSV |
| unfiltered SeasonDriver fallback | season-scoped SeasonDriver fallback | Phase 31 | correct cross-season scoring |
| no bye guard in aggregateMatchScores | early return on `isBye()` | Phase 31 | no NPE on bye match scoring |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `raceRepository.findById()` lookup in `isDriverInTeam()` has negligible performance impact for the legacy data path | Season Filter pattern | Could add latency if called very frequently with no RaceLineup; mitigation: only triggers on fallback path |
| A2 | `toRaceData()` is the only place in `RaceFormDataService` where `race.getHomeTeam().getId()` or `race.getAwayTeam().getId()` is called without null guard | Pitfall 3 | If there are other call sites, they would need the same treatment |

## Open Questions

1. **Should `toRaceData()` return null IDs or throw for bye matches?**
   - What we know: `toRaceData()` is called from both `getRaceFormData()` and `getResultsFormData()`. Callers pass these IDs into form data used for rendering the race edit form.
   - What's unclear: Whether the race edit form can render meaningfully with null team IDs (the template likely accesses `homeTeamId` for the dropdown pre-selection).
   - Recommendation: Return `null` for team IDs in `toRaceData()` when team is null — consistent with D-06 (safe defaults). The form will render with no team pre-selected, which is accurate for a bye.

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — all changes are code-only within existing Spring/JPA stack)

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + AssertJ, Maven Surefire |
| Config file | `pom.xml` (Surefire L184-194) |
| Quick run command | `./mvnw test` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-01 | Multi-race validation aborts if any team not found | unit | `./mvnw test -Dtest=CsvImportServiceTest` | ✅ (extend existing) |
| DATA-01 | Multi-race validation aborts if duplicate exists (overwrite=false) | unit | `./mvnw test -Dtest=CsvImportServiceTest` | ✅ (extend existing) |
| DATA-01 | All errors collected before any writes | unit | `./mvnw test -Dtest=CsvImportServiceTest` | ❌ Wave 0 |
| DATA-03 | `getRaceFormData()` returns form data for bye race without NPE | unit | `./mvnw test -Dtest=RaceFormDataServiceTest` | ❌ Wave 0 |
| DATA-03 | `getResultsFormData()` returns empty results for bye race without NPE | unit | `./mvnw test -Dtest=RaceFormDataServiceTest` | ❌ Wave 0 |
| DATA-03 | `aggregateMatchScores()` returns without scoring when race is bye | unit | `./mvnw test -Dtest=ScoringServiceTest` | ❌ Wave 0 |
| DATA-04 | `isDriverInTeam()` fallback only matches SeasonDriver from current season | unit | `./mvnw test -Dtest=ScoringServiceTest` | ❌ Wave 0 |
| DATA-04 | `isDriverInTeam()` fallback does not match driver from different season | unit | `./mvnw test -Dtest=ScoringServiceTest` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=CsvImportServiceTest,RaceFormDataServiceTest,ScoringServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` (866+ tests green, ≥82% coverage)

### Wave 0 Gaps
- [ ] New test cases in `CsvImportServiceTest` — covers DATA-01 multi-race abort
- [ ] New test cases in `RaceFormDataServiceTest` — covers DATA-03 bye match
- [ ] New test cases in `ScoringServiceTest` — covers DATA-03 bye + DATA-04 season filter
- [ ] Update `ScoringServiceTest.givenNoRaceLineup_*` — add `RaceRepository` mock + stub

*(Existing test files exist; new test methods only required, not new files)*

## Security Domain

Not applicable. Phase 31 is data integrity and null safety — no authentication, session, access control, input validation boundary, or cryptographic changes. `security_enforcement` is enabled in principle but ASVS categories V2/V3/V4/V5/V6 do not apply to these service-layer defensive programming changes.

## Sources

### Primary (HIGH confidence)
- Direct code inspection of `CsvImportService.java`, `RaceFormDataService.java`, `ScoringService.java`, `Race.java`, `Match.java`, `RaceRepository.java` — verified all NPE paths and current logic
- Existing test files: `CsvImportServiceTest.java`, `ScoringServiceTest.java`, `RaceFormDataServiceTest.java` — verified current test coverage and what needs extending
- `CONTEXT.md` decisions D-01 through D-11 — locked implementation choices

### Secondary (MEDIUM confidence)
- `RaceCalendarService.java` — reference pattern for null-safe team access in service layer
- `CONVENTIONS.md` — transaction patterns, logging levels, test structure
- `TESTING.md` — BDD naming convention, Mockito patterns, coverage minimum

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified by code inspection, no new libraries
- Architecture: HIGH — two-phase pattern is straightforward; null guards follow existing codebase pattern
- Pitfalls: HIGH — all pitfalls identified from code inspection of specific lines

**Research date:** 2026-04-13
**Valid until:** 2026-05-13 (stable — no external dependencies)
