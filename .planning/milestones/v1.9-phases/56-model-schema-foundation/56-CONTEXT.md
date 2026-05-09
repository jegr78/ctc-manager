# Phase 56: Model & Schema Foundation - Context

**Gathered:** 2026-04-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Lay down the new three-level Season → Phase → Group hierarchy as compilable JPA entities backed by a Flyway DDL migration that creates the `season_phases`, `season_phase_groups`, and `phase_teams` tables (H2 + MariaDB), plus add new `phase_id` FK columns on `matchdays` and `playoffs` alongside the existing `season_id` columns.

**Explicitly out of scope for Phase 56** (locked by ROADMAP/STATE):
- Data migration (REGULAR phase backfill, matchday/playoff re-keying) — Phase 57.
- Service-layer rewrite (`StandingsService`, `PlayoffService`, `SeasonPhaseService`, etc.) — Phase 58.
- Driver import & test/dev data rebuild — Phase 59.
- Admin UI (forms, tabs, group sub-tabs) — Phase 60.
- Drop of old `seasons` columns and `playoff_seasons` join table; flip of `phase_id` to NOT NULL — Phase 61 (MIGR-06).

</domain>

<decisions>
## Implementation Decisions

### Entity Java-side scope (D-01)
- **D-01:** Phase 56 is **parallel additive**. The three new entities are added (`SeasonPhase`, `SeasonPhaseGroup`, `PhaseTeam`) AND the existing entities receive new bidirectional fields **alongside** their old ones:
  - `Season` gets `@OneToMany List<SeasonPhase> phases` (cascade=ALL, orphanRemoval=true, `@OrderBy("sortIndex ASC")`) — analogous to `Season.matchdays`.
  - `Matchday` gets `@ManyToOne SeasonPhase phase` (LAZY, JoinColumn `phase_id`, **nullable**) AND optional `@ManyToOne SeasonPhaseGroup group` (JoinColumn `group_id`, nullable). The existing `Matchday.season` field stays untouched.
  - `Playoff` gets `@ManyToOne SeasonPhase phase` (LAZY, JoinColumn `phase_id`, nullable, UNIQUE). The existing `Playoff.season`, `Playoff.seasons` (M:N), and the M:N join table `playoff_seasons` are **not** touched in Phase 56 — they stay until Phase 61.
- All previously movable `Season` fields (`format`, `totalRounds`, `legs`, `eventDurationMinutes`, `startDate`, `endDate`, `raceScoring`, `matchScoring`) **stay on `Season`** for Phase 56. ROADMAP-Phase-56-SC3 wording ("Season entity no longer carries…") is interpreted as Phase 61's deliverable, not Phase 56's — see Deferred-Cleanup note below. Phase 56 keeps the existing services compiling and running unchanged.
- Foundation `BaseEntity` (`createdAt`/`updatedAt`) is extended by all three new entities. Lombok pattern: `@Getter @Setter @NoArgsConstructor @ToString(exclude = {…collections, parents…})`.

### Flyway migration & FK nullability (D-02)
- **D-02:** New migration file `V3__add_season_phase_tables.sql` (single file) adds:
  1. Tables `season_phases`, `season_phase_groups`, `phase_teams` (H2 + MariaDB compatible DDL).
  2. Columns `matchdays.phase_id UUID NULL`, `matchdays.group_id UUID NULL`, `playoffs.phase_id UUID NULL UNIQUE`.
  3. FK constraints `fk_matchday_phase`, `fk_matchday_group`, `fk_playoff_phase`, `fk_phaseteam_phase`, `fk_phaseteam_team`, `fk_phaseteam_group`, `fk_seasonphase_season`, `fk_seasonphase_race_scoring`, `fk_seasonphase_match_scoring`, `fk_seasonphasegroup_phase` — all with `ON DELETE RESTRICT` (DB default; JPA `orphanRemoval=true` handles cascade at the application level, matching the existing `Season.matchdays` pattern).
  4. FK indexes for every new FK column (consistent with V2 strategy).
- Phase 56 leaves `phase_id` columns **NULLABLE**. Phase 57 backfills data and flips them to `NOT NULL` in the same migration.
- File order: V3 is the next version; planner may split into V3 (phase tables) + V4 (matchdays/playoffs FK columns) if it reduces single-file size, but a single V3 is the default. Either way, all migrations are additive — V1 and V2 are checksum-frozen.

### DB-level uniqueness constraints (D-03)
- **D-03:** `UNIQUE (season_id, phase_type)` on `season_phases` — enforces max 1× of each `phase_type` (REGULAR / PLAYOFF / PLACEMENT) per season at the database level. H2 + MariaDB portable, simple SQL, no partial-index gymnastics. Matches ROADMAP-SC4 ("enforced at the database level").
- `UNIQUE (phase_id, team_id)` on `phase_teams` — enforces a team appears at most once per phase (group_id may differ but is a refinement, not a relaxation). Matches REQUIREMENT MODEL-04.
- `phase_type` and `layout` are persisted as `VARCHAR(20)` with `@Enumerated(EnumType.STRING)`. No DB-level CHECK constraint — JPA `@Enumerated(EnumType.STRING)` plus the typed enum guarantees only valid values from the application side.
- Service-layer guard against duplicate REGULAR/PLAYOFF/PLACEMENT in `SeasonPhaseService` is **not** Phase 56's responsibility — it lives in Phase 58.

### Enum strategy (D-04, D-05)
- **D-04:** Existing `org.ctc.domain.model.SeasonFormat` (LEAGUE / SWISS / ROUND_ROBIN) is **reused** for `SeasonPhase.format`. No rename to `PhaseFormat`. Rationale: semantic match (the format value didn't change meaning, just the entity that carries it), avoids touching unrelated callers, no DB-string-value churn.
- **D-05:** Two new top-level enums in `org.ctc.domain.model`, each in its own file:
  - `PhaseType` — `REGULAR`, `PLAYOFF`, `PLACEMENT`.
  - `PhaseLayout` — `LEAGUE`, `GROUPS`, `BRACKET`.
- Both annotated with `@Enumerated(EnumType.STRING)` on the corresponding `SeasonPhase` fields. Convention matches `SeasonFormat.java` and `AttachmentType.java`.

### Repository scope
- **D-06:** New repositories ship with **default Spring Data CRUD only** (`extends JpaRepository<Entity, UUID>`). No custom finders in Phase 56 — Phase 58 introduces phase-/group-aware queries (`findBySeasonAndPhaseType`, `findByPhaseAndGroup`, etc.) when the services that need them are built.

### Claude's Discretion
- Number of Flyway migration files (single V3 vs. V3 + V4 split) — planner picks based on size/readability; both are acceptable as long as Phase 56 ships only the additive DDL described in D-02.
- Exact placement of `@EntityGraph` annotations on the new repositories — convention says "on collection-returning methods", but Phase 56's repos have no such methods yet. Planner may seed simple `@EntityGraph` defaults if needed for tests; otherwise defer.
- Cascade detail on `Season.phases` — default is `CascadeType.ALL + orphanRemoval=true` mirroring `Season.matchdays`; planner may narrow if a concrete reason emerges.
- Test scope (unit vs. integration) for Phase 56 — recommendation: `@DataJpaTest` integration tests covering (a) all three entities persist + reload, (b) UNIQUE constraints fail on duplicates, (c) Flyway migration runs cleanly on H2. Planner finalizes.

</decisions>

<specifics>
## Specific Ideas

- "Saison bleibt eindeutige Klammer mit `year + number`" — `Season.year` and `Season.number` semantics unchanged; the new model lets `findByYearAndNumber(int, int)` be unambiguous (consumed in Phase 59).
- "Eine Tabelle, einfache Queries, kein Polymorphismus" — `Matchday` keeps a single FK column to phase plus an optional `group_id`; no polymorphic `Matchday`/`GroupMatchday` split.
- "PhaseTeam neben SeasonTeam" — clean separation: `PhaseTeam` = roster ("team plays in this phase/group"); `SeasonTeam` = season-wide overrides (colors, logo, succession). Both coexist permanently.
- "Bestand: rein mechanische Migration" — Phase 56 prepares the schema so Phase 57 can deterministically materialise 1× REGULAR phase per existing season; no heuristics for old group-workaround seasons (those stay as separate seasons; manual UI consolidation deferred to CONSOL-FUT-01).
- Group membership of a driver is implicit via `SeasonDriver.team` → `PhaseTeam` of the REGULAR phase. No per-driver group field on `SeasonDriver` (locked).

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Foundation & requirements (read before planning)
- `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` — Architecture plan from brainstorming session; defines the three-level hierarchy, field migration list, FK relocation strategy, and the six-step Flyway sequence. **MUST read** — non-negotiable design source.
- `.planning/REQUIREMENTS.md` §MODEL-01..08, MIGR-01, MIGR-07 — Locked requirements scoped to this phase.
- `.planning/ROADMAP.md` §"Phase 56: Model & Schema Foundation" — Goal, success criteria, dependency boundary.
- `.planning/STATE.md` §"Key Technical Context" — Field migration list (Season → SeasonPhase), critical files (~25–30 modify, ~12–15 create), v1.9 phase split rationale.
- `.planning/PROJECT.md` §"Current Milestone: v1.9 Season Phases & Groups" — Milestone goal and target features.

### Project conventions (binding)
- `CLAUDE.md` §"Constraints" — Flyway V1 immutable, OSIV enabled, 82% coverage minimum, H2 + MariaDB compatibility, no breaking URL changes.
- `CLAUDE.md` §"Architectural Principles" — "Do Not Modify Flyway Migrations" rule (V*-files frozen after release; new V-file required for any schema change).
- `.planning/codebase/CONVENTIONS.md` — Naming patterns (entities singular PascalCase, repos `{Entity}Repository`, snake_case columns).
- `.planning/codebase/STRUCTURE.md` §"Where to Add New Code" → "New Entity" — File locations for entity / repository / migration / test data.

### Existing code (read for pattern alignment)
- `src/main/java/org/ctc/domain/model/Season.java` — Pattern to align with (Lombok annotations, `@OneToMany` with `mappedBy` + `@OrderBy`, convenience methods); fields that the new `SeasonPhase` will eventually own.
- `src/main/java/org/ctc/domain/model/Matchday.java` — Receives new `phase` and optional `group` fields in Phase 56.
- `src/main/java/org/ctc/domain/model/Playoff.java` — Receives new `phase` field in Phase 56; existing `season` and M:N `seasons` stay until Phase 61.
- `src/main/java/org/ctc/domain/model/BaseEntity.java` — `@MappedSuperclass` parent for all three new entities.
- `src/main/java/org/ctc/domain/model/SeasonFormat.java` — Reused by `SeasonPhase.format` (D-04).
- `src/main/java/org/ctc/domain/model/SeasonTeam.java` — Reference for the join-entity pattern used by `PhaseTeam`.
- `src/main/resources/db/migration/V1__initial_schema.sql` — Reference for DDL style (UUID PKs, FK naming `fk_<short>_<target>`, audit columns, UNIQUE keys).
- `src/main/resources/db/migration/V2__add_fk_indexes.sql` — Reference for FK index naming convention (`idx_<table>_<column>`).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`BaseEntity`** (`@MappedSuperclass`, JPA Auditing): every new entity extends it; gives `createdAt` / `updatedAt` automatically.
- **Lombok pattern** (`@Getter @Setter @NoArgsConstructor @ToString(exclude = …)`): reused on all three new entities — keeps boilerplate consistent.
- **`SeasonFormat` enum**: reused as-is for `SeasonPhase.format` (D-04).
- **`SeasonTeam` join-entity pattern**: closest analog for `PhaseTeam` — same UUID-PK + bidirectional `@ManyToOne` style; same UNIQUE constraint pattern (`uk_season_team` → new `uk_phase_team`).
- **V2 migration FK-index pattern**: planner reuses `idx_<table>_<column>` naming for new indexes on `season_phases.season_id`, `season_phase_groups.phase_id`, `phase_teams.phase_id`, `phase_teams.team_id`, `phase_teams.group_id`, `matchdays.phase_id`, `matchdays.group_id`, `playoffs.phase_id`.

### Established Patterns
- **Bidirectional `@OneToMany` + `mappedBy` + `@OrderBy`**: `Season.matchdays` is the template for `Season.phases` and `SeasonPhase.groups`.
- **`@JoinColumn(name = "<entity>_id", nullable = …)`**: snake_case column names matching `season_id`, `team_id`, etc. New columns: `phase_id`, `group_id`, `season_phase_id` is **not** used (D-locked: column name is `phase_id`).
- **`@Enumerated(EnumType.STRING)` + `VARCHAR(20)` column**: `Season.format` is the precedent; reused for `phase_type`, `layout`, `format` on `season_phases`.
- **JPA `orphanRemoval=true` + `CascadeType.ALL`** for parent-owned children (e.g. `Season.matchdays`); pattern reused for `Season.phases` and `SeasonPhase.groups`.
- **OSIV enabled** (`spring.jpa.open-in-view=true` in `application.yml`): LAZY fetch is the default and safe; `@EntityGraph` is the optimization tool when collection access matters in templates.

### Integration Points
- `Season.phases` collection is added but stays empty after Phase 56 (no service writes to it yet); first inserts happen in Phase 57 data migration.
- `Matchday.phase` and `Playoff.phase` are added but stay null after Phase 56; populated by Phase 57 data migration.
- `phase_teams` table exists but is empty after Phase 56; Phase 57 derives its rows from existing `season_team` entries.
- `MatchdayRepository`, `PlayoffRepository`, `SeasonRepository` are **not** modified in Phase 56 — they keep their existing query methods. New `SeasonPhaseRepository`, `SeasonPhaseGroupRepository`, `PhaseTeamRepository` ship with default JpaRepository CRUD only (D-06).
- `TestDataService`, `DevDataSeeder`, `DemoDataSeeder` are **not** modified in Phase 56 — Phase 59 rebuilds them on the new model.

</code_context>

<deferred>
## Deferred Ideas

- **Drop of old `seasons` columns** (`format`, `total_rounds`, `legs`, `event_duration_minutes`, `start_date`, `end_date`, `race_scoring_id`, `match_scoring_id`) and removal of those fields from the `Season` Java entity — **Phase 61** (MIGR-06). Phase 56 leaves them in place so existing services and templates keep compiling.
- **Drop of M:N `playoff_seasons` join table** and removal of `Playoff.seasons` collection — **Phase 61**.
- **Flip `matchdays.phase_id` / `playoffs.phase_id` to NOT NULL** — **Phase 57** (after data migration backfills the values).
- **Custom phase/group-aware repository finders** (`findBySeasonAndPhaseType`, `findByPhaseAndGroupOrderBySortIndex`, etc.) — **Phase 58** when the services consuming them are built.
- **`SeasonPhaseService` (CRUD + roster management) and `SeasonPhaseController` / forms / templates** — Phases 58 and 60 respectively.
- **Service-layer guard against duplicate REGULAR/PLAYOFF/PLACEMENT phases** (belt-and-suspenders to the DB UNIQUE) — **Phase 58** in `SeasonPhaseService.create()`.
- **Sub-Group-aware Playoff brackets** (`PLAYOFF-FUT-01`) — modelled but not exposed; default stays a single shared bracket per PLAYOFF phase. Future milestone.
- **UI-driven consolidation of legacy group-workaround seasons** (`CONSOL-FUT-01`) — manual operator action via future UI, not part of v1.9.
- **Phase-/Group-Override column in driver-import sheet** (`IMPORT-FUT-01`) — not introduced; group resolves implicitly via team.

</deferred>

---

*Phase: 56-model-schema-foundation*
*Context gathered: 2026-04-26*
