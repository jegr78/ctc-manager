---
plan_id: 61-gap-07
phase: 61-cleanup-quality-gate
title: Dead code — admin layer + dataimport + sitegen + gt7sync
wave: 2
gap_closure: true
autonomous: true
depends_on: [61-gap-02, 61-gap-03]
files_modified:
  - src/main/java/org/ctc/admin/TestDataService.java
  - src/main/java/org/ctc/admin/DevDataSeeder.java
  - src/main/java/org/ctc/admin/service/SeasonFormService.java
  - src/main/java/org/ctc/admin/service/SeasonPhaseFormService.java
  - src/main/java/org/ctc/admin/service/PlayoffFormService.java
  - src/main/java/org/ctc/admin/service/MatchdayFormService.java
  - src/main/java/org/ctc/admin/service/StandingsViewService.java
  - src/main/java/org/ctc/admin/controller/SeasonController.java
  - src/main/java/org/ctc/admin/controller/PlayoffController.java
  - src/main/java/org/ctc/admin/controller/StandingsController.java
  - src/main/java/org/ctc/dataimport/CsvImportService.java
  - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/java/org/ctc/gt7sync/Gt7CarSyncService.java
  - src/main/java/org/ctc/gt7sync/Gt7TrackSyncService.java
requirements: [MIGR-06, QUAL-01, QUAL-02, QUAL-03]
must_haves:
  - "All identified dead branches/methods/imports in the listed subtrees are removed or NEEDS_CONTEXT-tracked"
  - "Defensive null-guards in admin layer classified KEEP (boundary) or REMOVE (internal)"
  - "Each removal has regression-test or grep-zero-callers proof"
  - "./mvnw verify -DskipITs BUILD SUCCESS, line coverage >= 82%"
---

<objective>
Continue G3 + G4 cleanup for the admin + dataimport + sitegen + gt7sync subtrees, applying the same defensive
removal protocol as 61-gap-06.

Scope highlights based on planning audit:
- WR-01 (already fixed) removed a dead fallback in SiteGeneratorService — but related dead code may remain
- WR-02 (already fixed) removed `getDetailData` — but other unused methods may exist
- DriverSheetImportService has heavy phase-narrative + likely unused private helpers from cascade churn
- TestDataService is dev-only; dead code here has lower risk because it doesn't affect production users — BUT
  removing it could break DevDataSeeder profile bootstrap, so caution is needed

Purpose: G3 + G4 cleanup for admin/dataimport/sitegen/gt7sync.

Output: dead code removed, regression tests added where applicable, coverage stays >= 82%.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/61-cleanup-quality-gate/61-CONTEXT.md
@.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md
@.planning/phases/61-cleanup-quality-gate/61-REVIEW-FIX.md
@CLAUDE.md

Same defensive removal protocol as 61-gap-06.

**Boundary KEEP cases for this layer:**
- All controller `@RequestParam`/`@PathVariable` validations (controllers ARE the system boundary per CLAUDE.md)
- All `try/catch (IOException ...)` in `dataimport` (CSV file reads, Google Sheets API)
- All `try/catch (DataIntegrityViolationException ...)` in admin services (Spring boundary)
- `NumberFormatException` catches in CSV-row parsing (user-supplied data boundary)
- gt7sync Jsoup parsing — external HTTP boundary, all defensive catches KEEP

**Removal candidates to AUDIT:**
- TestDataService private helpers — many were used during cascade migration as scaffolding; some may now be dead
- DriverSheetImportService — Phase 59 added many private methods (`resolvePhaseTeamGroup`, `mergeWarnings`, etc.); some may be unused after Phase 60-61 simplifications
- SiteGeneratorService — even after WR-01/WR-05 fixes, the `@SuppressWarnings("deprecation")` removal may have left orphan helper methods
- Form services in admin — old Phase 56-57 helpers that were replaced by Phase 58-60 cleaner equivalents

**DevDataSeeder special handling:** `DevDataSeeder.java` runs only on `dev,demo` profile. It calls into
`TestDataService.seedAll()`. Dead code here can be removed but ANY change to TestDataService methods that
DevDataSeeder transitively calls MUST keep DevDataSeeder green. Test gate:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo
```
is too heavy for plan execution; instead verify via the `DevDataSeederIntegrationTest` (if it exists — search first).
</context>

<tasks>

<task id="1" name="Audit + remove dead code in admin/admin.service/admin.controller">
  <action>
    Apply the same audit pattern as 61-gap-06-Task-1, but to the admin layer subset.

    Step 1 — enumerate private methods + unused fields:
    ```bash
    for f in src/main/java/org/ctc/admin/TestDataService.java \
             src/main/java/org/ctc/admin/DevDataSeeder.java \
             src/main/java/org/ctc/admin/service/*.java \
             src/main/java/org/ctc/admin/controller/SeasonController.java \
             src/main/java/org/ctc/admin/controller/PlayoffController.java \
             src/main/java/org/ctc/admin/controller/StandingsController.java; do
      echo "=== $f ==="
      grep -oE "private[^(]*\b[a-zA-Z][a-zA-Z0-9]*\s*\(" "$f" | sed -E 's/.*\b([a-zA-Z][a-zA-Z0-9]*)\s*\(/\1/' | while read m; do
        count=$(grep -c "\b${m}\s*(" "$f")
        if [ "$count" -le 1 ]; then
          echo "  POTENTIAL DEAD: $m ($count occurrences)"
        fi
      done
    done
    ```

    Step 2 — for each "POTENTIAL DEAD" entry:
    1. Confirm zero callers cross-file (`grep -rn "<methodName>" src/main src/test`)
    2. Check git blame: `git log -L :<methodName>:<filePath> | head -30`
    3. If safe (zero callers + not reflection-marked): write a one-paragraph commit message explaining
       why-safe-to-remove, then remove.
    4. Run `./mvnw test -Dtest='<TouchedFile>Test'` after each removal.

    **TestDataService caution:** Methods like `seedSeason1`, `seedSeason2024`, etc. are called from
    `seedAll()` and are NOT dead even if the local `grep -c` returns 1 (the call site might be in a
    different file pattern). Trace `seedAll()` carefully:
    ```bash
    grep -n "seed[A-Z]" src/main/java/org/ctc/admin/TestDataService.java | head -30
    ```
    Only remove if zero callers across the file.

    **DevDataSeeder caution:** Don't remove anything that DevDataSeeder consumes. Verify by:
    ```bash
    grep -rn "testDataService\." src/main/java/org/ctc/admin/DevDataSeeder.java
    ```

    Each removal: separate commit `refactor(61-gap): remove unused private <method> from <File>`
  </action>
  <read_first>
    - All listed files in admin layer
    - src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java (verify DevDataSeeder coverage path)
    - src/test/java/org/ctc/admin/DevDataSeederIntegrationTest.java IF EXISTS (find it: `find src/test -name "*DevData*"`)
  </read_first>
  <acceptance_criteria>
    - For each removed method: `grep -rn "<methodName>" src/main src/test` returns 0
    - `./mvnw test -Dtest='org.ctc.admin.**'` is BUILD SUCCESS after each removal
    - DevDataSeeder integration test (if exists) is BUILD SUCCESS
    - Each removal has a separate commit (bisect-friendly): `git log --oneline HEAD~10..HEAD | grep -c "refactor(61-gap)"`
  </acceptance_criteria>
</task>

<task id="2" name="Audit + remove dead code in dataimport + sitegen + gt7sync">
  <action>
    Same pattern as Task 1, applied to:
    - `src/main/java/org/ctc/dataimport/CsvImportService.java`
    - `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`
    - `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
    - `src/main/java/org/ctc/gt7sync/Gt7CarSyncService.java`
    - `src/main/java/org/ctc/gt7sync/Gt7TrackSyncService.java`

    Step 1 — enumerate private methods (same grep as Task 1).

    Step 2 — special audit for SiteGeneratorService: WR-01 fix removed the dead alltime-standings fallback.
    Verify the related `legacyStandings*` or `seasonsWithoutRegularPhase*` helpers are also gone or in use:
    ```bash
    grep -n "legacyStandings\|standingsWithoutRegular\|fallback" src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    ```

    Step 3 — special audit for DriverSheetImportService: many private helpers from Phase 59 (D-02, D-05,
    D-06, D-08). Trace which are still called:
    ```bash
    grep -n "private.*(" src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    grep -n "import org.ctc.dataimport.DriverSheetImportService" src/main src/test
    ```

    Step 4 — gt7sync: small files, manual scan. Both Gt7CarSyncService and Gt7TrackSyncService follow the same
    fetch-parse-save pattern; verify all parse helpers are reached at least once during a typical sync.

    Each removal: separate commit `refactor(61-gap): remove unused <method> from <File>`

    **Defensive null-guards in this group:**
    - CsvImportService null-checks at the FILE-CONTENT-PARSING boundary → KEEP (user input)
    - DriverSheetImportService null-checks against Google Sheets API responses → KEEP (external API boundary)
    - SiteGeneratorService null-checks in hand-rolled loops → audit case-by-case
    - gt7sync Jsoup `.first()` returns + null-checks → KEEP (HTML may not contain expected nodes)
  </action>
  <read_first>
    - All 5 listed files (read in full)
    - .planning/phases/61-cleanup-quality-gate/61-REVIEW-FIX.md (WR-01 fix narrative)
    - src/test/java/org/ctc/dataimport/* (coverage map for any audit decision)
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
  </read_first>
  <acceptance_criteria>
    - Each removed dead method has zero callers verified
    - `./mvnw test -Dtest='*ImportServiceTest,*ImportServiceIT,SiteGeneratorServiceTest,Gt7*Test'` is BUILD SUCCESS
    - For SiteGeneratorService: `grep -c "legacyStandings\|fallback" src/main/java/org/ctc/sitegen/SiteGeneratorService.java` returns 0 (or the residual is documented in SUMMARY as legitimate)
    - Each removal in a separate commit
  </acceptance_criteria>
</task>

<task id="3" name="Final verification + JaCoCo re-check">
  <action>
    Run a comprehensive verification pass:

    1. Full Surefire + JaCoCo:
       ```bash
       ./mvnw verify -DskipITs
       ```
       Expected: BUILD SUCCESS, coverage >= 82%.

    2. Run ALSO the integration tests (Failsafe non-e2e) to catch DevDataSeeder regressions:
       ```bash
       ./mvnw verify -DskipTests=false -Dgroups='!e2e'
       ```
       (or whatever the project's IT-only invocation is — check pom.xml `<failsafe>` config)

    3. Coverage check:
       ```bash
       awk -F, 'NR>1 { line+=$8+$9; missed+=$8 } END { print "Line coverage: " (1 - missed/line) * 100 "%" }' target/site/jacoco/jacoco.csv
       ```
       Must be >= 82%.

    4. If coverage dropped below 82%: revert the most recent removal commits one-by-one until >= 82%; document
       the reverted candidates in `deferred-items.md` as NEEDS_CONTEXT (the code IS dead in production but
       is exercised by a test, indicating the test itself may be obsolete OR the code path is rarely hit).
  </action>
  <read_first>
    - Outputs of Tasks 1 + 2
    - target/site/jacoco/jacoco.csv (after Task 1 + 2 verify run)
  </read_first>
  <acceptance_criteria>
    - `./mvnw verify -DskipITs` is BUILD SUCCESS
    - Line coverage >= 82% (record exact percentage in SUMMARY; flag any drop > 0.5pp from baseline)
    - DevDataSeederIntegrationTest (if exists) BUILD SUCCESS
    - All removals are atomic commits
  </acceptance_criteria>
</task>

</tasks>

<verification>
- ./mvnw verify -DskipITs BUILD SUCCESS
- Line coverage >= 82%
- No DevDataSeeder regression
- Each removal is a separate commit
</verification>

<success_criteria>
1. Dead code in admin/dataimport/sitegen/gt7sync removed
2. Defensive null-guards classified KEEP (boundary) or REMOVE (internal)
3. DevDataSeeder bootstrap path unaffected
4. Coverage >= 82%
</success_criteria>

<output>
Create `.planning/phases/61-cleanup-quality-gate/61-gap-07-SUMMARY.md` (same structure as 61-gap-06-SUMMARY.md).
</output>
