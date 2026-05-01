---
plan_id: 61-gap-03
phase: 61-cleanup-quality-gate
title: Stale comments — dataimport + sitegen + gt7sync + src/test
wave: 1
gap_closure: true
autonomous: true
depends_on: []
files_modified:
  - src/main/java/org/ctc/dataimport/CsvImportService.java
  - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
  - src/main/java/org/ctc/dataimport/GoogleSheetsService.java
  - src/main/java/org/ctc/dataimport/ImportPreviewService.java
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/java/org/ctc/gt7sync/Gt7CarSyncService.java
  - src/main/java/org/ctc/gt7sync/Gt7TrackSyncService.java
  - src/test/java/org/ctc/TestHelper.java
  - src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java
  - src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java
  - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
  - src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java
requirements: [MIGR-06, QUAL-01, QUAL-02, QUAL-03]
must_haves:
  - "grep -rn -E 'Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)' src/main/java/org/ctc/dataimport src/main/java/org/ctc/sitegen src/main/java/org/ctc/gt7sync returns 0 lines"
  - "Test files: phase-narrative comments stripped, but @Test method names + given-when-then comment scaffolds remain (CLAUDE.md BDD)"
  - "./mvnw test stays GREEN (does not run -Pe2e — that's wave 4)"
---

<objective>
Remove stale Phase-narrative comments from the remaining `src/main` packages (dataimport, sitegen, gt7sync) and from
the heaviest src/test files. The src/test cluster is included here because Phase 61 plan 61-04 + 61-05 added
heavy "Phase 61 D-XX" narrative to GroupsSeasonE2ETest + LegacyMigratedSeasonE2ETest + TestHelper + the test
counterparts of services edited during cascade migration.

Purpose: G2 cleanup for the remaining three `src/main` subtrees + the heaviest src/test files. Comment-only
changes — zero behavior risk.

Output: all listed files cleaned of phase-narrative comments. Test gate stays green.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/61-cleanup-quality-gate/61-CONTEXT.md
@.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md
@CLAUDE.md

Same classification rules as 61-gap-01 + 61-gap-02 (REMOVE stale phase narrative; KEEP genuine WHY).

**Test files special handling:**
- `// given`, `// when`, `// then` BDD scaffolds STAY (CLAUDE.md mandate "Body: // given / // when / // then")
- @Test method names STAY (CLAUDE.md given-when-then naming)
- Test fixture comments like `// 4 teams: 2 in Group A, 2 in Group B (D-14 prefix)` should drop the `D-14 prefix` reference but KEEP the data-shape note as `// 4 teams: 2 in Group A, 2 in Group B`
- E2E test step comments `// 1. create season ...` `// 2. add 2nd phase ...` STAY (they describe test structure, not history)

**SiteGenerator special handling:** SiteGeneratorService.java has 13 D-XX hits. Lines 837/843 have full Javadoc
blocks with phase narrative — these need surgical edits, not wholesale removal, because the surrounding Javadoc
documents real builder contracts.
</context>

<tasks>

<task id="1" name="Sweep dataimport + sitegen + gt7sync">
  <action>
    Run `grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)" src/main/java/org/ctc/dataimport src/main/java/org/ctc/sitegen src/main/java/org/ctc/gt7sync`
    to enumerate stale markers. Apply classification + removal:

    Concrete observed targets:

    `CsvImportService.java`:
    - Line 135, 269: `// Phase 61 MIGR-06: scoring now lives on the REGULAR phase.` — surrounding code already does the lookup; drop phase prefix; if line context is genuinely useful keep `// scoring resolved via REGULAR phase` else drop entirely.
    - Line 445: `// Phase 61 CR-01: scope lookup + sortIndex calculation to the REGULAR phase. The previous ...` — drop `Phase 61 CR-01:` prefix; KEEP the substantive WHY (it explains why phase-scoping matters — that's a permanent contract).
    - Line 515: same treatment.

    `DriverSheetImportService.java` (32 D-XX hits — heaviest in dataimport):
    - Most of these reference phase-discussion decisions (D-02, D-05, D-08 from Phase 59); strip phase prefixes; KEEP algorithmic rationale (Group resolution via PhaseTeam is non-trivial and worth a one-line WHY).

    `SiteGeneratorService.java`:
    - Line 192: `// D-23: phase-aware standings via REGULAR phase ...` — drop `D-23:` prefix.
    - Line 590: `// D-23 / Phase 61 MIGR-06: phase-aware standings via the REGULAR phase. Seasons ...` — drop `D-23 / Phase 61 MIGR-06:` prefix.
    - Line 706: `// Phase 61 MIGR-06: M:N playoff_seasons table is gone — only the direct playoff ...` — drop entirely; the JPQL/finder method already shows the phase path.
    - Line 837 (Javadoc): `* Phase 61 MIGR-06: startDate/endDate live on the REGULAR SeasonPhase, not on Season.` — drop the `Phase 61 MIGR-06:` prefix; keep the substantive sentence.
    - Line 843 (Javadoc): `* Phase 61 MIGR-06 builder: constructs a SeasonEntry pulling startDate/endDate from the ...` — drop `Phase 61 MIGR-06 ` prefix; keep `builder: constructs a SeasonEntry pulling ...`.

    `gt7sync/*` — likely lower density; quick sweep + strip.

    Commit message: `docs(61-gap): remove stale comments from dataimport, sitegen, gt7sync`
  </action>
  <read_first>
    - src/main/java/org/ctc/dataimport/CsvImportService.java
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    - src/main/java/org/ctc/dataimport/GoogleSheetsService.java
    - src/main/java/org/ctc/dataimport/ImportPreviewService.java
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/main/java/org/ctc/gt7sync/*.java
  </read_first>
  <acceptance_criteria>
    - `grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)" src/main/java/org/ctc/dataimport src/main/java/org/ctc/sitegen src/main/java/org/ctc/gt7sync | grep -v '^#'` returns 0 lines
    - `./mvnw test -Dtest='*ImportServiceTest,*ImportServiceIT,SiteGeneratorServiceTest,Gt7*Test'` is BUILD SUCCESS
    - SiteGeneratorService.java line count delta is non-zero AND smaller than 30 lines (we removed comment lines, not code)
  </acceptance_criteria>
</task>

<task id="2" name="Sweep src/test heavy files (TestHelper + E2E + key service tests)">
  <action>
    For each test file in `files_modified`, strip phase-narrative comments while preserving BDD scaffolds.

    Concrete observed targets:

    `TestHelper.java`:
    - Line 36, 46, 53, 62, 80, 88: 6 `Phase 61 MIGR-06:` markers in Javadoc + comments. The Javadoc itself
      describes a permanent contract ("Bootstraps a REGULAR phase carrying scoring + format") which STAYS,
      but the `Phase 61 MIGR-06:` prefix is process history.
    - Line 80: `@deprecated Phase 61 MIGR-06: prefer {@link #createMatchdayInRegularPhase(Season, String, int)}.` —
      audit: is the deprecation still active (i.e., the deprecated method still exists and is callable)? If yes,
      KEEP `@deprecated prefer {@link ...}` but drop the `Phase 61 MIGR-06:` tag. If the deprecated method has
      been removed, drop the `@deprecated` tag entirely.

    `GroupsSeasonE2ETest.java` + `LegacyMigratedSeasonE2ETest.java` (~36 D-XX hits combined):
    - These have heavy `// D-13 hybrid asserts:`, `// D-14 prefix`, `// D-15: full UI clicks`, `// D-16: 4 races`
      style markers in setup/assertion blocks. Drop the `D-XX:` prefix; KEEP substantive assertion descriptions.
    - Example:
      - BEFORE: `// D-13 hybrid asserts: UI tab + DB phaseTeam count`
      - AFTER:  `// hybrid asserts: UI tab + DB phaseTeam count` (or just delete if next line is self-evident)

    `SiteGeneratorServiceTest.java`, `DriverSheetImportServiceTest.java`, `DriverSheetImportServiceIT.java`,
    `StandingsServiceTest.java`, `SeasonManagementServiceTest.java` — each has 8-19 D-XX hits. Same treatment.

    **DO NOT TOUCH:**
    - `// given`, `// when`, `// then` scaffolds (CLAUDE.md mandate)
    - `@Test`-annotated method names with `givenXxx_whenXxx_thenXxx` shape
    - Sheet-mock tab-name strings like `"2099"` (test-data isolation per CLAUDE.md)
    - `T-`-prefixed identifiers in fixtures

    Commit message: `docs(61-gap): remove stale comments from heavy src/test files`
  </action>
  <read_first>
    - src/test/java/org/ctc/TestHelper.java
    - src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java
    - src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java
    - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
    - src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java
    - CLAUDE.md "Test Naming (Given-When-Then)" rule
  </read_first>
  <acceptance_criteria>
    - `grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+)" src/test/java/org/ctc/TestHelper.java | grep -v '^#'` returns 0 lines
    - `grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)" src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java | grep -v '^#'` returns 0 lines
    - All 5 service-test files report 0 lines for the same grep pattern
    - `grep -c "// given\|// when\|// then" src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` is UNCHANGED pre/post (BDD scaffolds preserved)
    - `./mvnw test -Dtest='SiteGeneratorServiceTest,DriverSheetImportServiceTest,StandingsServiceTest,SeasonManagementServiceTest'` is BUILD SUCCESS (Surefire only)
    - **NOTE:** GroupsSeasonE2ETest + LegacyMigratedSeasonE2ETest run in Failsafe (-Pe2e) — not invoked here. Their compilation is verified by Surefire's javac pass.
  </acceptance_criteria>
</task>

<task id="3" name="Final src/test sweep across remaining files">
  <action>
    Sweep the remaining ~95 test files NOT explicitly listed. Run:

    ```bash
    grep -rln -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)" src/test/java | grep -vE "(GroupsSeasonE2ETest|LegacyMigratedSeasonE2ETest|TestHelper|SiteGeneratorServiceTest|DriverSheetImportServiceTest|DriverSheetImportServiceIT|StandingsServiceTest|SeasonManagementServiceTest)"
    ```

    If the list is < 10 files, sweep them all in one commit. If 10-25 files, group by package
    (`src/test/java/org/ctc/admin/...`, `src/test/java/org/ctc/domain/service/...`, etc.) and commit per group.
    If > 25 files, escalate by creating a follow-up plan `61-gap-03b` and stop here (record in SUMMARY).

    Use the same classification rules. Append all touched files to the SUMMARY file.

    Commit message(s): `docs(61-gap): remove stale comments from remaining src/test files (<group>)`
  </action>
  <read_first>
    - Output of the grep enumeration above
  </read_first>
  <acceptance_criteria>
    - `grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)" src/test/java | grep -v '^#'` returns 0 lines
    - `./mvnw test` is BUILD SUCCESS (full Surefire pass)
    - Test count UNCHANGED at the project level: compare `grep -c '@Test' $(find src/test -name '*.java') | awk -F: '{s+=$2} END {print s}'` pre and post — must be identical
  </acceptance_criteria>
</task>

</tasks>

<verification>
- `./mvnw test` BUILD SUCCESS at end of plan (full Surefire suite)
- Combined grep across src/main/java/org/ctc/{dataimport,sitegen,gt7sync} + src/test/java returns 0 stale-marker lines
- BDD scaffolds (`// given|when|then`) and section headers preserved
- @Test count unchanged at project level
</verification>

<success_criteria>
1. All 16 listed files cleaned + remaining test files swept in Task 3
2. BDD test conventions preserved
3. ./mvnw test BUILD SUCCESS, test count unchanged
4. No code-line modifications (git diff confirms comment-only edits)
</success_criteria>

<output>
After completion, create `.planning/phases/61-cleanup-quality-gate/61-gap-03-SUMMARY.md` listing all touched
files (incl. those swept in Task 3) and the final test gate result.
</output>
