# Phase 4: Database Optimization - Research

**Researched:** 2026-04-04
**Domain:** Flyway FK-Indexes + JPA @EntityGraph
**Confidence:** HIGH

## Summary

Phase 4 adds indexes on all foreign key columns via a single Flyway V2 migration and introduces `@EntityGraph` annotations on repository methods that return collections. The scope is narrow and well-defined: one SQL migration file and annotation additions to existing repository interfaces.

Both H2 and MariaDB support `CREATE INDEX IF NOT EXISTS` syntax, making a universal migration file possible. MariaDB already auto-creates indexes on FK columns, so `IF NOT EXISTS` prevents errors there while actually creating the indexes on H2 (which does not auto-index FKs). The `@EntityGraph` annotations are purely JPA-level and require no schema changes.

**Primary recommendation:** Single V2 migration with all ~30 FK indexes using `CREATE INDEX IF NOT EXISTS`, followed by `@EntityGraph(attributePaths = {...})` annotations on all collection-returning repository methods that traverse relationships consumed by templates/services.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Alle ~30 FK-Columns im Schema bekommen einen Index (nicht nur die 10 in CONCERNS.md genannten)
- **D-02:** Nur Single-Column Indexes pro FK-Column — keine Composite Indexes. Bei Bedarf spaeter ergaenzen.
- **D-03:** Breit: Alle findBy*-Repository-Methoden die Collections zurueckgeben bekommen passende @EntityGraph-Annotationen
- **D-04:** Tiefe: Nur 1 Ebene (direkte Beziehungen). Tiefere Navigations bleiben lazy und werden von OSIV aufgeloest.
- **D-05:** Definition: Inline @EntityGraph(attributePaths = {...}) direkt auf den Repository-Methoden — keine @NamedEntityGraph auf Entities
- **D-06:** Hibernate SQL-Logging (spring.jpa.show-sql=true) in Tests aktivieren zur manuellen Verifikation. Keine dedizierten Query-Count-Assertion-Tests.
- **D-07:** Universale Migration mit CREATE INDEX IF NOT EXISTS — funktioniert auf H2 und MariaDB
- **D-08:** Eine einzelne Migrationsdatei: V2__add_fk_indexes.sql

### Claude's Discretion
- Reihenfolge der EntityGraph-Annotationen (welche Repositories zuerst)
- Exakte attributePaths pro Repository-Methode (basierend auf Template-Nutzung)
- Index-Naming-Convention (z.B. idx_{table}_{column})
- Ob show-sql nur temporaer fuer Verifikation oder dauerhaft in Test-Profil

### Deferred Ideas (OUT OF SCOPE)
None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DBIX-01 | Flyway V2 Migration mit Indexes auf allen FK-Columns (races, race_results, race_lineups, matches, matchdays, season_drivers, season_teams) | Complete FK column inventory below; `CREATE INDEX IF NOT EXISTS` verified for H2 + MariaDB |
| DBIX-02 | @EntityGraph Annotationen fuer haeufig traversierte Beziehungen (Match->Teams, Race->Matchday) | All collection-returning repository methods identified with recommended attributePaths |
</phase_requirements>

## Standard Stack

No new dependencies required. This phase uses existing stack components:

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Flyway | (managed by Spring Boot 4.0.5) | V2 migration file | Already configured, V1 migration exists |
| Spring Data JPA | (managed by Spring Boot 4.0.5) | @EntityGraph annotations | Built-in JPA feature, no extra dependency |
| H2 | (managed by Spring Boot 4.0.5) | Dev/test database | Already in use, supports `CREATE INDEX IF NOT EXISTS` |
| MariaDB | 11+ | Prod/docker database | Already in use, supports `CREATE INDEX IF NOT EXISTS` |

**Installation:** None required.

## Architecture Patterns

### Recommended Project Structure

```
src/main/resources/db/migration/
  V1__initial_schema.sql          # Existing (frozen)
  V2__add_fk_indexes.sql          # NEW: All FK indexes

src/main/java/org/ctc/domain/repository/
  MatchRepository.java            # MODIFIED: Add @EntityGraph
  RaceRepository.java             # MODIFIED: Add @EntityGraph
  MatchdayRepository.java         # MODIFIED: Add @EntityGraph
  RaceResultRepository.java       # MODIFIED: Add @EntityGraph
  RaceLineupRepository.java       # MODIFIED: Add @EntityGraph
  SeasonDriverRepository.java     # MODIFIED: Add @EntityGraph
  SeasonTeamRepository.java       # MODIFIED: Add @EntityGraph
  PlayoffMatchupRepository.java   # MODIFIED: Add @EntityGraph
  PlayoffRoundRepository.java     # MODIFIED: Add @EntityGraph
  PlayoffSeedRepository.java      # MODIFIED: Add @EntityGraph
```

### Pattern: @EntityGraph on Repository Methods

**What:** Inline `@EntityGraph(attributePaths = {...})` annotations on Spring Data repository methods to eager-fetch direct associations in a single query (LEFT JOIN FETCH).

**When to use:** On every `findBy*` method that returns `List<Entity>` where the entity has `@ManyToOne` relationships that are subsequently accessed (in templates or service logic).

**Example:**
```java
// Source: Spring Data JPA documentation
public interface MatchRepository extends JpaRepository<Match, UUID> {

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "matchday"})
    List<Match> findByMatchdayId(UUID matchdayId);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "matchday"})
    List<Match> findByMatchdaySeasonId(UUID seasonId);
}
```

### Pattern: Index Naming Convention

**Recommendation:** `idx_{table}_{column}` — consistent, readable, and follows the existing pattern in V1 (`idx_cars_gt7id`, `idx_alias_driver`).

**Example:**
```sql
CREATE INDEX IF NOT EXISTS idx_races_matchday_id ON races(matchday_id);
CREATE INDEX IF NOT EXISTS idx_matches_matchday_id ON matches(matchday_id);
```

### Anti-Patterns to Avoid
- **Composite indexes without evidence:** D-02 locks this to single-column only. Do not add composite indexes even if they seem useful.
- **Deep EntityGraph paths:** D-04 limits to 1 level. Never use paths like `"matchday.season"` — OSIV handles deeper navigation.
- **@NamedEntityGraph on entities:** D-05 locks to inline `@EntityGraph(attributePaths)` only.
- **Overriding inherited methods with @EntityGraph:** Do not override `findAll()` or `findById()` just to add EntityGraph — only annotate existing custom `findBy*` methods that return collections.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Eager loading | Custom JPQL with JOIN FETCH | `@EntityGraph(attributePaths)` | Spring Data handles query generation correctly |
| Index management | Manual ALTER TABLE | Flyway V2 migration | Reproducible, version-controlled, auto-executed |
| N+1 detection | Custom query counting | Hibernate show-sql logging (already enabled in dev) | D-06 explicitly chose manual verification |

## Common Pitfalls

### Pitfall 1: MultipleBagFetchException
**What goes wrong:** If an entity has two `@OneToMany` collections (bags) and both are eagerly fetched simultaneously, Hibernate throws `MultipleBagFetchException`.
**Why it happens:** Hibernate cannot handle multiple uninitialized bags in a single query — it produces a Cartesian product.
**How to avoid:** EntityGraph on the parent entity should NOT include multiple `@OneToMany` collections in the same graph. This phase only adds EntityGraphs on `@ManyToOne` attributePaths (going "up" to parent), not on `@OneToMany` (going "down" to children). For example, `Match` has `@ManyToOne homeTeam` and `@ManyToOne awayTeam` — these are safe. Do NOT add `"races"` or `"results"` to attributePaths.
**Warning signs:** `org.hibernate.loader.MultipleBagFetchException` at startup or during query execution.

### Pitfall 2: Cartesian Product Explosion
**What goes wrong:** EntityGraph with too many paths on a query returning many rows creates massive result sets.
**Why it happens:** Each LEFT JOIN multiplies rows. Three joins on a 100-row base table can produce 100 x N x M x O rows.
**How to avoid:** Keep attributePaths minimal — only include relationships that are actually accessed. For `RaceResult`, only `driver` is needed (not `race` which is already known from the query parameter).
**Warning signs:** Slow queries, high memory usage, unexpectedly large result sets visible in show-sql logs.

### Pitfall 3: H2/MariaDB Index Behavior Difference
**What goes wrong:** Tests pass on H2 (indexes created fresh) but behavior differs from MariaDB (which already has FK indexes).
**Why it happens:** MariaDB auto-creates indexes for FK columns. H2 does not. `CREATE INDEX IF NOT EXISTS` handles both — it creates on H2, skips on MariaDB.
**How to avoid:** Use `IF NOT EXISTS` on every index (D-07). This is already the locked decision.
**Warning signs:** Migration failure on MariaDB with "Duplicate key name" error (only if `IF NOT EXISTS` is forgotten).

### Pitfall 4: EntityGraph on methods returning Optional/single entity
**What goes wrong:** Adding `@EntityGraph` to `findById()` or `findBySeasonIdAndTeamId()` (returning Optional) is unnecessary overhead for single-entity lookups.
**Why it happens:** Single-entity lookups don't cause N+1 — the "N" is 1. OSIV handles the lazy load efficiently.
**How to avoid:** D-03 specifies "alle findBy*-Repository-Methoden die Collections zurueckgeben" — only `List<>` return types.
**Warning signs:** No functional issue, just unnecessary query complexity.

## Code Examples

### V2 Migration File: Complete FK Index Inventory

Based on analysis of `V1__initial_schema.sql`, here is the complete list of FK columns requiring indexes:

```sql
-- V2__add_fk_indexes.sql
-- Add indexes on all foreign key columns for query performance.
-- MariaDB auto-creates FK indexes; IF NOT EXISTS prevents duplicates.
-- H2 does not auto-index FKs, so these indexes are needed for dev/test parity.

-- seasons (2 FKs)
CREATE INDEX IF NOT EXISTS idx_seasons_race_scoring_id ON seasons(race_scoring_id);
CREATE INDEX IF NOT EXISTS idx_seasons_match_scoring_id ON seasons(match_scoring_id);

-- teams (1 FK)
CREATE INDEX IF NOT EXISTS idx_teams_parent_team_id ON teams(parent_team_id);

-- season_drivers (3 FKs)
CREATE INDEX IF NOT EXISTS idx_season_drivers_season_id ON season_drivers(season_id);
CREATE INDEX IF NOT EXISTS idx_season_drivers_driver_id ON season_drivers(driver_id);
CREATE INDEX IF NOT EXISTS idx_season_drivers_team_id ON season_drivers(team_id);

-- season_teams (3 FKs)
CREATE INDEX IF NOT EXISTS idx_season_teams_season_id ON season_teams(season_id);
CREATE INDEX IF NOT EXISTS idx_season_teams_team_id ON season_teams(team_id);
CREATE INDEX IF NOT EXISTS idx_season_teams_successor_season_team_id ON season_teams(successor_season_team_id);

-- season_cars (join table — PKs are the FKs, already indexed)
-- season_tracks (join table — PKs are the FKs, already indexed)

-- matchdays (1 FK)
CREATE INDEX IF NOT EXISTS idx_matchdays_season_id ON matchdays(season_id);

-- matches (3 FKs)
CREATE INDEX IF NOT EXISTS idx_matches_matchday_id ON matches(matchday_id);
CREATE INDEX IF NOT EXISTS idx_matches_home_team_id ON matches(home_team_id);
CREATE INDEX IF NOT EXISTS idx_matches_away_team_id ON matches(away_team_id);

-- playoffs (1 FK)
CREATE INDEX IF NOT EXISTS idx_playoffs_season_id ON playoffs(season_id);

-- playoff_rounds (1 FK)
CREATE INDEX IF NOT EXISTS idx_playoff_rounds_playoff_id ON playoff_rounds(playoff_id);

-- playoff_matchups (5 FKs)
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_round_id ON playoff_matchups(round_id);
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_team1_id ON playoff_matchups(team1_id);
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_team2_id ON playoff_matchups(team2_id);
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_winner_id ON playoff_matchups(winner_id);
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_next_matchup_id ON playoff_matchups(next_matchup_id);

-- playoff_seasons (join table — PKs are the FKs, already indexed)

-- playoff_seeds (2 FKs)
CREATE INDEX IF NOT EXISTS idx_playoff_seeds_playoff_id ON playoff_seeds(playoff_id);
CREATE INDEX IF NOT EXISTS idx_playoff_seeds_team_id ON playoff_seeds(team_id);

-- races (7 FKs)
CREATE INDEX IF NOT EXISTS idx_races_matchday_id ON races(matchday_id);
CREATE INDEX IF NOT EXISTS idx_races_match_id ON races(match_id);
CREATE INDEX IF NOT EXISTS idx_races_track_id ON races(track_id);
CREATE INDEX IF NOT EXISTS idx_races_car_id ON races(car_id);
CREATE INDEX IF NOT EXISTS idx_races_playoff_matchup_id ON races(playoff_matchup_id);
CREATE INDEX IF NOT EXISTS idx_races_home_team_id ON races(home_team_id);
CREATE INDEX IF NOT EXISTS idx_races_away_team_id ON races(away_team_id);

-- race_results (2 FKs)
CREATE INDEX IF NOT EXISTS idx_race_results_race_id ON race_results(race_id);
CREATE INDEX IF NOT EXISTS idx_race_results_driver_id ON race_results(driver_id);

-- race_lineups (3 FKs)
CREATE INDEX IF NOT EXISTS idx_race_lineups_race_id ON race_lineups(race_id);
CREATE INDEX IF NOT EXISTS idx_race_lineups_driver_id ON race_lineups(driver_id);
CREATE INDEX IF NOT EXISTS idx_race_lineups_team_id ON race_lineups(team_id);

-- race_settings (1 FK)
CREATE INDEX IF NOT EXISTS idx_race_settings_race_id ON race_settings(race_id);

-- psn_aliases — idx_alias_driver already exists in V1

-- race_attachments (1 FK)
CREATE INDEX IF NOT EXISTS idx_race_attachments_race_id ON race_attachments(race_id);
```

**Total: 35 indexes** (excluding 2 already in V1: `idx_cars_gt7id`, `idx_alias_driver`; and excluding join tables where PKs serve as indexes).

### EntityGraph Annotations: Complete Repository Method Map

Every `findBy*` method returning `List<Entity>` with its recommended `attributePaths`:

```java
// MatchRepository — homeTeam + awayTeam always accessed in listings
@EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
List<Match> findByMatchdayId(UUID matchdayId);

@EntityGraph(attributePaths = {"homeTeam", "awayTeam", "matchday"})
List<Match> findByMatchdaySeasonId(UUID seasonId);

// MatchdayRepository — season always accessed for context
@EntityGraph(attributePaths = {"season"})
List<Matchday> findBySeasonIdOrderBySortIndexAsc(UUID seasonId);

// RaceRepository — matchday, match, track, car frequently accessed
@EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
List<Race> findByMatchdayId(UUID matchdayId);

@EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
List<Race> findByMatchdaySeasonId(UUID seasonId);

@EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
List<Race> findByMatchdaySeasonIdAndPlayoffMatchupIsNull(UUID seasonId);

@EntityGraph(attributePaths = {"playoffMatchup", "track", "car"})
List<Race> findByPlayoffMatchupId(UUID matchupId);

@EntityGraph(attributePaths = {"playoffMatchup", "matchday", "track", "car"})
List<Race> findByPlayoffMatchupRoundPlayoffId(UUID playoffId);

@EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
List<Race> findByPlayoffMatchupIsNull();

// RaceResultRepository — driver always accessed for display
@EntityGraph(attributePaths = {"driver"})
List<RaceResult> findByRaceId(UUID raceId);

@EntityGraph(attributePaths = {"driver", "race"})
List<RaceResult> findByDriverId(UUID driverId);

@EntityGraph(attributePaths = {"driver", "race"})
List<RaceResult> findByRaceMatchdaySeasonId(UUID seasonId);

@EntityGraph(attributePaths = {"driver", "race"})
List<RaceResult> findByRacePlayoffMatchupIsNull();

// RaceLineupRepository — driver + team always accessed
@EntityGraph(attributePaths = {"driver", "team"})
List<RaceLineup> findByRaceId(UUID raceId);

@EntityGraph(attributePaths = {"driver", "team"})
List<RaceLineup> findByRaceIdAndTeamId(UUID raceId, UUID teamId);

@EntityGraph(attributePaths = {"driver", "team", "race"})
List<RaceLineup> findByRaceMatchdayId(UUID matchdayId);

@EntityGraph(attributePaths = {"driver", "team"})
List<RaceLineup> findByTeamIdIn(List<UUID> teamIds);

// SeasonDriverRepository — driver + team always accessed
@EntityGraph(attributePaths = {"driver", "team"})
List<SeasonDriver> findBySeasonId(UUID seasonId);

@EntityGraph(attributePaths = {"driver", "team"})
List<SeasonDriver> findBySeasonIdAndTeamId(UUID seasonId, UUID teamId);

@EntityGraph(attributePaths = {"driver", "team"})
List<SeasonDriver> findByDriverId(UUID driverId);

@EntityGraph(attributePaths = {"driver", "team"})
List<SeasonDriver> findByTeamIdIn(List<UUID> teamIds);

// SeasonTeamRepository — team always accessed for display
@EntityGraph(attributePaths = {"team"})
List<SeasonTeam> findBySeasonId(UUID seasonId);

// PlayoffMatchupRepository — teams always accessed
@EntityGraph(attributePaths = {"team1", "team2", "winner"})
List<PlayoffMatchup> findByRoundIdOrderByBracketPositionAsc(UUID roundId);

@EntityGraph(attributePaths = {"team1", "team2", "winner", "round"})
List<PlayoffMatchup> findByRoundPlayoffId(UUID playoffId);

// PlayoffRoundRepository — playoff context
@EntityGraph(attributePaths = {"playoff"})
List<PlayoffRound> findByPlayoffIdOrderByRoundIndexAsc(UUID playoffId);

// PlayoffSeedRepository — team always accessed
@EntityGraph(attributePaths = {"team"})
List<PlayoffSeed> findByPlayoffId(UUID playoffId);

// SeasonRepository — scoring presets accessed for display
@EntityGraph(attributePaths = {"raceScoring", "matchScoring"})
List<Season> findBySeasonTeamsTeamId(UUID teamId);
```

**Note:** `CarRepository.findAllByOrderByManufacturerAscNameAsc()`, `TrackRepository.findAllByOrderByNameAsc()`, and `DriverRepository.findByActiveTrue()` have NO `@ManyToOne` relationships to eagerly load — no EntityGraph needed.

### Hibernate SQL-Logging Verification

`show-sql: true` is already enabled in `application-dev.yml`. Tests run with the dev profile's H2 database which also uses this config. For explicit verification:

```yaml
# application-dev.yml (already present)
spring:
  jpa:
    show-sql: true
    # format_sql: true is in application.yml base config
```

**Recommendation:** Keep `show-sql: true` in dev profile permanently (already the case). No test profile changes needed — `@DataJpaTest` uses H2 with Flyway, which picks up the base config.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@NamedEntityGraph` on entities | Inline `@EntityGraph(attributePaths)` on repos | Spring Data JPA 1.10+ | Simpler, no entity pollution |
| JPQL `JOIN FETCH` | `@EntityGraph` annotation | Spring Data JPA 1.10+ | Declarative, no query string maintenance |
| Manual index scripts per DB | `CREATE INDEX IF NOT EXISTS` | H2 2.x / MariaDB 10.5+ | Universal migration, no DB-specific branching |

## Open Questions

1. **MatchdayRepository EntityGraph — is `season` actually needed?**
   - What we know: `findBySeasonIdOrderBySortIndexAsc` already filters by season, so the season_id is known
   - What's unclear: Whether templates access `matchday.season.name` or similar after this query
   - Recommendation: Include `season` — it is cheap (single FK) and templates likely display season context. Can be removed later if show-sql reveals it is unused.

2. **RaceRepository methods with deep traversal in Race.getHomeTeam()/getAwayTeam()**
   - What we know: `Race.getHomeTeam()` delegates to `match.getHomeTeam()` or `playoffMatchup.getTeam1()` — this is a 2-level traversal
   - What's unclear: Whether the EntityGraph `"match"` is sufficient or whether `"match.homeTeam"` is needed
   - Recommendation: Start with 1-level only (`"match"`) per D-04. OSIV will resolve `match.getHomeTeam()` lazily. If show-sql reveals excessive queries, can add `"match.homeTeam"` and `"match.awayTeam"` as a follow-up.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (via spring-boot-starter-test) |
| Config file | `pom.xml` (Surefire/Failsafe plugins) |
| Quick run command | `./mvnw test` |
| Full suite command | `./mvnw verify` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DBIX-01 | V2 migration runs on H2 without errors | integration | `./mvnw verify` (all @DataJpaTest and @SpringBootTest use H2 with Flyway) | Implicit via existing 73 test files |
| DBIX-01 | V2 migration runs on MariaDB without errors | manual | `docker compose up --build -d` then verify app starts | Manual |
| DBIX-02 | EntityGraph annotations produce correct JOIN queries | manual | Run with `show-sql: true`, verify JOIN in SQL output | Manual (D-06) |
| DBIX-02 | Existing tests still pass with EntityGraph changes | integration | `./mvnw verify` | Existing 73 test files |

### Sampling Rate
- **Per task commit:** `./mvnw verify`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
None — existing test infrastructure covers all phase requirements. The V2 migration is automatically validated by all existing tests that use H2 + Flyway. EntityGraph annotations do not change test behavior, only query efficiency.

## Sources

### Primary (HIGH confidence)
- `V1__initial_schema.sql` — Complete FK column inventory derived from actual schema
- All 21 repository interfaces in `src/main/java/org/ctc/domain/repository/` — Method signatures analyzed
- All entity models in `src/main/java/org/ctc/domain/model/` — Relationship mappings analyzed
- [MariaDB CREATE INDEX documentation](https://mariadb.com/docs/server/reference/sql-statements/data-definition/create/create-index) — `IF NOT EXISTS` support confirmed
- [H2 Database Commands](https://www.h2database.com/html/commands.html) — `CREATE INDEX IF NOT EXISTS` supported

### Secondary (MEDIUM confidence)
- Spring Data JPA `@EntityGraph` — well-established feature since Spring Data JPA 1.10, syntax verified against project's Spring Boot 4.0.5 stack

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies, using existing Flyway + JPA
- Architecture: HIGH — complete FK inventory from actual schema, all repos analyzed
- Pitfalls: HIGH — MultipleBagFetchException and Cartesian product are well-documented JPA pitfalls
- EntityGraph attributePaths: MEDIUM — exact paths depend on template usage patterns (D-04 discretion area)

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable domain, no fast-moving dependencies)
