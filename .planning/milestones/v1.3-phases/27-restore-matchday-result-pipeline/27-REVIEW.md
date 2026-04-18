---
phase: 27
status: issues_found
depth: standard
files_reviewed: 2
findings:
  critical: 1
  warning: 1
  info: 1
  total: 3
reviewed_at: 2026-04-10T00:00:00Z
---

# Code Review: Phase 27 — restore-matchday-result-pipeline

## Scope

| File | Status |
|------|--------|
| src/main/java/org/ctc/admin/TestDataService.java | Reviewed |
| src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java | Reviewed |

## Findings

### [CRITICAL] C-01: seedRoundRobinSeason uses % 5 modulus instead of % 3

**File:** `src/main/java/org/ctc/admin/TestDataService.java`, line 602
**Confidence:** 90

seedRoundRobinSeason iterates mdIndex over 0..2 (3 matchdays). Race 2 position-variety expression uses `(mdIndex + 1) % 5` — copied from seedSwissSeason which correctly uses % 5 for its 5-matchday window. For round-robin the correct modulus is % 3.

**Fix:** Change `% 5` to `% 3` on line 602.

### [WARNING] W-01: Stale comment references removed real CTC team names

**File:** `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java`, line 122
**Confidence:** 85

Comment says `// CLR, TNR, AHR parents should NOT be present` but assertion uses VRX, SGM, TBR.

**Fix:** Update comment to match assertion.

### [INFO] I-01: race2 and race3 in seedRaceLineups() saved without createTestSettings()

Pre-existing asymmetry, intentional for E2E lineup tests.

## Summary

One copy-paste bug (C-01) and one stale comment (W-01). Both easily fixable.
