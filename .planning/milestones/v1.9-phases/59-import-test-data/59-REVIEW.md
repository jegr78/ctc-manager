---
phase: 59-import-test-data
reviewed: 2026-04-29T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - src/main/java/org/ctc/domain/service/SeasonManagementService.java
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: issues_found
---

# Phase 59-05: Code Review Report

**Reviewed:** 2026-04-29
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

The hotfix correctly removes `@Transactional(readOnly = true)` from both `findUnique` overloads in `SeasonManagementService`, eliminating the `setRollbackOnly` poisoning that was producing `UnexpectedRollbackException` at the `preview()` commit boundary. The verbatim D-02 / D-04 error messages are preserved byte-for-byte, all other `@Transactional` annotations on the service are untouched, and the new `DriverSheetImportServiceTransactionIT` is correctly shaped (no class-level `@Transactional`, manual `@AfterEach` cleanup, and a properly chosen `FRESH_YEAR=2099` outside the dev seed range).

The fix is sound, but the new IT carries several real defects that weaken its value as a CI guard and risk producing flaky/orphan state in the shared H2 datasource:

- **WR-01 (state leakage):** The IT swallows ALL exceptions in `@AfterEach` cleanup (`catch (Exception ignore)`). Because the H2 datasource is configured with `DB_CLOSE_DELAY=-1` (in-memory but persisted across the entire JVM), any failed delete leaves orphan `(year=2099, number=1|2)` rows visible to every subsequent `@SpringBootTest`, which then fail with multi-hit ambiguities. This is exactly the "two-extra-seasons" condition the IT itself relies on.
- **WR-02 (cleanup hides regressions):** Because the test creates seasons WITHOUT a REGULAR phase, `seasonRepository.deleteById(...)` succeeds today, but if ever wired to go through `SeasonManagementService.delete(...)` (which has the D-18 strict guard), or if the seed evolves to attach a REGULAR phase by side-effect, the silent `ignore` will mask the cleanup failure rather than fail loud.
- **WR-03 (test profile choice):** `@ActiveProfiles("dev")` triggers `DevDataSeeder` (a `CommandLineRunner`) on each context start, seeding data through the FULL `TestDataService` graph (12 teams, 4 seasons, races, etc.). This is heavyweight and couples this regression IT to seed evolution. The sibling `DriverSheetImportServiceIT` does the same — but it benefits from class-level `@Transactional` rolling everything back. The new IT does not, and any seeder bug or seed-data drift surfaces here as test failure.
- **WR-04 (RED is configuration-dependent, not behavior-pinned):** The "RED" guarantee — i.e. that this test would have caught the bug pre-fix — depends entirely on the absence of the class-level `@Transactional`. There is no JUnit-level invariant or comment-test that fails if a future contributor adds `@Transactional` to the class (e.g. while "fixing flakiness"). The whole CI-protection value evaporates the moment that annotation is added back.

The production code change itself has no defects and exhibits correct error-message preservation. Caller paths (`DriverSheetImportService.preview()` → `findUnique`) still benefit from the outer `@Transactional(readOnly = true)` for repository session, and the existing JaCoCo gate (>= 82%) holds.

## Warnings

### WR-01: AfterEach cleanup silently swallows ALL exceptions, leaking state across tests

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java:67-75`
**Issue:** The `@AfterEach` swallows every `Exception` from `seasonRepository.deleteById(id)` with a bare comment `// best-effort cleanup`. Because this IT runs WITHOUT class-level `@Transactional` against the shared H2 in-memory datasource (`DB_CLOSE_DELAY=-1` in `application-dev.yml` keeps the in-memory DB alive for the entire JVM), any swallowed delete failure leaves persistent `(year=2099, number=1)` and `(year=2099, number=2)` rows behind. Subsequent test contexts that start in the same JVM (Surefire reuses the JVM by default) will see those rows. Worse, when this IT runs again in the same JVM, the `@BeforeEach` UNCONDITIONALLY adds two MORE rows, escalating the multi-hit count from 2 → 4 → 6 across iterations, until eventually `seasonRepository.findAll().stream().findFirst()` may pick a stale `Phase59-TxIT-Extra-A` season with stale FKs as the `template`, breaking other assertions silently.

The class-level Javadoc explicitly states "a failed test must not block subsequent tests" — but the actual implementation also fails to block silent CORRUPTION of subsequent tests, which is worse than blocking them.

**Fix:** Either (a) re-throw on cleanup failure to fail loud, or (b) at minimum log the failure with diagnostic information AND add an idempotent pre-state probe in `@BeforeEach` that asserts no leftover `Phase59-TxIT-Extra-*` rows exist before seeding. Option (b) preserves the "best-effort" intent while preventing silent corruption:
```java
@BeforeEach
void seedTwoSeasonsForFreshYear() {
    // Pitfall guard: H2 in-memory persists across test contexts (DB_CLOSE_DELAY=-1).
    // A previous failed cleanup must not leave 2099 rows behind.
    var leftover = seasonRepository.findByYear(FRESH_YEAR);
    if (!leftover.isEmpty()) {
        leftover.forEach(s -> seasonRepository.deleteById(s.getId()));
    }
    // ... existing seed code ...
}

@AfterEach
void cleanupExtraSeasons() {
    for (UUID id : createdSeasonIds) {
        try {
            seasonRepository.deleteById(id);
        } catch (Exception ex) {
            log.warn("Phase59-TxIT cleanup failed for season {}: {}", id, ex.getMessage());
        }
    }
    createdSeasonIds.clear();
}
```

### WR-02: BusinessRuleException is no longer in the catch chain after the fix — cleanup silent-swallow may mask real ORM errors

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java:69-72`
**Issue:** Now that the bug is fixed, the cleanup path runs in a perfectly healthy state. But if a future change reintroduces the rollback-only-poisoning regression (the very thing this IT is supposed to guard against), the `@AfterEach` will hit `seasonRepository.deleteById(...)` while the spring context still holds a poisoned transaction context (depending on test-method failure mode), and the resulting `UnexpectedRollbackException`, `TransactionSystemException`, or `JpaSystemException` will be silently swallowed. The test report will only show the primary assertion failure — it will hide secondary cleanup damage and any orphan rows it produces. This makes triage harder and trades a clear failure mode for an opaque one.

**Fix:** Catch only the narrow exception types that are genuinely "best-effort" (e.g. `EmptyResultDataAccessException` if the delete is a no-op), and let the rest propagate so JUnit reports them as test errors. Alternatively, log AT LEAST the exception class and message:
```java
} catch (Exception ex) {
    // Promote diagnostic visibility — silent ignore once masked the original bug too.
    System.err.println("Phase59-TxIT cleanup failed for " + id + ": "
            + ex.getClass().getSimpleName() + ": " + ex.getMessage());
}
```
Combined with WR-01's pre-state probe, this gives diagnostic value while preserving the "do not block subsequent tests" intent.

### WR-03: Heavyweight `@ActiveProfiles("dev")` triggers full DevDataSeeder and couples the regression test to seed data

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java:40,57-60`
**Issue:** `@ActiveProfiles("dev")` activates `DevDataSeeder` (a `CommandLineRunner` defined in `src/main/java/org/ctc/admin/DevDataSeeder.java`), which executes the full `TestDataService.seed()` graph — 12 teams, 4 seasons, races, results, playoffs, GROUPS-layout 2023 season — every time this test's Spring context is built. The IT then hard-depends on this graph at line 58: `seasonRepository.findAll().stream().findFirst()` is used to "borrow scoring config from any existing season". This couples a tx-rollback regression test to:
1. The dev seeder being enabled
2. The seeder having created at least ONE season with a non-null RaceScoring/MatchScoring
3. The first `findAll()` result actually having the FK columns populated

If `TestDataService` ever stops seeding seasons, or seeds them in a different order, or moves the scoring config to a per-test-config bean, this IT silently flips RED for an unrelated reason. The error message at line 59-60 (`"Dev seed empty — cannot borrow RaceScoring/MatchScoring"`) at least surfaces the coupling, but the underlying fragility remains.

**Fix:** Decouple the IT from `DevDataSeeder` by either:
- (a) Creating its OWN `RaceScoring` + `MatchScoring` rows in `@BeforeEach` (mirror what `TestDataService.createScoringConfigs()` does — small ~10 LoC), then deleting them in `@AfterEach`. This makes the IT self-contained.
- (b) Switching to a leaner profile or `@TestPropertySource` that disables `DevDataSeeder`. The simplest variant adds `@DynamicPropertySource` or `spring.main.web-application-type=none`-style guard, but the cleanest is `@SpringBootTest(properties = "dev-data-seeder.enabled=false")` if the seeder is gated on a property.
The current `findFirst()`-based template-borrowing pattern is acceptable as a pragmatic shortcut, but it should be documented at the call site that this IT INTENTIONALLY depends on `DevDataSeeder` so future maintainers do not silently disable that seed.

### WR-04: No JUnit-level invariant pins the absence of class-level `@Transactional`

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java:39-41`
**Issue:** The entire CI-protection value of this test depends on the absence of `@Transactional` at the class level. The Javadoc loudly explains this (lines 32-37 — "Test-shape note: this class deliberately does NOT carry a class-level `@Transactional` annotation"), but Javadoc is advisory, not enforced. A future contributor encountering "flaky cleanup" or "data leakage between tests" (which WR-01 makes likely!) will reach for the standard fix — adding `@Transactional` — and the IT will become a no-op in CI without anyone noticing. The unfixed-bug RED gate this test was built to provide will silently disappear.

**Fix:** Pin the invariant with a programmatic check that fails if the class ever gets `@Transactional`:
```java
@Test
void givenTestClass_whenCheckingClassAnnotations_thenIsNotTransactional() {
    // Self-protection: the entire purpose of this IT is to commit at the AOP boundary.
    // If a future maintainer adds @Transactional to this class, the rollback-only regression
    // test becomes a no-op. Pin the invariant.
    assertThat(DriverSheetImportServiceTransactionIT.class
            .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
            .as("This IT must NOT carry @Transactional — see class-level Javadoc")
            .isFalse();
}
```
This adds 6 lines and makes the design intent self-enforcing.

## Info

### IN-01: Inline import in `assertThatNoException` block reduces readability

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java:91`
**Issue:** `var ref = new java.util.concurrent.atomic.AtomicReference<DriverSheetImportPreview>();` uses the fully-qualified class name inline rather than a top-of-file import. The rest of the file imports its types via `import` statements, so this one inline FQN sticks out and blocks IDE refactor tools.
**Fix:** Add `import java.util.concurrent.atomic.AtomicReference;` to the imports block and shorten to `var ref = new AtomicReference<DriverSheetImportPreview>();`.

### IN-02: `@SuppressWarnings("unused")` candidate on `IOException` declaration — but actual throw path is mocked

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java:79`
**Issue:** The test method declares `throws IOException`, propagating the checked exception from `DriverSheetImportService.preview(String)`. However, the actual `preview()` call inside the test is wrapped in `assertThatNoException().isThrownBy(() -> ref.set(driverSheetImportService.preview(SHEET_URL)))`. AssertJ's lambda swallows all checked exceptions internally — the `throws IOException` declaration on the method is redundant. It is harmless, just dead syntax.
**Fix:** Remove `throws IOException` from the test method signature. The `assertThatNoException` lambda already handles checked-exception declarations.

### IN-03: `lenient()` stub may indicate a stub that is never actually called

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java:86-87`
**Issue:** The `readRangeFromSheet` mock is wrapped in `lenient()` to suppress Mockito's strict-stubbing warning. The comment says "header-only sheet — bug fires on findUnique regardless of data rows", which suggests the author anticipates the stub MAY not be invoked. This is reasonable defensively — but it also masks the case where a future refactor of `buildTabPreview` skips the row-read entirely. If `buildTabPreview` is rewired to short-circuit when `suggestedSeasonId == null` (currently it does NOT — it still calls `googleSheetsService.readRangeFromSheet` at line 257), this lenient stub silently allows the new behavior without flagging.
**Fix:** Two options. Either tighten to a regular `when(...)` (will fail loudly if behavior changes), or document at the stub site WHY leniency is needed: "lenient() because production code may short-circuit when suggestedSeasonId is null (D-05 wires findRegularPhase only on resolved season)". The documented form preserves intent and gives future maintainers context.

---

_Reviewed: 2026-04-29_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
