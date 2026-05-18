---
id: "06"
title: "BackupUploadExceptionHandler — MaxUploadSizeExceededException → Flash redirect"
phase: "74"
plan: "06"
type: execute
wave: 2
depends_on: ["01"]
requirements: [SECU-04]
files_modified:
  - src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java
  - src/test/java/org/ctc/backup/exception/BackupImportMultipartLimitIT.java
autonomous: true
tags: [spring-boot, multipart, exception-handler, flash-redirect, backup, security]

must_haves:
  truths:
    - "A 101 MB multipart upload to /admin/backup/import-preview redirects to /admin/backup with Flash errorMessage matching D-02#1 exactly."
    - "The HTTP response body for an oversized upload contains NO Tomcat / Spring stack trace markers (Servlet.service(), java.lang.Throwable, MaxUploadSizeExceededException class name)."
    - "An upload within the 100 MB limit is NOT intercepted by the new advice — the handler is selective to MaxUploadSizeExceededException only."
    - "GlobalExceptionHandler is left byte-identical — no method added, no import added, no test in GlobalExceptionHandlerTest needs to change."
    - "The new advice logs the rejection at WARN level (admin user error, not system fault) with requestURI + ex.getMaxUploadSize() for diagnosability."
  artifacts:
    - path: "src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java"
      provides: "Sibling @ControllerAdvice mapping MaxUploadSizeExceededException → redirect:/admin/backup with locked Flash string."
      contains: "@ControllerAdvice, @ExceptionHandler(MaxUploadSizeExceededException.class), \"redirect:/admin/backup\""
      min_lines: 30
    - path: "src/test/java/org/ctc/backup/exception/BackupImportMultipartLimitIT.java"
      provides: "Failsafe IT that proves (a) 101 MB upload yields the locked Flash + redirect and (b) the happy-path size is not intercepted."
      contains: "MockMultipartFile, multipart(\"/admin/backup/import-preview\"), flash().attribute"
      min_lines: 60
  key_links:
    - from: "MaxUploadSizeExceededException (thrown by Spring Multipart layer when request body > spring.servlet.multipart.max-request-size)"
      to: "BackupUploadExceptionHandler.handleMaxUploadSizeExceeded(...)"
      via: "Spring @ControllerAdvice exception-type dispatch"
      pattern: "@ExceptionHandler\\(MaxUploadSizeExceededException\\.class\\)"
    - from: "BackupUploadExceptionHandler.handleMaxUploadSizeExceeded"
      to: "/admin/backup (existing landing page with successMessage/errorMessage layout fragment)"
      via: "redirectAttributes.addFlashAttribute(\"errorMessage\", …) + return \"redirect:/admin/backup\""
      pattern: "redirect:/admin/backup"
    - from: "application.yml (Plan 01) spring.servlet.multipart.max-request-size: 100MB"
      to: "ex.getMaxUploadSize() at WARN log time"
      via: "Spring Multipart resolver reads the YAML value and surfaces it on the exception"
      pattern: "log\\.warn.*max-size exceeded"
---

# Plan 06 — `BackupUploadExceptionHandler`: `MaxUploadSizeExceededException` → Flash redirect

<objective>
Wire `MaxUploadSizeExceededException` (thrown by the Spring Multipart resolver after Plan 01 raised `spring.servlet.multipart.max-request-size` to `100MB`) to a Flash-redirect back to `/admin/backup`, using the exact locked D-02#1 string `Upload too large — maximum is 100 MB.`. Keep this handler in a **separate** `@ControllerAdvice` sibling class — never mixed into the existing `GlobalExceptionHandler` — because that class returns `ModelAndView` for every handler and adding a `String "redirect:..."` return mixes return types within a single advice (RESEARCH §Risk #2, D-14).

Purpose: SECU-04 — replace the default Tomcat stack-trace error page (an information disclosure smell) with a clean admin-readable flash message on the existing backup landing page. The user lands back exactly where they came from, sees a single sentence, no leaked exception class name or call stack.

Output:
- New: `src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java` (~35 LOC)
- New: `src/test/java/org/ctc/backup/exception/BackupImportMultipartLimitIT.java` (Failsafe IT covering rejection-branch + size-within-limit sanity)
- Zero modifications to `GlobalExceptionHandler` (verified by no entry in `files_modified`)
- Zero modifications to existing `GlobalExceptionHandlerTest`
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md
@.planning/REQUIREMENTS.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/01-PLAN.md

<!-- Source references (read-only — these are NOT modified by this plan) -->
@src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java
@src/main/java/org/ctc/admin/controller/CarController.java

<interfaces>
<!-- Key types and contracts the executor needs. No codebase exploration required. -->

From Spring Web (org.springframework.web.multipart):
```java
public class MaxUploadSizeExceededException extends MultipartException {
    public long getMaxUploadSize();           // returns -1 if unknown; otherwise the resolved limit in bytes
    public Throwable getCause();              // typically Tomcat SizeLimitExceededException
    @Override public String getMessage();     // Spring-formatted message — DO NOT surface to user
}
```

From Spring Web (org.springframework.web.servlet.mvc.support):
```java
public interface RedirectAttributes extends Model {
    RedirectAttributes addFlashAttribute(String attributeName, Object attributeValue);
    // Flash attributes survive exactly one redirect, accessible via Model on the redirected page.
}
```

From Spring Web (org.springframework.web.bind.annotation):
```java
@Target(TYPE) @Retention(RUNTIME) @Component
public @interface ControllerAdvice {
    String[] basePackages() default {};
    Class<?>[] basePackageClasses() default {};
    // Empty (the default) = global scope; Spring dispatches by exception-type match across ALL advice beans.
}
```

From CarController (analog — verbatim flash-redirect-on-reject shape, CarController.java:50-60):
```java
@PostMapping("/{id}/image")
public String uploadImage(@PathVariable UUID id, @RequestParam MultipartFile image,
                          RedirectAttributes redirectAttributes) {
    try {
        carService.uploadImage(id, image);
        redirectAttributes.addFlashAttribute("successMessage", "Image updated");
    } catch (BusinessRuleException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/admin/cars/" + id + "/edit";
}
```

From GlobalExceptionHandler (for reference — DO NOT MODIFY this file):
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    // All existing handlers return ModelAndView. Mixing String "redirect:..." here
    // would create a multi-return-type @ControllerAdvice — D-14 forbids that.
    @ExceptionHandler(EntityNotFoundException.class) public ModelAndView handleEntityNotFound(...) { ... }
    @ExceptionHandler(BusinessRuleException.class)   public ModelAndView handleBusinessRule(...) { ... }
    // ... etc.
}
```

Locked Flash string (D-02#1, CONTEXT.md §Implementation Decisions):
```
Upload too large — maximum is 100 MB.
```
This is the EXACT character sequence (en-dash `—` not hyphen `-`). Test must `equals`-compare, not contains.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 06.1: Author failing IT BackupImportMultipartLimitIT (RED)</name>

  <read_first>
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` lines 18-32 (D-01, D-02#1 — exact locked Flash string, including the en-dash `—`)
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md` row `BackupImportMultipartLimitIT` (Wave 2-3, SECU-03/SECU-04)
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/01-PLAN.md` (Plan 01 raises `spring.servlet.multipart.max-request-size: 100MB`; Plan 01 must be merged FIRST or test infra falls back to default 1 MB)
    - `src/test/java/org/ctc/backup/BackupControllerIT.java` (the analog `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("dev")` shape — copy header verbatim)
    - `src/test/java/org/ctc/dataimport/CsvImportControllerTest.java` lines around 52 (in-repo `MockMultipartFile` usage; pattern reference for `multipart(...)` MockMvc requests with file payloads)
  </read_first>

  <files>src/test/java/org/ctc/backup/exception/BackupImportMultipartLimitIT.java</files>

  <behavior>
    - Test 1 `given101MBMockMultipartFile_whenPostImportPreview_thenRedirectsToBackupWithErrorFlash()`:
      1. Build `MockMultipartFile` with name `"file"`, original filename `"oversized.zip"`, content type `"application/zip"`, content = `byte[101 * 1024 * 1024 + 1]` (101 MB + 1 byte — strictly above the 100 MB limit from Plan 01).
      2. `mockMvc.perform(multipart("/admin/backup/import-preview").file(file).with(csrf()))`.
      3. Assert `status().is3xxRedirection()`.
      4. Assert `redirectedUrl("/admin/backup")`.
      5. Assert `flash().attribute("errorMessage", "Upload too large — maximum is 100 MB.")` — **exact equality**, not `containsString`. The en-dash `—` (U+2014) is part of the locked D-02#1 string.
      6. Assert `result.getResponse().getContentAsString()` does NOT contain the strings `"Servlet.service()"`, `"java.lang.Throwable"`, or `"MaxUploadSizeExceededException"` (stack-trace-leak guard per SC#4).
    - Test 2 `givenUploadOf1KB_whenPostImportPreview_thenHandlerDoesNotIntercept()`:
      1. Build `MockMultipartFile` of `1024` bytes (sane size).
      2. Same `multipart(...)` POST.
      3. Assert that the response is NOT a redirect to `/admin/backup` carrying the `Upload too large` flash. In Phase 74 the controller endpoint may not exist yet (it is owned by Plan 08); accept either `400`, `404`, `415`, or `5xx` so long as the response does NOT carry `flash().attribute("errorMessage", "Upload too large — maximum is 100 MB.")`. The point is to prove the advice is selective on `MaxUploadSizeExceededException`, not a catch-all that intercepts every multipart POST.
    - Both tests use Given-When-Then naming + `// given` / `// when` / `// then` comments (CLAUDE.md §Test Naming).
  </behavior>

  <action>
    Create `src/test/java/org/ctc/backup/exception/BackupImportMultipartLimitIT.java`. Package: `org.ctc.backup.exception`.

    Class-header annotations (mirror `BackupControllerIT` verbatim per PATTERNS.md):
      - `@SpringBootTest`
      - `@AutoConfigureMockMvc`
      - `@ActiveProfiles("dev")`
      - No `@Transactional` (the test does not write; multipart parse fails before any service touch).

    Field-injection: `@Autowired MockMvc mockMvc;`. AssertJ + Hamcrest matchers imported from existing test dependencies (already on classpath — see `BackupControllerIT`).

    Test 1 body skeleton (Given-When-Then with explicit `// given` / `// when` / `// then` comments per CLAUDE.md §Test Naming):
      1. `// given`: allocate `byte[] payload = new byte[101 * 1024 * 1024 + 1];` and wrap in `MockMultipartFile("file", "oversized.zip", "application/zip", payload)`.
      2. `// when / then` (chained): `mockMvc.perform(multipart("/admin/backup/import-preview").file(file).with(csrf()))` → chain `.andExpect(status().is3xxRedirection())`, `.andExpect(redirectedUrl("/admin/backup"))`, `.andExpect(flash().attribute("errorMessage", "Upload too large — maximum is 100 MB."))`.
      3. Use `.andReturn().getResponse().getContentAsString()` then AssertJ `assertThat(body).doesNotContain("Servlet.service()", "java.lang.Throwable", "MaxUploadSizeExceededException")`.

    Test 2 body skeleton:
      1. `// given`: small `MockMultipartFile("file", "ok.zip", "application/zip", new byte[1024])`.
      2. `// when`: same `mockMvc.perform(multipart(...).with(csrf()))`.
      3. `// then`: capture `MvcResult`, then `assertThat(result.getFlashMap().get("errorMessage")).isNotEqualTo("Upload too large — maximum is 100 MB.");`. This passes whether the controller exists yet (might 400/415) or doesn't (404) — the assertion is purely about the advice being selective.

    Imports MUST include:
      - `static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart`
      - `static org.springframework.test.web.servlet.result.MockMvcResultMatchers.{status,redirectedUrl,flash}`
      - `static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf`
      - `static org.assertj.core.api.Assertions.assertThat`
      - `org.springframework.mock.web.MockMultipartFile`

    Why `.with(csrf())`: dev-profile has CSRF disabled in this codebase (per Phase 73 SecurityConfig), but adding `.with(csrf())` is a no-op on dev and future-proofs the test if profile-conditional auth changes (matches the defensive pattern in `BackupControllerIT`).

    **Run this test first to confirm it FAILS** with `flash().attribute(...)` mismatch — the advice does not exist yet. This is the RED phase of the TDD cycle (per workflow.tdd_mode and the Wave-0 Nyquist discipline in 74-VALIDATION.md).
  </action>

  <acceptance_criteria>
    - File exists at `src/test/java/org/ctc/backup/exception/BackupImportMultipartLimitIT.java`.
    - Compiles cleanly under `./mvnw test-compile`.
    - Executing `./mvnw -Dit.test=BackupImportMultipartLimitIT verify -DfailIfNoTests=false` **FAILS** on Test 1's `flash().attribute("errorMessage", ...)` expectation (the advice is not yet implemented — flash is empty or contains a different value). This is the RED state.
    - Test 1 asserts `redirectedUrl("/admin/backup")` literally (string equality), not a regex.
    - Test 1 asserts `flash().attribute("errorMessage", "Upload too large — maximum is 100 MB.")` with the en-dash `—` (U+2014), copy-pasted from D-02#1, NOT a hyphen.
    - Stack-trace-leak guard checks three distinct markers: `"Servlet.service()"`, `"java.lang.Throwable"`, `"MaxUploadSizeExceededException"`.
  </acceptance_criteria>

  <verify>
    <automated>./mvnw -Dit.test=BackupImportMultipartLimitIT verify -DfailIfNoTests=false 2>&1 | tee /tmp/p74-06-1-red.log; grep -E "(Tests run|BUILD FAILURE|BUILD SUCCESS)" /tmp/p74-06-1-red.log</automated>
  </verify>

  <done>
    IT class exists, compiles, RED phase confirmed: Test 1 fails on the flash-attribute mismatch (Spring's default 500 response page returns instead of the redirect). Test 2 may pass or fail — irrelevant in RED. The failure message must reference `errorMessage` or `flash` (proving the right assertion fired). No `MISSING` markers in the file.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 06.2: Implement BackupUploadExceptionHandler @ControllerAdvice (GREEN)</name>

  <read_first>
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` lines 22-23 (D-02#1 exact string) and the D-14 paragraph (lines around §"ZIP Hardening" → D-14)
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md` §"`org.ctc.backup.exception.BackupUploadExceptionHandler` (controller advice)" (the verbatim skeleton with `@ControllerAdvice`, `@ExceptionHandler(MaxUploadSizeExceededException.class)`, `RedirectAttributes` parameter, `return "redirect:/admin/backup"`)
    - `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` (reference ONLY — DO NOT modify; observe that every handler returns `ModelAndView`, which is precisely why D-14 forces a separate advice class)
    - `src/main/java/org/ctc/admin/controller/CarController.java` lines 50-60 (the verbatim `redirectAttributes.addFlashAttribute(...)` + `return "redirect:..."` shape)
  </read_first>

  <files>src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java</files>

  <behavior>
    - When `MaxUploadSizeExceededException` is thrown by the Spring Multipart resolver (configured at `spring.servlet.multipart.max-request-size: 100MB` per Plan 01), this handler:
      1. Logs at WARN level with parameterised `{}` placeholders, including `request.getRequestURI()` and `ex.getMaxUploadSize()`.
      2. Adds Flash attribute `errorMessage = "Upload too large — maximum is 100 MB."` (locked D-02#1 string with U+2014 en-dash).
      3. Returns the redirect view name `redirect:/admin/backup`.
    - The advice MUST NOT intercept any other exception type — `@ExceptionHandler(MaxUploadSizeExceededException.class)` is the only mapping in this class.
    - The advice MUST NOT add a method to `GlobalExceptionHandler` — separate `@ControllerAdvice` per D-14 + RESEARCH risk #2 (mixing `String "redirect:..."` and `ModelAndView` return types within one advice class creates Spring binding ambiguity).
  </behavior>

  <action>
    Create `src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java`. Package: `org.ctc.backup.exception` (a new package — matches D-12 / D-14 placement and groups with the future `BackupArchiveException` from Plan 02 per PATTERNS.md).

    Class header annotations (PATTERNS.md §"Existing handler pattern" + CONVENTIONS.md L7):
      - `@ControllerAdvice` from `org.springframework.web.bind.annotation` (global scope — Spring dispatches by exception type across all advice beans; narrower `basePackageClasses = BackupController.class` is intentionally NOT used, because `MaxUploadSizeExceededException` is thrown by the Spring Multipart resolver BEFORE controller dispatch, so a package-scoped advice would never match — verified by RESEARCH §Risk #2 commentary).
      - `@Slf4j` from `lombok` (CLAUDE.md §Logging — parameterised `{}` only).
      - NO `@Component` (implicit via `@ControllerAdvice`).
      - NO `@RequiredArgsConstructor` (no injected collaborators).

    Single `@ExceptionHandler` method, signature:
    ```
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes)
    ```
    Imports:
      - `org.springframework.web.bind.annotation.ControllerAdvice`
      - `org.springframework.web.bind.annotation.ExceptionHandler`
      - `org.springframework.web.multipart.MaxUploadSizeExceededException`
      - `org.springframework.web.servlet.mvc.support.RedirectAttributes`
      - `jakarta.servlet.http.HttpServletRequest`
      - `lombok.extern.slf4j.Slf4j`

    Method body, exactly three statements:
      1. `log.warn("Multipart upload rejected: max-size exceeded — uri={}, limit={}", request.getRequestURI(), ex.getMaxUploadSize());` — WARN, not ERROR (per CONTEXT.md "the advice does NOT log at ERROR — multipart-too-large is an admin user error, not a system fault"). The placeholder `{}` parameterisation is mandatory per CONVENTIONS.md L57-67. The en-dash `—` in the log message is cosmetic (not under D-02 lock).
      2. `redirectAttributes.addFlashAttribute("errorMessage", "Upload too large — maximum is 100 MB.");` — the value is the **exact** D-02#1 locked string: `Upload too large — maximum is 100 MB.` with U+2014 (en-dash), copy-pasted from CONTEXT.md §D-02#1. Flash key `errorMessage` is mandated by CLAUDE.md §"Flash attributes".
      3. `return "redirect:/admin/backup";` — the leading lowercase `redirect:` is Spring's view-resolution prefix; the target `/admin/backup` is the existing landing page from Phase 73 that already renders Flash messages via `admin/layout.html`.

    No JavaDoc on the method (matches existing handler style in `GlobalExceptionHandler.java`); a single class-level comment is acceptable but not required.

    **Do NOT** modify `GlobalExceptionHandler.java`. `files_modified` does not list it; any diff in that file is a rule violation.

    After writing the file, re-run the Task 06.1 IT — it MUST flip to GREEN.
  </action>

  <acceptance_criteria>
    - File exists at `src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java`.
    - Compiles cleanly under `./mvnw compile`.
    - Class is annotated with both `@ControllerAdvice` and `@Slf4j` (verifiable by `grep -E '^@ControllerAdvice|^@Slf4j' src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java | wc -l` returning `2`).
    - Exactly one `@ExceptionHandler` annotation in the class: `grep -c '@ExceptionHandler' src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java` returns `1`.
    - Method signature contains all three parameters: `MaxUploadSizeExceededException`, `HttpServletRequest`, `RedirectAttributes`. Verifiable by `grep -F 'MaxUploadSizeExceededException ex' src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java` and `grep -F 'RedirectAttributes redirectAttributes' src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java` each returning ≥ 1.
    - Body contains the locked D-02#1 string with the U+2014 en-dash: `grep -F 'Upload too large — maximum is 100 MB.' src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java` returns ≥ 1 (single hit).
    - Body returns the literal redirect: `grep -F 'return "redirect:/admin/backup";' src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java` returns ≥ 1.
    - WARN-level logging (NOT error): `grep -F 'log.warn(' src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java` returns ≥ 1; `grep -cE 'log\.error\(|log\.fatal\(' src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java` returns `0`.
    - `GlobalExceptionHandler.java` is byte-identical to its state at the start of this plan — `git diff src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` is empty.
    - `BackupImportMultipartLimitIT` (from Task 06.1) flips from RED to GREEN: both tests pass.
  </acceptance_criteria>

  <verify>
    <automated>./mvnw -Dit.test=BackupImportMultipartLimitIT verify -DfailIfNoTests=false 2>&1 | tee /tmp/p74-06-2-green.log; grep -E "Tests run: 2.*Failures: 0.*Errors: 0|BUILD SUCCESS" /tmp/p74-06-2-green.log && git diff --exit-code src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java</automated>
  </verify>

  <done>
    Handler class committed, IT green (2/2 tests pass), `GlobalExceptionHandler.java` untouched (verified by `git diff --exit-code` returning 0). The locked Flash string appears exactly once in the production source. No `log.error` or `log.fatal` calls introduced.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Browser → /admin/backup/import-preview (multipart POST) | Untrusted multipart body crosses the Spring filter chain; size is the first attack surface (memory exhaustion / DoS via large upload). |
| Spring Multipart resolver → Servlet container | Tomcat enforces `max-http-form-post-size` (set by Plan 01); on overflow it throws upstream of any controller code. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-74-06-01 | Denial of Service | Multipart upload endpoint `/admin/backup/import-preview` | mitigate | Plan 01 sets `spring.servlet.multipart.max-request-size: 100MB` and `server.tomcat.max-http-form-post-size: 104857600`. This plan ensures the resulting `MaxUploadSizeExceededException` produces a clean Flash redirect, not a server-error page that could surface internal state. |
| T-74-06-02 | Information Disclosure | Default Spring/Tomcat error page on multipart overflow | mitigate | New advice catches `MaxUploadSizeExceededException` and returns `redirect:/admin/backup` with a single sentence Flash. IT (`BackupImportMultipartLimitIT` Test 1) asserts the response body contains none of `"Servlet.service()"`, `"java.lang.Throwable"`, `"MaxUploadSizeExceededException"` — guarantees no stack-trace / class-name leak per SC#4. |
| T-74-06-03 | Tampering | The locked Flash string `Upload too large — maximum is 100 MB.` (D-02#1) | accept | String is hard-coded in the advice; admin sees a stable English-only message per D-01. No i18n yet (CONTEXT.md §Deferred Ideas — no `messages.properties`). Risk of mis-translation or runtime substitution is zero. |
| T-74-06-04 | Repudiation | Admin claims they never attempted an oversized upload | mitigate | `log.warn` includes `request.getRequestURI()` and `ex.getMaxUploadSize()` — sufficient diagnostic context in the SLF4J log to correlate with admin's session timestamp. No persistent audit row (Phase 75 introduces `data_import_audit`, but rejected-upload audit is explicitly out of v1.10 scope per IMPORT-08 commentary). |
</threat_model>

<verification>
## Plan-Level Verification

1. **Single-test rerun (Task 06.1 + 06.2 together):**
   ```
   ./mvnw -Dit.test=BackupImportMultipartLimitIT verify -DfailIfNoTests=false
   ```
   Expected output: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` plus `BUILD SUCCESS`.

2. **GlobalExceptionHandler immutability gate:**
   ```
   git diff --exit-code src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java
   ```
   Expected: empty diff, exit code 0. ANY diff is a rule violation (`files_modified` does not list this file).

3. **GlobalExceptionHandlerTest unchanged gate:**
   ```
   git diff --exit-code src/test/java/org/ctc/admin/controller/GlobalExceptionHandlerTest.java
   ```
   Expected: empty diff (test file may not exist; if absent the command exits 0 anyway).

4. **Source-string uniqueness gate (locked D-02#1 string appears once in production code):**
   ```
   grep -rF 'Upload too large — maximum is 100 MB.' src/main/java | wc -l
   ```
   Expected: `1` (exactly the new advice). Any other occurrence in `src/main/java` means a duplicate.

5. **Log-level discipline gate (no ERROR/FATAL in the new advice — admin user error is WARN per CONTEXT.md):**
   ```
   grep -v '^[[:space:]]*//' src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java | grep -cE 'log\.error\(|log\.fatal\('
   ```
   Expected: `0` (filters comments per critical_rules so a future descriptive comment cannot self-invalidate the gate).

6. **Final phase-suite sanity (run last, after Task 06.2 green):**
   ```
   ./mvnw verify
   ```
   Expected: all existing IT/UT still pass, JaCoCo coverage ≥ 82 %. The two new tests add coverage; the advice is fully covered by Test 1.
</verification>

<success_criteria>
- [ ] `BackupUploadExceptionHandler.java` exists at the specified path with `@ControllerAdvice`, exactly one `@ExceptionHandler(MaxUploadSizeExceededException.class)`, and the locked D-02#1 Flash string.
- [ ] `BackupImportMultipartLimitIT.java` exists, both tests green: oversized-rejection produces redirect + locked Flash, happy-path size does not trigger the advice.
- [ ] No Tomcat stack-trace marker (`"Servlet.service()"`, `"java.lang.Throwable"`, `"MaxUploadSizeExceededException"`) appears in the oversized-upload response body — verified by Test 1's `assertThat(body).doesNotContain(...)`.
- [ ] `GlobalExceptionHandler.java` is byte-identical to its pre-plan state (verified by `git diff --exit-code`).
- [ ] The locked D-02#1 string `Upload too large — maximum is 100 MB.` (with U+2014 en-dash) appears exactly once in `src/main/java`.
- [ ] The new advice logs at WARN level only — zero `log.error` / `log.fatal` calls (verified by comment-stripped grep).
- [ ] `./mvnw verify` is green end-to-end with the two new tests included; JaCoCo line coverage ≥ 82 % maintained.
- [ ] Maps to SECU-04 acceptance: `MaxUploadSizeExceededException` is centrally mapped to a readable Flash on the existing backup landing page; the German-string example in REQUIREMENTS.md §SECU-04 is overridden by D-01 (English-only UI) — the English D-02#1 string is the authoritative one.
</success_criteria>

<notes>
- **Why a SEPARATE @ControllerAdvice (not a method added to `GlobalExceptionHandler`):** Every handler in `GlobalExceptionHandler` returns `ModelAndView`. The IMPORT-04 / SECU-04 contract requires a Flash redirect (a `String "redirect:..."` return). Mixing return types in one `@ControllerAdvice` class works at runtime, but RESEARCH §Risk #2 flagged it as fragile: when Spring resolves which advice handles a thrown exception, mixed return-shape handlers in the same class invite subtle ordering bugs if future handlers are added. D-14 settles this — a sibling class is the canonical solution. This plan ships exactly that.
- **Why global @ControllerAdvice scope (not `basePackageClasses = BackupController.class`):** `MaxUploadSizeExceededException` is thrown by the Spring Multipart resolver during request parsing — before the dispatcher routes to `BackupController`. A package-scoped advice would never see the exception (because there is no controller bean associated with the request at that point). Verified by RESEARCH §Risk #2. Global scope is correct; it remains narrow because no other code in the app throws `MaxUploadSizeExceededException` (multipart is currently used only by `CarController.uploadImage` for small image files well below the limit, and the new backup endpoint).
- **Why WARN, not ERROR, log level:** A 101 MB upload is an admin user error (chose the wrong file, or the source league is genuinely larger than 100 MB and needs a developer to bump the limit). It is not a system fault. CONTEXT.md §"Notes" line — "The advice does NOT log at ERROR — multipart-too-large is an admin user error, not a system fault. WARN level is correct." — is the authority.
- **Why `request.getRequestURI()` in the log:** Future-proofing — if a second multipart endpoint is added (e.g. CSV upload), an admin can grep `Multipart upload rejected: max-size exceeded — uri=...` and see which endpoint was hit. Cheap diagnostic value; no PII because the URI is `/admin/backup/import-preview` only.
- **No new package documentation:** `org.ctc.backup.exception` is a new package, but per project convention (and CLAUDE.md "no documentation files unless requested"), no `package-info.java` is added.
- **Dependency on Plan 01:** This plan's IT relies on `spring.servlet.multipart.max-request-size: 100MB` from Plan 01's `application.yml` change. Plan 01 is in Wave 1 (`depends_on: ["01"]` in this plan's frontmatter), so the execute-phase orchestrator will land Plan 01 before invoking Plan 06. If Plan 01 has not run, the multipart resolver falls back to Spring's default 1 MB and the 101 MB test would still trigger `MaxUploadSizeExceededException` (just earlier), so the IT remains correct — but the production behaviour would diverge from D-13. Plan 01 must merge first; the dependency declaration enforces this.
- **What this plan does NOT do:**
  - Wire `BackupController.importPreview` (`POST /admin/backup/import-preview`) — owned by Plan 08.
  - Handle `BackupArchiveException` (ZIP-Slip, ZipBomb, schema mismatch) — owned by Plan 07's per-Reason flash routing in `BackupController.importPreview`.
  - Add an i18n bundle — deferred per CONTEXT.md §Deferred Ideas.
- **Test coverage signal:** Test 1 exercises the entire advice method (3 statements, all branches). Combined with Test 2 (selectivity), the new file should reach 100 % line coverage on the new class, contributing positively to the ≥ 82 % JaCoCo gate.
</notes>

<output>
After completion, create `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-06-SUMMARY.md` per the standard execute-plan SUMMARY template (objective, artifacts, decisions, validation results, follow-ups).
</output>

## PLAN COMPLETE 06
