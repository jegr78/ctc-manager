---
phase: 56-model-schema-foundation
plan: "04"
type: execute
status: complete
completed: 2026-04-26
subsystem: domain-model
tags: [jpa, entity, bidirectional, additive, java25]

# Dependency graph
requires:
  - 56-01 (PhaseType, PhaseLayout enums)
  - 56-02 (SeasonPhase, SeasonPhaseGroup entities)
  - 56-03 (V3 migration with phase_id, group_id columns on matchdays/playoffs)
provides:
  - Season.phases bidirectional collection (mappedBy = "season", cascade = ALL, orphanRemoval = true)
  - Matchday.phase ManyToOne (nullable; @JoinColumn name="phase_id")
  - Matchday.group ManyToOne (nullable; @JoinColumn name="group_id")
  - Playoff.phase ManyToOne (nullable; @JoinColumn name="phase_id")
affects:
  - 56-05-PLAN (integration tests round-trip Season-to-SeasonPhase persistence)
  - phase 57 (data migration sets non-null values; flips DB columns to NOT NULL)
  - phase 58 (Service layer reads via these fields)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Strict additive: existing Season.format/totalRounds/legs/raceScoring/matchScoring/eventDurationMinutes/startDate/endDate plus Season.matchdays and Season.playoffs collections remain untouched (D-01)"
    - "Bidirectional helper methods (addPhase/removePhase) follow existing Season.matchdays / Season.playoffs convention"
    - "Matchday.phase / Matchday.group / Playoff.phase fields kept nullable in Java — DB columns stay nullable in V3 and flip to NOT NULL in Phase 57's V4 backfill migration (D-02)"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/model/Season.java
    - src/main/java/org/ctc/domain/model/Matchday.java
    - src/main/java/org/ctc/domain/model/Playoff.java

key-decisions:
  - "D-01 parallel additive enforced — old Season fields and old season FKs on Matchday/Playoff remain in place"
  - "D-02 nullable in Phase 56 — both Matchday.phase/group and Playoff.phase nullable so existing rows continue to satisfy the schema; Phase 57 backfills + flips to NOT NULL"
  - "@JoinColumn names match V3 migration columns exactly (phase_id, group_id) so Hibernate column resolution does not fall back to default naming"

patterns-established:
  - "Additive schema-evolution pattern for v1.9: new bidirectional fields land alongside the legacy season-FK fields; cleanup is deferred to Phase 61's MIGR-06"

requirements-completed: [MODEL-04, MODEL-05, MODEL-06, MODEL-07]

# Metrics
duration: ~10m (mixed: subagent + orchestrator finalization)
completed: 2026-04-26
---

# Phase 56 Plan 04: Bidirectional Fields on Existing Entities Summary

**Three existing entities (`Season`, `Matchday`, `Playoff`) gained the bidirectional links to the new `SeasonPhase` / `SeasonPhaseGroup` entities — strictly additive, no removals — so that Phase 57's data migration can populate them and Phase 58's services can navigate the new model without breaking the legacy paths still in use until Phase 61.**

## Performance

- **Tasks:** 2/2
- **Files modified:** 3 (all existing entities)
- **Files created:** 0

## Accomplishments
- `Season.phases` bidirectional collection added (mappedBy = "season", cascade = ALL, orphanRemoval = true) — matches existing `Season.matchdays` / `Season.playoffs` shape.
- `Matchday.phase` (`@JoinColumn(name = "phase_id")`) and `Matchday.group` (`@JoinColumn(name = "group_id")`) added — both nullable, ready for Phase 57 backfill.
- `Playoff.phase` (`@JoinColumn(name = "phase_id")`) added — nullable.
- Existing `Matchday.season` and `Playoff.season` FKs kept untouched (D-01 parallel additive).
- `./mvnw -q compile -DskipTests` exit 0; full `./mvnw verify` after wave merge confirms no regressions.

## Task Commits

1. **Task 1: Add Season.phases bidirectional collection** — `902c0cf` (feat)
2. **Task 2: Add Matchday.phase, Matchday.group, Playoff.phase** — `92ad332` (feat)

Plan-metadata commit follows below (this SUMMARY.md commit).

## Files Modified

- `src/main/java/org/ctc/domain/model/Season.java` — added `private List<SeasonPhase> phases = new ArrayList<>();` with `@OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)` and `addPhase(...)` / `removePhase(...)` helper methods. `@ToString` exclude list extended with `phases`.
- `src/main/java/org/ctc/domain/model/Matchday.java` — added `private SeasonPhase phase;` and `private SeasonPhaseGroup group;` (`@ManyToOne(fetch = FetchType.LAZY)`, both with explicit `@JoinColumn`). `@ToString` exclude list extended with `phase` and `group`.
- `src/main/java/org/ctc/domain/model/Playoff.java` — added `private SeasonPhase phase;` (`@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "phase_id")`). `@ToString` exclude list extended with `phase`.

## Decisions Made
- **D-01 enforced strictly:** No removal, rename, or repurposing of legacy fields. `Season.format`, `Season.totalRounds`, `Season.legs`, `Season.raceScoring`, `Season.matchScoring`, `Season.eventDurationMinutes`, `Season.startDate`, `Season.endDate`, `Season.matchdays`, `Season.playoffs` all remain. Same for `Matchday.season` and `Playoff.season`.
- **D-02 honored:** Both `Matchday.phase` / `Matchday.group` and `Playoff.phase` are nullable Java fields. The matching V3 columns are nullable too. Phase 57's data migration will backfill and flip the DB columns to NOT NULL; Phase 58's service layer will then enforce non-null at write time.
- **Helper-method shape mirrors existing `Season.addMatchday`/`removeMatchday`:** sets both sides of the relationship and uses a list-add followed by a back-pointer assignment.

## Deviations from Plan

- The first executor agent only completed Task 1 (Season.phases) before exiting without a "PLAN COMPLETE" signal. The orchestrator detected the partial state via spot-check (Matchday.java had unstaged changes; Playoff.java was untouched; no SUMMARY.md), finished Task 2 inline (Playoff.phase add), committed Task 2 atomically (`92ad332`), and authored this SUMMARY.md. Both task commits remain on the worktree branch and round-trip cleanly through the wave-3 merge.
- No deviations from the plan content itself — all four required field additions are present, with the correct annotations and nullability per the plan and CONTEXT decisions.

## Issues Encountered

- The worktree branch was based on `origin/master` (not the local master with the plan files), so the executor agent could not Read `56-04-PLAN.md` from disk. Inline prompt context was sufficient for Task 1; Task 2 was finished by the orchestrator. The orchestrator will merge local master into `gsd/v1.9-season-phases-groups` after Wave 3 to bring the plan files in for Wave 4.

## User Setup Required

None.

## Threat Surface Scan

Reviewed: three additive `@ManyToOne` / `@OneToMany` fields, no new endpoints, no I/O, no input parsing, no PII. Mass-assignment surface unchanged (controllers still bind via Form DTOs per CLAUDE.md). Threat register entries for Phase 56 remain accurate; no new threats introduced.

## Next Phase Readiness

- 56-05 (integration tests) can now persist `Season → SeasonPhase → Matchday/Playoff` graphs through the new collections and verify the V3 UNIQUE constraints (`uk_season_phase_type`, `uk_phase_team`).
- Phase 57 (data migration) has the Java side ready — write a `SeasonPhase` row per existing season, attach existing matchdays/playoffs to it, then flip `matchdays.phase_id` / `playoffs.phase_id` to NOT NULL in V4.
- Phase 58 service layer has both legacy and new field paths available; cleanup is deferred to Phase 61's MIGR-06.

## Self-Check: PASSED

- `Season.phases` field present (verified via grep `private List<SeasonPhase> phases`)
- `Matchday.phase` field present with `@JoinColumn(name = "phase_id")`
- `Matchday.group` field present with `@JoinColumn(name = "group_id")`
- `Playoff.phase` field present with `@JoinColumn(name = "phase_id")`
- `./mvnw -q compile -DskipTests`: exit 0 in worktree
- Commits `902c0cf` and `92ad332`: present on worktree branch

---
*Phase: 56-model-schema-foundation*
*Completed: 2026-04-26*
