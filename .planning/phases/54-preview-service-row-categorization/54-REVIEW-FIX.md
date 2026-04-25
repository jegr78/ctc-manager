---
phase: 54-preview-service-row-categorization
fixed_at: 2026-04-25T00:02:00Z
review_path: .planning/phases/54-preview-service-row-categorization/54-REVIEW.md
iterations: 2
fix_scope: all
status: complete
findings_in_scope: 4
fixed: 4
skipped: 0
findings_fixed:
  - id: WR-01
    iteration: 1
    title: Missing test for EXACT match when suggestedSeasonId == null
    files_modified:
      - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
    commit: fa8f7d7
  - id: IR-01
    iteration: 2
    title: Redundant .trim() on already-trimmed PSN
    files_modified:
      - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    commit: 4a25fb3
  - id: IR-02
    iteration: 2
    title: ErrorRow.rawPsnId / rawTeamCode renamed to psnId / teamCode
    files_modified:
      - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
      - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
    commit: 74133f4
  - id: IR-03
    iteration: 2
    title: UnchangedRow gains existingSeasonDriverId field for Phase 55 audit symmetry
    files_modified:
      - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
      - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
    commit: dea9ee3
findings_skipped: []
tests_before_iter1: 1041
tests_after_iter1: 1042
tests_before_iter2: 1042
tests_after_iter2: 1042
commits:
  - hash: fa8f7d7
    iteration: 1
    subject: "test(54): add EXACT-match + ambiguous-season coverage for WR-01"
  - hash: 4a25fb3
    iteration: 2
    subject: "refactor(54): remove redundant .trim() in duplicate-PSN check"
  - hash: 74133f4
    iteration: 2
    subject: "refactor(54): rename ErrorRow.rawPsnId/rawTeamCode to psnId/teamCode"
  - hash: dea9ee3
    iteration: 2
    subject: "feat(54): add UnchangedRow.existingSeasonDriverId for Phase 55 audit symmetry"
---

# Phase 54: Code Review Fix Report (combined, iterations 1 + 2)

**Fixed at:** 2026-04-25T00:02:00Z
**Source review:** `.planning/phases/54-preview-service-row-categorization/54-REVIEW.md`
**Iterations:** 2

**Summary:**
- Findings in scope (CRITICAL + WARNING + INFO): 4
- Fixed: 4 (1 in iteration 1, 3 in iteration 2)
- Skipped: 0
- Tests before iter 1: 1041
- Tests after iter 1: 1042 (+1 new test)
- Tests before iter 2: 1042
- Tests after iter 2: 1042 (no new tests; existing tests updated for renamed/added fields)
- JaCoCo coverage gate: held in both iterations ("All coverage checks have been met.")
- Full `./mvnw verify` after iter 2: BUILD SUCCESS, 1042 tests, 0 failures.

## Fixed Issues

### WR-01: Missing test for EXACT match when `suggestedSeasonId == null`  *(iteration 1)*

**Files modified:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java`
**Commit:** `fa8f7d7`
**Applied fix:** Added one new `@Test` method
`givenExistingDriverAndAmbiguousSeason_whenPreview_thenCategorisedAsNewAssignment`
near the existing NEW_ASSIGNMENT tests. The test:

1. Stubs `seasonRepository.findByYear(2024)` with two seasons so
   `suggestedSeasonId` becomes `null` and `ambiguousReason` becomes
   `"Multiple seasons for year 2024"`.
2. Stubs `teamRepository.findByShortName("AHR")` to return `teamAhr`.
3. Stubs `driverMatchingService.findDriver("existing_psn")` to return
   `MatchResult.exact("existing_psn", existingDriver)`.
4. Asserts the row lands in `newAssignments` (size 1, correct
   `existingDriverId` and `teamShortName`).
5. Asserts `unchanged` and `conflicts` are both empty.
6. Asserts `verifyNoInteractions(seasonDriverRepository)` — pinning the
   D-12 step-6 short-circuit invariant: the `Optional.empty()` /
   ambiguous-season path must NOT consult `SeasonDriverRepository`. This
   covers the previously un-exercised `else` branch at
   `DriverSheetImportService.java:174-177`.

**Verification:**
- `./mvnw test -Dtest=DriverSheetImportServiceTest` → 17 tests, 0 failures.
- `./mvnw verify` → 1042 tests, 0 failures, JaCoCo "All coverage checks have been met."

### IR-01: Redundant `.trim()` on already-trimmed PSN  *(iteration 2)*

**Files modified:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`
**Commit:** `4a25fb3`
**Applied fix:** Removed the `String normalizedPsn = rawPsnId.trim();`
line in the D-12 step-4 duplicate-PSN check. `rawPsnId` is already
trimmed by `cellToString(...)` (see `cell.toString().trim()` in the
helper), so the second `.trim()` was a pure no-op. The
`seenPsnIds.contains(...)`, `seenPsnIds.add(...)`, and
`driverMatchingService.findDriver(...)` calls now use `rawPsnId`
directly. A clarifying comment was added so future maintainers know the
contract ("rawPsnId is already trimmed by cellToString — no further
normalisation needed").

**Verification:**
- Re-read service lines 128-138 — fix present, surrounding code intact.
- Tier 2: `./mvnw test -Dtest=DriverSheetImportServiceTest` → 17 tests
  green, no compile errors.
- Behaviour identical (cellToString trims; double-trim was idempotent).

### IR-02: `ErrorRow.rawPsnId` / `rawTeamCode` renamed to `psnId` / `teamCode`  *(iteration 2)*

**Files modified:**
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java`

**Commit:** `74133f4`
**Applied fix (option a per reviewer — lower risk, matches actual
semantics):**
- `public record ErrorRow(String rawPsnId, String rawTeamCode, ErrorReason reason)`
  → `public record ErrorRow(String psnId, String teamCode, ErrorReason reason)`.
- Service-side `new ErrorRow(rawPsnId, rawTeamCode, ...)` call sites
  unchanged — only positional args, the local variable names (`rawPsnId`,
  `rawTeamCode`) are kept because they reflect what `cellToString`
  returns in the local read context. Only the record's external field
  names changed.
- Test-side accessors: `assertThat(tab.errors().get(0).rawTeamCode())`
  at lines 437 and 464 → `.teamCode()`. No new test methods were added.

**Rationale:** Phase 55 (admin UI consumer) is not yet built. The
record is a pre-consumer public API contract, so tightening the field
name now is non-breaking. The values `ErrorRow` carries are trimmed
(via `cellToString`), not raw — the new name reflects this honestly.

**Verification:**
- Re-read service lines 256-260 (record) and 105-131 (call sites).
- Re-read test lines 437 and 464 (renamed accessor calls).
- Tier 2: `./mvnw test -Dtest=DriverSheetImportServiceTest` → 17 tests
  green, compile clean.

### IR-03: `UnchangedRow` gains `existingSeasonDriverId` for Phase 55 audit symmetry  *(iteration 2)*

**Files modified:**
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java`

**Commit:** `dea9ee3`
**Applied fix:**
- `public record UnchangedRow(String psnId, UUID existingDriverId, String teamShortName)`
  → `public record UnchangedRow(String psnId, UUID existingDriverId, UUID existingSeasonDriverId, String teamShortName)`.
- Call site at the same-team UNCHANGED branch updated:
  `unchanged.add(new UnchangedRow(rawPsnId, matchedDriver.getId(), rawTeamCode))`
  → `unchanged.add(new UnchangedRow(rawPsnId, matchedDriver.getId(), sd.getId(), rawTeamCode))`.
  The fetched `SeasonDriver` (`sd`) is already in scope at this point,
  so no additional repository call is required.
- Test-side: strengthened the existing
  `givenExistingSeasonDriverSameTeam_whenPreview_thenCategorisedAsUnchanged`
  test with one extra assertion exercising the new accessor:
  `assertThat(tab.unchanged().get(0).existingSeasonDriverId()).isEqualTo(seasonDriverSameTeam.getId())`.
  No new test method was added — the existing UNCHANGED test now pins
  the new field's wiring.

**Rationale:** `ConflictRow` already carried `existingSeasonDriverId`
so Phase 55 can re-team the `SeasonDriver` directly. `UnchangedRow`
omitted the same field even though `sd` was in scope at line 156. Phase
55 will likely emit a per-row audit entry for no-op rows — having the
ID on the row avoids a redundant re-fetch. Same Phase-55-not-built
rationale as IR-02 makes this safe to add now.

**Verification:**
- Re-read service lines 250-256 (record) and 157-159 (call site).
- Re-read test lines 374-381 (strengthened assertion block).
- Tier 2: `./mvnw test -Dtest=DriverSheetImportServiceTest` → 17 tests
  green; the new `existingSeasonDriverId()` accessor assertion passes.

## Skipped Issues

None — all 4 findings (1 warning + 3 info) are now fixed.

## Final Verification

After all 4 fix commits, `./mvnw verify` was run end-to-end:
- 1042 tests, 0 failures, 0 errors, 0 skipped.
- JaCoCo `check` reports "All coverage checks have been met." (82% gate
  held; the service module sits well above the floor).
- BUILD SUCCESS in ~2:55.

No files outside `DriverSheetImportService.java` and
`DriverSheetImportServiceTest.java` were modified across the three
iteration-2 commits. STATE.md, ROADMAP.md, REQUIREMENTS.md, and all
other planning artifacts were not touched.

---

_Fixed (combined iter 1 + iter 2): 2026-04-25T00:02:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iterations: 2_
