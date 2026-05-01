---
plan_id: 61-gap-06
phase: 61-cleanup-quality-gate
status: complete
completed: 2026-05-01T23:55:00Z
gap_closure: true
---

# 61-gap-06 — Dead code: domain.service unused fields, branches, defensive guards

## What changed

Removed two unused fields from `domain.service` and audited the defensive null-guards in
the touched files. Per CLAUDE.md: "If you are certain that something is unused, you can
delete it completely." The audit also documents which null-guards must STAY (boundary or
sentinel cases) and which were attempted-but-reverted (test-fixture dependency).

## Removals

| File | Symbol | Reason |
|------|--------|--------|
| `PlayoffService.java:36` | `private final PlayoffSeedRepository playoffSeedRepository` | Zero callers in `PlayoffService` — bracket logic delegates to `PlayoffBracketViewService`, seeding logic to `PlayoffSeedingService`. The `Lombok @RequiredArgsConstructor` regenerates the constructor without it. Closes the `deferred-items.md` entry. |
| `StandingsService.java:24` | `private final TeamRepository teamRepository` | IDE flagged after the gap-04 import cleanup; verified zero callers. |

## Defensive null-guard audit

| File:line | Disposition | Rationale |
|-----------|-------------|-----------|
| `StandingsService.java:191` (`if (season == null) return Map.of();` in `calculateBuchholzScores`) | KEEP (boundary) | Attempted REMOVE → reverted. Three Surefire tests (`StandingsServiceTest$PhaseAwareStandingsTest`) build phase fixtures whose Season ID is not flushed; the silent `Map.of()` fallback is load-bearing for those test paths. Documented with a one-line boundary comment. |
| `StandingsService.java:260` (`if (homeStanding == null) return;` in `processMatch`) | KEEP (Map-miss) | `homeStanding = standingsMap.get(homeId)` — Java `Map.get()` legitimately returns null when the team is not in the standings map (e.g., a substituted team that no longer has a `PhaseTeam` row). |
| `SwissPairingService.java:194` (`if (groupId == null) return null;` in `resolveGroup`) | KEEP (sentinel) | Documented LEAGUE-layout sentinel: callers pass `null` intentionally to skip group lookup. |
| `SwissPairingService.java:267` (`if (opponent == null)` in pairing inner loop) | KEEP (Map-miss) | Map.get-derived; can be legitimately absent. |
| `FileStorageService.sanitize` boundary check | KEEP (user input) | Explicit per CLAUDE.md "validate at system boundaries". |

## Commits

- `451eca8 refactor(61-gap-06): remove unused fields from PlayoffService and StandingsService`

## Files touched

2 files in `src/main/java/org/ctc/domain/service/`:
- `PlayoffService.java` (1 deletion)
- `StandingsService.java` (1 deletion + 1 boundary-comment line)

## Diff size

2 files, 1 insertion, 3 deletions.

## Test gate

`./mvnw test -Dtest='StandingsServiceTest,PlayoffServiceTest'`

→ `Tests run: 61, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS

## Acceptance criteria

- [x] `playoffSeedRepository` removed from `PlayoffService` (closes deferred-items.md entry)
- [x] Defensive null-checks classified per CLAUDE.md boundary rules; KEEP items have a
      rationale comment on the surrounding method or are clearly map-derived
- [x] No private method removals attempted in this plan — every method in the listed
      services has at least one in-file caller (verified by grep)
- [x] Targeted Surefire suite GREEN (61 tests)

## NEEDS_CONTEXT

The `StandingsService.calculateBuchholzScores` defensive null-check is technically dead
code if all production callers go through `seasonPhaseService.findById(...).getSeason()`,
which guarantees the season exists. However, three Surefire tests rely on the silent
fallback. A clean fix requires updating those tests to seed the Season properly. This
is a NEEDS_CONTEXT item for a follow-up phase (e.g., test-fixture refactor) — not
appropriate for a Javadoc/dead-code sweep.

## Self-Check: PASSED

Two unused fields removed atomically. Defensive guards classified and documented. No
behavior change. Test gate green.
