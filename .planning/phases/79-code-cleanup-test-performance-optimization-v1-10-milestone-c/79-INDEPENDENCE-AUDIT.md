# Phase 79 — Test Independence Audit (D-05 Wave 1)

## Surefire reverse-order run

**Command:** `./mvnw test -Dsurefire.runOrder=reversealphabetical -Dspring.profiles.active=dev --no-transfer-progress`

**Note:** The first attempt (run immediately after the 13m 27s wallclock baseline run) produced 90 Errors from sitegen tests. Root cause: `TeamProfilePageGeneratorTest` runs FIRST in reverse-alphabetical order and its `@BeforeAll` calls `TestDataService.seed()` which triggers `TeamCardService.generateCard()` (Playwright screenshot). Chromium OS resources were exhausted right after the full E2E baseline run. This is a transient OS resource issue, NOT a test-ordering isolation bug. The second run (after a few minutes' cooldown) was clean.

| Column | Value |
|--------|-------|
| Run | 2nd attempt (1st was transient Playwright OS crash after full E2E run) |
| Command | `./mvnw test -Dsurefire.runOrder=reversealphabetical -Dspring.profiles.active=dev --no-transfer-progress` |
| Duration | 5m 36s real |
| Build result | **BUILD SUCCESS** |
| Tests run | 1410 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 5 |

**Result: GREEN**

---

## Surefire random-seed runs

### Seed 1234

#### Initial run (pre-fix) — BUILD FAILURE

| Column | Value |
|--------|-------|
| Command | `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234 -Dspring.profiles.active=dev --no-transfer-progress` |
| Duration | 6m 43s real |
| Build result | **BUILD FAILURE** |
| Tests run | 1410 |
| Failures | 2 |
| Errors | 0 |
| Skipped | 5 |

**Result: RED — ORDERING DEPENDENCY FOUND**

Failing tests: `TestDataServiceIntegrationTest.givenDevSeed_whenStarted_thenSwissSeasonHasFiveMatchdays` and `TestDataServiceIntegrationTest.givenDevSeed_whenStarted_thenS2HasFormatSwiss`.

Error: `java.lang.AssertionError: Season not found: year=2024 name=Regular Season`

Root cause: With seed=1234, a sitegen test class (one of the 7 classes that calls `Flyway.clean()` + `Flyway.migrate()` + `testDataService.seed()` in `@BeforeAll`) runs BEFORE `TestDataServiceIntegrationTest`. That sitegen test's `@DirtiesContext` closes the Spring context after completion. The next Spring context for `TestDataServiceIntegrationTest` boots `DevDataSeeder`, which calls `testDataService.seed()` — but `seasonRepository.count() > 0` is true from the prior sitegen seed (H2 `DB_CLOSE_DELAY=-1` keeps the schema alive across Spring context reloads), so `DevDataSeeder` short-circuits without re-creating the canonical seed fixtures `TestDataServiceIntegrationTest` queries. This failure was **reproducible** (confirmed on two consecutive seed=1234 runs).

#### Inline fix applied (Wave 1 inline-fix)

`TestDataServiceIntegrationTest` was made independent of preceding context state by adopting the canonical project pattern used by sitegen tests:

- Added `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` to enable non-static `@BeforeAll`.
- Added `@DirtiesContext` to evict the Spring context after the class.
- Added `@BeforeAll setUp()` that runs `Flyway.configure().cleanDisabled(false)…clean()` + `Flyway.configure()…migrate()` + `testDataService.seed()` — guaranteeing fresh DB state regardless of preceding test classes.
- Kept `@Transactional` on the class to preserve lazy-loading support (`Season.getPhases()`, `Season.getSeasonTeams()`, `Race.getMatchday()` etc.) inside test methods. `@BeforeAll` runs outside any test transaction, so its commits are visible to all subsequent `@Test` methods.

#### Re-run (post-fix) — BUILD SUCCESS

| Column | Value |
|--------|-------|
| Command | `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234 -Dspring.profiles.active=dev --no-transfer-progress` |
| Duration | 6m 34s real |
| Build result | **BUILD SUCCESS** |
| Tests run | 1410 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 5 |

**Result: GREEN — Ordering dependency RESOLVED. Plan 03 (parallelization) UNBLOCKED.**

### Seed 5678

| Column | Value |
|--------|-------|
| Command | `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678 -Dspring.profiles.active=dev --no-transfer-progress` |
| Duration | 6m 15s real |
| Build result | **BUILD SUCCESS** |
| Tests run | 1410 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 5 |

**Result: GREEN**

### Seed 9999

| Column | Value |
|--------|-------|
| Command | `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999 -Dspring.profiles.active=dev --no-transfer-progress` |
| Duration | 6m 11s real |
| Build result | **BUILD SUCCESS** |
| Tests run | 1410 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 5 |

**Result: GREEN**

---

## Failsafe reverse-order run

**Command:** `./mvnw verify -Dsurefire.runOrder=reversealphabetical -Dfailsafe.runOrder=reversealphabetical -Dspring.profiles.active=dev -Ddocker.available=true --no-transfer-progress`

| Column | Value |
|--------|-------|
| Command | (above) |
| Duration | 12m 34s real |
| Build result | **BUILD SUCCESS** |
| Tests run (Surefire) | 1410 |
| Surefire failures | 0 |
| IT failures (Failsafe) | 0 |
| Skipped | 5 |
| JaCoCo | All coverage checks met (≥ 82%) |

**Result: GREEN**

---

## Verdict

**Independence audit GREEN after Wave 1 inline-fix.**

- Surefire reverse-order: GREEN
- Surefire seed=1234 pre-fix: **RED** — reproducible ordering dependency in `TestDataServiceIntegrationTest` (2 failures)
- Surefire seed=1234 post-fix: **GREEN** — Wave 1 inline-fix on `TestDataServiceIntegrationTest` (Flyway clean+migrate+seed in `@BeforeAll`, matches sitegen pattern) resolves the ordering dependency
- Surefire seed=5678: GREEN
- Surefire seed=9999: GREEN
- Failsafe reverse-order: GREEN

**Plan 03 (parallelization) is UNBLOCKED.** The seed=1234 ordering dependency was a real test-isolation bug in `TestDataServiceIntegrationTest` — it depended on shared H2 `DB_CLOSE_DELAY=-1` state surviving across Spring context reloads, which made `DevDataSeeder.run()` short-circuit (`count > 0` from a preceding sitegen test's seed) without re-creating the canonical seed fixtures. The fix makes the class self-sufficient (its own clean+migrate+seed in `@BeforeAll`).

---

## @DirtiesContext Audit (CD-04)

**Reality-check (pre-flight grep, per plan `<interfaces>` command):**

```bash
grep -rn "@DirtiesContext" src/test/ > /tmp/79-dirtiescontext-raw.txt
grep -v '^\s*//\|^\s*\*' /tmp/79-dirtiescontext-raw.txt > /tmp/79-dirtiescontext-effective.txt
TOTAL=$(wc -l < /tmp/79-dirtiescontext-effective.txt)
```

**TOTAL effective `@DirtiesContext` annotations in `src/test/` = 13** (plan's pre-flight grep; includes 3 false-positive comment lines — see discrepancy resolution below)

- RESEARCH expectation: 10
- CONTEXT.md §`Code Context` prior: 13
- Measured: **13** (plan's pre-flight grep) → **10** (corrected code-only grep)
- Discrepancy resolution: **The plan's pre-flight grep command has a bug** — after `grep -rn`, each output line has the format `filepath:linenum:content`. The `-v '^\s*//\|^\s*\*'` filter tries to exclude comment lines, but the pattern anchors to line-start (`^`) which in the `grep -rn` output is the file path (not `//` or `*`). The filter therefore fails to exclude 3 comment-containing lines: `ImportConcurrentLockIT.java:81` (a `//` comment), `TeamProfilePageGeneratorTest.java:107` (a `*` Javadoc line), and `BackupStagingCleanupIT.java:46` (a `* {@code @DirtiesContext}` Javadoc line). Corrected grep using `grep -v ':[[:space:]]*//' | grep -v ':[[:space:]]*\*'` (filtering on the content after the file:line: prefix) yields exactly 10 code-level annotations. **RESEARCH expectation of 10 is correct. CONTEXT.md's "13" cited the uncorrected grep count.**

Corrected effective annotations (code-only, 10 total):

```
src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java:43:@DirtiesContext
src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java:52:@DirtiesContext
src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java:50:@DirtiesContext
src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java:32:@DirtiesContext
src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java:43:@DirtiesContext
src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java:65:@DirtiesContext
src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java:43:@DirtiesContext
src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java:69:@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)
src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java:68:@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)
src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java:86:@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)
```

| # | Test class | Annotation form | Rationale | Verdict | Source-of-truth grep line |
|---|-----------|----------------|-----------|---------|--------------------------|
| 1 | `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` | `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)` | `BlockingRestoreFailureInjector.Config` exposes a non-resettable `CountDownLatch`; new context per method is the only reset path | KEEP — non-resettable CountDownLatch | line 86 |
| 2 | `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` | `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)` | Same CountDownLatch rationale; comment in source explains it | KEEP — non-resettable CountDownLatch | line 69 |
| 3 | `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` | `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)` | Same CountDownLatch rationale | KEEP — non-resettable CountDownLatch | line 68 |
| 4 | `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` | `@DirtiesContext` | `siteProperties.setOutputDir(tempDir.toString())` in `@BeforeAll` mutates `SiteProperties` `@ConfigurationProperties` singleton | KEEP — SiteProperties.outputDir singleton mutation | line 43 |
| 5 | `src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java` | `@DirtiesContext` | Same `SiteProperties.outputDir` mutation | KEEP — SiteProperties.outputDir singleton mutation | line 65 |
| 6 | `src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java` | `@DirtiesContext` | Same `SiteProperties.outputDir` mutation | KEEP — SiteProperties.outputDir singleton mutation | line 52 |
| 7 | `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java` | `@DirtiesContext` | `siteGeneratorService.setOutputDir(...)` + `siteProperties.setLinks(...)` mutate multiple singletons | KEEP — SiteProperties.outputDir singleton mutation | line 32 |
| 8 | `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java` | `@DirtiesContext` | Same `SiteProperties.outputDir` mutation | KEEP — SiteProperties.outputDir singleton mutation | line 50 |
| 9 | `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java` | `@DirtiesContext` | Same `SiteProperties.outputDir` mutation | KEEP — SiteProperties.outputDir singleton mutation | line 43 |
| 10 | `src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java` | `@DirtiesContext` | Same `SiteProperties.outputDir` mutation | KEEP — SiteProperties.outputDir singleton mutation | line 43 |

Per CD-04 discretion area: **10 KEEP-mandatory annotations, 0 REMOVE-CANDIDATE, 0 ALREADY REMOVED** (n/a). The remaining `@DirtiesContext` annotations are unchanged by this plan. This audit document verifies that no defensive-cargo annotations are inflating the suite — all 10 are technically mandatory.

**Note on Seed=1234 connection:** The `@DirtiesContext` annotations on the sitegen tests are themselves KEEP-mandatory, but their interaction with `TestDataServiceIntegrationTest` under certain run orderings (seed=1234) reveals a DB-state isolation gap. The sitegen tests' `Flyway.clean()` in `@BeforeAll` + `@DirtiesContext` combination does not fully protect `TestDataServiceIntegrationTest` from stale-database state when execution order places a sitegen test immediately before `TestDataServiceIntegrationTest`. This is a separate isolation issue from the `@DirtiesContext` verdict (which remains KEEP). The fix belongs in a Wave 1.5 cleanup plan before Wave 3 parallelization.
