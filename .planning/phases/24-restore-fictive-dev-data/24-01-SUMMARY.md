---
phase: 24-restore-fictive-dev-data
plan: 01
subsystem: dev-data
tags: [seed-data, fictive-teams, test-data, team-cards]
dependency_graph:
  requires: []
  provides: [fictive-seed-data, team-card-generation]
  affects: [TestDataService, dev-profile-startup]
tech_stack:
  added: []
  patterns: [seed-data-restoration]
key_files:
  created:
    - src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java
  modified:
    - src/main/java/org/ctc/admin/TestDataService.java
decisions:
  - Used Phase 22 (commit 5472fd6) as source for fictive data restoration
  - Made integration test filter by fictive team prefixes for robustness in shared H2
metrics:
  duration: 219s
  completed: 2026-04-10
  tasks: 3
  files: 2
---

# Phase 24 Plan 01: Restore Fictive Dev Data Summary

Restored fictive team/driver seed data in TestDataService from Phase 22 version, replacing real CTC names introduced by Phase 23, and re-enabled TeamCardService injection for dev startup card generation.

## Changes Made

### Task 1: Restore fictive teams, drivers, and TeamCardService
- Replaced 10 real CTC teams (P1R, CLR, TCR, etc.) with 10 fictive parent teams (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR)
- Restored 7 fictive sub-teams: VRX(2), SGM(2), TBR(3)
- Replaced ~120 real drivers with 100 fictive drivers (10 per team, pattern: {TEAM}_Driver{01-10})
- Restored fictive aliases (VRX_OldPSN01, SGM_CallumOld, SGM_CBriggs, ADR_JackOB_v1)
- Restored fictive season-driver assignments (100 drivers to Season 4/2026)
- Re-added TeamCardService import, final field injection, and seedTeamCards() method call
- Translated German comment to English
- E2E test data (T-ALF, T-BRV) preserved unchanged

### Task 2: Integration test for fictive seed data
- Created TestDataServiceIntegrationTest with 8 test methods
- Verifies DATA-01: 10 fictive parent teams, sub-team structure, no real CTC names
- Verifies DATA-02: 100 fictive drivers, 10 per team prefix
- Verifies DATA-03: Active season 2026, 100 season-driver assignments
- Verifies E2E test teams preserved

### Task 3: Full test suite verification
- 828 tests pass, 0 failures
- JaCoCo coverage checks met (82%+ threshold)
- No regressions from data changes

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed integration test assertions for shared H2 database**
- **Found during:** Task 3
- **Issue:** Integration tests counted ALL non-test teams/drivers, but other SpringBootTest tests create additional entities in the shared H2 database, causing count mismatches (21 parents instead of 10, 106 drivers instead of 100)
- **Fix:** Changed assertions to filter by fictive team short names (FICTIVE_TEAM_SHORT_NAMES set) and fictive driver prefix pattern ({TEAM}_Driver) instead of broad exclusion filters
- **Files modified:** src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java
- **Commit:** 0545280

## Commits

| Task | Commit | Message |
|------|--------|---------|
| 1 | 65e86a7 | feat(24-01): restore fictive teams, drivers, and TeamCardService in TestDataService |
| 2 | 4bdf003 | test(24-01): add integration test for fictive seed data constraints |
| 3 | 0545280 | fix(24-01): make integration test robust against shared H2 database |

## Self-Check: PASSED

- All 2 artifacts exist on disk
- All 3 commits found in git log
