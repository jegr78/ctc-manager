---
phase: 56-model-schema-foundation
plan: "02"
type: execute
status: complete
completed: 2026-04-26
subsystem: domain.model
tags:
  - jpa
  - entity
  - repository
  - milestone-v1.9
requirements:
  - MODEL-01
  - MODEL-03
  - MODEL-04
  - MODEL-08
dependency_graph:
  requires:
    - 56-01 (PhaseType + PhaseLayout enums in org.ctc.domain.model)
    - SeasonFormat (existing — reused per D-04)
    - BaseEntity (audit superclass)
  provides:
    - SeasonPhase entity (org.ctc.domain.model)
    - SeasonPhaseGroup entity (org.ctc.domain.model)
    - PhaseTeam entity (org.ctc.domain.model)
    - SeasonPhaseRepository (default CRUD)
    - SeasonPhaseGroupRepository (default CRUD)
    - PhaseTeamRepository (default CRUD)
  affects:
    - none — purely additive; no existing class touched
tech-stack:
  added:
    - none (Java 25, Spring Boot 4.x, Hibernate, Lombok already in project)
  patterns:
    - Lombok entity pattern (@Getter @Setter @NoArgsConstructor @ToString(exclude=…))
    - extends BaseEntity (audit columns via JPA Auditing)
    - @Enumerated(EnumType.STRING) for all three enum fields on SeasonPhase
    - @ManyToOne(fetch = FetchType.LAZY) + snake_case @JoinColumn
    - @OneToMany(mappedBy=…) bidirectional collection with @OrderBy + cascade=ALL + orphanRemoval=true
    - default JpaRepository<Entity, UUID> only (no custom finders) per D-08
key-files:
  created:
    - src/main/java/org/ctc/domain/model/SeasonPhase.java
    - src/main/java/org/ctc/domain/model/SeasonPhaseGroup.java
    - src/main/java/org/ctc/domain/model/PhaseTeam.java
    - src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java
    - src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java
    - src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java
  modified: []
decisions:
  - "Reused existing SeasonFormat enum on SeasonPhase.format per D-04 (no PhaseFormat introduced)."
  - "PhaseTeam.group is nullable — group is optional, refinement of phase membership (D-01)."
  - "No @Table(uniqueConstraints=…) on PhaseTeam — UNIQUE (phase_id, team_id) is a DB-level concern (D-03), enforced by V3 migration in plan 56-03."
  - "Repositories ship with default JpaRepository<Entity, UUID> only per D-08 — custom finders deferred to Phase 58."
metrics:
  tasks_completed: 2
  files_created: 6
  files_modified: 0
  duration_minutes: ~12
  completed_at: 2026-04-26
---

# Phase 56 Plan 02: Phase / Group / Team Entities + Repositories Summary

**One-liner:** Three new JPA entities (`SeasonPhase`, `SeasonPhaseGroup`, `PhaseTeam`) plus three default-CRUD `JpaRepository` interfaces — the Java side of the new Season → Phase → Group → Team hierarchy, additive on top of the existing `Season` model.

## What Was Built

Created the compilable Java foundation for milestone v1.9's three-level hierarchy:

- **`SeasonPhase`** — parent phase entity carrying scoring FKs (`raceScoring`, `matchScoring`), three `@Enumerated(EnumType.STRING)` enums (`phaseType`, `layout`, `format`), the movable `Season` fields (`totalRounds`, `legs`, `eventDurationMinutes`, `startDate`, `endDate`, `label`), and a bidirectional `@OneToMany List<SeasonPhaseGroup> groups` collection.
- **`SeasonPhaseGroup`** — child of phase: parent FK `phase_id`, `name`, `sortIndex`. Mirrors `Matchday` shape.
- **`PhaseTeam`** — join entity with three `@ManyToOne` FKs: `phase_id` and `team_id` non-nullable, `group_id` **nullable** (group membership is optional). Mirrors `SeasonTeam` shape; no entity-level UNIQUE.
- **Three repositories** — `SeasonPhaseRepository`, `SeasonPhaseGroupRepository`, `PhaseTeamRepository`, each `extends JpaRepository<Entity, UUID>` with empty bodies (D-08, no custom finders).

All three entities extend `BaseEntity` (inherits `createdAt` / `updatedAt` via `AuditingEntityListener`) and follow the Lombok header convention from `Season.java` / `Matchday.java`.

## Pattern Analogs Reused

| New file | Closest analog | Notes |
|---|---|---|
| `SeasonPhase.java` | `Season.java` | Parent-with-children + scoring FKs + format enum + `@OneToMany` groups (mappedBy="phase"). Verbatim copy of the `legs`, `totalRounds`, `eventDurationMinutes`, `raceScoring`, `matchScoring` field declarations. |
| `SeasonPhaseGroup.java` | `Matchday.java` | Small child entity: parent FK + name + sortIndex; field is `name` (not `label`) per CONTEXT.md. |
| `PhaseTeam.java` | `SeasonTeam.java` | Join entity with two `@ManyToOne` parents + UUID PK. Adds optional third `@ManyToOne` (`group`) — nullable per D-01. |
| `SeasonPhaseRepository.java` (and the two siblings) | `PlayoffRepository.java` (stripped of custom finders) | Default `JpaRepository<Entity, UUID>` baseline per D-08. |

## Verification

- `./mvnw -q compile -DskipTests` exits 0 after Task 2 (Hibernate accepts all three new entities and the bidirectional `SeasonPhase.groups` mapping).
- All 18 acceptance-grep checks for `SeasonPhase` pass (entity header, three `@Enumerated(EnumType.STRING)`, all field declarations including `private int legs` and `@Column(name = "event_duration_minutes")`, `mappedBy = "phase"`, `@OrderBy("sortIndex ASC")`, four-arg constructor, `@ToString` exclusion list).
- All 11 acceptance-grep checks for Task 2 pass (table names, parent FK nullability, `group_id` NOT `nullable=false`, no `uniqueConstraints` on entity, three repositories extend `JpaRepository<…, UUID>` with zero method declarations).
- `grep -rn "uniqueConstraints" src/main/java/org/ctc/domain/model/SeasonPhase.java SeasonPhaseGroup.java PhaseTeam.java` returns no matches.
- `grep -rn "@Query|Optional<|List<.*> find|long count|boolean exists"` on the three new repositories returns no matches.

## Decisions Made

- **D-04 reuse confirmed in code:** `SeasonPhase.format` is typed `SeasonFormat` and defaults to `SeasonFormat.LEAGUE`, matching `Season.format` line for line. No new `PhaseFormat` enum introduced.
- **D-01 group nullability confirmed:** `PhaseTeam.group` carries `@JoinColumn(name = "group_id")` without `nullable = false`. A team in a non-grouped phase (REGULAR/PLAYOFF without GROUPS layout) keeps `group` as `null`.
- **D-03 DB-level UNIQUE preserved:** `PhaseTeam` declares no `@Table(uniqueConstraints = …)`. The `UNIQUE (phase_id, team_id)` constraint is plan 56-03's responsibility (V3 Flyway migration).
- **D-08 honored:** All three new repository interfaces have empty bodies — the orchestrator script `grep -cE '^\s*(Optional|List|Page|Stream|long|int|boolean|void|@Query)' …` returns 0.

## Deviations from Plan

### `[Rule 3 - Blocking]` Compile verification deferred from Task 1 to Task 2

- **Found during:** Task 1 commit
- **Issue:** Task 1's `<verify><automated>` block requires `./mvnw -q compile -DskipTests` to pass after creating only `SeasonPhase.java`. But `SeasonPhase` declares `private List<SeasonPhaseGroup> groups`, and `SeasonPhaseGroup` is created in Task 2. A compile attempt at the end of Task 1 therefore fails with `cannot find symbol: SeasonPhaseGroup`.
- **Fix:** Committed Task 1 with the structural acceptance-grep checks (all 18 passed) and deferred the compile-pass requirement to the end of Task 2, where it is satisfied. The Task 1 commit message documents the deferral. Task 2's `<verify>` already requires the compile-pass and produced exit code 0.
- **Files modified:** none extra — the deviation is purely about ordering of the compile gate, not file content.
- **Commits:** `72df5f8` (Task 1), `3402455` (Task 2)
- **Rationale:** Per CLAUDE.md §"Subagent Rules → Atomic Tasks": "Tasks in the plan must be individually executable. If a change forces multiple tasks, plan them as a single task." The plan has split a non-atomic gate. Splitting `SeasonPhase` into a stub-then-fill sequence would have been more invasive than deferring the compile check by one commit.

No other deviations. No auth gates encountered. No architectural decisions required.

## Notes for Downstream Plans

- **Plan 56-03 (V3 Flyway migration):** Now has all three Java entities to mirror in DDL (`season_phases`, `season_phase_groups`, `phase_teams`), with their column shapes locked by this plan. The `UNIQUE (phase_id, team_id)` on `phase_teams` and `UNIQUE (season_id, phase_type)` on `season_phases` must come from the migration — they are deliberately absent from the entities (D-03).
- **Plan 56-04 (integration tests):** Can now persist + reload all three entities via the new repositories. The `SeasonPhase.groups` bidirectional mapping is ready for end-to-end smoke testing.
- **Plan 56-05 (`Season.phases` + `Matchday.phase`/`group` + `Playoff.phase`):** This plan deliberately did NOT touch `Season.java` / `Matchday.java` / `Playoff.java`. Plan 05 will add the inverse `@OneToMany List<SeasonPhase> phases` on `Season` and the new `@ManyToOne` fields on `Matchday` and `Playoff`. The new `phase_id` FK columns on the existing tables are also Plan 03's scope.
- **Phase 58 (services):** First custom finders on these repositories (`findBySeasonAndPhaseType`, `findByPhaseAndGroupOrderBySortIndex`, etc.) land here when `SeasonPhaseService` is built.

## Self-Check: PASSED

Files verified to exist on disk after final commit:
- `src/main/java/org/ctc/domain/model/SeasonPhase.java` — FOUND
- `src/main/java/org/ctc/domain/model/SeasonPhaseGroup.java` — FOUND
- `src/main/java/org/ctc/domain/model/PhaseTeam.java` — FOUND
- `src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java` — FOUND
- `src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java` — FOUND
- `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java` — FOUND

Commits verified to exist in `git log`:
- `72df5f8` — feat(56-02): add SeasonPhase entity (parent phase with scoring + format) — FOUND
- `3402455` — feat(56-02): add SeasonPhaseGroup + PhaseTeam entities and three repositories — FOUND

Compile gate:
- `./mvnw -q compile -DskipTests` — exit 0 (verified after Task 2)
