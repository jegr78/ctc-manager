---
phase: 70-driver-import-parent-only-team-resolution
reviewed: 2026-05-09T14:05:17Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - src/main/java/org/ctc/admin/controller/DriverSheetImportController.java
  - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
  - src/main/resources/templates/admin/driver-import-preview.html
  - src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
findings:
  critical: 1
  warning: 4
  info: 5
  total: 10
status: issues_found
---

# Phase 70: Code Review Report

**Reviewed:** 2026-05-09T14:05:17Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

Phase 70 cleanly inverts the Phase-66 sub-team-precedence resolver to parent-precedence in `DriverSheetImportService.resolveTeamByShortName` (D-05) and decommissions the Group column / `TEAM_NOT_IN_REGULAR_PHASE` warning UX (D-09). The new resolver is small, well-documented, and the new regression test (`DriverSheetImportServiceTest #23 / D-13`) explicitly proves parent-always with execute-side `ArgumentCaptor`. No leftover references to `usesGroups`, `resolvedGroupName`, `showGroupColumn`, `TEAM_NOT_IN_REGULAR_PHASE`, or `findRegularPhase` exist in any of the reviewed `dataimport` files.

The review surfaces **one BLOCKER** that is not introduced by Phase 70 but lives squarely inside the files Phase 70 changes (and is now actively masked by tests in this PR's scope): the live form posts `seasonId_<tabName>` (e.g. `seasonId_2025_S2`) while the service reads `seasonId_<tabYear>` (e.g. `seasonId_2025`) — every `^\d{4}_S\d+$` tab will be silently skipped on `execute`, with the same mismatch on `skip_*` and `accept_*` form keys. This regressed in Phase 60-05 (commit `d556287`), survived because every existing test synthesises params programmatically and never round-trips through Thymeleaf rendering, and is now actively re-asserted by the Phase-70 IT update at `DriverSheetImportServiceIT:193/235` (uses `seasonId_2023` for a `2023_S1` tab — passes the test, contradicts the running app). I'm flagging it as BLOCKER for Phase 70 because the IT file is in this PR's scope, and the green CI signal it produces says "Phase 70 import works" while the production form path is broken for any post-Phase-59 tab. Given Phase 70 D-22 is "manual UAT on Saison 2023", the typical first sheet a UAT-er would test is `2023_S1` — that is exactly the broken case.

The remaining findings are correctness-of-counters edge cases (FUZZY accept/no-accept cross-tab interaction with PSN unique constraint), Phase-70-incidental refactoring debt (skipped-tab message reports `year` not `tabName` after the same Phase-60 contract change), and minor cleanup on naming and dead Javadoc references.

## Critical Issues

### CR-01: `seasonId_*` / `skip_*` / `accept_*` form keys mismatch between template and service for `^\d{4}_S\d+$` tabs — every `2025_S2`-style tab is silently skipped on execute

**File:**
- `src/main/resources/templates/admin/driver-import-preview.html:25-27, 94, 123`
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:109, 151, 168, 175`
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java:188-194, 234-236` (test masks the bug)

**Issue:**
Phase 60-05 (commit `d556287`) changed the template so the season-dropdown's `name` attribute, the conflict-skip checkbox, and the fuzzy-accept checkbox all use `tab.tabName()` (e.g. `2025_S2`):

```html
<select th:name="'seasonId_' + ${tab.tabName()}" ...>
<input type="checkbox" th:name="'skip_' + ${row.psnId()} + '_' + ${tab.tabName()}" ...>
<input type="checkbox" th:name="'accept_' + ${row.psnId()} + '_' + ${tab.tabName()}" ...>
```

But `DriverSheetImportService.execute()` still reads them by `tab.year()` (an `int`, formatted bare):

```java
String seasonIdStr = allParams.get("seasonId_" + tab.year());          // line 109
String skipKey = "skip_" + row.psnId() + "_" + tab.year();             // line 151
String acceptKey = "accept_" + row.psnId() + "_" + tab.year();         // line 168
```

For a sheet tab named `2025_S2`:
- Template emits `seasonId_2025_S2` in the form payload.
- Service looks up `seasonId_2025` → returns `null` → `result.addSkippedTab(tab.year())` → continue. Every `_S<N>` tab is silently skipped, no driver is imported.
- Same mismatch for `skip_imp_existing_drv_2025_S2` (template) vs `skip_imp_existing_drv_2025` (service): conflict-skip checkboxes have no effect → all conflicts are overwritten regardless of UI state.
- Same mismatch for `accept_<psn>_2025_S2` vs `accept_<psn>_2025`: fuzzy-accept selections are ignored → fuzzy rows always create a new driver instead of linking to the suggestion.

The user-visible flash message would read `Import successful: 0 new drivers, ... Skipped tabs: [2025] (no season selected).` — even though the user did select a season in the dropdown.

**Why this is a Phase-70 BLOCKER (not just a pre-existing issue):**
1. `DriverSheetImportServiceIT` is in the Phase-70 review scope and was modified by commit `1855eb6` (`test(70-03): reconcile DriverSheetImportServiceIT`). The reconciliation re-asserts the bug: tabs named `2023_S1` are exercised with `params.put("seasonId_2023", ...)` (lines 193 and 235). The IT passes only because it bypasses the template; it gives a false-green CI signal that "Phase 70 import works for `_S<N>` tabs".
2. Phase 70 D-22 explicitly calls for manual UAT on Saison 2023 (parent MRL + sub-teams in groups). The most natural sheet tab name a UAT-er would create is `2023_S1` — exactly the broken case.
3. The two collisions (year-only vs full tabName) co-exist *only* for legacy `^\d{4}$` tabs (where `tabName()` and `year()` print identical strings); every other Phase-59-era tab silently breaks the entire happy path.

**Fix:**
Pick a single key — recommend `tabName` because it is unique per tab and survives the legacy/new co-existence (multiple `^\d{4}_S\d+$` tabs in the same year):

```java
// DriverSheetImportService.execute()
String seasonIdStr = allParams.get("seasonId_" + tab.tabName());
// ...
String skipKey = "skip_" + row.psnId() + "_" + tab.tabName();
// ...
String acceptKey = "accept_" + row.psnId() + "_" + tab.tabName();
// Cache key for accept path:
driver = crossTabCreatedDrivers.computeIfAbsent(row.psnId() + "_accept_" + tab.tabName(), ...);
// Skipped-tab tracking:
result.addSkippedTab(tab.tabName());   // and change ExecuteResult.skippedTabYears: List<Integer> -> List<String>
// Update Javadoc lines 90-91:
//   skip_<psnId>_<tabName>, accept_<psnId>_<tabName>=<driverUUID>
```

Also update Javadoc on `execute()` lines 90-91 (it currently advertises the wrong contract: `seasonId_<year>`). Then add a regression IT that round-trips through MockMvc with form key `seasonId_2025_S2` to lock the contract, and update the existing IT params at `DriverSheetImportServiceIT:193, 235` and the controller-test params (every `seasonId_2021`, `seasonId_2022` is fine because tabs are legacy `2021`/`2022`; only the `2023_S1` IT path needs the rename to `seasonId_2023_S1`).

## Warnings

### WR-01: Cross-tab cache for FUZZY rows can attempt to insert a duplicate `Driver.psnId` and crash mid-import

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:166-196`

**Issue:**
The execute loop maintains `crossTabCreatedDrivers` using two different cache keys for FUZZY rows:
- accept path → key `row.psnId() + "_accept_" + tab.year()` (tab-scoped, line 175)
- no-accept path → key `row.psnId()` (NOT tab-scoped, line 179)

If the same sheet PSN appears as FUZZY in two tabs and the user **accepts** in tab 1 but **rejects** (no checkbox) in tab 2, the flow is:
1. Tab 1 (accept): cache key = `"X_accept_2024"` → looked up driverA from DB. No entry under bare `"X"` is created.
2. Tab 2 (no-accept): cache key = `"X"` → cache miss → executes the lambda → `new Driver("X", "X")` + `driverRepository.save(d)`.

`Driver.psnId` is `unique=true`/`nullable=false` (verified by `findByPsnId` usage and existing `DriverMatchingService` reliance on PSN uniqueness). The second `save` will throw `DataIntegrityViolationException`, the surrounding `@Transactional` rolls back the entire import, and the user sees the controller's catch-block "Import failed due to an internal error" flash.

The reverse order (no-accept in tab 1, accept in tab 2) is fine: tab 1 caches the new driver under key `"X"`, tab 2 uses key `"X_accept_2024"` → finds the FUZZY-accepted driver via DB → both saves succeed (different drivers).

**Fix:**
Either (a) tab-scope **both** cache keys for FUZZY rows so accept and no-accept never alias, or (b) before creating a new Driver in the no-accept branch, look in the cache *both* by `row.psnId()` and by all `row.psnId() + "_accept_*"` patterns. (a) is simpler:

```java
} else {
    String createKey = row.psnId() + "_create_" + tab.tabName();
    driver = crossTabCreatedDrivers.computeIfAbsent(createKey, ignored -> { ... });
}
```

(But note: this changes the cross-tab reuse semantics for the case where the same fuzzy-no-accept PSN appears in both tabs — the user gets two distinct new drivers with identical PSNs, which still violates the unique constraint. The deeper fix is to call `driverRepository.findByPsnId(psnId)` first inside the create-lambda and only create when absent. Recommend that as the actual remediation.)

### WR-02: Stale Javadoc on `execute()` — documents removed contract and wrong key shape

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:84-93`

**Issue:**
The Javadoc on `execute()` says:
```
* @param allParams form parameters from the execute POST (seasonId_<year>,
*                  skip_<psnId>_<year>, accept_<psnId>_<year>=<driverUUID>)
```

This (a) reflects the buggy code path described in CR-01 rather than the live form contract, and (b) uses `<year>` placeholders which were never accurate after Phase 60-05 changed the template. Until CR-01 is fixed this Javadoc should at least match the actual code; once CR-01 is fixed it should match the corrected code.

**Fix:**
After fixing CR-01:
```java
* @param allParams form parameters from the execute POST: seasonId_&lt;tabName&gt;,
*                  skip_&lt;psnId&gt;_&lt;tabName&gt;, accept_&lt;psnId&gt;_&lt;tabName&gt;=&lt;driverUUID&gt;.
*                  tabName is the raw sheet-tab name (legacy "2024" or new "2025_S2").
```

### WR-03: `ExecuteResult.skippedTabYears` accumulates duplicate years for `_S<N>` tabs and loses the disambiguating S-number from the user-facing message

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:111, 477, 485-486`

**Issue:**
`addSkippedTab(int year)` stores `tab.year()`. If a user uploads a sheet with `2025_S1` AND `2025_S2` and forgets to pick a season for both, the success-flash will read `Skipped tabs: [2025, 2025]` — they cannot tell which tab to fix. After CR-01 is fixed (using `tab.tabName()` as the key), this also causes the message to lose information that is otherwise readily available in the preview.

**Fix:**
Change `skippedTabYears: List<Integer>` to `skippedTabNames: List<String>` and store the raw `tab.tabName()`. Update the controller flash format string in `DriverSheetImportController:84` accordingly. Update test assertion in `DriverSheetImportControllerTest:438` (`containsString("Skipped tabs")` still passes; the body content changes from `[2021]` to `[2021]` for legacy or `[2025_S2]` for new — same reading).

### WR-04: `DriverSheetImportServiceIT` Test #4 / #5 leak the broken contract — passes form key `seasonId_2023` for a `2023_S1` tab, masking CR-01

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java:186-194, 225-236`

**Issue:**
Test `givenNewDriverRowOnConsolidated2023_whenExecute_thenOnlySeasonDriverIsWritten` and `givenTeamWithoutPhaseTeam_whenExecute_thenSeasonDriverWrittenAndPhaseTeamUnchanged` stub the sheet tab as `2023_S1` (line 186, 225) but supply `params.put("seasonId_2023", ...)` (lines 193, 235). They pass green only because the service incorrectly reads `seasonId_<year>` (CR-01). Once CR-01 is fixed using `tabName`, both tests will fail with "Skipped tabs: [2023_S1]" and `result.getNewDriversCount() == 0`.

This is the primary reason CR-01 has not been caught — Phase 70's own IT update reinforced the wrong contract.

**Fix:**
After fixing CR-01, change both `params.put("seasonId_2023", …)` to `params.put("seasonId_2023_S1", …)`. Add at least one *negative* test that proves wrong-key skips the tab:
```java
// new test
@Test
void givenSeasonIdKeyUsesYearOnly_whenExecuteWithSeasonedTab_thenTabSkipped() throws IOException {
    setupSheetsStub(Map.of("2023_S1", oneDataRow(...)));
    Map<String,String> params = Map.of("seasonId_2023", season.getId().toString()); // legacy bug shape
    ExecuteResult r = driverSheetImportService.execute(SHEET_URL, params);
    assertThat(r.hasSkippedTabs()).isTrue();
    assertThat(r.getNewDriversCount()).isZero();
}
```

## Info

### IN-01: `DriverSheetImportControllerTest:485-491` — test name and comment mention "drivers.html toolbar contains the import link (IMPORT-01)" but assertion is on a different controller

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java:485-491`

**Issue:**
The test `givenDriversPage_whenGet_thenContainsImportButton` does `mockMvc.perform(get("/admin/drivers"))` and asserts the response contains `/admin/drivers/import`. This test belongs in a `DriverControllerTest` (the controller serving `/admin/drivers`), not in `DriverSheetImportControllerTest`. It causes confusing test surfaces when `/admin/drivers` UI changes — failures land on a file unrelated to the changed code. Not a Phase-70 regression but visible in the reviewed file.

**Fix:** Move to `DriverControllerTest` or to a dedicated `DriverImportEntrypointTest`.

### IN-02: `DriverSheetImportController.preview` derives `hasAmbiguousTabs` from `suggestedSeasonId == null` while the per-tab banner uses `ambiguousReason != null`

**File:** `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java:49-50`

**Issue:**
The two predicates are equivalent today (every code path in `buildTabPreview` sets both fields together), but using two different keys for the same logical signal is fragile — the next change to `TabPreview` could break the page-wide banner without breaking the per-tab banner (or vice versa). A reader of the controller has to trace through 30+ lines of `buildTabPreview` to confirm equivalence.

**Fix:** Use the same predicate everywhere:
```java
model.addAttribute("hasAmbiguousTabs", preview.tabPreviews().stream()
        .anyMatch(t -> t.ambiguousReason() != null));
```

### IN-03: Magic value `"on"` for skip-checkbox check — should be a constant or use `containsKey`

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:152`

**Issue:**
`if ("on".equals(allParams.get(skipKey)))` couples the service to the HTML default checkbox value. If the template ever changes `value="on"` to `value="1"` or `value="true"` (or omits `value=` and lets browsers default to `"on"` only on some agents), this silently breaks. The accept-checkbox (`accept_*`) uses a UUID as its value and checks `!isBlank()` — a more agnostic predicate.

**Fix:** Either define a constant `private static final String CHECKBOX_ON = "on";` or use the more agnostic check `allParams.containsKey(skipKey)` (Spring only includes a checkbox in the param map when the user checked it). The latter is also more robust to template-value drift.

### IN-04: Legacy `findRegularPhase` import / helper still present in `DriverSheetImportServiceIT` after Phase-70 cleanup

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java:90-95`

**Issue:**
`findRegularPhase(Season)` helper and `phaseTeamRepository`-based assertions remain in the IT to preserve the "PhaseTeam unchanged" invariant from D-07 / D-16. This is correct and should stay — but the helper's `findFirst()` over `season.getPhases()` is fragile: it relies on lazy-loaded `phases`, which works only because the IT is `@Transactional` and OSIV is enabled. If anyone copies this helper into a non-transactional context (e.g. a unit test for a different service), they will get `LazyInitializationException` at runtime.

**Fix:** Add a comment on the helper documenting the OSIV/`@Transactional` requirement, or use an `EntityGraph`-aware repository call.

### IN-05: `@Transactional` placement on `preview()` is `readOnly = true`, but `execute()` re-invokes `preview()` from inside its own write transaction — preview's read-only transaction is suspended/replaced silently

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:58-59, 94-103`

**Issue:**
`execute()` calls `this.preview(sheetUrl)` directly (line 100). Because both methods are on the same proxy, `preview()`'s `@Transactional(readOnly = true)` annotation is *not* honored — Spring proxy semantics apply only to external calls. The active transaction at the call site is `execute()`'s read-write transaction, so `preview()` participates in it. This isn't a correctness bug today (preview only reads), but the `readOnly=true` hint on `preview()` is silently ignored when called from `execute()`, which could mislead a reader.

**Fix:** Either (a) extract a private `buildPreviewInternal(spreadsheetId)` that has no `@Transactional` annotation and is called from both public methods, or (b) add a Javadoc note on `preview()` noting that the readOnly hint applies only to the external `preview` flow. Not a behavioral bug; documentation/clarity issue only.

---

_Reviewed: 2026-05-09T14:05:17Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
