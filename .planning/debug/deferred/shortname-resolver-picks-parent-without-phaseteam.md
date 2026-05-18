---
status: deferred
trigger: "Phase 66 driver-import resolver: when a shortName collision occurs (parent + sub-team share shortName), the new resolveTeamByShortName helper unconditionally picks the parent team. In the user's data the parents are organisational buckets without PhaseTeam(REGULAR) entries, so the subsequent phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.id, parent.id) lookup misses → warning 'Team {shortName} has no PhaseTeam in REGULAR phase of target season' → potentially incomplete import (group assignment fails)."
created: 2026-05-08T05:00:00Z
updated: 2026-05-18T16:00:00Z
deferred_at: 2026-05-18
deferred_to: v1.12
deferred_reason: "Diagnosed with root cause + suggested season-aware algorithm fully documented; out of scope for v1.11 tooling/tech-debt sweep. Hand-off to v1.12 driver-import gap-closure phase. Bug surface is Driver-Import semantic correctness (resolver picks parent over sub-team-with-PhaseTeam) — DATA-CORRECTNESS impact, prioritize over the group-warnings sibling debt."
---

# Debug Session: ShortName Resolver Picks Parent Without PhaseTeam

## Current Focus

hypothesis: confirmed — see Resolution
test: completed
expecting: -
next_action: hand off to gap-closure planner

## Symptoms

expected: Driver import resolves shortName collision to a team that has a PhaseTeam in the target season's REGULAR phase, so group assignment succeeds without warnings and the import is complete.
actual: "PArent Team wird verwendet, jedoch mit Warning in der Preview. Dies führt dann am Ende möglicherweise zu einem unvollstöndigen Import" — resolver picks parent (organisational bucket without PhaseTeam in target season), warning fires "Team {shortName} has no PhaseTeam in REGULAR phase of target season", group assignment is null in the preview row, execute may persist the parent assignment instead of the correct sub-team.
errors: none (not an exception — Phase 66 fixed the original NonUniqueResultException; this is a semantic-correctness issue surfaced by the new resolver).
reproduction: |
  1. Start app: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (port 9091)
  2. Open `http://localhost:9091/admin/drivers/import`
  3. Submit a Google Sheet that references shortName MRL (or P1R, ZFS) for season 2023, 2024, or 2025 (data shapes confirmed via /admin/teams: parent + sub-team share shortName)
  4. Preview renders → tab-level "Group assignment warnings" panel shows "Team MRL has no PhaseTeam in REGULAR phase of target season" (etc).
started: Pre-existed in Phase 66 design (D-06 parent precedence); surfaced during /gsd-verify-work for Phase 66 on 2026-05-08.

## Evidence

- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` lines 411-427: resolver signature is `private Optional<Team> resolveTeamByShortName(String shortName)` — no season/phase context. Multi-match branch picks `parentTeam == null` first, falls back to `matches.get(0)` after WARN log.
- All 5 call sites (lines 135, 146, 166, 195, 296) pass only the raw shortName. Preview call site (line 296) already has `SeasonPhase regularPhase` resolved at line 252; the four execute call sites (135, 146, 166, 195) have `Season season` in scope and can resolve `regularPhase` via `seasonPhaseService.findRegularPhase(season.getId())`.
- PhaseTeam lookup at line 314-315 (`phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.id, team.id)`) is the visible failure point — when `parent.id` is fed in, `Optional.empty()` returns and the warning at lines 320-323 fires. `resolvedGroupName` propagates as `null` into `NewDriverRow`, `NewAssignmentRow`, `ConflictRow`, `FuzzySuggestionRow`, `UnchangedRow`.
- UAT Test 4 was skipped by the user with the explicit rationale "in jeder Saison gibt es mindestens 1 Team mit Sub-Teams und dadurch auch kollidierenden Short Names" — confirming this is the dominant production path, not a corner case.
- `PhaseTeamRepository` already exposes `findByPhaseIdAndTeamId(UUID, UUID)` — sufficient to refine candidate selection without any new repository surface.
- `TeamRepository.findAllByShortName(String)` returns `List<Team>` — exactly the pre-filtered candidate set the season-aware step needs.
- 66-CONTEXT.md D-06 ("parent precedence") rests on the assumption that parents are canonical race entities. Confirmed via `/admin/teams`: MRL has sub-teams MRL 1 + MRL 2; P1R has sub-teams P1R + P1Rx; ZFS has 2 sub-teams. Parents are organisational buckets only.

## Resolution

root_cause: |
  `DriverSheetImportService.resolveTeamByShortName` (lines 411-427) is a season-blind resolver. On multi-match shortName collision it picks `parentTeam == null` unconditionally per CONTEXT.md D-06. The user's actual data inverts D-06's assumption: parents are organisational buckets without PhaseTeam(REGULAR) entries; sub-teams are the racing entities. The resolver picks the bucket parent → line 314-315 PhaseTeam lookup misses → TEAM_NOT_IN_REGULAR_PHASE warning fires → resolvedGroupName stays null → on execute, SeasonDriver.team persists the wrong (parent) team for every collision row.

  Phase 66 hotfix did remove the original NonUniqueResultException 500 (Test 1 PASS), but the resolution policy locked by D-06 ("prefer parent unconditionally") rests on a false assumption.

fix: ""  # owned by gap-closure planner

suggested_algorithm: |
  Make the resolver season-aware:
  ```
  Optional<Team> resolveTeamByShortName(String shortName, SeasonPhase regularPhase):
    candidates = teamRepository.findAllByShortName(shortName)
    if candidates.empty:        return Optional.empty
    if candidates.size == 1:    return Optional.of(candidates[0])
    if regularPhase != null:
      forEach c in candidates:
        if phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.id, c.id).isPresent
          return Optional.of(c)                  // NEW: prefer team-with-PhaseTeam-in-target-season
    // Legacy / pre-PhaseTeam fallback (e.g. legacy seasons with no REGULAR phase):
    parent = candidates.stream.filter(t -> t.parentTeam == null).findFirst
    if parent.isPresent: return parent
    log.warn(...); return Optional.of(candidates[0])
  ```

  All 5 call sites pass `regularPhase`. Preview site (line 296) already has it cached at line 252; execute sites (135, 146, 166, 195) resolve once per season via `seasonPhaseService.findRegularPhase(season.getId())` with `EntityNotFoundException → null` graceful pattern. When `regularPhase == null` (legacy data, no REGULAR phase), resolver falls through to existing parent-precedence — preserving Phase 66 semantics for legacy seasons.

files_to_change:
  - src/main/java/org/ctc/dataimport/DriverSheetImportService.java (resolver signature + 5 call sites + per-tab regularPhase resolution in execute)
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java (invert TDD tests D-11, D-12: assert sub-team-with-PhaseTeam wins; multi-parent edge becomes the no-PhaseTeam fallback case)
  - .planning/phases/66-team-shortname-collision-fix/66-CONTEXT.md (revise D-06 to document new precedence)

edge_cases_the_fix_must_handle:
  - shortName matches BOTH parent (no PhaseTeam) AND sub-team WITH PhaseTeam → prefer sub-team-with-PhaseTeam
  - shortName matches multiple sub-teams in different seasons but only one has PhaseTeam in target season → prefer that one
  - shortName matches parent-only (no sub-team) AND parent IS in target season's REGULAR phase → keep parent
  - shortName matches no team in target season's REGULAR phase at all (legacy data) → fall back to current parent-precedence behaviour and emit existing warning
