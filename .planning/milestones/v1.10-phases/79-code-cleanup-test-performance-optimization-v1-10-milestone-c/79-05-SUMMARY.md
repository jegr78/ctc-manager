---
phase: 79
plan: "05"
subsystem: docs
tags: [docs, conventions, test-discipline]
dependency_graph:
  requires: [79-03]
  provides: [test-invocation-discipline-doc]
  affects: []
tech_stack:
  added: []
  patterns: [convention-codification]
key_files:
  created: []
  modified:
    - .planning/codebase/TESTING.md
decisions:
  - "Replaced the placeholder Test Invocation Discipline section added during Wave 3 with the full plan-mandated content (3 sub-rules + run-order independence verification)"
  - "Updated trailing analysis-date line to note both Phase 79 D-08 additions (Test Invocation Discipline + Test Categorization @Tag)"
metrics:
  duration: "~10 min"
  completed_date: "2026-05-15"
  tasks_completed: 1
  files_modified: 1
---

# Phase 79 Plan 05: Test Invocation Discipline Doc Summary

Codified the existing `feedback_test_call_optimization` user-memory rule into a permanent project-level convention by appending the canonical `## Test Invocation Discipline` section to `.planning/codebase/TESTING.md`. Per D-08: NOT a GSD-orchestrator change — only a documented convention.

## What Was Built

### Task 1: Append Test Invocation Discipline section to TESTING.md

The Wave-3 perf commit (`93cd0f5`) had introduced a placeholder version of this section (a single table) to support the immediate need for the project-level rule reference. This plan replaces that placeholder with the full plan-mandated content from RESEARCH §"Test-Invocation-Discipline Section Draft":

- **H2 heading** `## Test Invocation Discipline`
- **Attribution line** `**Codified from `feedback_test_call_optimization` (Phase 79 D-08).**`
- **Sub-rule 1** `### Rule: One Final Full Run Per Phase` — explains `./mvnw verify -Pe2e` is the phase final gate, with 4 targeted-invocation examples for between-wave use
- **Sub-rule 2** `### Rule: Do Not Re-Run Full Suite Between Waves` — 5-row table mapping context → invocation
- **Sub-rule 3** `### Rule: Run Order for Independence Verification` — `reversealphabetical` + 3 random seeds (1234/5678/9999)

Updated the trailing italic analysis-date line to note both Phase 79 D-08 additions:

```
*Testing analysis: 2026-04-07 (last updated: 2026-05-15 — Phase 79 D-08 added Test Invocation Discipline section + Test Categorization (`@Tag`) section)*
```

## Deviations from Plan

### 1. [Rule 2 — Process] Section was partially added during Wave 3 (intentional pre-write)

- **Found during:** Wave 3 implementation (commit `93cd0f5`)
- **Issue:** When implementing Plan 03's @Tag-based routing, the new `## Test Categorization (@Tag)` section needed a sibling `## Test Invocation Discipline` section to make the cross-references coherent. Rather than leaving a forward-reference to "Plan 05 will add this", a placeholder version (single table, attribution) was added in the Wave-3 commit.
- **Fix:** Plan 05 replaces the placeholder with the full plan-mandated content (3 sub-rules + run-order independence). The placeholder's table content is fully covered by the new sub-rule 2's table. No content lost; structure expanded as plan specified.
- **Files modified:** TESTING.md
- **Commits:** the docs(79) commit at HEAD

## Verification

```
✓ ## Test Invocation Discipline (heading present)
✓ feedback_test_call_optimization (attribution present)
✓ Run Order for Independence Verification (sub-rule 3 present)
✓ seed=1234, seed=5678, seed=9999 (3 random seeds present)
✓ reversealphabetical (run-order idiom present)
✓ Phase 79 D-08 (cross-reference present)
✓ Backtick fence balance: 50 (even)
```

## Known Stubs

None.

## Threat Flags

None — docs-only change.

## Self-Check

Files exist:
- `.planning/codebase/TESTING.md` updated section: VERIFIED via grep checks
- All 7 acceptance grep patterns match: VERIFIED

Branch: `gsd/v1.10-platform-and-backup`: VERIFIED

## Self-Check: PASSED
