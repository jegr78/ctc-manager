# Phase 17: Duplicate-Handling - Research

**Researched:** 2026-04-07
**Domain:** Spring Data JPA / duplicate detection and deletion within a transactional service
**Confidence:** HIGH

## Summary

Phase 17 extends the existing `DriverMergeService.merge()` method (built in Phase 16) with proactive duplicate detection before each FK reassignment. When a source driver entry conflicts with an existing target driver entry (same season or same race), the source entry is deleted instead of being reassigned. All non-conflicting entries continue to be reassigned normally.

The scope is entirely within the service layer: no new entities, no Flyway migrations, no controller changes. Two additions are required before the service logic: (1) a new `findByRaceIdAndDriverId()` query method on `RaceResultRepository`, and (2) three new fields on the `MergeResult` record to report dropped counts.

The implementation follows established project patterns exactly: `Optional.isPresent()` for existence checks, `repository.delete()` for removal, `log.info()` with parameterized `{}` format for audit, and TDD sequence (unit tests first, then implementation).

**Primary recommendation:** Implement as three parallel in-loop duplicate checks within `merge()`, each using the existing `findByXxx()` repository methods. Extract no helper method — inline checks keep the loop logic readable and consistent with Phase 16's existing style.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Proactive duplicate check before each FK reassignment — for each source entry, query whether the target driver already has an entry for the same season/race before attempting reassignment.

**D-02:** Use existing repository methods: `SeasonDriverRepository.findBySeasonIdAndDriverId()`, `RaceLineupRepository.findByRaceIdAndDriverId()` for lookup; add `RaceResultRepository.findByRaceIdAndDriverId()` (new method needed).

**D-03:** When a duplicate is detected (target already has an entry for the same season/race), delete the source entry instead of reassigning it — the target's existing entry is preserved unchanged.

**D-04:** Applies to all three FK tables: SeasonDriver (UniqueConstraint on season_id+driver_id), RaceResult (UniqueConstraint on race_id+driver_id), RaceLineup (no DB constraint but logical duplicates handled defensively).

**D-05:** Extend `MergeResult` record with additional dropped counts: `seasonDriversDropped`, `raceLineupsDropped`, `raceResultsDropped`.

**D-06:** Log dropped duplicates at `log.info()` level with specific entity details (season name/race name) for audit trail.

**D-07:** Even though `race_lineups` table has no UniqueConstraint, check for logical duplicates (same race + same driver) and handle them identically.

**D-08:** No new Flyway migration to add a DB constraint — defensive logic only.

### Claude's Discretion

- Internal ordering of duplicate detection within the reassignment loop
- Whether to use a single loop with inline check or extract a helper method
- Exact field names for the new MergeResult counts

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MERGE-11 | Unique-Constraint-Konflikte bei SeasonDriver (gleiche Season) werden erkannt und geloest | `SeasonDriverRepository.findBySeasonIdAndDriverId()` exists at line 19; returns `Optional<SeasonDriver>`; `seasonDriverRepository.delete(sd)` removes duplicate |
| MERGE-12 | Unique-Constraint-Konflikte bei RaceLineup (gleiches Race) werden erkannt und geloest | `RaceLineupRepository.findByRaceIdAndDriverId()` exists at line 19; returns `Optional<RaceLineup>`; no DB constraint but logical duplicate must be caught |
| MERGE-13 | Unique-Constraint-Konflikte bei RaceResult (gleiches Race) werden erkannt und geloest | `RaceResultRepository` currently missing `findByRaceIdAndDriverId()` — new method required; pattern identical to SeasonDriver/RaceLineup |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | (Spring Boot 4.x managed) | Repository query derivation, `Optional` return types, `delete()` | Project stack — all repos already use this [VERIFIED: codebase grep] |
| JUnit 5 + Mockito | (Spring Boot 4.x managed) | Unit tests — mock repos, verify `delete()` vs `save()` calls | Established test framework per CLAUDE.md [VERIFIED: codebase] |
| AssertJ | (Spring Boot 4.x managed) | Fluent assertions in tests | Established per TESTING.md [VERIFIED: codebase] |

### Supporting
No additional libraries needed. All capabilities required are already present in the existing stack.

**Installation:** No new dependencies required.

## Architecture Patterns

### Recommended Project Structure

No new files or packages. All changes are within:

```
src/
├── main/java/org/ctc/domain/
│   ├── repository/RaceResultRepository.java   # Add findByRaceIdAndDriverId()
│   └── service/DriverMergeService.java        # Extend merge() + MergeResult
└── test/java/org/ctc/domain/service/
    └── DriverMergeServiceTest.java            # Add DuplicateHandlingTests @Nested class
```

### Pattern 1: In-loop proactive duplicate check (SeasonDriver example)

**What:** Before reassigning each source entry, query whether the target already occupies the same slot. If yes, delete source. If no, reassign.

**When to use:** Any FK reassignment where a UniqueConstraint (or logical uniqueness) exists on (foreign_key_1, foreign_key_2).

```java
// Source: established project pattern (Optional.isPresent() + repository.delete())
var seasonDrivers = seasonDriverRepository.findByDriverId(sourceId);
int seasonDriversDropped = 0;
for (var sd : seasonDrivers) {
    var conflict = seasonDriverRepository.findBySeasonIdAndDriverId(
            sd.getSeason().getId(), targetId);
    if (conflict.isPresent()) {
        log.info("Dropping duplicate SeasonDriver for season [{}] during merge of driver [{}] into [{}]",
                sd.getSeason().getName(), sourceId, targetId);
        seasonDriverRepository.delete(sd);
        seasonDriversDropped++;
    } else {
        sd.setDriver(target);
        seasonDriverRepository.save(sd);
    }
}
```

[VERIFIED: codebase — `findBySeasonIdAndDriverId()` at SeasonDriverRepository.java:19, `Optional` return type confirmed; `delete()` pattern from Phase 16 `driverRepository.delete(source)`]

### Pattern 2: MergeResult record extension

**What:** Add three new fields to the existing record. Java records are immutable and require all fields in the canonical constructor.

```java
// Extend existing record — add dropped counts after existing reassigned counts
public record MergeResult(
        int seasonDrivers,
        int raceLineups,
        int raceResults,
        int aliasesReassigned,
        int seasonDriversDropped,
        int raceLineupsDropped,
        int raceResultsDropped) {}
```

**Impact on existing tests:** All existing `ResultTests` that call `new MergeResult(...)` directly will break if they use a constructor call. They currently call `driverMergeService.merge()` and assert on the returned value — that pattern will still work since the service constructs the record internally. Check: existing tests use `result.seasonDrivers()` etc. — these assertions remain valid. The record construction in `merge()` itself must be updated.

[VERIFIED: codebase — existing record at DriverMergeService.java:29; existing tests at DriverMergeServiceTest.java:282-307 use `driverMergeService.merge()` return value, not direct record construction]

### Pattern 3: New repository method `findByRaceIdAndDriverId()`

**What:** Spring Data JPA query derivation — identical to the pattern already used in `SeasonDriverRepository` and `RaceLineupRepository`.

```java
// Add to RaceResultRepository.java — no @Query annotation needed
Optional<RaceResult> findByRaceIdAndDriverId(UUID raceId, UUID driverId);
```

[VERIFIED: codebase — `SeasonDriverRepository.findBySeasonIdAndDriverId()` at line 19 and `RaceLineupRepository.findByRaceIdAndDriverId()` at line 19 use identical derivation pattern; `RaceResult.race` is `@JoinColumn(name="race_id")` and `driver` is `@JoinColumn(name="driver_id")`, confirming field names resolve correctly]

### Anti-Patterns to Avoid

- **Catching DataIntegrityViolationException:** The decision (D-01) is proactive detection, not reactive exception handling. Never let the constraint violation reach the DB.
- **Modifying the target entry:** D-03 is explicit — the target's existing entry is preserved unchanged. Only the source entry is deleted.
- **Adding a UniqueConstraint to `race_lineups` via Flyway:** D-08 explicitly forbids this for scope reasons.
- **Calling `saveAll()` on the batch:** The existing Phase 16 pattern uses individual `save()` calls per entry. Keep this consistent.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Duplicate existence check | Custom JPQL or `@Query` | Spring Data JPA derived query `findByRaceIdAndDriverId()` | SeasonDriver and RaceLineup repos already use this pattern; consistent and zero extra code |
| Conflict resolution logic | Complex merge strategies | Simple delete-source-keep-target | D-03 locked this; no merge of RaceResult points needed |

**Key insight:** The entire duplicate-handling logic requires zero new infrastructure — it reuses the Optional pattern already established in Phase 16's self-merge check and the existing `findBySeasonIdAndDriverId()` / `findByRaceIdAndDriverId()` methods.

## Common Pitfalls

### Pitfall 1: Lazy-loading the `season` / `race` field for log output

**What goes wrong:** `sd.getSeason().getName()` triggers lazy loading. In unit tests with Mockito, the mock entity has no real JPA session, so `getSeason()` returns a mock `Season` with `getName()` returning `null` unless explicitly set up.

**Why it happens:** `SeasonDriver.season` is `@ManyToOne(fetch = FetchType.LAZY)`. Existing `findByDriverId()` uses `@EntityGraph(attributePaths = {"driver", "team"})` — note that `season` is NOT in the graph.

**How to avoid:** In the service, access `sd.getSeason().getId()` for the conflict check (ID is set on the proxy even without loading). For the log message, use `sd.getSeason().getName()` — but be aware it triggers a load. In the transaction context, this is fine. In unit tests, set up `season.setName("TestSeason")` on the mock Season object. Alternatively, log the season ID instead of name to avoid the lazy-load dependency. Decision D-06 says "season name/race name" — this implies the service should log names, so tests must stub the season/race name.

**Warning signs:** `NullPointerException` in tests on `sd.getSeason().getName()` when Season mock is not configured with a name.

### Pitfall 2: Existing `ResultTests` expected counts break after adding dropped fields

**What goes wrong:** The `givenAllFkTypes_whenMerge_thenMergeResultContainsCorrectCounts` test asserts `result.seasonDrivers() == 2`. After adding duplicate-handling, if the test sets up entries without conflicts, the reassigned counts remain unchanged. But if the test accidentally triggers the duplicate-check path, the counts change.

**Why it happens:** The test uses `setupStandardMerge()` which stubs all repos to return empty lists, then overrides specific ones. As long as `findBySeasonIdAndDriverId()` / `findByRaceIdAndDriverId()` / `findByRaceIdAndDriverId()` return `Optional.empty()` (no conflict), existing counts stay correct.

**How to avoid:** Add `when(seasonDriverRepository.findBySeasonIdAndDriverId(any(), eq(targetId))).thenReturn(Optional.empty())` stubs in existing tests, or add these as defaults in `setupStandardMerge()`. The new `@Nested DuplicateHandlingTests` class stubs these to return `Optional.of(conflictEntry)` to exercise the delete path.

**Warning signs:** Mockito `UnnecessaryStubbingException` or Mockito strict mode complaints if stubs are added but not invoked in all test paths. Use `lenient()` stubbing for the no-conflict defaults, or add the stubs only in the `DuplicateHandlingTests` class.

### Pitfall 3: `raceResultsDropped` count vs. `raceResults` count semantics

**What goes wrong:** The `raceResults` field (Phase 16) counts total entries found on the source driver. After Phase 17, some of those may be dropped instead of reassigned. The planner must decide: does `raceResults` mean "reassigned count" or "total found count"?

**Why it happens:** Phase 16 set `raceResults = raceResults.size()` (total found). Phase 17 adds `raceResultsDropped`. The UI (Phase 18) will use these for the success message.

**How to avoid:** Maintain `raceResults` as "reassigned" count (not total), so that `raceResults + raceResultsDropped = total source entries`. This is the natural interpretation given D-05's framing "3 reassigned, 1 duplicate dropped". Update the `merge()` method to count only non-dropped entries in `raceResults`.

**Warning signs:** Existing `givenAllFkTypes_whenMerge_thenMergeResultContainsCorrectCounts` test expects `raceResults == 3` — if all 3 are reassigned (no conflicts in that test), the count stays 3. New duplicate tests create conflicts and expect `raceResultsDropped == 1` + `raceResults == 1` (for 2 total source results with 1 conflict).

### Pitfall 4: Flyway migration inadvertently added

**What goes wrong:** Planner adds a Flyway migration to add UniqueConstraint to `race_lineups`.

**Why it happens:** D-08 explicitly forbids this — mention it in the plan.

**How to avoid:** D-08 is a locked decision. No Flyway migration in this phase. The `RaceLineup` entity remains without `@UniqueConstraint`. Only defensive service logic is added.

## Code Examples

Verified patterns from codebase:

### Existing `findBySeasonIdAndDriverId()` return type
```java
// Source: SeasonDriverRepository.java:19
Optional<SeasonDriver> findBySeasonIdAndDriverId(UUID seasonId, UUID driverId);
```

### Existing `findByRaceIdAndDriverId()` return type (RaceLineupRepository)
```java
// Source: RaceLineupRepository.java:19
Optional<RaceLineup> findByRaceIdAndDriverId(UUID raceId, UUID driverId);
```

### New method to add (RaceResultRepository) — follows identical derivation pattern
```java
// Add to RaceResultRepository.java
Optional<RaceResult> findByRaceIdAndDriverId(UUID raceId, UUID driverId);
```

### Repository delete pattern (from Phase 16)
```java
// Source: DriverMergeService.java:84
driverRepository.delete(source);
```

### Log.info with parameterized format (from Phase 16)
```java
// Source: DriverMergeService.java:94-99
log.info("Driver merge complete: source=[{}] '{}', target=[{}] '{}', " +
        "seasonDrivers={}, raceLineups={}, raceResults={}, aliases={}",
        source.getId(), source.getPsnId(), ...);
```

### Unit test mock setup for Optional (established pattern)
```java
// Source: DriverMergeServiceTest.java:72
when(driverRepository.findById(sourceId)).thenReturn(Optional.empty());
// For duplicate conflict:
when(seasonDriverRepository.findBySeasonIdAndDriverId(seasonId, targetId))
    .thenReturn(Optional.of(existingTargetEntry));
// For no conflict:
when(seasonDriverRepository.findBySeasonIdAndDriverId(seasonId, targetId))
    .thenReturn(Optional.empty());
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito (Spring Boot 4.x managed) |
| Config file | pom.xml (Surefire plugin, lines 184-194) |
| Quick run command | `./mvnw test -Dtest=DriverMergeServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MERGE-11 | SeasonDriver duplicate detected → source deleted, not reassigned | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | Yes (extend existing) |
| MERGE-11 | SeasonDriver no conflict → still reassigned normally | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | Yes (extend existing) |
| MERGE-12 | RaceLineup duplicate detected → source deleted, not reassigned | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | Yes (extend existing) |
| MERGE-12 | RaceLineup no conflict → still reassigned normally | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | Yes (extend existing) |
| MERGE-13 | RaceResult duplicate detected → source deleted, not reassigned | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | Yes (extend existing) |
| MERGE-13 | RaceResult no conflict → still reassigned normally | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | Yes (extend existing) |
| MERGE-11+12+13 | Mixed: some conflict, some not → correct reassigned + dropped counts in MergeResult | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | Yes (extend existing) |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=DriverMergeServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` (full suite green, JaCoCo >= 82%)

### Wave 0 Gaps
None — `DriverMergeServiceTest.java` already exists with full mock infrastructure. New `@Nested DuplicateHandlingTests` class added inside the existing test file. No new test files, no framework config changes.

## Project Constraints (from CLAUDE.md)

| Directive | Impact on Phase 17 |
|-----------|-------------------|
| Minimum 82% line coverage | New code in `merge()` and `RaceResultRepository` must be covered by new tests |
| Do not modify Flyway V1 migration | No Flyway changes in this phase (D-08 already forbids this) |
| TDD: tests first, then implementation | Unit tests for duplicate scenarios written before modifying `merge()` |
| BDD test naming: `givenContext_whenAction_thenExpectedResult()` | New tests follow this pattern |
| No business logic in controllers | Service-only change, no controllers touched |
| DTOs instead of entities in POST controllers | Not applicable — service-only phase |
| OSIV enabled, use @EntityGraph for optimization | No new `@EntityGraph` needed; duplicate-check queries are simple Optional lookups |
| Keep controllers thin | Not applicable — no controller changes |
| log.info() for state changes with parameterized {} format | Dropped-duplicate log messages follow this pattern |
| @RequiredArgsConstructor for constructor injection | Not applicable — no new Spring beans |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `raceResults` field in MergeResult should count only "reassigned" entries (not total found), so the sum `raceResults + raceResultsDropped = total source entries` | Architecture Patterns / Pitfall 3 | If planner counts total in `raceResults`, the Phase 18 UI message would double-count; requires clarification if planner disagrees |

**All other claims were verified against the codebase in this session.**

## Open Questions

1. **`raceResults` count semantics after Phase 17**
   - What we know: Phase 16 sets `raceResults = raceResults.size()` (total found on source). Phase 17 adds `raceResultsDropped`.
   - What's unclear: Should `raceResults` stay as "total" or change to "reassigned only"? D-05 says "3 reassigned, 1 duplicate dropped" which implies "reassigned" is the primary number.
   - Recommendation: Change `raceResults` (and analogously `seasonDrivers`, `raceLineups`) to count only reassigned entries. Update existing `ResultTests` assertions accordingly. The planner has discretion on exact field naming (per CONTEXT.md Claude's Discretion).

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — pure Java service and repository changes, no CLI tools, external services, or runtime environments beyond the existing JVM/Maven stack).

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/domain/service/DriverMergeService.java` — Phase 16 implementation, loop structure, MergeResult record, log pattern
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` — `findBySeasonIdAndDriverId()` method exists at line 19
- `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` — `findByRaceIdAndDriverId()` method exists at line 19
- `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` — `findByRaceIdAndDriverId()` method is absent (confirmed by reading all 24 lines)
- `src/main/java/org/ctc/domain/model/SeasonDriver.java` — UniqueConstraint on (season_id, driver_id) at lines 13-14
- `src/main/java/org/ctc/domain/model/RaceResult.java` — UniqueConstraint on (race_id, driver_id) at lines 15-16
- `src/main/java/org/ctc/domain/model/RaceLineup.java` — No UniqueConstraint (confirmed by reading entire file)
- `src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java` — Full test structure, `setupStandardMerge()` helper, `@Nested` class pattern, all existing assertions
- `.planning/phases/17-duplicate-handling/17-CONTEXT.md` — All locked decisions

### Secondary (MEDIUM confidence)
- `.planning/codebase/TESTING.md` — Test framework versions, JaCoCo 82% threshold, test naming conventions
- `.planning/codebase/CONVENTIONS.md` — Logging pattern, Lombok usage, repository patterns

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all patterns verified in existing codebase
- Architecture: HIGH — loop structure, Optional pattern, delete pattern all verified from Phase 16 code
- Pitfalls: HIGH — derived from direct inspection of entity definitions, test structure, and existing field semantics

**Research date:** 2026-04-07
**Valid until:** Stable domain — valid until Phase 18 changes MergeResult consumption
