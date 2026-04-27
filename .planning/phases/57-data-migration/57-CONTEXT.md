# Phase 57: Data Migration - Context

**Gathered:** 2026-04-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Backfill all existing production data into the new Phase-schema introduced in Phase 56:
- Every `Season` gets exactly **one** `season_phases` row with `phase_type = REGULAR` (fields `format`, `totalRounds`, `legs`, `eventDurationMinutes`, `startDate`, `endDate`, `raceScoring_id`, `matchScoring_id` copied 1:1 from the season).
- Every `Playoff` gets a newly-created `season_phases` row with `phase_type = PLAYOFF` and is linked via `playoff.phase_id`.
- Every `Matchday` gets `phase_id` populated, pointing to its season's REGULAR phase.
- `phase_teams` is populated 1:1 from `season_teams` (REGULAR phase, `group_id = NULL`, LEAGUE layout).
- `matchdays.phase_id` and `playoffs.phase_id` are flipped from NULLABLE to **NOT NULL** in the same migration after backfill (per Phase 56 D-02).

**Explicitly out of scope for Phase 57** (locked by ROADMAP/STATE):
- Drop of old `seasons.format/totalRounds/legs/...` columns and `playoff_seasons` M:N table — Phase 61 (MIGR-06).
- Service-layer rewrite (`StandingsService`, `PlayoffService`, `SeasonPhaseService`) — Phase 58.
- Driver import + test/dev data rebuild — Phase 59.
- Admin UI for phases/groups — Phase 60.
- Group-aware data (sub-group rosters, group matchdays) — out of scope for Phase 57; all migrated phases use LEAGUE layout, no groups.
- Bridge-column drop: `matchday.season_id` and `playoff.season_id` columns **stay intact** in Phase 57 (ROADMAP-SC5) so existing services/templates keep compiling.

</domain>

<decisions>
## Implementation Decisions

### Migration mechanism (D-01..D-05)

- **D-01: Java-based migration.** A single class `V4__MigrateSeasonsToPhases` extends `org.flywaydb.core.api.migration.BaseJavaMigration`. Reason: portable UUID generation across H2 + MariaDB without dialect-specific SQL placeholders, type-safe, easier to log progress, easier to test.
- **D-02: Single migration class with explicit private methods.** The class exposes one `migrate(Context context)` entry point that delegates in fixed order to:
  1. `migrateRegularPhases(JdbcTemplate)`
  2. `migratePlayoffPhases(JdbcTemplate)`
  3. `migrateMatchdayFKs(JdbcTemplate)`
  4. `migratePhaseTeams(JdbcTemplate)`
  5. `flipNotNullConstraints(JdbcTemplate, dialect)`
  Reason: lazy disk reads, one Flyway version entry, deterministic order, atomic recovery on failure.
- **D-03: Package `db.migration`.** File at `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java`. Reason: Spring Boot Flyway scans `db/migration` by default — no extra `spring.flyway.locations` config needed; consistent with `src/main/resources/db/migration/`.
- **D-04: Single transaction (Flyway default).** `canExecuteInTransaction()` returns `true` (default). Whole migration rolls back on any error. MariaDB DDL implicitly commits intermediate steps; for our small data size this trade-off is acceptable.
- **D-05: Fail-fast on data integrity issues.** If a Season has `raceScoring=NULL` or `matchScoring=NULL` (theoretically forbidden by V1 NOT NULL but defensive), throw `org.flywaydb.core.api.FlywayException` with the offending Season's ID and field name. Migration aborts, application does not start. Reason: data integrity > availability for an admin-only tool; clear error pointing at the broken row.

### Field mapping — REGULAR phase (D-06)

- **D-06: REGULAR phase fields are 1:1 copies from Season.**
  - `id` = `UUID.randomUUID()` (Java side)
  - `season_id` = `season.id`
  - `sort_index` = `0`
  - `phase_type` = `'REGULAR'`
  - `layout` = `'LEAGUE'` (no groups in legacy data — group migration deferred to manual UI per CONSOL-FUT-01)
  - `format` = `season.format`
  - `label` = `null` (no semantic label needed for the auto-migrated REGULAR phase; UI can render "Regular Season" as fallback when label is null)
  - `start_date` = `season.start_date`
  - `end_date` = `season.end_date`
  - `total_rounds` = `season.total_rounds`
  - `legs` = `season.legs`
  - `event_duration_minutes` = `season.event_duration_minutes`
  - `race_scoring_id` = `season.race_scoring_id`
  - `match_scoring_id` = `season.match_scoring_id`
  - `created_at` / `updated_at` = `CURRENT_TIMESTAMP`

### Field mapping — PLAYOFF phase (D-07..D-09)

- **D-07: PLAYOFF phase inherits scoring from Season.** `raceScoring` / `matchScoring` for the new PLAYOFF phase are copied directly from `playoff.season.raceScoring` / `playoff.season.matchScoring`. Same effect as inheriting from REGULAR phase (which itself copied from Season). Reason: simplest, matches lived practice (playoffs use same scoring as the regular season unless explicitly changed via UI later).
- **D-08: PLAYOFF phase uses standard defaults for non-Season fields.**
  - `id` = `UUID.randomUUID()`
  - `season_id` = `playoff.season_id`
  - `sort_index` = `10` (after REGULAR=0, leaving room for future PLACEMENT at sort_index=20)
  - `phase_type` = `'PLAYOFF'`
  - `layout` = `'BRACKET'`
  - `format` = `'LEAGUE'` (DB default — semantically not ideal for BRACKET but matches DB DEFAULT clause and avoids a NULL; UI/Service layer in Phase 58 may ignore `format` for BRACKET layout)
  - `label` = `playoff.name` (carry the existing playoff name as a human-readable label)
  - `start_date` = `playoff.start_date`
  - `end_date` = `playoff.end_date`
  - `total_rounds` = `null`
  - `legs` = `1`
  - `event_duration_minutes` = `playoff.event_duration_minutes`
  - `race_scoring_id` = `playoff.season.race_scoring_id`
  - `match_scoring_id` = `playoff.season.match_scoring_id`
- **D-09: `playoffs.phase_id` is set to the newly-created PLAYOFF phase id immediately after insert.** UPDATE `playoffs SET phase_id = :newPhaseId WHERE id = :playoffId` per playoff. The existing `playoffs.season_id` column and the `playoff_seasons` M:N join table are **not** touched in Phase 57 (ROADMAP-SC5; Phase 61 handles cleanup).

### Matchday FK backfill (D-10)

- **D-10: `matchdays.phase_id` is set to the REGULAR phase of the matchday's season.** Single SQL: `UPDATE matchdays m SET phase_id = (SELECT sp.id FROM season_phases sp WHERE sp.season_id = m.season_id AND sp.phase_type = 'REGULAR')`. `matchdays.group_id` stays NULL for all migrated rows. The existing `matchdays.season_id` column stays intact (ROADMAP-SC5).

### PhaseTeam derivation (D-11)

- **D-11: `phase_teams` is derived 1:1 from `season_teams`.** For each `season_teams` row, insert one `phase_teams` row with:
  - `id` = `UUID.randomUUID()`
  - `phase_id` = REGULAR-phase.id of `season_teams.season_id`
  - `team_id` = `season_teams.team_id`
  - `group_id` = `NULL`
  - Audit columns = `CURRENT_TIMESTAMP`
  Reason: ROADMAP-SC4 mandates "one row per `season_team` entry"; LEAGUE layout means no groups; UNIQUE (`phase_id`, `team_id`) is naturally satisfied because `season_teams` already has UNIQUE (`season_id`, `team_id`).

### NOT-NULL flip (D-12, D-13)

- **D-12: NOT-NULL flip is performed via `JdbcTemplate.execute()` inside the same Java migration class.** Dialect detection via `Connection.getMetaData().getDatabaseProductName()`:
  - H2: `ALTER TABLE matchdays ALTER COLUMN phase_id SET NOT NULL` and same for `playoffs.phase_id`
  - MariaDB: `ALTER TABLE matchdays MODIFY COLUMN phase_id UUID NOT NULL` and same for `playoffs.phase_id`
- **D-13: NOT-NULL flip is the LAST step in the migration sequence**, after PhaseTeams insert. Reason: the flip presupposes all rows have a non-null `phase_id` — running it earlier would fail the constraint check.

### Logging (D-14)

- **D-14: Structured logging with row counts per step.** Use `org.slf4j.Logger` (constructor-bound `private static final Logger log = LoggerFactory.getLogger(V4__MigrateSeasonsToPhases.class)`). For each migration step: `log.info("Migrated {} REGULAR phases (one per season)", count)`, etc. Counts are obtained via `JdbcTemplate.queryForObject("SELECT COUNT(*) ...", Integer.class)` after the step completes. Reason: visible audit trail in production logs during the one-time prod migration.

### Test strategy (D-15..D-19)

- **D-15: Java integration test with manual Flyway setup.** Test class `V4MigrateSeasonsToPhasesIT` uses `@JdbcTest` (or programmatic Flyway) to:
  1. Create an in-memory H2 instance (separate from main app context)
  2. Run only V1, V2, V3 migrations
  3. Seed legacy data via `JdbcTemplate` (Pre-V4 state — Seasons/Matchdays/Playoffs without `phase_id`)
  4. Manually trigger V4 via `Flyway.configure()...migrate()` or invoke `V4__MigrateSeasonsToPhases.migrate()` directly
  5. Assert on the resulting state
  Reason: enables real before/after verification of the migration without coupling to the main `DemoDataSeeder` flow.
- **D-16: One test method per Success Criterion (5 SCs + NOT-NULL constraint test).** Test methods:
  - `givenLegacySeasons_whenMigrationRuns_thenEachSeasonHasOneRegularPhase()` (SC1)
  - `givenLegacyPlayoff_whenMigrationRuns_thenPlayoffLinkedViaPhaseId()` (SC2)
  - `givenLegacyMatchdays_whenMigrationRuns_thenAllMatchdaysHavePhaseId()` (SC3)
  - `givenLegacySeasonTeams_whenMigrationRuns_thenPhaseTeamsPopulated()` (SC4)
  - `givenLegacyData_whenMigrationRuns_thenBridgeColumnsRemainIntact()` (SC5)
  - `givenMigratedSchema_whenMatchdayInsertedWithoutPhaseId_thenViolatesNotNullConstraint()` (NOT-NULL flip)
- **D-17: Realistic seed scenario — multiple seasons with edge cases.** Seed: 3 seasons (one with playoff, one without, one empty: no matchdays, no teams) + 2 teams per season + 2 matchdays per season + 1 playoff on the first season. Reason: covers happy-path AND edge-cases (empty season, season-without-playoff).
- **D-18: Application-boot smoke test.** Add a thin `@SpringBootTest` class `V4MigrationSmokeIT` that does nothing more than: Spring context loads (= Flyway green) AND `seasonRepository.findAll()` returns seasons with non-empty `phases` collection. Reason: end-to-end confidence that migration + JPA mapping + repositories all align.
- **D-19: Tests live next to other Flyway tests.** Path: `src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java` and `src/test/java/db/migration/V4MigrationSmokeIT.java`. Mirrors the production-side package convention from D-03.

### Claude's Discretion

- Exact MariaDB column type in `MODIFY COLUMN` ALTER (e.g., whether `BINARY(16)` or `UUID` literal) — planner verifies against actual DB metadata; Spring Data JPA's UUID handling on MariaDB uses `BINARY(16)`. Detect at runtime if needed.
- Whether to wrap each step's `log.info` count query in a try/catch to avoid masking the real exception (counter failures are non-fatal). Planner decides.
- Per-step transaction boundary if MariaDB DDL implicit-commit becomes a problem during prod migration — fallback could be `canExecuteInTransaction()=false` with manual savepoints; not the default but allowed if planner discovers a concrete reason.
- Test data UUIDs — fixed UUIDs (deterministic) vs `UUID.randomUUID()` per test run. Planner picks; deterministic UUIDs are nicer for debugging.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Foundation & requirements (read before planning)
- `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` §"Migration" — six-step Flyway sequence (V*-files for create_phase_tables → migrate_seasons_to_phases → migrate_matchdays_to_phases → migrate_playoffs_to_phases → migrate_team_rosters → cleanup_season_columns). Phase 57 implements steps 2-5 in a single Java migration; step 6 is Phase 61.
- `.planning/REQUIREMENTS.md` §MIGR-02..MIGR-05 — locked requirements for this phase.
- `.planning/ROADMAP.md` §"Phase 57: Data Migration" — Goal, success criteria, dependency boundary.
- `.planning/STATE.md` §"Key Technical Context" — Field migration list (Season → SeasonPhase), v1.9 phase split rationale.
- `.planning/phases/56-model-schema-foundation/56-CONTEXT.md` §"D-01" and §"D-02" — Phase 56's parallel-additive decision and the explicit deferral of NOT-NULL flip to Phase 57.
- `.planning/phases/56-model-schema-foundation/56-VERIFICATION.md` — confirms the Phase 56 schema landed exactly as expected; pre-conditions for Phase 57.

### Project conventions (binding)
- `CLAUDE.md` §"Constraints" — Flyway V1+V2+V3 immutable, OSIV enabled, 82% coverage minimum, H2 + MariaDB compatibility, no breaking URL changes.
- `CLAUDE.md` §"Architectural Principles" — "Do Not Modify Flyway Migrations" rule (V4+ are new); "No Fallback Calculations" (data integrity errors must fail loud — D-05).
- `CLAUDE.md` §"Development Approach" — TDD: write tests first (V4MigrateSeasonsToPhasesIT before V4 implementation); BDD given/when/then naming.
- `.planning/codebase/CONVENTIONS.md` — naming patterns; logging (`@Slf4j` for managed classes, `LoggerFactory.getLogger(...)` for non-managed migration classes).
- `.planning/codebase/STRUCTURE.md` §"Where to Add New Code" — file location guidance; new test packages mirror production packages.

### Existing code (read for pattern alignment)
- `src/main/java/org/ctc/domain/model/Season.java` — fields that get copied into REGULAR phase (`format`, `totalRounds`, `legs`, `eventDurationMinutes`, `startDate`, `endDate`, `raceScoring_id`, `matchScoring_id`).
- `src/main/java/org/ctc/domain/model/Matchday.java` — receives `phase_id` populate via UPDATE; `season_id` stays.
- `src/main/java/org/ctc/domain/model/Playoff.java` — receives `phase_id` populate; `season_id` and `seasons` (M:N) stay.
- `src/main/java/org/ctc/domain/model/SeasonPhase.java` — entity for inserts; field mapping must match column names.
- `src/main/java/org/ctc/domain/model/PhaseTeam.java` — entity for inserts.
- `src/main/java/org/ctc/domain/model/SeasonTeam.java` — source of truth for `phase_teams` derivation (D-11).
- `src/main/resources/db/migration/V3__add_season_phase_tables.sql` — schema reference; column types and constraints that V4 must satisfy.
- `src/main/resources/db/migration/V1__initial_schema.sql` — reference for legacy-data shape (Season columns currently on `seasons` table); used in test seed scripts.
- `src/main/resources/db/migration/V2__add_fk_indexes.sql` — index naming reference (no new indexes in V4).
- `src/test/java/org/ctc/.../*IntegrationTest.java` — pattern for `@DataJpaTest` / `@SpringBootTest` — D-15 mirrors this pattern.

### Flyway / Spring Boot 4 references
- Flyway `BaseJavaMigration` JavaDoc — required for `migrate(Context)` signature, `getChecksum()` override, `canExecuteInTransaction()` semantics.
- Spring Boot 4 Flyway autoconfiguration — confirms `db/migration` package is scanned without extra config (D-03).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `JdbcTemplate` is available via `Context.getConnection()` in Flyway Java migrations (or constructed from the connection). All `INSERT` / `UPDATE` / `ALTER` statements go through it for type-safe parameter binding.
- Spring's `LoggerFactory` works in Java migrations even though they are not Spring-managed (no `@Slf4j`); use `private static final Logger log = LoggerFactory.getLogger(V4__MigrateSeasonsToPhases.class)`.
- Phase 56 entities are already in place — once V4 runs, JPA can read the new schema without any further Phase 57 entity changes.

### Established Patterns
- **Versioned migration numbering** — V3 (Phase 56) → V4 (Phase 57). No V3.5; if multiple V-files become needed for Phase 57 (currently rejected per D-02), they would be V4 / V5.
- **Audit columns** — `created_at` / `updated_at` use `CURRENT_TIMESTAMP` (DB default); Java migration explicitly inserts these so values reflect migration time, not insertion time of the source row.
- **No service layer in migrations** — Java migrations talk to `JdbcTemplate` only; never use Spring-managed services or repositories. Reason: migration runs before Spring context is fully initialized.
- **UUID storage** — H2 uses native UUID; MariaDB uses `BINARY(16)`. Spring Data JPA handles both via `@Id @GeneratedValue(strategy = GenerationType.UUID)` on entity side. In raw `JdbcTemplate` inserts, pass `UUID.randomUUID()` as `Object` — Flyway's `JdbcTemplate` handles the binding.

### Integration Points
- After V4 runs, `Season.phases` collection (added in Phase 56) is non-empty — this is the first phase where reads of `Season.phases` return data.
- After V4 runs, every `Matchday.phase` and `Playoff.phase` association resolves to a non-null `SeasonPhase` — services in Phase 58 can safely depend on this.
- `phase_teams` table goes from empty (after Phase 56) to populated (after Phase 57) — Phase 58's roster-aware services can read it.
- `matchdays.season_id` and `playoffs.season_id` remain NOT NULL and populated — existing services and templates that filter on those columns continue to work unchanged.
- `TestDataService`, `DevDataSeeder`, `DemoDataSeeder` are **not** modified in Phase 57 — Phase 59 rebuilds them on the new model. For dev profile (which seeds via `DevDataSeeder`), this means: V4 runs on an empty DB, does nothing meaningful (zero rows), and `DevDataSeeder` later inserts data using the existing pre-Phase-58 entity APIs (which still reference `Season.format` etc. — D-01 of Phase 56 keeps these intact).

</code_context>

<specifics>
## Specific Ideas

- "Bestand: rein mechanische Migration" (foundation doc §Schluessel-Entscheidungen #8) — every legacy season → exactly one REGULAR phase with LEAGUE layout. No heuristics for old group-workaround seasons. They stay as separate seasons with one LEAGUE phase each; manual consolidation deferred to CONSOL-FUT-01.
- The migration must run cleanly on an empty DB (dev/test profile with H2): no rows to migrate → all inserts are no-ops → NOT-NULL flip succeeds because zero rows can satisfy any constraint. Tests cover the populated case.
- Seed scenario covers: 3 seasons (one with playoff, one without, one empty); 2 teams per season; 2 matchdays per season; 1 playoff. The "empty season" case (no matchdays, no teams) is essential — it must not break the migration even though SC4's count-based asserts must still pass for that season's phase_teams (= 0 rows expected).
- The PLAYOFF phase's `format` column gets `'LEAGUE'` even though it's semantically not ideal for BRACKET layout. Reason: DB has `DEFAULT 'LEAGUE' NOT NULL` and we don't want to introduce a special "BRACKET-default format" for a single non-decision-driving column. Phase 58 service-layer can ignore `format` for BRACKET-layout phases.

</specifics>

<deferred>
## Deferred Ideas

- **Drop of `playoff_seasons` M:N join table and removal of `Playoff.seasons` collection** — Phase 61 (MIGR-06).
- **Drop of legacy Season columns** (`format`, `total_rounds`, `legs`, `event_duration_minutes`, `start_date`, `end_date`, `race_scoring_id`, `match_scoring_id`) — Phase 61 (MIGR-06). Phase 57 leaves them populated and intact.
- **Drop of `matchdays.season_id` and `playoffs.season_id`** — Phase 61 (MIGR-06).
- **Service-layer rewrite to consume `phase_id` instead of `season_id`** — Phase 58.
- **Custom phase/group-aware repository finders** (`findBySeasonAndPhaseType`, etc.) — Phase 58.
- **TestDataService / DevDataSeeder rewrite on the new model** — Phase 59. Phase 57 deliberately does NOT touch these; they continue to reference legacy Season fields that Phase 56 D-01 preserved.
- **Group-aware data migration (sub-group rosters, group matchdays)** — out of scope. Legacy data has no groups; manual UI consolidation per CONSOL-FUT-01 in a future milestone.
- **Phase-Override on driver-import sheet** (`IMPORT-FUT-01`) — out of scope; future enhancement.
- **PLACEMENT phases for legacy data** — no PLACEMENT phases are migrated (none exist in legacy). PLACEMENT-phase support for new seasons is part of Phase 60 UI work.

</deferred>

---

*Phase: 57-data-migration*
*Context gathered: 2026-04-27*
