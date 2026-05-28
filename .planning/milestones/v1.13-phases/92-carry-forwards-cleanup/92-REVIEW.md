---
phase: 92-carry-forwards-cleanup
reviewed: 2026-05-28T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - pom.xml
  - src/main/java/org/ctc/dataimport/CsvImportController.java
  - src/main/resources/templates/admin/import-preview.html
  - src/main/resources/templates/admin/import.html
  - src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java
  - src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java
  - src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java
  - src/test/java/org/ctc/dataimport/GoogleCalendarServiceIT.java
  - src/test/java/org/ctc/dataimport/GoogleSheetsServiceIT.java
findings:
  critical: 1
  warning: 6
  info: 4
  total: 11
status: issues_found
---

# Phase 92: Code Review Report

**Reviewed:** 2026-05-28T00:00:00Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

Phase 92 carries forward the v1.12 typed-catch + `errorCategory` flash + badge UX into the third Google-Sheets consumer (`CsvImportController`), adds `RaceControllerCalendarTest` to restore JaCoCo coverage on the calendar-event handler, adds two ITs against the real `GoogleSheetsService` / `GoogleCalendarService` for IOException error-path coverage, and lands an `assumptions-fence` build-guard with a predicate test.

The implementation generally matches the v1.12 parity pattern (`RaceController.createCalendarEvent`) — error categories, sealed-exhaustiveness defensive arm, log-vs-warn split, no `e.getMessage()` echo in Google paths. However, the review surfaces one **BLOCKER** (a security/contract violation of the T-91-02-IL "never echo Google `e.getMessage()`" invariant in the `previewSheet` IllegalArgumentException/IllegalStateException arm, which is reachable from Google-side data shape errors), plus six **WARNING** items around test correctness (deep-stub fluent-mock chains, IT reflection-injection without restore, missed coverage of the `defensive base` arm despite a comment claiming it is "covered"), and four documentation/comment-pollution **INFO** items per the project No-Comment-Pollution rule.

## Critical Issues

### CR-01: `previewSheet` still echoes `e.getMessage()` for IllegalArgumentException/IllegalStateException — Google-internal details can leak

**File:** `src/main/java/org/ctc/dataimport/CsvImportController.java:154-159`
**Issue:** The Google-Sheets-path handler (`previewSheet`) catches the four typed `GoogleApiException` permits with whitelisted literal messages (good), but the trailing catch arm

```java
} catch (IllegalArgumentException | IllegalStateException e) {
    log.error("Error reading Google Sheet", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Error reading Google Sheet: " + e.getMessage());
    return "admin/import";
}
```

is reachable from the Google-Sheets call chain itself, not just from local validation. Concrete reachable paths inside the `try` block:

1. `googleSheetsService.extractSpreadsheetId(sheetUrl)` throws `IllegalArgumentException("URL must not be null or blank")` and `IllegalArgumentException("Invalid Google Sheets URL: ...")` — fine, those are local messages.
2. `googleSheetsService.getSheetNames(...)` / `readRangeFromSheet(...)` — when the Sheets API returns a well-formed but unexpected payload, the Google client library throws `IllegalArgumentException` / `IllegalStateException` with Google-internal details (e.g. parser errors mentioning private field names, internal class paths). Those messages reach `errorMessage` verbatim and render via Thymeleaf — `<span th:text="${errorMessage}">` in `import.html:14`.
3. `scorecardParser.parseMultipleRaces(...)` may surface `IllegalStateException` carrying row/column data from the sheet (potentially PII-ish driver names) — that's the same risk vector that drove T-91-02-IL.

This is the exact "do not echo Google-side `e.getMessage()`" invariant called out in CLAUDE.md context for Phase 92. The sibling controllers fixed in v1.12 either (a) wrap with `"URL validation failed: ..."` only on the `extractSpreadsheetId`-specific arm, or (b) catch `IllegalArgumentException` strictly outside the Google call section.

**Fix:** Split the arm into two: a validation-only arm covering the `extractSpreadsheetId` path with the local message, and a defensive Google-side arm that uses a whitelisted literal:

```java
} catch (IllegalArgumentException e) {
    log.error("Invalid Google Sheet URL", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Invalid Google Sheet URL");
    return "admin/import";
} catch (IllegalStateException e) {
    log.error("Unexpected error reading Google Sheet", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Error reading Google Sheet — see server logs");
    model.addAttribute("errorCategory", "TRANSIENT");
    return "admin/import";
}
```

The same pattern issue exists in `execute()` at line 265 (`BusinessRuleException | ValidationException | IllegalArgumentException` echoing `e.getMessage()`) — there the `IllegalArgumentException` is reachable from `googleSheetsService.extractSpreadsheetId` AND from `UUID.fromString(entry.getValue())` in the confirmed-match loop AND from any deeper Google-side call. Apply the same split.

---

## Warnings

### WR-01: `IOException` arm in `preview()` is now structurally unreachable but kept anyway

**File:** `src/main/java/org/ctc/dataimport/CsvImportController.java:62-67`
**Issue:** The CSV-only `preview()` handler still declares `catch (IOException | IllegalArgumentException | IllegalStateException e)`. After the Google-Sheets typed-catch refactor, the IOException source is solely `file.getInputStream()` — which is in-scope. Good. But the test `CsvImportControllerExceptionTest.givenIoException_whenPreviewCsv_thenRedirectsWithError` stubs `csvImportService.parseAndPreview(...)` to throw IOException. `parseAndPreview` is declared with `throws IOException`, so the catch is exercised; that's fine. However, the test asserts only `attributeExists("errorMessage")` — it does not assert the message does NOT contain the raw `e.getMessage()`. Since the controller still concatenates `"Error reading CSV: " + e.getMessage()`, malicious filename content uploaded as `MockMultipartFile` would echo back. Not strictly a security finding for CSV reading (the user uploaded the file), but inconsistent with the Google-side hygiene applied a few lines down.
**Fix:** Assert in the test that `errorMessage` starts with `"Error reading CSV"` AND does not contain the original throwable's message verbatim, OR sanitize the controller. At minimum log `e.getMessage()` (already done) and render a generic literal.

### WR-02: `GoogleCalendarServiceIT` uses deep-stub fluent-mock with non-idempotent stub return — verifies nothing the unit tests don't already

**File:** `src/test/java/org/ctc/dataimport/GoogleCalendarServiceIT.java:67-69` and `:81-83`
**Issue:** The IT does

```java
when(mockCalendar.events().insert(anyString(), any()).execute())
        .thenThrow(authError);
```

`RETURNS_DEEP_STUBS` returns a *new* mock per call only if the path involves a generic return type; here `events()` returns the same deep-stubbed mock per invocation, BUT `insert(anyString(), any())` is a method with non-generic return — each invocation with matcher `any()` may return the same Insert mock or may return a fresh one. The Mockito guarantee is that the LAST `when(...).thenReturn(...)` chain wins for that exact matcher-set. In production, `GoogleCalendarService.createEvent` likely calls `events().insert(calendarId, event)` ONCE with concrete arguments, then `.execute()`. The deep-stub matching on the actual concrete args matches because the stub uses `anyString()/any()` — provided the production code calls the method only once in the chain. If `createEvent` calls `events()` twice (e.g. for setup + send), the stub may not bind. This is brittle without inspecting the production implementation.

Worse, the IT asserts only `isInstanceOf(AuthGoogleApiException.class)` / `isInstanceOf(PermissionGoogleApiException.class)`. The unit test `GoogleSheetsServiceTest.TypedThrowsContractTest` already validates the mapper. The IT does NOT prove anything beyond "the mapper still maps." There's no Spring-context-specific behavior under test. The "IT" tag is misleading — this is a unit test in IT clothing.

**Fix:** Either (a) replace the deep-stub with an explicit `Mockito.spy()` on a real `Calendar` builder + `WireMock` stub for the 403 response (the project's WireMock dependency already supports this; CLAUDE.md "WireMock is not Real-API Coverage" rule applies — but here a WireMock-driven test exercises real client+mapper), or (b) demote the test out of `@Tag("integration")` and let the existing unit-level mapper tests cover it, removing the duplicative IT-tagged class.

### WR-03: ITs reflectively inject mocks into `@Autowired` Spring bean without restoring state — pollutes shared application context

**File:** `src/test/java/org/ctc/dataimport/GoogleCalendarServiceIT.java:45-49` and `src/test/java/org/ctc/dataimport/GoogleSheetsServiceIT.java:42-46`
**Issue:** Both ITs are `@SpringBootTest @Transactional` and use reflection to overwrite the `calendarClient` / `sheetsClient` private field on the live Spring bean:

```java
private static void injectCalendarClient(GoogleCalendarService service, Calendar client) throws Exception {
    Field field = GoogleCalendarService.class.getDeclaredField("calendarClient");
    field.setAccessible(true);
    field.set(service, client);
}
```

`@Transactional` does NOT roll back field mutations on a singleton bean. After test #1 in the class injects a mock client and test #2 runs, the bean still holds the mock from #1 if `@DirtiesContext` is not set. Worse, because Spring caches the application context across test classes (default behavior under Surefire/Failsafe), a later IT class that depends on `GoogleSheetsService.isAvailable()` may inherit the dangling mock. Surefire forks the class load but Failsafe with `reuseForks=true` (pom.xml:344) keeps the JVM alive, and Spring's `DefaultCacheAwareContextLoaderDelegate` keys context on `MergedContextConfiguration` — two `@SpringBootTest @ActiveProfiles("dev")` ITs reuse the SAME context. This is a context-pollution bug waiting to happen.

**Fix:** Either (a) save the original field value in `@BeforeEach`, restore it in `@AfterEach`; (b) annotate the class with `@DirtiesContext(classMode = AFTER_CLASS)`; or (c) replace the field injection with a `@MockitoBean GoogleSheetsService`/`Calendar` if the architecture permits, which Spring isolates per test class. Option (a) is the minimal-blast-radius fix:

```java
private Calendar originalClient;
@BeforeEach void snapshot() throws Exception {
    Field f = GoogleCalendarService.class.getDeclaredField("calendarClient");
    f.setAccessible(true);
    originalClient = (Calendar) f.get(googleCalendarService);
}
@AfterEach void restore() throws Exception {
    Field f = GoogleCalendarService.class.getDeclaredField("calendarClient");
    f.setAccessible(true);
    f.set(googleCalendarService, originalClient);
}
```

### WR-04: `RaceControllerCalendarTest` constructs a second controller manually — bypasses Spring AOP, can desync from `@SpringBootTest` controller behavior

**File:** `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java:84-93`
**Issue:** The class is `@SpringBootTest @AutoConfigureMockMvc` (autowiring `mockMvc`), but `@BeforeEach setupModelOnlyMockMvc()` constructs a *second* `RaceController` with `new RaceController(...)` and runs it via `MockMvcBuilders.standaloneSetup(...)`. This standalone controller has NO Spring-managed advice (`@ControllerAdvice`, `@ExceptionHandler` in base classes, Spring Security filters, the `csrfFetch` interceptor expected by the import.html template, etc.). The POST tests use the autowired `mockMvc` (Spring-managed) but the GET tests use the standalone one — they exercise different MVC stacks with different filter chains. A future change to a global `@ControllerAdvice` (say, logging response status or rewriting flash attributes) will cause the GET tests to diverge from production behavior silently.

The rationale comment ("Lets the GET handler run end-to-end while skipping template rendering") is workable but the project already has a precedent for using `@AutoConfigureMockMvc(addFilters=false)` plus `view().name(...)` assertions without forcing render — that's what the autowired `mockMvc` should do by default (status 200 is checked, view name is asserted, render is skipped because there's no template-class-resolver attached to ModelAndView assertion path).

**Fix:** Drop the standaloneSetup. Replace the GET tests with the same autowired `mockMvc`. Spring's MockMvc does NOT auto-render Thymeleaf in `view().name(...)` assertion mode if a `ViewResolver` is supplied that returns an `InternalResourceView` (default for path resolution) — the resolver looks up the view but the actual rendering is deferred to forwarded request. In `@WebMvcTest`/`@SpringBootTest` flows the `ThymeleafViewResolver` does render; in that case use a `@TestConfiguration` that registers a no-op `ViewResolver` with higher precedence, applied to the class — not a fresh standalone controller. That keeps Spring's full advice chain.

### WR-05: `RaceControllerCalendarTest` does not actually cover the defensive `GoogleApiException` base arm

**File:** `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java:37-50` (class javadoc) and `:117-209` (tests)
**Issue:** The class Javadoc says "The defensive `catch (GoogleApiException e)` arm on the sealed base is acknowledged unreachable from external tests (sealed permits forbid subclassing) and not exercised here." Combined with the file-existence of `CsvImportControllerExceptionTest` that ALSO does not cover it, AND the production controller (`CsvImportController.java:145-153` / `:259-264`) and `RaceController.java:292-297` having identical defensive blocks, the result is that this whole arm contributes ~24 uncovered lines per controller × 3 controllers = ~72 lines counted against the 82% JaCoCo line-coverage floor. The phase claims to "restore" coverage but does not address the defensive arm at all.

The "sealed permits forbid subclassing" claim is true at the language level, but JaCoCo measures bytecode-level coverage of the catch instruction — the arm DOES emit a basic block (the `log.error/addFlashAttribute(...)` block) that JaCoCo counts as missed. The CLAUDE.md JaCoCo exclude list does not exempt these controllers.

**Fix:** Either (a) annotate the defensive blocks with `@SuppressFBWarnings`/JaCoCo exclude via `pom.xml`'s jacoco `<excludes>` for the exact catch handler (not feasible at line level), or (b) actually exercise the arm with a reflection-or-Powermock-injected subtype, or (c) accept that the defensive arms are pragmatic and document the coverage cost in `92-VERIFICATION.md`. Without one of those, the v1.12 COV-01 audit may regress.

### WR-06: `AssumptionsFencePredicateTest` writes to filesystem with platform-dependent path quoting and no shell-injection guard

**File:** `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java:39-45` and `:60-65`
**Issue:** The test builds a bash command line by string concatenation:

```java
var pb = new ProcessBuilder("bash", "-c",
        "grep -rE '" + FENCE_REGEX + "' " + tmp.resolve("src/test/java"));
```

`tmp` is JUnit's `@TempDir` — on macOS it lives under `/var/folders/...`, on Linux under `/tmp/...`, on Windows under `C:\Users\...\AppData\Local\Temp\` (the test will fail on Windows because the project uses `bash` literally). More importantly, if the JUnit-Jupiter base TempDir ever returns a path containing a space or shell metachar (which it doesn't on macOS/Linux today but is not guaranteed by the JUnit contract), the unquoted shell concatenation breaks. Also `FENCE_REGEX` is hard-coded as a Java string literal — if `pom.xml`'s regex evolves, the test's copy goes stale silently (its contract is "MUST stay byte-for-byte identical to the … value in pom.xml").

**Fix:** Either (a) quote the path: `"'" + tmp.resolve(...) + "'"`, (b) better, pass the path via env var (`pb.environment().put("ROOT", tmp...)` and refer to it as `"$ROOT"`), and (c) add a verifying assertion that reads the literal regex out of `pom.xml` at test time:

```java
String pomRegex = extractAssumptionsFenceRegex(Path.of("pom.xml"));
assertThat(pomRegex).isEqualTo(FENCE_REGEX);
```

so a future edit to `pom.xml` cannot silently desync.

---

## Info

### IN-01: Comment-pollution — defensive-arm Javadoc explaining "javac required" appears 3× verbatim

**File:** `src/main/java/org/ctc/dataimport/CsvImportController.java:146-148` and `:260-261`; `src/main/java/org/ctc/admin/controller/RaceController.java:293-294`
**Issue:** The defensive arm carries a 3-line explanatory comment about sealed exhaustiveness in every catch block. Per CLAUDE.md "No Comment Pollution / Allowed (rare): single-line comments for non-obvious WHY", a single-line `// sealed-base defensive arm — javac exhaustiveness` per occurrence is sufficient. Three near-identical multi-line blocks are repetition.
**Fix:** Collapse to a single line, or factor an `@ExceptionHandler(GoogleApiException.class)` method on a `@ControllerAdvice` and drop the per-controller catch entirely (also DRYs WR-05 coverage).

### IN-02: Comment-pollution — `// Read all race sheet data` and `// Parse multiple races` restate the next line

**File:** `src/main/java/org/ctc/dataimport/CsvImportController.java:90, 97, 106, 176, 189, 196`
**Issue:** Each is a single-line comment restating the immediately-following named method call (`googleSheetsService.readRangeFromSheet`, `scorecardParser.parseMultipleRaces`, `csvImportService.checkDuplicate`). The names already self-document.
**Fix:** Remove the comments.

### IN-03: Comment-pollution — phase/coverage references in test Javadoc

**File:** `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java:37-51`, `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java:28-33`, `src/test/java/org/ctc/dataimport/GoogleSheetsServiceIT.java:22-32`, `src/test/java/org/ctc/dataimport/GoogleCalendarServiceIT.java:27-35`
**Issue:** The Javadoc blocks reference "closes the JaCoCo cold-spot identified in v1.12 COV-01 audit", "supplementing the unit-level coverage in `GoogleSheetsServiceTest.TypedThrowsContractTest`", and "Restores the JaCoCo line-coverage cold spots". Per CLAUDE.md "No Comment Pollution / hard-banned: Phase / Plan / Task / UAT / Wave references". This is the v1.12 COV-01 reference flavour — bannned.
**Fix:** Reduce to a one-line class purpose statement, e.g. `/** Tests RaceController calendar-event POST + GET model attributes. */`. Git history carries the COV-01 provenance.

### IN-04: Inline `style="..."` survives in import-preview.html

**File:** `src/main/resources/templates/admin/import-preview.html:36, 42, 83, 98, 104, 145`
**Issue:** The `<ul style="margin:8px 0 0 16px;">`, `<div ... style="margin-bottom:16px; border-color:#ffa726; background:rgba(255,167,38,0.1); color:#ffa726;">`, and `<span class="badge" style="background:#3a2e1a;color:#ffb74d;">Create new</span>` all violate CLAUDE.md "No Inline Styles on Buttons" extended to alerts/badges in `admin.css` (rules `.alert--warning`, `.badge-create-new`, `.list-tight`). The new `error-badge--auth` / `error-badge--not_found` etc. classes added in this phase live in admin.css; the page-level survivors above should also move.
**Issue (scope clarification):** Phase 92's diff likely did not introduce these — they pre-exist — but the touched file's style-survivors should be migrated under the "When refactoring, remove pollution from touched files" rule of CLAUDE.md "No Comment Pollution".
**Fix:** Add `alert--warning-amber`, `badge-create-new`, `list-tight` classes to admin.css; replace the inline styles in import-preview.html.

---

_Reviewed: 2026-05-28T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
