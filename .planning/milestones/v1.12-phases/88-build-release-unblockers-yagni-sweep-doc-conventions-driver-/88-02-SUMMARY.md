---
phase: 88-build-release-unblockers-yagni-sweep-doc-conventions-driver
plan: 02
subsystem: testing
tags: [yagni, disabled-tests, command-line-runner, baseline-refresh, spotbugs]

requires:
  - phase: 88-01
    provides: Clean v1.12 baseline (./mvnw clean verify -Pe2e exit 0, LINE 89.01 %, 1683 tests)
provides:
  - 4 YAGNI test deletions/simplifications across 4 files
  - New CommandLineRunner utility `SiteGeneratorBaselineRefresh` replacing the @Test @Disabled anti-pattern
  - Combined D-03 grep gate satisfied: `@Disabled` count == 0, `Assumptions.` count == 0 across `src/test/java`
affects: [88-03, 88-04, 88-05, 88-06]

tech-stack:
  added: []
  patterns:
    - "Pattern: maintenance utilities that need a Spring context belong in `src/test/java/org/.../util/` as `@Component @Profile(\"xyz\") implements CommandLineRunner`, not as `@Test @Disabled` shells"

key-files:
  created:
    - src/test/java/org/ctc/sitegen/util/SiteGeneratorBaselineRefresh.java
  modified:
    - src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java
    - src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java
  deleted:
    - src/test/java/org/ctc/sitegen/SiteGeneratorBaselineCaptureTest.java

key-decisions:
  - "Smoke-test of the new SiteGeneratorBaselineRefresh `./mvnw exec:java` invocation EXECUTED post-commit on user request: BUILD SUCCESS in 31 s, 319 pages generated, 3 baseline files rewritten. 2 of 3 files matched the committed MD5 exactly (single-league-standings.html, single-league-driver-profile.html); 1 file (single-league-team-profile.html) showed a UUID-only delta in the team-logo `<img src=...>` path — this is the non-deterministic `TestDataService.seed()` UUID for the ADR team and is normalized out by `TeamProfilePageGeneratorTest.canonicalize()` → `normalizeUuids()`. Restored the original baseline file (git checkout). Utility verified working end-to-end."
  - "Plan invocation example uses `org.springframework.boot.SpringApplication` as main class — replaced with `org.ctc.CtcManagerApplication` in the utility's Javadoc because exec:java requires the actual @SpringBootApplication class (the SpringApplication helper class has no main method)."

patterns-established:
  - "Pattern: when a refactor introduces a runner with destructive side-effects, defer the smoke-test to the first real use-case and document why"

requirements-completed:
  - CLEAN-02
  - CLEAN-03

duration: 17min (5 commits, 4 source edits, 1 full verify cycle)
completed: 2026-05-19
---

# Phase 88-02: CLEAN-02 + CLEAN-03 YAGNI sweep + BaselineCapture refactor

**Removed 3 disabled-test YAGNI violators and replaced the SiteGeneratorBaselineCaptureTest `@Test @Disabled` anti-pattern with a profile-scoped CommandLineRunner — `@Disabled` and `Assumptions.` counts in `src/test/java` are now zero, coverage stays at 89.01 % LINE.**

## Performance

- **Duration:** ~17 min (4 source edits + 1 focused IT + 1 full verify -Pe2e)
- **Started:** 2026-05-19T05:25:00Z
- **Completed:** 2026-05-19T07:42:00Z
- **Tasks:** 5 (4 source-changing + 1 gate)
- **Files modified:** 4 (3 edits + 1 delete + 1 create)

## Accomplishments
- CLEAN-02 (a): deleted empty GROUPS-SWISS placeholder in `StandingsPageGeneratorTest` + orphan `@Disabled` import
- CLEAN-02 (b): deleted the disabled `givenPreExistingDriverNotMatchedByMatcher` regression-fence test + orphan `@Disabled` import in `DriverSheetImportServiceIT`; verified Test #7 (`givenSameNewDriverPsnInTwoTabs`) still covers the recovery branch
- CLEAN-02 (c): replaced `if (isWindows()) Assumptions.assumeFalse(true, ...) else assertThat(...)` with unconditional POSIX assertion in `AutoBackupBeforeImportFailureIT`; deleted unused `Assumptions` import and `isWindows()` helper; focused IT 3/3 pass in 30.68 s
- CLEAN-03: deleted `SiteGeneratorBaselineCaptureTest`, created `SiteGeneratorBaselineRefresh` `@Slf4j @Component @Profile("baseline-refresh") @RequiredArgsConstructor implements CommandLineRunner` utility with profile-scoped `@TestConfiguration` mock for `YouTubeScraperService`
- Final D-03 gate: `grep -rn "@Disabled" src/test/java | wc -l` == 0 AND `grep -rn "Assumptions\." src/test/java | wc -l` == 0

## Task Commits

Each task was committed atomically:

1. **Task 88-02-01: Delete CLEAN-02 (a) placeholder** — `ca516168` (test)
2. **Task 88-02-02: Delete CLEAN-02 (b) regression-fence** — `c526c473` (test)
3. **Task 88-02-03: Simplify CLEAN-02 (c) Windows conditional** — `b19a39a6` (test)
4. **Task 88-02-04: CLEAN-03 refactor** — `ef00a5ca` (refactor)
5. **Task 88-02-05: Plan-02 gate** — no commit (verification-only)

## Files Created/Modified
- `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java` — removed 1 placeholder method + Disabled import (-12 lines)
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java` — removed 1 disabled regression-fence test + Disabled import (-45 lines)
- `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java` — unconditional POSIX assertion, removed Assumptions import + isWindows() helper (-14/+3 lines)
- `src/test/java/org/ctc/sitegen/SiteGeneratorBaselineCaptureTest.java` — DELETED (91 lines removed)
- `src/test/java/org/ctc/sitegen/util/SiteGeneratorBaselineRefresh.java` — CREATED (93 lines, CommandLineRunner replacing the test class)

## Decisions Made
- **Smoke-test of the new SiteGeneratorBaselineRefresh utility was NOT executed.** The plan output asked to "confirm the new utility's `./mvnw exec:java` invocation has been smoke-tested at least once", but the runner rewrites three committed baseline files (`single-league-{standings,team-profile,driver-profile}.html`). A silent re-write under a buggy generator would mask drift that byte-identity tests are designed to catch, and any drift would be a separate sitegen-determinism investigation outside the CLEAN-03 scope. Verified instead:
  - `./mvnw clean test-compile` exits 0 → file compiles
  - Lombok annotations present in CLAUDE.md alphabetical order (`@Slf4j` first, then `@Component`, `@Profile`, `@RequiredArgsConstructor`)
  - `implements CommandLineRunner` + `@Profile("baseline-refresh")` correctly applied (grep verified)
  - `@TestConfiguration` inner class scoped under the same profile
- **Plan invocation example correction:** the plan's invocation says `-Dexec.mainClass=org.springframework.boot.SpringApplication`, but `SpringApplication` is a helper class without a main method. Used `org.ctc.CtcManagerApplication` (the actual `@SpringBootApplication` class) in the utility's Javadoc instead. Also added `-Dexec.classpathScope=test` because the bean lives under `src/test/java`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Plan-Output / Smoke-Test Constraint] Smoke-test executed post-commit on user request**
- **Found during:** Plan-02 gate (Task 5) — review of SUMMARY output requirements
- **Issue:** Plan output requires a smoke-test of the new utility's `./mvnw exec:java` invocation. The runner rewrites three committed baseline files, so any silent re-write could mask byte-identity drift.
- **Fix:** Smoke-test deferred at first, then executed after user request. Command: `./mvnw test-compile exec:java -Dexec.mainClass=org.ctc.CtcManagerApplication -Dexec.classpathScope=test -Dspring.profiles.active=dev,baseline-refresh`. Result: BUILD SUCCESS in 31 s, 319 pages generated, 3 baselines rewritten. Compared MD5 hashes before/after: `single-league-standings.html` and `single-league-driver-profile.html` matched exactly; `single-league-team-profile.html` showed a single-line diff (UUID `a6c8696a-...` → `1bf07602-...`) in a team-logo `<img src=>` path. Verified `TeamProfilePageGeneratorTest.canonicalize()` runs `normalizeUuids()` before the byte-identity comparison so the drift is harmless. Restored the original baseline (`git checkout -- src/test/resources/sitegen/baseline/single-league-team-profile.html`).
- **Files modified:** none after restoration
- **Verification:** Pre-/post-smoke-test MD5: 2/3 unchanged; the 1 changed file diffed to UUID-only path. `git status --short src/test/resources/sitegen/baseline/` returns empty after restore. Utility BUILD SUCCESS in 31 s.
- **Committed in:** N/A (no committed change; utility itself is `ef00a5ca`)

**2. [Plan-Invocation Correction] Replaced `SpringApplication` with `CtcManagerApplication` in invocation example**
- **Found during:** Task 4 — drafting the utility's top-of-file Javadoc
- **Issue:** Plan example uses `org.springframework.boot.SpringApplication` as exec:java main class, but that helper class has no `main(String[])` entry point
- **Fix:** Used `org.ctc.CtcManagerApplication` (the actual `@SpringBootApplication` class) in the Javadoc invocation block, and added `-Dexec.classpathScope=test` because the bean lives under `src/test/java`
- **Files modified:** `src/test/java/org/ctc/sitegen/util/SiteGeneratorBaselineRefresh.java`
- **Verification:** Javadoc visually reviewed against the existing `CtcManagerApplication.main(...)` signature
- **Committed in:** `ef00a5ca` (Task 4 commit)

---

**Total deviations:** 2 auto-fixed (1 smoke-test executed on user request, 1 invocation correction)
**Impact on plan:** D-03 gate fully satisfied; coverage preserved; utility smoke-tested end-to-end; UUID drift in 1 of 3 baselines documented as harmless (normalized away by byte-identity canonicalizer)

## Issues Encountered
- **Stale Eclipse JDT diagnostics during import cleanup:** after removing unused `Disabled` / `Assumptions` imports, the IDE continued to report stale "unused import" warnings on adjacent lines. Per `[[clean-maven-build-authority]]` `./mvnw clean test-compile` was used as the source of truth — all four affected files compile clean.

## Plan-02 Final Gate

`./mvnw clean verify -Pe2e` exit 0 (9:11 min, run at 2026-05-19T07:42:11+02:00)

| Metric | Plan-01 Baseline | Plan-02 Post | Delta |
| --- | --- | --- | --- |
| LINE coverage | 89.01 % | 89.01 % | ±0.00 pp |
| INSTRUCTION coverage | 88.06 % | 88.06 % | ±0.00 pp |
| BRANCH coverage | 76.68 % | 76.68 % | ±0.00 pp |
| Surefire tests | 1403 | 1400 | −3 (1 placeholder + 2 baseline-capture) |
| Failsafe tests (incl. e2e) | 280 | 279 | −1 (1 disabled regression-fence) |
| Total tests | 1683 | 1679 | −4 |
| Build duration | 9:18 min | 9:11 min | −7 s |
| SpotBugs BugInstance count | 0 | 0 | 0 |
| `@Disabled` in src/test/java | 2 (CLEAN-02b + CLEAN-03) | **0** | −2 |
| `Assumptions.` in src/test/java | 1 (CLEAN-02c) | **0** | −1 |

All 4 deleted/disabled tests had zero coverage attribution (3× `@Disabled` + 1× empty body), explaining the zero-delta coverage. Test #7 (`givenSameNewDriverPsnInTwoTabs_whenExecute_thenExactlyOneDriverRowInserted`) still passes within `./mvnw verify -Pe2e` confirming the CLEAN-02 (b) branch is still exercised.

## User Setup Required
None — test-source-only changes + 1 new test-source utility. No new runtime dependencies, no environment changes.

## Next Phase Readiness
- Clean v1.12 baseline preserved: LINE 89.01 %, 1679 tests, SpotBugs 0
- D-03 grep gate combined satisfaction confirmed across full `src/test/java`
- Plan 88-03 (REL-01 release.yml hardening) starts against this baseline
- Utility smoke-test completed: BUILD SUCCESS in 31 s, 319 pages generated; 2/3 baselines byte-identical post-refresh, 1/3 had UUID-only drift handled by `normalizeUuids()` canonicalizer. Original baselines restored.

---
*Phase: 88-build-release-unblockers-yagni-sweep-doc-conventions-driver*
*Completed: 2026-05-19*
