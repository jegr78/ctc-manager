---
status: diagnosed
phase: 59
issue_summary: "UnexpectedRollbackException on POST /admin/drivers/import/preview when sheet tab '2025' matches multiple seasons in the dev-seeded DB (year=2025 exists as 'Test-Season 2025' with number=98, and the user's real 2025 season also exists, triggering BusinessRuleException inside a shared transaction that marks the outer tx rollback-only)"
created: 2026-04-29
updated: 2026-04-29
related_uat: .planning/phases/59-import-test-data/59-UAT.md
---

## Current Focus

hypothesis: "CONFIRMED — SeasonManagementService.findUnique(int year) throws BusinessRuleException when multiple seasons share the same year. Spring's REQUIRED propagation causes the inner @Transactional(readOnly=true) to JOIN the outer @Transactional(readOnly=true) on preview(). The caught exception still marks the shared transaction as rollback-only. When preview() tries to commit at the Spring AOP boundary, it finds the tx poisoned and throws UnexpectedRollbackException."
evidence_count: 7
eliminated_count: 1
next_action: "Implement chosen fix option (C or B) via /gsd-plan-phase --gaps"

## Symptoms

expected: "POST /admin/drivers/import/preview processes all tabs, including a '2025' tab that has multiple matching seasons, surfaces the ambiguity in the UI as ambiguousReason, and returns HTTP 200 with the preview page."
actual: "POST /admin/drivers/import/preview returns HTTP 500. GlobalExceptionHandler logs 'Unhandled exception: Transaction silently rolled back because it has been marked as rollback-only'."
errors: "org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only"
reproduction: "Start dev server (./mvnw spring-boot:run -Dspring-boot.run.profiles=dev). The dev seed creates Test-Season 2025 (year=2025, number=98). If the user's Google Sheet has a tab named '2025' AND a real 2025 season also exists in the DB (or any second season with year=2025), triggering findUnique(2025) with 2 hits causes the crash."
started: "After Phase 59 ship — introduced by TestDataService creating 'Test-Season 2025' (year=2025, number=98) combined with the user having a real 2025 season."

## Eliminated

- hypothesis: "116 ErrorRow records are causing the transaction rollback"
  evidence: "ErrorRow is pure control-flow data accumulated in a List<ErrorRow>. buildTabPreview() has no @Transactional annotation itself; it runs inside the outer preview() transaction. ErrorRow creation involves no DB writes and throws no exceptions. The debug log 'Tab 2025: errors=116' appears BEFORE the exception, proving the tab completed successfully."
  timestamp: 2026-04-29

## Evidence

- timestamp: 2026-04-29
  checked: "DriverSheetImportService.java lines 65-88 — preview() method"
  found: "@Transactional(readOnly = true) on preview(). No @Transactional on buildTabPreview() (private method runs in the same tx). BusinessRuleException is caught at lines 241-244 to populate ambiguousReason."
  implication: "The catch block for BusinessRuleException exists and correctly populates ambiguousReason — the design intention was to handle multi-season gracefully. However, Spring has already marked the shared tx rollback-only before the catch executes."

- timestamp: 2026-04-29
  checked: "SeasonManagementService.java lines 107-133 — findUnique(int year) and findUnique(int year, int number)"
  found: "Both overloads are annotated @Transactional(readOnly = true) with NO propagation override — defaults to Propagation.REQUIRED. When called from within preview()'s transaction, they JOIN the existing transaction rather than creating an isolated one."
  implication: "When findUnique(year) throws BusinessRuleException, Spring's transaction infrastructure sets the SHARED transaction's rollbackOnly flag BEFORE the exception propagates up to buildTabPreview()'s catch block. The catch block runs, but the damage is already done at the transaction level."

- timestamp: 2026-04-29
  checked: "TestDataService.java lines 901-904 — Test-Season 2025 creation"
  found: "createSeason(\"Test-Season 2025\", 2025, 98, \"Test\", scorings) creates a season with year=2025 in the dev seed. The UAT scenario uses a real 2025 season from the user's Google Sheet — meaning the dev DB has at least two seasons with year=2025."
  implication: "This is the trigger condition. The legacy tab name '2025' (matching ^\d{4}$) hits findUnique(2025), which calls seasonRepository.findByYear(2025) and finds 2+ results, throwing BusinessRuleException."

- timestamp: 2026-04-29
  checked: "Spring transaction propagation semantics (REQUIRED default)"
  found: "When an inner @Transactional(readOnly=true, propagation=REQUIRED) method throws a RuntimeException, Spring marks the transaction as rollbackOnly=true even if the exception is caught by the caller. This is documented Spring behavior: once rollbackOnly is set, it cannot be unset."
  implication: "The catch(BusinessRuleException ex) in buildTabPreview() at line 241 catches the exception at the Java level and continues execution, but the transaction is already poisoned. When preview() returns normally and Spring's AOP proxy tries to commit, it sees rollbackOnly=true and throws UnexpectedRollbackException instead of committing."

- timestamp: 2026-04-29
  checked: "DriverSheetImportController.java lines 45-63 — preview() handler"
  found: "The controller catches IOException, IllegalArgumentException, IllegalStateException. UnexpectedRollbackException is a RuntimeException but NOT any of those three types — it falls through all catch blocks and reaches GlobalExceptionHandler as an unhandled exception."
  implication: "Even if the inner logic 'handles' the BusinessRuleException, the UnexpectedRollbackException at commit time is uncaught at the controller layer, resulting in the 500 response."

- timestamp: 2026-04-29
  checked: "DriverSheetImportServiceIT.java lines 163-190 — test 3 (ambiguous season case)"
  found: "Test class is annotated @Transactional at class level (line 50). The test explicitly calls seasonRepository.save(extra2024) to create a second 2024 season inside the test's own outer transaction. Then calls driverSheetImportService.preview(). Because the test's @Transactional wraps everything, the test framework auto-rolls-back after the test — the UnexpectedRollbackException is NEVER triggered because Spring's test infrastructure flushes/rolls back differently than a real HTTP request context. The test asserts on the return value of preview(), which it receives — but the commit-time failure does not surface in this test pattern."
  implication: "This is the test gap. The IT test uses @Transactional at the test class level, which means the entire test runs in a single transaction that is rolled back at the end. The commit that would trigger UnexpectedRollbackException never happens — so the test passes even though real HTTP calls fail."

- timestamp: 2026-04-29
  checked: "Phase 58 prior art in PROJECT.md — bridge fix for similar class of problem"
  found: "Phase 58 resolution: 'Bridge uses findByType (Optional) instead of findRegularPhase to avoid transaction rollback-only poisoning; legacy fallback for pre-V4 seasons.' The fix was to use a non-throwing code path rather than catching an exception from within a shared transaction."
  implication: "Phase 58 solved the same class of problem with the same solution class recommended here as Option C — use a non-throwing query variant instead of catching an exception that poisons the shared transaction."

## Resolution

root_cause: "SeasonManagementService.findUnique(int year) is annotated @Transactional(readOnly=true) with default Propagation.REQUIRED. When called from within DriverSheetImportService.preview()'s transaction, it joins the existing transaction. When findUnique throws BusinessRuleException (because the dev seed has 'Test-Season 2025' year=2025 AND the user has a real 2025 season), Spring sets the shared transaction's rollbackOnly=true flag BEFORE the exception propagates. buildTabPreview()'s catch(BusinessRuleException ex) runs and populates ambiguousReason correctly, but the transaction is already poisoned. When preview() returns normally, Spring's @Transactional AOP proxy finds rollbackOnly=true and throws UnexpectedRollbackException, which is uncaught in the controller and surfaces as HTTP 500."

fix: "Remove @Transactional from both findUnique overloads (Option C) OR change them to @Transactional(noRollbackFor=BusinessRuleException.class, readOnly=true) (Option B). Option C is preferred per Phase 58 prior art."

verification: ""
files_changed: []

---

## Root Cause

**Exact code path with file:line references:**

1. **HTTP POST** `/admin/drivers/import/preview` arrives at `DriverSheetImportController.preview()` (line 39).

2. **Controller** calls `driverSheetImportService.preview(sheetUrl)` (line 46). Spring's AOP proxy opens a `readOnly=true` transaction (from `@Transactional(readOnly = true)` at `DriverSheetImportService.java:65`).

3. **`preview()`** iterates over year-numbered tabs and calls `buildTabPreview(spreadsheetId, "2025")` (line 85).

4. **`buildTabPreview()`** (line 218) parses `tabName="2025"`, extracts `year=2025`, `number=null`, then calls:
   ```java
   // DriverSheetImportService.java:231
   Optional<Season> resolved = seasonManagementService.findUnique(year);
   ```

5. **`SeasonManagementService.findUnique(int year)`** (line 124-133):
   ```java
   @Transactional(readOnly = true)           // ← Propagation.REQUIRED (default)
   public Optional<Season> findUnique(int year) {
       var hits = seasonRepository.findByYear(year);
       if (hits.size() > 1) {
           throw new BusinessRuleException(   // ← RuntimeException thrown here
                   "Multiple seasons exist for year " + year
                   + " — consolidate them first or rename sheet tab to disambiguate");
       }
       return hits.stream().findFirst();
   }
   ```
   `findByYear(2025)` returns 2 rows: the user's real 2025 season AND `"Test-Season 2025"` (year=2025, number=98) created by `TestDataService.java:902`.

6. **Spring transaction infrastructure** intercepts the `BusinessRuleException` (a `RuntimeException`) thrown by the inner `@Transactional(REQUIRED)` method. Because the inner method **joined** the outer transaction (REQUIRED = join existing), Spring calls `doSetRollbackOnly()` on the **shared** transaction resource, setting `rollbackOnly = true`. This happens inside Spring's `TransactionAspectSupport.completeTransactionAfterThrowing()` — **before** the exception is passed back to the Java call stack.

7. **`buildTabPreview()`** catch block (line 241-244) receives the `BusinessRuleException`:
   ```java
   // DriverSheetImportService.java:241-244
   } catch (BusinessRuleException ex) {
       // D-18: multi-hit → surface as ambiguousReason (NOT a 5xx)
       suggestedSeasonId = null;
       ambiguousReason = ex.getMessage();
   }
   ```
   Java-level exception handling works correctly. Execution continues. `buildTabPreview()` returns a `TabPreview` with `suggestedSeasonId=null, ambiguousReason="Multiple seasons exist for year 2025 — ..."`.

8. **`preview()`** finishes all tabs and returns `new DriverSheetImportPreview(tabPreviews)` (line 88) — normal return, no exception.

9. **Spring's `@Transactional` AOP proxy** for `preview()` calls `AbstractPlatformTransactionManager.commit()`. Inside `processCommit()`, Spring checks `isRollbackOnly()` on the transaction status and finds `true`. It throws:
   ```
   org.springframework.transaction.UnexpectedRollbackException:
     Transaction silently rolled back because it has been marked as rollback-only
   ```

10. **`DriverSheetImportController.preview()`** catch blocks (lines 53-63) only catch `IOException`, `IllegalArgumentException`, `IllegalStateException`. `UnexpectedRollbackException` (a `RuntimeException` subtype of `TransactionException`) matches none of these. It propagates to `GlobalExceptionHandler`, which logs it as an unhandled exception and returns HTTP 500.

---

## Why IT Tests Miss It

**The test gap is in `DriverSheetImportServiceIT.java:50`:**

```java
@SpringBootTest
@ActiveProfiles("dev")
@Transactional          // ← THIS is the masking annotation
class DriverSheetImportServiceIT {
```

When `@Transactional` is applied at the test-class level, the entire test method runs inside **one outer Spring-managed transaction** that is **automatically rolled back** after the test. The flow differs from production:

| Production HTTP request | IT test |
|------------------------|---------|
| Controller calls `preview()` | Test method calls `preview()` directly |
| `preview()` opens a new `@Transactional(readOnly=true)` transaction | `preview()` finds the test's existing outer transaction and JOINS it (REQUIRED) |
| `findUnique()` also JOINS that transaction | `findUnique()` also JOINS the outer test transaction |
| `BusinessRuleException` sets `rollbackOnly=true` on the preview() transaction | `BusinessRuleException` sets `rollbackOnly=true` on the **test** transaction |
| **Spring tries to commit `preview()`'s tx → finds rollbackOnly → THROWS** | **Spring tries to "commit" but since it's a test tx, it was never going to commit — Spring Test rolls it back, no commit attempt, no throw** |
| `UnexpectedRollbackException` surfaces as HTTP 500 | Test passes, returns the preview object normally |

Specifically, in `DriverSheetImportServiceIT` test 3 (`givenLegacyTabWithMultipleSeasons_whenPreview_thenAmbiguousReasonStartsWithMultipleSeasons`, line 164), the test:
1. Saves `extra2024` within the test transaction
2. Calls `driverSheetImportService.preview(SHEET_URL)` — the call completes and returns a `DriverSheetImportPreview`
3. Asserts on the return value — passes

The test **never reaches a real commit** because `@Transactional` on the test class causes the test framework to roll back the whole test transaction after the test method returns. The `UnexpectedRollbackException` that would occur at commit time in production never fires in the test context.

This is the same gap the Phase 58 prior art refers to when it documented transaction rollback-only poisoning as a real production issue not caught by `@Transactional`-annotated tests.

---

## Fix Options

### Option A: `REQUIRES_NEW` on both `findUnique` overloads

```java
// SeasonManagementService.java
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
public Optional<Season> findUnique(int year, int number) { ... }

@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
public Optional<Season> findUnique(int year) { ... }
```

**Mechanism:** `REQUIRES_NEW` suspends the outer transaction, opens a fresh independent transaction for `findUnique`, runs it (throwing `BusinessRuleException` if multi-hit), and then **resumes the outer transaction**. The inner transaction's rollback state does NOT propagate to the outer transaction.

**Pros:**
- Precisely targets the tx-isolation problem with minimal code change.
- The outer `preview()` transaction is completely unaffected by the inner method's exception.
- Error messages locked by Plan 59-02 SUMMARY are unchanged.

**Cons:**
- `REQUIRES_NEW` opens a second DB connection for every tab that calls `findUnique`, even in the common case where the season is unique. With many tabs this is an unnecessary overhead.
- For a `readOnly=true` method that only does a SELECT, suspending a readOnly outer transaction to open a new readOnly inner transaction is semantically odd.
- **Similar transaction patterns in Phase 58 were explicitly avoided** (Phase 58 chose non-throwing alternatives over tx isolation manipulation — this is the same class of risk).
- `REQUIRES_NEW` can cause deadlocks if the outer tx holds locks that the inner tx needs — unlikely here since both are `readOnly`, but a risk to document.

**Impact on existing tests:** Test 3 in `DriverSheetImportServiceIT` still passes. The `REQUIRES_NEW` inner transaction commits (or rolls back) independently; the test's outer rollback-transaction is unaffected. No test changes required.

---

### Option B: `noRollbackFor = BusinessRuleException.class` on both `findUnique` overloads

```java
// SeasonManagementService.java
@Transactional(readOnly = true, noRollbackFor = BusinessRuleException.class)
public Optional<Season> findUnique(int year, int number) { ... }

@Transactional(readOnly = true, noRollbackFor = BusinessRuleException.class)
public Optional<Season> findUnique(int year) { ... }
```

**Mechanism:** Tells Spring "when `BusinessRuleException` is thrown from this method, do NOT mark the current transaction rollback-only". The exception still propagates normally up the call stack. The outer transaction remains in a committable state.

**Pros:**
- No extra DB connections or transaction suspension.
- Semantically correct: `BusinessRuleException` is a business-logic signal, not a data-integrity failure — there is no reason the transaction should be poisoned by it.
- Minimal, targeted change to 2 method annotations.
- Error messages locked by Plan 59-02 SUMMARY are unchanged.
- This is the cleanest fix without changing method signatures or public contracts.

**Cons:**
- `noRollbackFor` is easy to forget when adding future `@Transactional` methods that call `findUnique`. A future caller that expects the RuntimeException to force a rollback would be surprised.
- It couples the transaction rollback policy to the exception type — if `BusinessRuleException` is ever reused for situations that SHOULD force a rollback (e.g., a write method that encounters an integrity violation), this annotation creates a footgun.
- The Phase 58 prior art solved a similar problem by eliminating the throwing path, not by annotating around it. This option solves the symptom without eliminating the root cause.

**Impact on existing tests:** All tests pass unchanged. The `noRollbackFor` applies at the method level; the `@Transactional` test-class rollback is the test framework's own mechanism and is unaffected.

---

### Option C: Remove `@Transactional` from both `findUnique` overloads (Phase 58 prior art approach)

```java
// SeasonManagementService.java — NO @Transactional annotation
public Optional<Season> findUnique(int year, int number) {
    var hits = seasonRepository.findByYearAndNumber(year, number);
    if (hits.size() > 1) {
        throw new BusinessRuleException(
                "Multiple seasons exist for (" + year + ", " + number
                + ") — consolidate them first or rename sheet tab to disambiguate");
    }
    return hits.stream().findFirst();
}

public Optional<Season> findUnique(int year) {
    var hits = seasonRepository.findByYear(year);
    if (hits.size() > 1) {
        throw new BusinessRuleException(
                "Multiple seasons exist for year " + year
                + " — consolidate them first or rename sheet tab to disambiguate");
    }
    return hits.stream().findFirst();
}
```

**Mechanism:** Without `@Transactional`, these methods run within whatever transaction is already active in the call stack (Spring's default: they participate in the caller's transaction but Spring's AOP interceptor does NOT set rollbackOnly on an exception, because there is no transaction proxy for these methods). The `BusinessRuleException` thrown inside `findUnique` propagates to `buildTabPreview()`'s catch block — and nothing in the transaction machinery intercepts it to set rollbackOnly.

Wait — important clarification: removing `@Transactional` from the method means Spring's transaction interceptor does NOT wrap these calls. The Spring `TransactionInterceptor` only sets `rollbackOnly` when it intercepts a `@Transactional`-annotated method boundary. Without the annotation, the exception simply propagates as a plain Java exception with no transaction side-effects. The outer `preview()` transaction is completely unaffected.

**Pros:**
- **Exact same solution pattern as Phase 58 prior art** — highest confidence of correctness.
- No extra DB connections, no transaction suspension, no new annotation semantics.
- `findUnique` is conceptually a "pure query with business rule validation" — not transactional infrastructure.
- Already runs within the caller's transaction by virtue of Spring's default entity-manager propagation; the `@Transactional` annotation is redundant (the repositories already use the inherited session).
- Error messages locked by Plan 59-02 SUMMARY are unchanged.
- Simplest code change: just delete two `@Transactional` annotations.

**Cons:**
- Without `@Transactional`, if `findUnique` is called outside of any transaction context (e.g., a future non-transactional caller), the repositories will open and close their own short-lived transactions per call. This is the standard Spring Data behavior and not a problem in practice — `findByYear` is a single SELECT.
- Marginally less explicit about the transactional intent of the method.
- Other callers of `findUnique` (if any) that relied on `@Transactional` guarantees would lose that implicit protection — but `findUnique` is a read-only single-SELECT wrapper; no existing caller needs explicit TX for it.

**Impact on existing tests:** All tests pass unchanged. Test 3 in `DriverSheetImportServiceIT` continues to pass (it tests the correct result, not the commit behavior). No test changes required for the fix itself.

---

## Recommended Fix

**Option C — remove `@Transactional` from both `findUnique` overloads.**

Rationale:
1. **Phase 58 prior art is identical.** The documented fix was "use non-throwing query path to avoid rollback-only poisoning." This option follows the same principle: eliminate the Spring AOP interception boundary that triggers `setRollbackOnly`.
2. **Option B (`noRollbackFor`) is semantically correct but couples exception-type to rollback policy**, which creates a footgun for future `@Transactional` writers.
3. **Option A (`REQUIRES_NEW`) is overpowered** — opening a new DB connection per tab for a read-only SELECT is unnecessary overhead, and `REQUIRES_NEW` introduces CGLIB proxy-call complexity.
4. **`findUnique` does not need its own transaction.** It runs one `SELECT` via Spring Data; the caller's transaction (or the repository's implicit transaction) is sufficient. The `@Transactional(readOnly=true)` annotations on these methods are defensive but not required.

---

## Test Strategy

### What test would have caught this in CI

A `@SpringBootTest` integration test that:
1. **Does NOT** annotate the test class or method with `@Transactional`.
2. Creates a second season with the same year as an existing one (to trigger the multi-hit path).
3. Calls `driverSheetImportService.preview(SHEET_URL)` through the **real Spring AOP proxy**.
4. Asserts that the result is a valid `DriverSheetImportPreview` with `ambiguousReason` set — **no `UnexpectedRollbackException` thrown**.
5. Optionally cleans up the extra season via `@AfterEach` (using a repository delete or `@Transactional` on the cleanup only).

### Where it should live

**New test class:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java`

Distinct from `DriverSheetImportServiceIT` (which uses `@Transactional`) to avoid the masking issue. Example structure:

```java
@SpringBootTest
@ActiveProfiles("dev")
// NOTE: NO @Transactional at class level — that would mask commit-time tx failures
class DriverSheetImportServiceTransactionIT {

    @Autowired private DriverSheetImportService driverSheetImportService;
    @Autowired private SeasonRepository seasonRepository;
    @MockitoBean private GoogleSheetsService googleSheetsService;

    private UUID extraSeasonId;

    @BeforeEach
    void createDuplicateSeason() {
        // Find an existing season to borrow scoring config
        var existing2025 = seasonRepository.findAll().stream()
                .filter(s -> s.getYear() == 2025).findFirst()
                .orElseThrow();
        var extra = new Season();
        extra.setName("Phase59-TxIT-Extra-2025");
        extra.setYear(2025);
        extra.setNumber(97);
        extra.setActive(false);
        extra.setFormat(SeasonFormat.LEAGUE);
        extra.setLegs(1);
        extra.setRaceScoring(existing2025.getRaceScoring());
        extra.setMatchScoring(existing2025.getMatchScoring());
        extraSeasonId = seasonRepository.save(extra).getId();
    }

    @AfterEach
    void cleanup() {
        if (extraSeasonId != null) {
            seasonRepository.deleteById(extraSeasonId);
        }
    }

    @Test
    void givenMultipleSeasonsForYear_whenPreview_thenAmbiguousReasonNotTxException() throws IOException {
        // given — two seasons with year=2025 now exist in the DB
        when(googleSheetsService.extractSpreadsheetId(SHEET_URL)).thenReturn(SPREADSHEET_ID);
        when(googleSheetsService.getSheetNames(SPREADSHEET_ID)).thenReturn(List.of("2025"));
        when(googleSheetsService.readRangeFromSheet(SPREADSHEET_ID, "2025", "A:C"))
                .thenReturn(List.of(List.of("PSN ID", "Name", "Team")));

        // when — must NOT throw UnexpectedRollbackException
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — ambiguousReason is set, no tx exception, no HTTP 500
        assertThat(preview.tabPreviews()).hasSize(1);
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).startsWith("Multiple seasons exist for year 2025");
    }
}
```

**Why this test catches the bug:** Without `@Transactional` on the test class, each call to `driverSheetImportService.preview()` creates its own transaction at the Spring AOP boundary and actually **commits** (or throws at commit time). The `UnexpectedRollbackException` would surface as a test failure before the fix, and pass after the fix.

**Naming convention compliance (CLAUDE.md):**
```
givenMultipleSeasonsForYear_whenPreview_thenAmbiguousReasonNotTxException()
// given / when / then structure in method body
```

**Coverage note:** This test also validates the happy-path of the `BusinessRuleException`-to-`ambiguousReason` routing, closing the coverage gap on the catch block at `DriverSheetImportService.java:241-244` that the existing `@Transactional`-masked test does not actually exercise at the transaction commit level.
