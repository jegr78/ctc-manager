---
phase: 04-database-optimization
plan: 01
subsystem: database
tags: [flyway, jpa, entitygraph, indexing, n-plus-one, performance]

# Dependency graph
requires:
  - phase: 02-controller-cleanup
    provides: clean service layer for repository access
provides:
  - "FK indexes on all 36 foreign key columns via V2 migration"
  - "@EntityGraph annotations on 28 collection-returning repository methods across 11 interfaces"
affects: [05-security-hardening]

# Tech tracking
tech-stack:
  added: []
  patterns: ["@EntityGraph on all List-returning findBy* repository methods", "IF NOT EXISTS for H2+MariaDB index compatibility"]

key-files:
  created:
    - src/main/resources/db/migration/V2__add_fk_indexes.sql
  modified:
    - src/main/java/org/ctc/domain/repository/MatchRepository.java
    - src/main/java/org/ctc/domain/repository/MatchdayRepository.java
    - src/main/java/org/ctc/domain/repository/RaceRepository.java
    - src/main/java/org/ctc/domain/repository/RaceResultRepository.java
    - src/main/java/org/ctc/domain/repository/RaceLineupRepository.java
    - src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java
    - src/main/java/org/ctc/domain/repository/SeasonTeamRepository.java
    - src/main/java/org/ctc/domain/repository/PlayoffMatchupRepository.java
    - src/main/java/org/ctc/domain/repository/PlayoffRoundRepository.java
    - src/main/java/org/ctc/domain/repository/PlayoffSeedRepository.java
    - src/main/java/org/ctc/domain/repository/SeasonRepository.java

key-decisions:
  - "36 FK indexes (not 35 as plan estimated) — race_attachments FK was in the SQL block but not counted in plan text"
  - "Only 1-level deep EntityGraph paths — OSIV handles 2nd-level lazy navigation in templates"

patterns-established:
  - "@EntityGraph pattern: all List-returning findBy* get inline @EntityGraph with direct @ManyToOne attributePaths"
  - "FK index naming: idx_{table}_{column} matching V1 convention"

requirements-completed: [DBIX-01, DBIX-02]

# Metrics
duration: 3min
completed: 2026-04-04
---

# Phase 04 Plan 01: FK Indexes and EntityGraph Optimization Summary

**Flyway V2 migration with 36 FK indexes and 28 @EntityGraph annotations across 11 repositories to eliminate N+1 queries**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-04T08:41:26Z
- **Completed:** 2026-04-04T08:44:35Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- Created V2 Flyway migration adding indexes on all 36 FK columns across 15 tables
- Added @EntityGraph annotations to 28 collection-returning repository methods across 11 interfaces
- All 660 tests pass with zero failures, 82%+ coverage maintained

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Flyway V2 migration with FK indexes** - `cbe5335` (perf)
2. **Task 2: Add @EntityGraph annotations to all collection-returning repository methods** - `240dfe8` (perf)

## Files Created/Modified
- `src/main/resources/db/migration/V2__add_fk_indexes.sql` - 36 CREATE INDEX IF NOT EXISTS statements for all FK columns
- `src/main/java/org/ctc/domain/repository/MatchRepository.java` - EntityGraph for homeTeam, awayTeam, matchday
- `src/main/java/org/ctc/domain/repository/MatchdayRepository.java` - EntityGraph for season
- `src/main/java/org/ctc/domain/repository/RaceRepository.java` - EntityGraph for matchday, match, track, car, playoffMatchup
- `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` - EntityGraph for driver, race
- `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` - EntityGraph for driver, team, race
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` - EntityGraph for driver, team
- `src/main/java/org/ctc/domain/repository/SeasonTeamRepository.java` - EntityGraph for team
- `src/main/java/org/ctc/domain/repository/PlayoffMatchupRepository.java` - EntityGraph for team1, team2, winner, round
- `src/main/java/org/ctc/domain/repository/PlayoffRoundRepository.java` - EntityGraph for playoff
- `src/main/java/org/ctc/domain/repository/PlayoffSeedRepository.java` - EntityGraph for team
- `src/main/java/org/ctc/domain/repository/SeasonRepository.java` - EntityGraph for raceScoring, matchScoring

## Decisions Made
- Plan listed 35 FK indexes but the actual SQL block contained 36 (race_attachments was in the block but not counted). Used the complete block with all 36 indexes.
- Only applied 1-level deep EntityGraph paths. OSIV remains active so 2nd-level lazy navigation in Thymeleaf templates continues to work without explicit deep paths.

## Deviations from Plan

None - plan executed exactly as written. The index count (36 vs 35) was a counting discrepancy in the plan text vs the plan's own SQL block; the SQL block was authoritative.

## Issues Encountered
None

## Known Stubs
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Database optimization complete, all FK columns indexed
- N+1 query patterns eliminated for collection-returning repository methods
- Ready for Phase 05 (security hardening)

## Self-Check: PASSED

All created files exist. All commit hashes verified.

---
*Phase: 04-database-optimization*
*Completed: 2026-04-04*
