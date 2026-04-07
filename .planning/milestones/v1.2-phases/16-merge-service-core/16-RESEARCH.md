# Phase 16: Merge Service Core - Research

**Researched:** 2026-04-07
**Domain:** Spring service layer — transactional FK reassignment with duplicate-safe alias handling
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** New `DriverMergeService` in `domain.service` — separate from existing `DriverService`
- **D-02:** Single `@Transactional` method wrapping all FK reassignments + source deletion — atomic
- **D-03:** Self-merge prevention — `BusinessRuleException` when source == target
- **D-04:** Source and target must exist — `EntityNotFoundException`
- **D-05:** No restriction on active/inactive drivers — inactive drivers are mergeable
- **D-06:** Source driver's PSN-ID becomes a new `PsnAlias` on the target driver (MERGE-09)
- **D-07:** If source PSN-ID already exists as alias on target, skip silently (idempotent) — log as "already exists"
- **D-08:** Existing `PsnAlias` entries of source driver: reassign FK via `alias.setDriver(target)` through repository (not via Driver.aliases collection, due to CascadeType.ALL + orphanRemoval)
- **D-09:** `merge()` returns a `MergeResult` Java record with counts per FK table (seasonDrivers, raceLineups, raceResults, aliasesReassigned) — used by Phase 18 UI
- **D-10:** Additionally log via `log.info()` with structured parameters: source id/name, target id/name, timestamp, counts per FK table (MERGE-14)

### Claude's Discretion

- Method signature details (parameter types, exact record field names)
- Internal ordering of FK reassignment steps
- Repository method additions needed for bulk updates

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MERGE-05 | All SeasonDriver entries reassigned from source to target driver | `SeasonDriverRepository.findByDriverId()` exists; iterate and `setDriver(target)` + save |
| MERGE-06 | All RaceLineup entries reassigned from source to target driver | `RaceLineupRepository` needs new `findByDriverId()` method; same pattern as MERGE-05 |
| MERGE-07 | All RaceResult entries reassigned from source to target driver | `RaceResultRepository.findByDriverId()` exists; same pattern |
| MERGE-08 | All PsnAlias entries transferred to target driver | Use `alias.setDriver(target)` via repository (NOT via Driver.aliases collection — see D-08 pitfall) |
| MERGE-09 | Source PSN-ID added as alias on target driver | Check for existing alias via `PsnAliasRepository.findByAliasIgnoreCase()` before creating; idempotent skip if found |
| MERGE-10 | Source driver deleted after all FK references reassigned | `driverRepository.delete(source)` after all reassignments — safe because all FKs are gone |
| MERGE-14 | Merge action logged (source, target, timestamp, affected references) | `log.info()` with structured `{}` params after merge completes |
</phase_requirements>

---

## Summary

Phase 16 implements a pure service-layer driver merge operation. The core work is transactional FK reassignment across four tables (SeasonDriver, RaceLineup, RaceResult, PsnAlias), followed by deletion of the source driver. All logic lives in a new `DriverMergeService` in `domain.service`, following the exact same architectural patterns as the existing `DriverService`.

The codebase already provides most of the required infrastructure: `findByDriverId()` exists on `SeasonDriverRepository` and `RaceResultRepository`; exception types `BusinessRuleException` and `EntityNotFoundException` are in place; the Java record pattern for return values is established via `DriverService.DriverEditData`. The only missing infrastructure is `RaceLineupRepository.findByDriverId()` and optionally `PsnAliasRepository.findByDriverId()`.

The most significant design constraint is the PsnAlias cascade configuration on `Driver.aliases` (`CascadeType.ALL + orphanRemoval = true`): alias FK reassignment MUST be done through the repository directly (not through the collection), otherwise the orphanRemoval mechanism would delete aliases that are reassigned away from the source driver.

**Primary recommendation:** Implement `DriverMergeService` with a single `@Transactional merge(UUID sourceId, UUID targetId)` method, add `findByDriverId()` to `RaceLineupRepository` and `PsnAliasRepository`, and test with Mockito unit tests following the established `DriverServiceTest` pattern.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring `@Transactional` | Spring Boot 4.x | Atomic wrap of all FK reassignment steps | Project-standard; used in all service methods |
| Spring Data JPA (`JpaRepository`) | Spring Boot 4.x | Repository CRUD and custom query methods | Project-standard for all data access |
| Lombok `@Slf4j`, `@RequiredArgsConstructor`, `@Service` | Latest via Spring Boot | Service class boilerplate | Project-standard in every service class |
| JUnit 5 + Mockito | Via Spring Boot Test | Unit testing with mocked repositories | Project-standard for all domain service tests |
| AssertJ | Via Spring Boot Test | Fluent assertions in tests | Project-standard assertion library |

[VERIFIED: codebase grep — all existing services use these annotations]

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Java record (JDK 25) | JDK 25 | `MergeResult` return value | Structured multi-field return without a full class |

[VERIFIED: `DriverService.DriverEditData` is a Java record at line 38 — same pattern for `MergeResult`]

**Installation:** No new dependencies — all needed libraries are already present in `pom.xml`.

---

## Architecture Patterns

### Service Class Structure

```
src/main/java/org/ctc/domain/service/
├── DriverService.java          (existing — CRUD + alias sync, do not modify)
└── DriverMergeService.java     (new — merge only)

src/main/java/org/ctc/domain/repository/
├── RaceLineupRepository.java   (add findByDriverId)
└── PsnAliasRepository.java     (add findByDriverId)

src/test/java/org/ctc/domain/service/
└── DriverMergeServiceTest.java (new)
```

### Pattern 1: Service Class Skeleton

The established pattern from `DriverService.java`:

```java
// Source: src/main/java/org/ctc/domain/service/DriverService.java (lines 27-33, 38-43)
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverMergeService {

    private final DriverRepository driverRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final RaceResultRepository raceResultRepository;
    private final PsnAliasRepository psnAliasRepository;

    public record MergeResult(
            int seasonDrivers,
            int raceLineups,
            int raceResults,
            int aliasesReassigned
    ) {}

    @Transactional
    public MergeResult merge(UUID sourceId, UUID targetId) {
        // validation
        // FK reassignment
        // source deletion
        // logging
        // return MergeResult
    }
}
```

[VERIFIED: codebase read — mirrors exact pattern from DriverService.java]

### Pattern 2: FK Reassignment via setDriver + save (entity mutation)

The correct approach for SeasonDriver, RaceLineup, RaceResult (no cascade issues):

```java
// For SeasonDriver reassignment (MERGE-05)
var seasonDrivers = seasonDriverRepository.findByDriverId(sourceId);
for (var sd : seasonDrivers) {
    sd.setDriver(target);
    seasonDriverRepository.save(sd);
}
return seasonDrivers.size();
```

The same pattern applies for RaceLineup and RaceResult. `setDriver()` is available because all entities use `@Getter @Setter`. [VERIFIED: codebase read — SeasonDriver.java line 16, RaceLineup.java line 14, RaceResult.java line 17]

### Pattern 3: PsnAlias Reassignment via Repository (CRITICAL — avoid orphanRemoval trap)

`Driver.aliases` has `CascadeType.ALL + orphanRemoval = true`. If you reassign via the collection (remove from source, add to target), orphanRemoval deletes the removed alias before the add completes. The safe approach:

```java
// CORRECT: Bypass the collection, use repository directly (D-08)
var aliases = psnAliasRepository.findByDriverId(sourceId);
for (var alias : aliases) {
    alias.setDriver(target);
    psnAliasRepository.save(alias);
}
```

```java
// WRONG: do NOT do this
source.getAliases().remove(alias);   // orphanRemoval fires DELETE
target.getAliases().add(alias);      // too late
```

[VERIFIED: Driver.java line 40 — `cascade = CascadeType.ALL, orphanRemoval = true`]

### Pattern 4: PSN-ID → Alias Transfer (MERGE-09) with Idempotency

```java
// D-06 + D-07: Add source PSN-ID as alias on target, skip if already present
String sourcePsnId = source.getPsnId();
boolean alreadyExists = psnAliasRepository.existsByAliasIgnoreCase(sourcePsnId);
if (alreadyExists) {
    log.info("PSN alias '{}' already exists on target driver, skipping", sourcePsnId);
    // aliasesReassigned count does NOT increment
} else {
    psnAliasRepository.save(new PsnAlias(target, sourcePsnId));
    // aliasesReassigned count increments
}
```

`existsByAliasIgnoreCase` already exists on `PsnAliasRepository`. [VERIFIED: PsnAliasRepository.java line 13]

### Pattern 5: Audit Logging (MERGE-14)

```java
// D-10: log after merge completes
log.info("Driver merge complete: source=[{}] '{}', target=[{}] '{}', " +
         "seasonDrivers={}, raceLineups={}, raceResults={}, aliases={}, timestamp={}",
         source.getId(), source.getPsnId(),
         target.getId(), target.getPsnId(),
         result.seasonDrivers(), result.raceLineups(),
         result.raceResults(), result.aliasesReassigned(),
         java.time.Instant.now());
```

[VERIFIED: CLAUDE.md — "log.info() for state changes", "Always use parameterized {} format"]

### Pattern 6: Validation Guards

```java
// D-03: Self-merge prevention
if (sourceId.equals(targetId)) {
    throw new BusinessRuleException("Cannot merge driver with itself");
}

// D-04: Entity existence
var source = driverRepository.findById(sourceId)
        .orElseThrow(() -> new EntityNotFoundException("Driver", sourceId));
var target = driverRepository.findById(targetId)
        .orElseThrow(() -> new EntityNotFoundException("Driver", targetId));
```

[VERIFIED: codebase read — BusinessRuleException.java, EntityNotFoundException.java, DriverService.java lines 88-94]

### Anti-Patterns to Avoid

- **Modifying Driver.aliases collection directly:** The `orphanRemoval = true` on `Driver.aliases` means removing an alias from the source's collection triggers an immediate `DELETE` — the alias never reaches the target. Always use `psnAliasRepository.save()` with `alias.setDriver(target)`.
- **Multiple @Transactional methods:** Do not split the merge into multiple transactional calls. One transaction = one atomic operation (D-02).
- **Calling DriverService.delete() at the end:** The existing `DriverService.delete()` wraps in `DataIntegrityViolationException` handling — that's for the case where FK references still exist. After FK reassignment, call `driverRepository.delete(source)` directly.
- **Using DriverService from DriverMergeService:** Do not inject `DriverService` into `DriverMergeService` — inject repositories directly. This avoids transaction nesting and keeps responsibilities clear (D-01, CONTEXT.md code_context lines 88-89).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Idempotent alias check | Custom duplicate-check logic | `PsnAliasRepository.existsByAliasIgnoreCase()` | Already on the repository; case-insensitive |
| Entity lookup with not-found error | Null check + manual exception | `EntityNotFoundException` constructor | Project standard; consistent error messages |
| Structured return value | Map or array of counts | Java record `MergeResult` | Established pattern (`DriverEditData`), type-safe |

---

## Repository Gaps — New Methods Required

Two repositories need new query methods before the service can be implemented.

### RaceLineupRepository — add `findByDriverId`

Currently only has `findByRaceIdAndDriverId`. Required for MERGE-06:

```java
// Add to RaceLineupRepository.java
List<RaceLineup> findByDriverId(UUID driverId);
```

No `@EntityGraph` needed for merge (only `driver` field is accessed for setDriver).

[VERIFIED: RaceLineupRepository.java — no findByDriverId exists]

### PsnAliasRepository — add `findByDriverId`

Currently no bulk lookup by driver. Required for MERGE-08:

```java
// Add to PsnAliasRepository.java
List<PsnAlias> findByDriverId(UUID driverId);
```

[VERIFIED: PsnAliasRepository.java — only `findByAliasIgnoreCase` and `existsByAliasIgnoreCase`]

---

## Common Pitfalls

### Pitfall 1: orphanRemoval Alias Deletion
**What goes wrong:** Developer reassigns PsnAlias by removing from `source.getAliases()` and adding to `target.getAliases()`. The `orphanRemoval = true` causes Hibernate to issue a `DELETE` for the alias when removed from the source collection — the alias is gone before it can be added to the target.
**Why it happens:** `CascadeType.ALL + orphanRemoval = true` on `Driver.aliases` means "if an alias is no longer in this collection, delete it."
**How to avoid:** Always bypass the collection. Use `psnAliasRepository.findByDriverId(sourceId)` and `alias.setDriver(target)` + `psnAliasRepository.save(alias)`.
**Warning signs:** Test failure with `TransientPropertyValueException` or alias disappearing from target after merge.

### Pitfall 2: Source Driver Delete Before FK Reassignment
**What goes wrong:** Developer deletes the source driver before reassigning all FK references, causing FK constraint violations.
**Why it happens:** Incorrect step ordering.
**How to avoid:** The canonical step order is: (1) validate, (2) reassign all FKs, (3) transfer PSN-ID as alias, (4) delete source.

### Pitfall 3: Calling DriverService.delete() Instead of Repository Delete
**What goes wrong:** `DriverService.delete()` internally catches `DataIntegrityViolationException` and throws `BusinessRuleException`. If there's a bug in the FK reassignment step and references remain, the merge swallows the FK error and fails silently.
**How to avoid:** Call `driverRepository.delete(source)` directly after reassignment. Any remaining FK constraint violation will propagate as-is and roll back the transaction.

### Pitfall 4: Unique Constraint Violations (out of scope for Phase 16)
**What goes wrong:** SeasonDriver has `UniqueConstraint(season_id, driver_id)` and RaceResult has `UniqueConstraint(race_id, driver_id)`. If both source and target have entries for the same season or same race, reassignment hits a DB constraint.
**Why it doesn't apply here:** Phase 16 is the happy path only (no duplicate detection). Phase 17 adds duplicate handling (MERGE-11, MERGE-12, MERGE-13).
**How to handle in Phase 16:** Let the constraint violation propagate — the `@Transactional` rolls back automatically. The Phase 18 UI can display the error; Phase 17 will handle resolution.

---

## Code Examples

### Unit Test Pattern for DriverMergeService

Following the established pattern from `DriverServiceTest.java`:

```java
// Source: src/test/java/org/ctc/domain/service/DriverServiceTest.java (structure)
@ExtendWith(MockitoExtension.class)
class DriverMergeServiceTest {

    @Mock private DriverRepository driverRepository;
    @Mock private SeasonDriverRepository seasonDriverRepository;
    @Mock private RaceLineupRepository raceLineupRepository;
    @Mock private RaceResultRepository raceResultRepository;
    @Mock private PsnAliasRepository psnAliasRepository;

    @InjectMocks
    private DriverMergeService driverMergeService;

    @Nested
    class MergeTest {

        @Test
        void givenSourceEqualsTarget_whenMerge_thenThrowsBusinessRuleException() {
            // given
            var id = UUID.randomUUID();
            // when / then
            assertThatThrownBy(() -> driverMergeService.merge(id, id))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void givenNonExistentSource_whenMerge_thenThrowsEntityNotFoundException() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            when(driverRepository.findById(sourceId)).thenReturn(Optional.empty());
            // when / then
            assertThatThrownBy(() -> driverMergeService.merge(sourceId, targetId))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void givenValidDrivers_whenMerge_thenAllSeasonDriversReassigned() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "Source_PSN");
            var target = createDriver(targetId, "Target_PSN");
            var sd = createSeasonDriver(source);

            when(driverRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(driverRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of(sd));
            when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of());
            when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of());
            when(psnAliasRepository.findByDriverId(sourceId)).thenReturn(List.of());
            when(psnAliasRepository.existsByAliasIgnoreCase("Source_PSN")).thenReturn(false);

            // when
            var result = driverMergeService.merge(sourceId, targetId);

            // then
            assertThat(sd.getDriver()).isEqualTo(target);
            verify(seasonDriverRepository).save(sd);
            assertThat(result.seasonDrivers()).isEqualTo(1);
        }

        @Test
        void givenValidDrivers_whenMerge_thenSourceDriverDeleted() {
            // ... setup all repos returning empty lists ...
            // then
            verify(driverRepository).delete(source);
        }
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| MatchdayLineup | RaceLineup | v1.1 migration (Phase RaceLineup migration) | FK table for merge is `RaceLineup`, not `MatchdayLineup` |

[VERIFIED: RaceLineupRepository.java — class is RaceLineup, table is race_lineups]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `psnAliasRepository.findByDriverId()` does not yet exist (needs adding) | Repository Gaps | Low — verified by reading PsnAliasRepository.java |
| A2 | `raceLineupRepository.findByDriverId()` does not yet exist (needs adding) | Repository Gaps | Low — verified by reading RaceLineupRepository.java |

All other claims verified by direct codebase inspection.

---

## Open Questions

1. **MergeResult count for idempotent PSN-ID alias skip**
   - What we know: D-07 says skip silently if PSN-ID already exists as alias on target
   - What's unclear: Should `aliasesReassigned` count include the skipped PSN-ID or only count actual new aliases created?
   - Recommendation: Count only actual creations (not skips) — the log line already records the "already exists" case. This is Claude's discretion per CONTEXT.md.

2. **Ordering of FK reassignment steps**
   - What we know: D-02 requires a single transaction; D-05 permits inactive drivers
   - What's unclear: Should aliases be reassigned before or after deletion check?
   - Recommendation: Canonical order — (1) validate, (2) SeasonDriver, (3) RaceLineup, (4) RaceResult, (5) PsnAlias reassignment, (6) PSN-ID alias creation, (7) delete source, (8) log result. This is Claude's discretion per CONTEXT.md.

---

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — pure Java service layer implementation)

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito |
| Config file | `pom.xml` (Surefire plugin, lines 184-194) |
| Quick run command | `./mvnw test -Dtest=DriverMergeServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MERGE-05 | SeasonDriver entries reassigned to target | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ❌ Wave 0 |
| MERGE-06 | RaceLineup entries reassigned to target | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ❌ Wave 0 |
| MERGE-07 | RaceResult entries reassigned to target | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ❌ Wave 0 |
| MERGE-08 | PsnAlias entries reassigned to target | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ❌ Wave 0 |
| MERGE-09 | Source PSN-ID added as alias on target (idempotent) | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ❌ Wave 0 |
| MERGE-10 | Source driver deleted after reassignment | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ❌ Wave 0 |
| MERGE-14 | Merge logged with source/target/counts (via log.info, not directly testable) | unit (verify mock interactions) | `./mvnw test -Dtest=DriverMergeServiceTest` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=DriverMergeServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green (`./mvnw verify`) before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java` — covers MERGE-05 through MERGE-10, MERGE-14
- [ ] `src/main/java/org/ctc/domain/service/DriverMergeService.java` — the service under test

*(Framework install: not needed — JUnit 5 + Mockito already available via Spring Boot Test)*

---

## Security Domain

Phase 16 is a pure service layer with no HTTP endpoints, no user input parsing, and no authentication boundaries. The service is called from the controller in Phase 18 (which will handle HTTP input validation). No ASVS categories apply directly to this phase.

[VERIFIED: CONTEXT.md domain boundary — "service layer only"]

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on This Phase |
|-----------|---------------------|
| Minimum 82% line coverage | `DriverMergeService` must be fully unit tested; all branches (self-merge, not-found, alias skip, happy path) must be covered |
| Keep Controllers Thin | No business logic in any future controller — all merge logic in `DriverMergeService` only |
| DTOs instead of Entities in Controllers | `MergeResult` record is the correct return type for Phase 18 to consume |
| No Fallback Calculations | If merge fails (constraint violation), let the exception propagate — do not silently swallow and compute partial results |
| Do Not Modify Flyway Migrations | Phase 16 requires no schema changes — FK reassignment is pure data mutation on existing schema |
| OSIV Enabled | Not directly relevant to service layer, but confirms lazy loading is safe within the transaction |
| TDD: Red → Green → Refactor | Write `DriverMergeServiceTest` first, then implement `DriverMergeService` |
| Test Naming: givenContext_whenAction_thenResult | All test methods must follow BDD naming |
| Logging: log.info() + {} params | MERGE-14 logging must use parameterized format, never string concatenation |
| Services use @RequiredArgsConstructor + final fields | Constructor injection via `final` repositories — no `@Autowired` |

---

## Sources

### Primary (HIGH confidence)

- [VERIFIED: codebase] `src/main/java/org/ctc/domain/service/DriverService.java` — service pattern, Java record pattern, exception usage
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/model/Driver.java` — CascadeType.ALL + orphanRemoval on aliases
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/model/SeasonDriver.java` — UniqueConstraint(season_id, driver_id)
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/model/RaceLineup.java` — no unique constraint on (race_id, driver_id)
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/model/RaceResult.java` — UniqueConstraint(race_id, driver_id)
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/model/PsnAlias.java` — unique alias column
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` — missing findByDriverId confirmed
- [VERIFIED: codebase] `src/main/java/org/ctc/domain/repository/PsnAliasRepository.java` — missing findByDriverId confirmed, existsByAliasIgnoreCase present
- [VERIFIED: codebase] `src/test/java/org/ctc/domain/service/DriverServiceTest.java` — unit test pattern reference
- [VERIFIED: codebase] `.planning/codebase/TESTING.md` — test infrastructure, coverage thresholds
- [VERIFIED: codebase] `CLAUDE.md` — project constraints, coding conventions

### Secondary (MEDIUM confidence)

None required — all needed information available from direct codebase inspection.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries verified in existing codebase
- Architecture: HIGH — service pattern verified from DriverService.java
- Pitfalls: HIGH — orphanRemoval trap verified from Driver.java model definition
- Repository gaps: HIGH — verified by reading all four repositories

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable codebase, no fast-moving external dependencies)
