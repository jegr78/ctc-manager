---
phase: 72
plan: 01
subsystem: backup
tags: [v1.10, backup, schema, jpa-metamodel, topological-sort, wave-1]
dependency_graph:
  requires:
    - "org.ctc.domain.model.* (24 @Entity classes — read-only Metamodel introspection)"
    - "Spring Boot 4.0.6 JPA Metamodel API (EntityManagerFactory.getMetamodel())"
    - "Jakarta Persistence 3.2 (jakarta.persistence.OneToOne, ManyToOne, Table)"
  provides:
    - "org.ctc.backup.schema.BackupSchema (Spring @Component, SCHEMA_VERSION=1 + exportOrder bean)"
    - "org.ctc.backup.schema.EntityRef (record — Class<?>, String tableName, String fileName)"
    - "org.ctc.backup.schema.EntityTopoSorter (package-private @Component, Kahn over JPA Metamodel)"
  affects:
    - "Future BackupExportService (Phase 73) — consumes BackupSchema.getExportOrder()"
    - "Future BackupImportService (Phase 75) — consumes the same list in reverse for wipe"
tech_stack:
  added:
    - "JPA Metamodel topo-sort (no codebase precedent before this plan)"
  patterns:
    - "Constructor injection via Lombok @RequiredArgsConstructor on final fields"
    - "Spring @Component + @PostConstruct + List.copyOf(...) for immutable startup state"
    - "Package-name filter as structural exclusion mechanism (D-06)"
key_files:
  created:
    - "src/main/java/org/ctc/backup/schema/BackupSchema.java"
    - "src/main/java/org/ctc/backup/schema/EntityRef.java"
    - "src/main/java/org/ctc/backup/schema/EntityTopoSorter.java"
    - "src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java"
    - "src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java"
  modified: []
decisions:
  - "SCHEMA_VERSION is an integer (`int`) constant — not a semver triple — bumped on every wire-incompatible schema change (GAP-2 resolution)"
  - "EntityTopoSorter is package-private to enforce that only BackupSchema may invoke it (D-05 testability extraction)"
  - "Inverse-side @OneToOne(mappedBy=...) attributes must be filtered out via reflection on the underlying Field — the JPA Metamodel reports them as SingularAttribute with ONE_TO_ONE type, identical to the owning side. Without this filter Race ↔ RaceSettings deadlocks Kahn (Rule 1 fix during Task 4)"
metrics:
  duration: "~25 min"
  completed: 2026-05-11
  tasks_executed: 4
  files_created: 5
  files_modified: 0
  commits: 5
---

# Phase 72 Plan 01: BackupSchema + EntityRef + EntityTopoSorter Foundation Summary

**One-liner:** FK-respecting `BackupSchema.exportOrder` derived from the JPA Metamodel via Kahn's algorithm, with `SCHEMA_VERSION = 1` integer constant and structural `org.ctc.domain.model.*` package filter excluding `data_import_audit` (IMPORT-08).

---

## What Was Built

Three production classes plus two Failsafe ITs landing the v1.10 backup wire-contract foundation:

1. **`org.ctc.backup.schema.BackupSchema`** (`@Component`) — Spring-managed singleton exposing:
   - `public static final int SCHEMA_VERSION = 1` (SCHEMA-01).
   - `getExportOrder()` returning an immutable `List<EntityRef>` of 24 operative domain entities in FK-respecting order, populated at startup via `@PostConstruct`.
   - Parameterised `log.info("BackupSchema initialized: SCHEMA_VERSION={}, exportOrder size={}, entities=[{}]", …)` boot record.

2. **`org.ctc.backup.schema.EntityRef`** (Java `record`) — three locked components (D-07):
   - `Class<?> entityClass` — the JPA entity type.
   - `String tableName` — `@Table(name=...)` value or lowercased entity name.
   - `String fileName` — kebab-case derived: `season_phases` → `data/season-phases.json` (matches EXPORT-03 examples).
   - Static factory `fromEntityType(EntityType<?> et)`.

3. **`org.ctc.backup.schema.EntityTopoSorter`** (package-private `@Component`) — Kahn's algorithm over `EntityType<?>` singular attributes:
   - Walks `MANY_TO_ONE` (always owning) and `ONE_TO_ONE` (owning side only — see Deviations below).
   - Self-FK skip: `if (depClass.equals(ownerClass)) continue;` for `Team.parentTeam`.
   - Cycle guard: `IllegalStateException` if the result size diverges from the input size.

4. **`BackupSchemaTopologyIT`** (Failsafe) — 4 `given..._when..._then...` tests:
   - `givenSpringContext_whenGetExportOrder_thenReturns24Entities` — asserts size 24.
   - `givenSpringContext_whenGetExportOrder_thenManyToOneDependenciesPrecedeOwners` — reflective walk of every `@ManyToOne` / owning `@OneToOne` field asserts dependency index < owner index.
   - `givenSpringContext_whenGetExportOrder_thenReturnedListIsImmutable` — `List.copyOf(...)` rejects `add(...)` with `UnsupportedOperationException`.
   - `givenTeamSelfFK_whenGetExportOrder_thenTeamAppearsExactlyOnce` — `Team` count == 1 despite `parentTeam` self-FK.

5. **`BackupSchemaExclusionIT`** (Failsafe) — 1 test asserting `exportOrder` contains neither the FQN `org.ctc.backup.audit.DataImportAudit` nor the table name `data_import_audit`. Uses an FQN-string compare so it compiles in Wave 0 before plan 04 lands `DataImportAudit`.

---

## Wave 0 → GREEN Transition

| IT class | Wave 0 state | Final state |
| --- | --- | --- |
| `BackupSchemaTopologyIT` | RED — `cannot find symbol: class BackupSchema` (compile failure) | GREEN — 4 / 4 tests pass |
| `BackupSchemaExclusionIT` | RED — same compile failure | GREEN — 1 / 1 test passes |

Run command: `./mvnw -Dsurefire.failIfNoSpecifiedTests=false -Dtest='_NoUnit_' -Dit.test='BackupSchemaTopologyIT,BackupSchemaExclusionIT' verify -Pe2e`

Failsafe report excerpt:
```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 — BackupSchemaTopologyIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 — BackupSchemaExclusionIT
```

---

## Topo-Sort Observation

Sample boot output (order is deterministic per run but the queue draining is depth-stable, not lexicographic — sample only):

```
BackupSchema initialized: SCHEMA_VERSION=1, exportOrder size=24,
entities=[seasons, tracks, teams, cars, drivers, match_scorings, race_scorings,
         season_teams, psn_aliases, season_drivers, season_phases, playoffs,
         season_phase_groups, playoff_seeds, playoff_rounds, phase_teams,
         matchdays, playoff_matchups, matches, races, race_attachments,
         race_settings, race_results, race_lineups]
```

Key invariants observed:
- FK-leaves at the head: `seasons`, `tracks`, `cars`, `drivers`, `match_scorings`, `race_scorings`, `teams` (no domain-model FKs incoming).
- `Team` appears exactly once at depth 0 — `Team.parentTeam` self-FK does not deadlock Kahn.
- FK-deep entities at the tail: `races` → `race_attachments`, `race_settings`, `race_results`, `race_lineups`.
- `playoff_rounds` sits between `playoffs` and `playoff_matchups` — confirms D-03 amendment (24 entities including `PlayoffRound`).
- 24-entity count confirmed: `1 + 23` (24 with `PlayoffRound` reconciled per D-03 amendment).

---

## Resolved Entity Count

D-03 originally said 23 entities; the amendment in CONTEXT.md authorised **24** (adding `PlayoffRound`). The runtime `Metamodel.getEntities()` filter returns exactly 24 classes from `org.ctc.domain.model.*`:

`Car, Track, Season, SeasonPhase, SeasonPhaseGroup, Team, PhaseTeam, SeasonTeam, Driver, SeasonDriver, PsnAlias, RaceScoring, MatchScoring, RaceSettings, Matchday, Match, Race, RaceLineup, RaceResult, RaceAttachment, Playoff, PlayoffRound, PlayoffMatchup, PlayoffSeed`

`BaseEntity` (`@MappedSuperclass`) is correctly excluded — confirmed by Metamodel filter.

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Inverse-side `@OneToOne(mappedBy)` produced false topo edges**

- **Found during:** Task 4 (first run of `BackupSchemaTopologyIT`).
- **Symptom:** `IllegalStateException: Topo-sort produced 19 entries, expected 24 — likely an unexpected cycle outside the known Team.parentTeam self-FK`.
- **Root cause:** The plan-prescribed implementation (verbatim from RESEARCH §Pattern 1) asserts "SingularAttribute covers ManyToOne (always owning) + OneToOne owning side". This is incorrect: the JPA Metamodel emits a `SingularAttribute` with `PersistentAttributeType.ONE_TO_ONE` for **both** sides of a `@OneToOne` association, including the inverse `mappedBy` side. Without filtering, `Race`'s `@OneToOne(mappedBy = "race") RaceSettings settings` field registered a `RaceSettings → Race` edge, while `RaceSettings`'s `@OneToOne Race race` owning field registered the reverse `Race → RaceSettings` edge. The mutual edges form a length-2 cycle that Kahn's queue cannot drain, dropping all 5 entities on the affected branch (24 − 19 = 5).
- **Fix:** Added private static helper `isInverseOneToOne(SingularAttribute<?, ?> attr)` to `EntityTopoSorter` that reflects the underlying `Field` and reads `@OneToOne#mappedBy()`. If `mappedBy` is non-empty, the attribute backs the inverse side and the edge is skipped.
- **Files modified:** `src/main/java/org/ctc/backup/schema/EntityTopoSorter.java` (added imports `jakarta.persistence.OneToOne`, `java.lang.reflect.Field`, `java.lang.reflect.Member`; added `isInverseOneToOne(...)`; one new `if` early-exit in the edge loop).
- **Commit:** `e95a1b4` (`fix(72-01): exclude inverse-side @OneToOne(mappedBy) from topo edges`).
- **Test impact:** `BackupSchemaTopologyIT` flipped from RED (cycle exception) → GREEN (4/4 tests pass).

No other deviations.

---

## Forward Pointer to Plan 04

Plan 04 lands the actual `org.ctc.backup.audit.DataImportAudit` JPA entity + repository. At that point:
- `BackupSchemaExclusionIT.DATA_IMPORT_AUDIT_FQN` string constant **may** be replaced with a direct `import org.ctc.backup.audit.DataImportAudit;` and a `.entityClass()` equality compare (`assertThat(ref.entityClass()).isNotEqualTo(DataImportAudit.class)`).
- The behaviour does NOT change — `DataImportAudit` lives under `org.ctc.backup.audit`, which the `org.ctc.domain.model` package-name gate in `BackupSchema.initializeExportOrder()` already structurally rejects.

---

## Commits (this plan)

| # | Hash | Message |
| - | --- | --- |
| 1 | `aa40e86` | `test(72-01): add Wave 0 IT stubs for BackupSchema (RED)` |
| 2 | `57e0db8` | `feat(72-01): add EntityRef record + fromEntityType factory` |
| 3 | `8b1c24b` | `feat(72-01): add EntityTopoSorter (Kahn's algorithm helper)` |
| 4 | `e95a1b4` | `fix(72-01): exclude inverse-side @OneToOne(mappedBy) from topo edges` |
| 5 | `dc2fe73` | `feat(72-01): add BackupSchema @Component with SCHEMA_VERSION + exportOrder` |

---

## Requirements Coverage

| Req ID | Status | Evidence |
| --- | --- | --- |
| SCHEMA-01 | ✅ Complete | `BackupSchema.SCHEMA_VERSION = 1` is a `public static final int` (commit `dc2fe73`). |
| IMPORT-08 | ✅ Complete | `BackupSchema.initializeExportOrder()` filters via `getPackage().getName().startsWith("org.ctc.domain.model")` — structural exclusion gate. Verified by `BackupSchemaExclusionIT` (FQN compare + table-name compare, both PASS). |

---

## Self-Check: PASSED

**Created files exist:**
- `src/main/java/org/ctc/backup/schema/BackupSchema.java` — FOUND
- `src/main/java/org/ctc/backup/schema/EntityRef.java` — FOUND
- `src/main/java/org/ctc/backup/schema/EntityTopoSorter.java` — FOUND
- `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java` — FOUND
- `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java` — FOUND

**Commits exist on `worktree-agent-ade16b2e2007da0e3` branch (verified via `git log`):**
- `aa40e86` — FOUND
- `57e0db8` — FOUND
- `8b1c24b` — FOUND
- `e95a1b4` — FOUND
- `dc2fe73` — FOUND

**Tests GREEN:**
- `BackupSchemaTopologyIT` — 4 tests / 0 failures / 0 errors (Failsafe report `target/failsafe-reports/org.ctc.backup.schema.BackupSchemaTopologyIT.txt`).
- `BackupSchemaExclusionIT` — 1 test / 0 failures / 0 errors (Failsafe report `target/failsafe-reports/org.ctc.backup.schema.BackupSchemaExclusionIT.txt`).
