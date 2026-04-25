---
phase: 54-preview-service-row-categorization
reviewed: 2026-04-24T00:00:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - src/main/java/org/ctc/domain/repository/SeasonRepository.java
  - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
findings:
  critical: 0
  warning: 1
  info: 3
  total: 4
status: issues_found
---

# Phase 54: Code Review Report

**Reviewed:** 2026-04-24
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found (1 warning, 3 info — no critical issues)

## Summary

The Phase 54-01 slice is a well-scoped, correctness-first implementation. The
public API (7 records + `ErrorReason` enum) matches D-04/D-05 verbatim, the
D-12 waterfall is correctly ordered in `buildTabPreview`, and the service is
genuinely stateless and thread-safe (all fields `final`, no mutable static
collections, only a `Pattern` constant). Guardrail greps are clean: no
`RaceLineup` reference (DATA-05), no `@EntityGraph` on `findByYear` (D-01),
no `@Transactional`, no `@SessionAttributes`, no free-text `findByName` lookup,
no `Map<Bucket, ...>` pseudo-typing. Logging uses parameterized `{}` and
Given-When-Then naming is consistent across all 16 tests.

One real coverage gap exists: the `EXACT`-match branch when `suggestedSeasonId
== null` (ambiguous or no-season year) falls through to `NEW_ASSIGNMENT` at
lines 174-177, and no test currently exercises that branch. The remaining
three findings are minor code-hygiene observations (redundant `.trim()`,
field-naming mismatch on `ErrorRow`, and an optional ergonomics improvement
for `UnchangedRow`).

No security, injection, or data-integrity issues. No DB writes are performed.
No Flyway migration introduced. The implementation is ready for Phase 55 to
consume unchanged, pending the warning below.

## Warnings

### WR-01: Missing test for EXACT match when `suggestedSeasonId == null`

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:174-177`
**Issue:** The D-12 waterfall step 6 has a distinct sub-branch: when an
`EXACT` driver match is found but `suggestedSeasonId` is `null` (because the
year has zero or multiple seasons), the row is classified as `NEW_ASSIGNMENT`
without consulting `SeasonDriverRepository`. The test suite covers the three
sub-cases that presuppose a resolved season (same-team UNCHANGED, different-team
CONFLICT, no-SeasonDriver NEW_ASSIGNMENT) but never combines `EXACT` with a
year that has ambiguous/no season. This leaves the `else` branch at
lines 174-177 un-covered by JaCoCo line/branch coverage and could hide a
regression (for example, if a future refactor accidentally swapped the
branches or removed the null guard).

**Fix:** Add one test along the lines of:

```java
@Test
void givenExistingDriverAndAmbiguousSeason_whenPreview_thenCategorisedAsNewAssignment() throws IOException {
    // given — two seasons for 2024 → suggestedSeasonId will be null
    Season season2024b = new Season();
    season2024b.setId(UUID.randomUUID());
    season2024b.setYear(2024);
    season2024b.setNumber(2);

    setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existing_psn", "Existing", "AHR")));
    when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024, season2024b));
    when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr));
    when(driverMatchingService.findDriver("existing_psn"))
            .thenReturn(MatchResult.exact("existing_psn", existingDriver));

    // when
    DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

    // then — no season resolved → fall through to NEW_ASSIGNMENT, no SeasonDriver lookup
    TabPreview tab = preview.tabPreviews().get(0);
    assertThat(tab.suggestedSeasonId()).isNull();
    assertThat(tab.newAssignments()).hasSize(1);
    assertThat(tab.unchanged()).isEmpty();
    assertThat(tab.conflicts()).isEmpty();
    verifyNoInteractions(seasonDriverRepository);
}
```

The final `verifyNoInteractions(seasonDriverRepository)` is the key assertion
— it pins the control-flow invariant that `SeasonDriver` is never queried
without a resolved season, which is what makes the ambiguous-season short-circuit
safe and idempotent.

## Info

### IR-01: Redundant `.trim()` on already-trimmed PSN

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:129`
**Issue:** `String normalizedPsn = rawPsnId.trim();` trims a value that
`cellToString(row, 0)` at line 105 has already trimmed (see
`cell.toString().trim()` at line 201). The second `.trim()` is a no-op.
**Fix:** Either drop it:

```java
// D-12 step 4: Duplicate PSN in tab (D-11 first occurrence wins)
if (seenPsnIds.contains(rawPsnId)) {
    errors.add(new ErrorRow(rawPsnId, rawTeamCode, ErrorReason.DUPLICATE_IN_TAB));
    continue;
}
seenPsnIds.add(rawPsnId);
```

or — if the intent was to make the normalisation explicit at the matching
boundary — rename `cellToString` to something less normalising and do a single
trim here. Either way, the current duplication muddies the contract ("is the
value I hold already trimmed?") for future maintainers.

### IR-02: `ErrorRow.rawPsnId` / `rawTeamCode` are actually trimmed, not raw

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:256-260`
**Issue:** The record field names `rawPsnId` and `rawTeamCode` suggest
unmodified source values, but every `new ErrorRow(rawPsnId, rawTeamCode, ...)`
call passes the already-trimmed output of `cellToString(...)`. A Phase-55
consumer rendering an error report may reasonably assume the fields preserve
leading/trailing whitespace that caused the error (for example, `"   "` vs
`""` both surface as `BLANK_PSN_ID`, but the report can't distinguish them).
**Fix:** Either (a) rename the fields to drop the `raw` prefix since the
content is trimmed, or (b) read the cell twice in `cellToString` — once raw,
once trimmed — and pass the untrimmed version to `ErrorRow` while continuing
to branch on the trimmed version. (a) is lower-risk and matches the actual
semantics; (b) matches the field name's promise. Not a functional bug today;
flag for Phase 55 when error rendering is added.

### IR-03: `UnchangedRow` omits `existingSeasonDriverId`

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:250-254`
**Issue:** `ConflictRow` carries `existingSeasonDriverId` so Phase 55 can
directly re-team the `SeasonDriver`. `UnchangedRow` does not, even though the
underlying `SeasonDriver` was fetched at line 154 and its id is available at
line 156. Phase 55 will either need to re-fetch or accept a weaker audit trail
for no-op rows. If the intent is genuinely "no-op, nothing to log", leave as
is; if Phase 55 wants to emit an audit entry per preview-driven unchanged row,
add the field now while the fetched `SeasonDriver` is in scope.
**Fix (optional):**

```java
public record UnchangedRow(
        String psnId,
        UUID existingDriverId,
        UUID existingSeasonDriverId,
        String teamShortName
) {}

// caller:
unchanged.add(new UnchangedRow(rawPsnId, matchedDriver.getId(), sd.getId(), rawTeamCode));
```

This is a forward-looking ergonomics note, not a bug. Leave open unless
Phase 55 planning surfaces the need.

---

_Reviewed: 2026-04-24_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
