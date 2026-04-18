---
phase: 36-audit-remediation
plan: 01
subsystem: templates, documentation
tags: [dead-code-removal, traceability, audit-closure, v1.5]
dependency_graph:
  requires: [34-02]
  provides: [clean-race-results-js, v1.5-traceability-closure]
  affects: [race-results.html, REQUIREMENTS.md]
tech_stack:
  added: []
  patterns: []
key_files:
  created: []
  modified:
    - src/main/resources/templates/admin/race-results.html
    - .planning/REQUIREMENTS.md
decisions:
  - "Removed only the second var parts block (dead code); preserved first var parts block (active breakdown labels)"
  - "CONV-02, CONV-03, CONV-05 checkboxes left unchecked since they are Compliant (no change needed), not Verified"
metrics:
  duration: 4m
  completed: 2026-04-14
  tasks: 2/2
  files: 2
requirements-completed: [CONV-04]
---

# Phase 36 Plan 01: Audit Remediation - Dead Code and Traceability Summary

Remove dead JS inline-style code block from race-results.html and close all 18 v1.5 requirements in REQUIREMENTS.md traceability table.

## What Was Done

### Task 1: Remove dead JS code block from race-results.html (75c6a01)

Removed 5 lines of dead JavaScript from the `calcPoints()` function in `race-results.html`. The dead code was a `var parts = []` block (lines 148-152) that built HTML strings with inline styles (`<span style="font-weight:700; color:...">`) but was never used for output -- the subsequent DOM-based implementation with CSS classes (`classList.add('results-total-value')`) had superseded it in Phase 34.

**Preserved:**
- First `var parts` block (lines 137-141): active driver breakdown labels (e.g., "10R+5Q+3FL")
- Active DOM loop (lines 148-163 after edit): CSS-class-based team totals rendering
- `// Team totals` comment as section marker

**Verification:** 885 tests pass, 0 failures, coverage threshold maintained.

### Task 2: Update REQUIREMENTS.md traceability to close v1.5 audit (5005ea7)

Updated REQUIREMENTS.md to reflect the verified completion state of all 18 v1.5 requirements:
- Changed 11 requirements from `[ ]` to `[x]` (SECU-03/04, DATA-01/03/04, ARCH-01-04, CONV-01/04)
- Changed 11 traceability table entries from "Pending" to "Verified"
- Updated CONV-04 phase column to "Phase 34, Phase 36"
- Updated coverage summary: 15 verified, 3 compliant, 0 pending

## Commits

| Task | Commit | Message |
|------|--------|---------|
| 1 | 75c6a01 | style(36-01): remove dead JS code block with inline styles from race-results.html |
| 2 | 5005ea7 | docs(36-01): update REQUIREMENTS.md traceability to close v1.5 audit |

## Deviations from Plan

### Minor Acceptance Criteria Adjustment

**1. classList.add count is 2, not 3 as plan stated**
- **Found during:** Task 1 verification
- **Issue:** Plan acceptance criteria expected `grep -c "classList.add.*results-total-value"` to return 3, but the actual code has 2 matching lines (line 159: `classList.add('results-total-value')` and line 160: `classList.add(... 'results-total-value--home' : 'results-total-value--away')`). The third `classList.add` on line 154 uses `results-total-sep`, which does not match the grep pattern.
- **Resolution:** This is a plan documentation inaccuracy, not a code issue. The active DOM loop is fully intact with all 3 classList.add calls present.

## Verification Results

| Check | Expected | Actual | Status |
|-------|----------|--------|--------|
| `grep -c "parts.push.*span style" race-results.html` | 0 | 0 | PASS |
| `grep -c "parts.push.*'R'" race-results.html` | 1 | 1 | PASS |
| `grep -c "classList.add.*results-total-value" race-results.html` | 3 (plan) / 2 (actual) | 2 | PASS (see deviation) |
| `grep -c "totalsEl.textContent" race-results.html` | 1 | 1 | PASS |
| `grep -c "\| Pending" REQUIREMENTS.md` | 0 | 0 | PASS |
| `grep -c "\[x\]" REQUIREMENTS.md` | 15 | 15 | PASS |
| `grep "Pending verification: 0" REQUIREMENTS.md` | match | match | PASS |
| `grep "Verified: 15" REQUIREMENTS.md` | match | match | PASS |
| `./mvnw verify` | 0 failures | 0 failures (885 tests) | PASS |

## Known Stubs

None -- no stubs introduced or remaining.

## Self-Check: PASSED

- [x] race-results.html exists and modified
- [x] REQUIREMENTS.md exists and modified
- [x] 36-01-SUMMARY.md created
- [x] Commit 75c6a01 exists (Task 1)
- [x] Commit 5005ea7 exists (Task 2)
