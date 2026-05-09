---
phase: 70
plan: 01
subsystem: dataimport
tags: [refactor, driver-import, parent-only-resolver, group-resolution-removal]
requires:
  - phase: 66
    plan: 02
    note: "Phase 66 D-04 sub-team-with-PhaseTeam-wins resolver (now superseded)"
provides:
  - "Parent-precedence resolveTeamByShortName(String) — single-arg signature"
  - "DriverSheetImportService stripped of SeasonPhaseService, PhaseTeamRepository, PhaseLayout, SeasonPhase, EntityNotFoundException dependencies"
  - "Slimmed TabPreview record (no warnings, no usesGroups)"
  - "Slimmed 5 row records (no resolvedGroupName field)"
affects:
  - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java  # intentionally RED until plan 70-03
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java     # intentionally RED until plan 70-03
tech_stack:
  added: []
  patterns:
    - "Parent-precedence multi-match resolver (Phase 70 D-05 — inverts Phase 66 D-04)"
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
decisions:
  - "D-05 invert: parent always wins on multi-match; phaseTeamRepository never consulted"
  - "D-06 implement: regularPhase parameter removed from resolveTeamByShortName signature"
  - "D-07 implement: both findRegularPhase call-sites in execute() and buildTabPreview() deleted"
  - "D-08 implement: phaseTeamRepository.findByPhaseIdAndTeamId call gone from preview path"
  - "D-09 implement: WarningType enum + TabWarning record + warnings field + resolvedGroupName field + usesGroups field all removed"
  - "D-10 confirm: ErrorReason enum unchanged (BLANK_PSN_ID, BLANK_TEAM_CODE, UNKNOWN_TEAM_CODE, DUPLICATE_IN_TAB)"
  - "D-18 confirm: branch invariant gsd/v1.9-season-phases-groups held across both commits"
  - "D-19 confirm: atomic per-task commits, refactor(70-01): scope, no branch switching/stash/reset"
  - "D-21 confirm: no mid-phase ./mvnw verify; production compile only (./mvnw clean compile -> BUILD SUCCESS)"
metrics:
  duration_minutes: 8
  completed_at: "2026-05-09T13:25:21Z"
  tasks_completed: 2
  files_modified: 1
  lines_removed: 122
  lines_added: 32
---

# Phase 70 Plan 01: Driver Import — Parent-Only Team Resolver + Group-Resolution Removal Summary

Inverted Phase 66 D-04 sub-team resolver to parent-precedence and stripped the entire group-resolution surface (PhaseTeam lookup, TEAM_NOT_IN_REGULAR_PHASE warning, usesGroups flag, resolvedGroupName field on 5 row records) from `DriverSheetImportService` so `SeasonDriver.team` is always the parent at the season level.

## Resolver — New Signature + Body

```java
/**
 * Resolves a team by shortName with parent-precedence on multi-match.
 * <p>
 * The {@code teams.short_name} column is intentionally non-unique — a parent team and
 * one of its sub-teams may share the same shortName (e.g. parent {@code ZFS} + sub
 * {@code ZFS}). Per Phase 70 (D-01..D-05) the import always assigns the parent at the
 * season level; sub-team variation is per-match (RaceLineup), not per-season.
 * <ol>
 *   <li><b>0 matches:</b> empty — caller emits {@code UNKNOWN_TEAM_CODE}.</li>
 *   <li><b>1 match:</b> return it (parent or solo-sub with its own unique shortName, both legitimate).</li>
 *   <li><b>N matches:</b> return the first {@code parentTeam == null} candidate.
 *       If no candidate is a parent (data-integrity edge), log WARN and return the first deterministically.</li>
 * </ol>
 * Inverts Phase 66 D-04 — see {@code 70-CONTEXT.md} D-05.
 *
 * @param shortName trimmed team short code from the sheet
 * @return the resolved team (parent precedence on multi-match), or empty if no team matches
 */
private Optional<Team> resolveTeamByShortName(String shortName) {
    List<Team> matches = teamRepository.findAllByShortName(shortName);
    if (matches.isEmpty()) {
        return Optional.empty();
    }
    if (matches.size() == 1) {
        return Optional.of(matches.get(0));
    }
    Optional<Team> parent = matches.stream()
            .filter(t -> t.getParentTeam() == null)
            .findFirst();
    if (parent.isPresent()) {
        return parent;
    }
    log.warn("Multiple teams share shortName '{}' with no parent — picking first deterministically (data-integrity issue)", shortName);
    return Optional.of(matches.get(0));
}
```

The previous Phase-66 implementation had signature `resolveTeamByShortName(String shortName, SeasonPhase regularPhase)` and looped over `phaseTeamRepository.findByPhaseIdAndTeamId(...)` to prefer sub-teams rostered in the REGULAR phase before falling back to parent. That loop is gone.

## Removed Symbols

### Imports (5 — all removed)

- `org.ctc.domain.exception.EntityNotFoundException`
- `org.ctc.domain.model.PhaseLayout`
- `org.ctc.domain.model.SeasonPhase`
- `org.ctc.domain.repository.PhaseTeamRepository`
- `org.ctc.domain.service.SeasonPhaseService`

`BusinessRuleException` and `SeasonManagementService` (used by `findUnique`) are kept.

### Constructor-injected fields (2 — removed)

- `private final SeasonPhaseService seasonPhaseService;`
- `private final PhaseTeamRepository phaseTeamRepository;`

Surrounding comment `// Group-resolution dependencies` removed too.

### Method-body deletions

- `execute(...)` — REGULAR-phase resolution try/catch block (former lines 127-133) removed
- `buildTabPreview(...)` — REGULAR-phase resolution + `usesGroups` block (former lines 257-269) removed
- `buildTabPreview(...)` — group-resolution block (former lines 324-340: `phaseTeamRepository.findByPhaseIdAndTeamId(...)` call + `warnings.add(new TabWarning(...))` emission) removed
- `List<TabWarning> warnings` local + `Set<String> warnedTeams` local removed
- `log.debug(...)` at end of `buildTabPreview` no longer references `warnings.size()`

### Records / enums / fields (removed)

- `TabPreview.warnings` field (List<TabWarning>) — gone
- `TabPreview.usesGroups` field (boolean) — gone
- `NewDriverRow.resolvedGroupName` — gone
- `NewAssignmentRow.resolvedGroupName` — gone
- `ConflictRow.resolvedGroupName` — gone
- `FuzzySuggestionRow.resolvedGroupName` — gone
- `UnchangedRow.resolvedGroupName` — gone
- `public record TabWarning(WarningType type, String teamShortName, String message)` — gone
- `public enum WarningType { TEAM_NOT_IN_REGULAR_PHASE(...) }` — gone

`ErrorReason` enum unchanged (D-10): BLANK_PSN_ID, BLANK_TEAM_CODE, UNKNOWN_TEAM_CODE, DUPLICATE_IN_TAB.

## Call-Site Updates

All 5 call-sites of `resolveTeamByShortName` were updated to drop the `regularPhase` argument:

| Old line | New line | Context | Now passes |
|----------|----------|---------|------------|
| 144 | 127 | execute() NEW_DRIVER loop | `row.teamShortName()` |
| 155 | 138 | execute() NEW_ASSIGNMENT loop | `row.teamShortName()` |
| 175 | 158 | execute() CONFLICT loop | `row.sheetTeamShortName()` |
| 204 | 187 | execute() FUZZY_SUGGESTION loop | `row.teamShortName()` |
| 309 | 276 | buildTabPreview() Step 3 | `rawTeamCode` |

Total occurrences in file: 6 (1 declaration + 5 calls). Verified by `grep -cE 'resolveTeamByShortName\\(' = 6`.

## Compilation State

`./mvnw clean compile` after Task 2 → `BUILD SUCCESS` (182 source files). The production code compiles clean.

## Test State (Intentionally Broken)

The unit and integration test suites for `DriverSheetImportService` are **intentionally RED** after this plan. Plan 70-03 reconciles them. Affected tests reference removed symbols:

- `DriverSheetImportServiceTest`:
  - line 7: `import org.ctc.dataimport.DriverSheetImportService.WarningType;` (broken import)
  - line 651: `tab.newDrivers().get(0).resolvedGroupName()` (record field gone)
  - line 676, 807: `tab.warnings().get(0).type()).isEqualTo(WarningType.TEAM_NOT_IN_REGULAR_PHASE)` (warnings field + enum gone)
  - line 726: `tab.newDrivers().get(0).resolvedGroupName()` (record field gone)
- `DriverSheetImportServiceIT`:
  - lines 7-8: imports of `TabWarning` + `WarningType` (broken)
  - lines 204, 225, 257: `row.resolvedGroupName()` references
  - lines 251-252: `tab.warnings()` + `WarningType.TEAM_NOT_IN_REGULAR_PHASE`

This is **expected and not a regression** — Plan 70-03 (Wave 2) deletes superseded tests (#16, #19, #20, #23, #24) and inverts the parent-precedence test, then runs the final `./mvnw verify -Pe2e` gate.

Plan 70-02 (Wave 1, parallel) handles the disjoint Controller + Template + ControllerTest surface (`showGroupColumn`, group-column header/cells, warning box).

## Deviations from Plan

None — plan executed exactly as written. The two atomic commits map 1:1 to Tasks 1 and 2; all grep gates passed on first verify run; production compile clean on first attempt.

## Self-Check Hashes

- Task 1 commit: `090c2a3` — `refactor(70-01): parent-precedence resolveTeamByShortName, drop regularPhase parameter (D-05, D-06)`
- Task 2 commit: `e300bf1` — `refactor(70-01): remove group-resolution branch + TEAM_NOT_IN_REGULAR_PHASE warning + reduce row records (D-07, D-08, D-09, D-10)`

Branch at every commit: `gsd/v1.9-season-phases-groups` (D-18 invariant held).

## Verification Snapshot (post-Task-2)

```
grep -c TEAM_NOT_IN_REGULAR_PHASE       → 0
grep -c phaseTeamRepository             → 0
grep -cE PhaseLayout|SeasonPhase\\s     → 0
grep -c findRegularPhase                → 0
grep -c usesGroups                      → 0
grep -cE WarningType|TabWarning         → 0
grep -c resolvedGroupName               → 0
grep -cE removed-imports                → 0
grep -c seasonManagementService         → 3 (≥3, field + 2 findUnique calls)
grep -cE resolveTeamByShortName\\(      → 6 (1 decl + 5 callers)
grep -cF 'private Optional<Team> resolveTeamByShortName(String shortName)' → 1
./mvnw clean compile                    → BUILD SUCCESS (182 source files)
git branch --show-current               → gsd/v1.9-season-phases-groups
```

## Self-Check: PASSED

- 70-01-SUMMARY.md exists at `.planning/phases/70-driver-import-parent-only-team-resolution/70-01-SUMMARY.md`
- DriverSheetImportService.java exists and compiles
- Commit `090c2a3` (Task 1) found in git log
- Commit `e300bf1` (Task 2) found in git log
- Branch is `gsd/v1.9-season-phases-groups`
