---
phase: 70-driver-import-parent-only-team-resolution
fixed_at: 2026-05-09T14:24:32Z
review_path: .planning/phases/70-driver-import-parent-only-team-resolution/70-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 70: Code Review Fix Report

**Fixed at:** 2026-05-09T14:24:32Z
**Source review:** .planning/phases/70-driver-import-parent-only-team-resolution/70-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (1 Critical + 4 Warnings — Info findings out of scope for this iteration)
- Fixed: 5
- Skipped: 0

CR-01, WR-02, WR-03, and WR-04 share a single root cause (the form-key
contract drifted from `<year>` to `<tabName>` in Phase 60-05 but the
service was never updated). They are inseparable: changing the service
contract requires updating the controller, the IT tests, and the
Javadoc together — otherwise CI would go red. They were therefore fixed
in a single atomic commit. WR-01 is independent and was fixed in its
own commit.

## Fixed Issues

### CR-01: Form key mismatch — every `_S<N>` tab silently skipped on execute

**Files modified:**
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`
- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java`

**Commit:** `a41fbd7`

**Applied fix:** Switched `DriverSheetImportService.execute()` to read
`seasonId_<tabName>`, `skip_<psn>_<tabName>`, and `accept_<psn>_<tabName>`
keys (matching the live template emitted by Phase 60-05). This brings
service in sync with the form payload for every `^\d{4}_S\d+$` tab —
seasoned tabs (`2025_S2`, `2023_S1`, …) are no longer silently skipped.
Cache key for the FUZZY-accept path was also changed to `tab.tabName()`
so cross-tab semantics extend correctly to seasoned tabs. Targeted
verify: `./mvnw -Dtest='DriverSheetImportServiceTest,DriverSheetImportControllerTest' test`
→ 43/43 green; `./mvnw -Dtest='DriverSheetImportServiceIT' test` →
6/6 green (including the new negative regression added under WR-04).

### WR-01: Cross-tab FUZZY accept/no-accept can crash with duplicate PSN

**Files modified:**
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`
- `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java`

**Commit:** `8256a71`

**Applied fix:** Hardened the FUZZY-no-accept branch in `execute()` to
look up the existing driver by PSN inside the `computeIfAbsent`
lambda before creating a new one. This prevents the
`DataIntegrityViolationException` that previously rolled back the
entire transaction whenever the same sheet PSN appeared as FUZZY in
two tabs with mixed accept/no-accept decisions. Added regression test
`givenSameFuzzyPsnAcceptedInOneTabAndUnacceptedInAnother_whenExecute_thenNoDuplicatePsnCreated`
that reproduces the historic crash and now passes. Targeted verify:
`./mvnw -Dtest='DriverSheetImportControllerTest' test` → 20/20 green.

### WR-02: Stale Javadoc on `execute()` — documented removed `<year>` contract

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`
**Commit:** `a41fbd7` (folded into the CR-01 commit — Javadoc must
match the corrected contract introduced there)

**Applied fix:** Updated the `@param allParams` Javadoc on `execute()`
to advertise `seasonId_<tabName>`, `skip_<psnId>_<tabName>`,
`accept_<psnId>_<tabName>=<driverUUID>`, with a note that `tabName` is
the raw sheet-tab name covering both the legacy `2024` and new
`2025_S2` shapes.

### WR-03: `skippedTabYears` loses disambiguating S-number for `_S<N>` tabs

**Files modified:**
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`
- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`
**Commit:** `a41fbd7` (folded into the CR-01 commit — the rename
touches the same `ExecuteResult` class, controller flash builder, and
service `addSkippedTab` call as CR-01)

**Applied fix:** Renamed `ExecuteResult.skippedTabYears: List<Integer>`
to `skippedTabNames: List<String>` and changed `addSkippedTab(int)` to
`addSkippedTab(String)`. The user-facing flash now reads e.g.
`Skipped tabs: [2025_S2]` instead of the ambiguous `[2025, 2025]`.
Controller updated to call `getSkippedTabNames()`. Existing controller
test `givenTabWithoutSeasonId_..._thenTabSkippedAndFlaggedInFlash` only
asserts `containsString("Skipped tabs")` and continues to pass
unchanged.

### WR-04: Phase-70 IT reinforces the broken contract — `seasonId_2023` for a `2023_S1` tab

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java`
**Commit:** `a41fbd7` (folded into the CR-01 commit — the IT must
agree with the corrected service contract introduced there, otherwise
the existing two ITs go red)

**Applied fix:** Updated `givenNewDriverRowOnConsolidated2023_…` and
`givenTeamWithoutPhaseTeam_…` to use `params.put("seasonId_2023_S1", …)`
matching the `2023_S1` tab they exercise. Added a new negative
regression test `givenSeasonIdKeyUsesYearOnly_whenExecuteWithSeasonedTab_thenTabSkipped`
that locks the post-CR-01 contract: a seasoned `2023_S1` tab supplied
with the legacy `seasonId_2023` key shape is silently skipped (no
driver is created, the flash lists the tab name). Pre-fix this test
would have green-passed with `newDriversCount == 1`; post-fix it
green-passes with `skippedTabNames == ["2023_S1"]` and zero new drivers.

---

_Fixed: 2026-05-09T14:24:32Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
