---
phase: 26-restore-fictive-team-logos
plan: "01"
subsystem: demo-data
tags: [demo, team-logos, test-data, classpath-resources]
one_liner: "Restored 10 fictive team logo PNGs from git history, removed 10 real CTC logos, added DATA-08 classpath resource integration tests"
dependency_graph:
  requires: []
  provides: [fictive-team-logos-classpath, DATA-08-verification]
  affects: [TestDataService-copyDemoLogos, demo-profile-startup]
tech_stack:
  added: []
  patterns: [ClassPathResource-lookup, TDD-classpath-resource-test]
key_files:
  created:
    - src/main/resources/demo/team-logos/VRX.png
    - src/main/resources/demo/team-logos/SGM.png
    - src/main/resources/demo/team-logos/ADR.png
    - src/main/resources/demo/team-logos/TBR.png
    - src/main/resources/demo/team-logos/ICL.png
    - src/main/resources/demo/team-logos/SVT.png
    - src/main/resources/demo/team-logos/NFR.png
    - src/main/resources/demo/team-logos/EGP.png
    - src/main/resources/demo/team-logos/HMS.png
    - src/main/resources/demo/team-logos/PWR.png
    - src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java
  modified: []
  deleted:
    - src/main/resources/demo/team-logos/AHR.png
    - src/main/resources/demo/team-logos/ART.png
    - src/main/resources/demo/team-logos/CLR.png
    - src/main/resources/demo/team-logos/DTR.png
    - src/main/resources/demo/team-logos/GXR.png
    - src/main/resources/demo/team-logos/MRL.png
    - src/main/resources/demo/team-logos/P1R.png
    - src/main/resources/demo/team-logos/TCR.png
    - src/main/resources/demo/team-logos/TNR.png
    - src/main/resources/demo/team-logos/VEZ.png
decisions:
  - "Restored logos via `git checkout 3e640f9 -- src/main/resources/demo/team-logos/` then manually removed real CTC logos (commit contained both sets)"
  - "Integration test does not require @SpringBootTest — pure ClassPathResource.exists() check suffices"
metrics:
  duration_minutes: 12
  completed_date: "2026-04-10"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 21
---

# Phase 26 Plan 01: Restore Fictive Team Logos Summary

**One-liner:** Restored 10 fictive team logo PNGs from git history, removed 10 real CTC logos, added DATA-08 classpath resource integration tests.

## Objective

Fix DATA-08 gap — `copyDemoLogos()` in TestDataService was failing silently because it looked for fictive short names (VRX, SGM etc.) but only found real CTC logos (AHR, CLR etc.) in the classpath.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Restore fictive team logos from git history | 22f907f | +10 fictive PNGs, -10 real CTC PNGs |
| 2 | Add DATA-08 integration tests | c6d8e29 | +TestDataServiceIntegrationTest.java |

## Verification Results

1. `ls src/main/resources/demo/team-logos/` — exactly 10 files: ADR, EGP, HMS, ICL, NFR, PWR, SGM, SVT, TBR, VRX
2. `./mvnw verify` — 854 tests, 0 failures, BUILD SUCCESS
3. `TestDataServiceIntegrationTest` — 2/2 tests pass (both fictive logo presence and real logo absence verified)

## Deviations from Plan

**1. [Rule 1 - Bug] git checkout 3e640f9 restored both fictive and real logos**
- **Found during:** Task 1
- **Issue:** Commit `3e640f9` (Phase 22) contained all 20 logos (10 fictive + 10 real). The plan expected only fictive logos to be present at that commit, but Phase 22 had replaced the old logos, not cleaned up.
- **Fix:** After `git checkout 3e640f9 -- src/main/resources/demo/team-logos/`, manually deleted the 10 real CTC logos (AHR, ART, CLR, DTR, GXR, MRL, P1R, TCR, TNR, VEZ).
- **Files modified:** 20 logo files (10 added, 10 deleted)
- **Commit:** 22f907f

## Self-Check

### Files verified:

- [x] src/main/resources/demo/team-logos/VRX.png (9908 bytes)
- [x] src/main/resources/demo/team-logos/SGM.png (9795 bytes)
- [x] src/main/resources/demo/team-logos/ADR.png (9861 bytes)
- [x] src/main/resources/demo/team-logos/TBR.png (9028 bytes)
- [x] src/main/resources/demo/team-logos/ICL.png (8857 bytes)
- [x] src/main/resources/demo/team-logos/SVT.png (9479 bytes)
- [x] src/main/resources/demo/team-logos/NFR.png (9168 bytes)
- [x] src/main/resources/demo/team-logos/EGP.png (9470 bytes)
- [x] src/main/resources/demo/team-logos/HMS.png (9693 bytes)
- [x] src/main/resources/demo/team-logos/PWR.png (9648 bytes)
- [x] src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java

### Commits verified:

- [x] 22f907f — fix(26-01): restore 10 fictive team logos, remove 10 real CTC logos
- [x] c6d8e29 — test(26-01): add DATA-08 integration tests for fictive team logo classpath resources

## Self-Check: PASSED
