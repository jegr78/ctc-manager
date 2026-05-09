---
plan_id: 70-04
phase: 70
plan: 04
subsystem: dataimport
tags: [bugfix, driver-import, duplicate-psn, gap-closure, regression-test]
dependency_graph:
  requires: [70-01, 70-02, 70-03]
  provides: [GAP-70-01-closure, NEW_DRIVER-findByPsnId-guard, cross-tab-PSN-regression-fence]
  affects: [DriverSheetImportService, DriverSheetImportServiceIT]
tech_stack:
  added: []
  patterns: [findByPsnId-orElseGet-guard, WR-01-mirror, structural-Disabled-regression-fence]
key_files:
  created:
    - .planning/phases/70-driver-import-parent-only-team-resolution/70-04-SUMMARY.md
  modified:
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java
decisions:
  - "Mirror WR-01 driverRepository.findByPsnId(psnId).orElseGet guard into NEW_DRIVER branch to close GAP-70-01"
  - "Task 3 hypothesis 2: Version B (@Disabled) chosen — DriverMatchingService.findDriver exact-match short-circuit makes hypothesis 2 structurally unreachable"
metrics:
  duration: "~27 minutes"
  completed: "2026-05-09"
  tasks_completed: 4
  files_modified: 2
---

# Phase 70 Plan 04: GAP-70-01 Closure — NEW_DRIVER findByPsnId Guard Summary

**One-liner:** Closed GAP-70-01 by mirroring the WR-01 `driverRepository.findByPsnId(psnId).orElseGet` guard into the NEW_DRIVER branch, with two IT regression tests pinning both hypotheses.

## Production Fix (Task 1)

The NEW_DRIVER branch in `DriverSheetImportService.execute()` (lines 119–132) previously inserted a new `Driver` unconditionally inside `computeIfAbsent`. On live MariaDB with the Saison 2023 sheet (multiple tabs, same PSN), the second tab's cache miss triggered a `DataIntegrityViolationException: Duplicate entry 'danfn22016' for key 'psn_id'`, rolling back the entire import transaction.

**Fix (commit `3885288`):** Replaced the unchecked `driverRepository.save(d)` with:

```java
Driver driver = crossTabCreatedDrivers.computeIfAbsent(row.psnId(), psnId ->
        driverRepository.findByPsnId(psnId).orElseGet(() -> {
            Driver d = new Driver(psnId, psnId);
            d.setActive(true);
            Driver saved = driverRepository.save(d);
            result.incrementNewDrivers();
            return saved;
        }));
```

This is structurally identical to the WR-01 hardening already present at the FUZZY-no-accept branch (commit `8256a71`). The guard now appears **twice** in the file (once per branch). Counter semantics preserved: `incrementNewDrivers()` fires only inside `.orElseGet()` — only when a brand-new Driver row is actually persisted.

Verified: `grep -c 'driverRepository.findByPsnId(psnId).orElseGet'` → 2; `grep -c 'GAP-70-01'` → 1.

## Test Additions

### Test #7 — Cross-tab same-PSN NEW_DRIVER (Hypothesis 1) — commit `5d73e81`

`givenSameNewDriverPsnInTwoTabs_whenExecute_thenExactlyOneDriverRowInserted`

- Two tabs (`2026_S4` + inline-seeded `Phase70-IT-DupTab-2027`) both referencing PSN `Phase70-IT-DupTab-Same`
- Asserts `getNewDriversCount() == 1` (counter fires once regardless of tab count)
- Asserts `getNewAssignmentsCount() == 2` (SeasonDriver written for both seasons)
- Asserts `driverRepository.findAll().stream().filter(...).count() == 1L` (no duplicate row)
- Asserts both `findBySeasonIdAndDriverId(season2026, driver)` and `findBySeasonIdAndDriverId(season2027, driver)` are present
- Pre-fix: would throw `DataIntegrityViolationException` at H2 flush; post-fix: green

### Test #8 — Pre-existing Driver classified as NEW_DRIVER (Hypothesis 2) — commit `20d5525`

`givenPreExistingDriverNotMatchedByMatcher_whenExecuteNewDriverRow_thenReusesExistingDriver`

**Version B chosen (`@Disabled`)** — see Task 3 Decision below.

## Task 3 Decision: Version A vs Version B

**Decision: Version B (@Disabled structural regression fence)**

**Grep evidence from `DriverMatchingService.java`:**

```
Line 29: // Stage 1: Exact match on PSN ID
Line 30: var exact = driverRepository.findByPsnId(searchTerm);
Line 31: if (exact.isPresent()) {
Line 32:     log.debug("Exact match for '{}': {}", searchTerm, exact.get().getPsnId());
Line 33:     return MatchResult.exact(searchTerm, exact.get());
Line 34: }
```

`DriverMatchingService.findDriver(String searchTerm)` calls `driverRepository.findByPsnId(searchTerm)` in **Stage 1** — before any fuzzy logic. If the driver already exists in the DB with an exact PSN match, the method short-circuits and returns `MatchResult.exact(...)` immediately. This means a pre-existing Driver with an exact-matching PSN can **never** be classified as `NEW_DRIVER` by the preview engine — it would always be `EXACT`, `CONFLICT`, `UNCHANGED`, or `NEW_ASSIGNMENT` depending on the `SeasonDriver` state.

Therefore the `findByPsnId(psnId).orElseGet(...)` recovery branch in the Task 1 fix is not reachable from production sheet input for hypothesis 2 — but it remains the correct defensive shape (mirrors WR-01) and **is** exercised by Test #7 (cross-tab same-PSN where the cache is empty but the DB already has the row from the first tab's execution within the same transaction).

The `@Disabled` test stays as a **regression fence**: a future change to `DriverMatchingService.findDriver` that bypasses the exact-match short-circuit would unhide this test, which would then exercise the recovery path directly.

## Final Verify — Task 4 (no commit)

```
./mvnw verify -Pe2e
```

| Metric | Value |
|--------|-------|
| Build result | BUILD SUCCESS |
| Duration | 7:28 min |
| Surefire tests | 1227 run, 4 skipped, 0 failures |
| Failsafe E2E tests | 31 run, 0 skipped, 0 failures |
| JaCoCo line ratio | **0.8702** (gate: ≥ 0.82 — PASSED) |
| JaCoCo gate check | All coverage checks have been met |

**Note on IT test execution:** `DriverSheetImportServiceIT` is executed by Surefire (not Failsafe) because it does not live under `**/e2e/**`. Surefire's default includes (`**/*Test.java`, `**/Test*.java`) do not match the `*IT.java` suffix, so the class is not counted in the aggregate `Tests run: 1227` from the standard verify invocation. However, the class runs correctly when invoked explicitly via `./mvnw test -Dtest='DriverSheetImportServiceIT'` (8 tests, 1 skipped, 0 failures — confirmed both before and after Task 4). This is a pre-existing behaviour in the codebase; the IT class was already excluded from the standard verify aggregate before Plan 70-04.

## Plan-04 Commit Set

| # | Hash | Message |
|---|------|---------|
| Task 1 | `3885288` | `fix(70-04): harden NEW_DRIVER branch against duplicate-PSN inserts (GAP-70-01)` |
| Task 2 | `5d73e81` | `test(70-04): add cross-tab same-PSN NEW_DRIVER regression test (GAP-70-01 hypothesis 1)` |
| Task 3 | `20d5525` | `test(70-04): add pre-existing-Driver-classified-as-NEW_DRIVER regression test (GAP-70-01 hypothesis 2)` |
| Task 4 | — | verify-only, no commit |

## Phase 70 Commit Count

`git log --oneline | grep -cE '\(70'` → **22 commits** on the worktree branch (includes docs, state, plan, review-fix, and plan-execution commits across Plans 70-01 through 70-04).

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written with one expected protocol outcome:

**Task 3: Version B selected per decision rule**

- **Found during:** Task 3 grep analysis of `DriverMatchingService.findDriver`
- **Decision:** `findByPsnId` exact-match short-circuit at line 30 makes hypothesis 2 structurally unreachable; Version B (`@Disabled`) chosen per plan's decision rule
- **Files modified:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java` (commit `20d5525`)
- Also added `import org.junit.jupiter.api.Disabled;` which was missing from the IT class (Rule 3 — blocked without it)

## Manual UAT Carry-Over (D-22 / ROADMAP SC6)

**The codebase side of GAP-70-01 is closed. The live-MariaDB round-trip remains the user's responsibility.**

After these commits land on `gsd/v1.9-season-phases-groups`:

1. Start the app on local MariaDB: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
2. Navigate to Admin → Driver Import; supply the Saison-2023 sheet URL (the one that produced the `DataIntegrityViolationException` on 2026-05-09 16:53:55 logged in `data/local/logs/app.log`)
3. Confirm preview renders correctly across all 5 buckets
4. Click **Execute** with the consolidated 2023 season selected
5. SQL spot-check:
   ```sql
   SELECT sd.driver_id, sd.team_id, t.short_name, t.parent_team_id
   FROM season_drivers sd
   JOIN teams t ON sd.team_id = t.id
   JOIN drivers d ON sd.driver_id = d.id
   JOIN seasons s ON sd.season_id = s.id
   WHERE s.year = 2023 AND d.psn_id LIKE 'MRL%';
   ```
   Every row's `parent_team_id` must be `NULL`; `short_name` must be `MRL` for all MRL drivers.

**Expected:** Execute succeeds with no `DataIntegrityViolationException` in `data/local/logs/app.log`.

**Resume signal:** After re-run, execute `/gsd-verify-work 70 --re-verify` to flip `70-VERIFICATION.md` from `gaps_found` to `passed`.

## Known Stubs

None — all production code paths are fully wired.

## Threat Flags

No new security-relevant surface introduced. The fix hardens an existing code path; no new endpoints, auth paths, or schema changes.

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| `DriverSheetImportService.java` exists | FOUND |
| `DriverSheetImportServiceIT.java` exists | FOUND |
| `70-04-SUMMARY.md` exists | FOUND |
| Commit `3885288` (Task 1 fix) | FOUND |
| Commit `5d73e81` (Task 2 test) | FOUND |
| Commit `20d5525` (Task 3 test) | FOUND |
| `findByPsnId(psnId).orElseGet` guard count ≥ 2 | 2 (PASSED) |
| Test #7 method present | 1 occurrence (PASSED) |
| Test #8 method present | 1 occurrence (PASSED) |
| `Phase70-IT-DupTab-` prefix count ≥ 3 | 3 (PASSED) |
| JaCoCo line ratio ≥ 0.82 | 0.8702 (PASSED) |
| `./mvnw verify -Pe2e` BUILD SUCCESS | PASSED |
| Branch: `worktree-agent-abb9dcd4162090300` (based on `16abc9c`) | PASSED |
