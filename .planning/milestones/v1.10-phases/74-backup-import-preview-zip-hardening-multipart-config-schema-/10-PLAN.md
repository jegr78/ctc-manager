---
id: "10"
title: "BackupImportE2ETest — Playwright walkthrough"
phase: 74
plan: "10"
type: execute
wave: 3
depends_on: ["08", "09"]
requirements: [IMPORT-01, IMPORT-02, IMPORT-03, IMPORT-04]
files_modified:
  - src/test/java/org/ctc/e2e/BackupImportE2ETest.java
autonomous: true
tags: [backup, e2e, playwright, stateless-proof]

must_haves:
  truths:
    - "A real Phase-73 export ZIP (generated at runtime via BackupArchiveService.writeZip) uploaded through the admin UI lands the user on the preview page with the 24-card grid, the schema-match pill, and the header block visible — proves IMPORT-01 (preview rendered from real ZIP)."
    - "The schema-version pill renders the locked UI-SPEC string 'Schema version 1 matches.' for a Phase-73 export (current schema = 1) — proves IMPORT-02 (schema-version gate passes for valid ZIPs)."
    - "After clicking Proceed to Confirm, ticking the @AssertTrue acknowledgment checkbox, and clicking Execute Import, the browser lands on /admin/backup with the locked D-02#5 stub-success Flash 'Validation succeeded. Import execution will be enabled in Phase 75.' — proves IMPORT-04 (confirm-dialog + checkbox gate)."
    - "Submitting the confirm form without ticking the checkbox re-renders the confirm page with the inline .field-error message 'You must acknowledge the deletion warning to continue.' — proves IMPORT-04 server-side @AssertTrue enforcement (D-10)."
    - "Clicking Cancel on the preview page submits a POST to /admin/backup/import-cancel (hidden stagingId), deletes the staging file `upload-{uuid}.zip` from the configured app.backup.staging-dir, and redirects to /admin/backup — proves D-06 + D-16 (Cancel cleanup via POST form per Plan 08)."
    - "After clearing the browser context's cookie jar between the preview-render and a programmatic re-POST to /admin/backup/import-confirm with the same stagingId, the confirm page renders identically — proves SC#5 / IMPORT-03 (stateless re-parse via staging UUID, no @SessionAttributes)."
    - "Test class lives at src/test/java/org/ctc/e2e/BackupImportE2ETest.java, extends PlaywrightConfig (inherits @SpringBootTest(WebEnvironment.RANDOM_PORT) + @ActiveProfiles(\"dev\")), uses *Test suffix so the -Pe2e Failsafe profile picks it up via **/e2e/**/*Test.java, and is skipped by Surefire (which excludes **/e2e/**)."
  artifacts:
    - path: "src/test/java/org/ctc/e2e/BackupImportE2ETest.java"
      provides: "Playwright E2E walkthrough — happy path, cancel cleanup, missing-checkbox validation, stateless-cookie-jar proof"
      exports:
        - "givenPhase73ExportZip_whenAdminUploadsAndConfirms_thenLandsOnBackupWithStubFlash"
        - "givenPreviewPage_whenAdminClicksCancel_thenStagingFileDeletedAndRedirects"
        - "givenConfirmPage_whenAdminSubmitsWithoutTickingCheckbox_thenSeesFieldError"
        - "givenStagingUuid_whenCookieJarIsClearedBetweenPreviewAndExecute_thenPagesStillFunction"
  key_links:
    - from: "BackupImportE2ETest"
      to: "PlaywrightConfig"
      via: "extends — inherits browser/context/page lifecycle + @SpringBootTest RANDOM_PORT + @ActiveProfiles(\"dev\")"
      pattern: "extends PlaywrightConfig"
    - from: "BackupImportE2ETest fixture generation"
      to: "BackupArchiveService.writeZip"
      via: "@Autowired BackupArchiveService — runtime export-then-import round-trip (no committed binary fixtures per D-25)"
      pattern: "archiveService\\.writeZip"
    - from: "BackupImportE2ETest cancel + cookie-jar tests"
      to: "@Value(\"${app.backup.staging-dir:...}\") Path stagingDir"
      via: "field-level @Value resolves the same property Plan 01 sets in application.yml — test asserts staging-file existence directly on the filesystem"
      pattern: "@Value.*app\\.backup\\.staging-dir"
    - from: "BackupImportE2ETest happy path"
      to: "Plan 08 BackupController.importPreview/importConfirm/importExecute"
      via: "click-through HTTP requests via Playwright (server-rendered Thymeleaf views from Plan 09)"
      pattern: "page\\.navigate|page\\.locator|page\\.getByRole"
    - from: "BackupImportE2ETest stateless-cookie-jar test"
      to: "BackupImportService.reparse(UUID)"
      via: "POST /admin/backup/import-confirm after browserContext.clearCookies() — server must re-read staging file by UUID, not by HttpSession"
      pattern: "browserContext\\.clearCookies"
---

## Objective

Author the Phase 74 end-to-end Playwright walkthrough that exercises the full Backup Import surface (upload → preview → confirm → stub-execute) through a real browser against the live Spring context. The test is the only piece of Phase 74 verification that drives the **full stack** — multipart parsing (Plan 01 config), ZIP hardening (Plan 02 + Plan 04 reader), DTO binding (Plan 03), service-layer staging + reparse (Plan 05), exception-handler Flash mapping (Plan 06), controller endpoints (Plan 08), and Thymeleaf templates (Plan 09) — and is the sole proof on disk for two of the phase success criteria:

- **SC#1 (preview rendered):** the 24-card grid and schema-match pill must appear in real DOM after a real multipart POST.
- **SC#5 (stateless re-parse, no session):** the confirm-page renders identically after the cookie jar is wiped between preview and execute, demonstrating that the server re-reads the staged ZIP by UUID rather than out of `HttpSession`.

The test class follows the established sister-test shape: `extends PlaywrightConfig` (which provides `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("dev")` + Chromium lifecycle), `setupPage()`/`teardownPage()` in `@BeforeEach`/`@AfterEach`, and the `*Test` suffix so the `-Pe2e` Maven profile's Failsafe binding (`**/e2e/**/*Test.java`) picks it up. Surefire excludes `**/e2e/**` so the test never runs in the standard unit phase.

**Purpose:** make Phase 74 shippable. Without this test there is no automated proof that the four IMPORT requirements (IMPORT-01..04) compose correctly end-to-end. Integration tests (Plans 04/05/08) cover the layers individually; this Playwright walkthrough is the system test.

**Output:** one new test file (`src/test/java/org/ctc/e2e/BackupImportE2ETest.java`) with 4 `@Test` methods. No production code changes. No committed binary fixtures — the test ZIP is generated at runtime via `BackupArchiveService.writeZip` (D-25). Runs under `./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest`.

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-UI-SPEC.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md
@.planning/REQUIREMENTS.md
@src/test/java/org/ctc/e2e/PlaywrightConfig.java
@src/test/java/org/ctc/e2e/BackupExportE2ETest.java
@pom.xml

<interfaces>
<!-- Key types and contracts the executor needs. Embedded so the executor does not explore. -->

From `src/test/java/org/ctc/e2e/PlaywrightConfig.java` (base class — DO NOT MODIFY):
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public abstract class PlaywrightConfig {
    static Playwright playwright;        // @BeforeAll
    static Browser browser;              // @BeforeAll (Chromium, headless)
    @LocalServerPort int port;
    BrowserContext context;              // setupPage() — fresh context per test
    Page page;                           // setupPage() — fresh page per test
    protected void setupPage();          // call from @BeforeEach
    protected void teardownPage();       // call from @AfterEach
    protected String url(String path);   // "http://localhost:" + port + path
}
```

From `src/test/java/org/ctc/e2e/BackupExportE2ETest.java` (sister Playwright test — STRUCTURAL TWIN):
```java
class BackupExportE2ETest extends PlaywrightConfig {
    @BeforeEach void setUp()    { setupPage();    }
    @AfterEach  void tearDown() { teardownPage(); }
    // Uses: page.navigate, page.getByRole(AriaRole.LINK/BUTTON, ...), page.waitForDownload(...),
    // assertThat(page).hasURL(Pattern), assertThat(page.locator("h1")).containsText(...), etc.
}
```

Production endpoints under test (from Plan 08 — `org.ctc.backup.BackupController`):
- `GET  /admin/backup`                     → view `admin/backup` (landing with Export + Import forms)
- `POST /admin/backup/import-preview`      → multipart, view `admin/backup-preview` on success; redirect:/admin/backup with errorMessage on reject
- `POST /admin/backup/import-confirm`      → view `admin/backup-confirm` on success
- `POST /admin/backup/import-execute`      → @Valid form; redirect:/admin/backup with successMessage = "Validation succeeded. Import execution will be enabled in Phase 75." (D-02#5)
- `POST /admin/backup/import-cancel`       → form-encoded body with hidden `stagingId` UUID field; deletes staging file via `BackupImportService.deleteStagingFile(UUID)` and redirects to /admin/backup with `successMessage = "Import canceled."` (Plan 08 + Plan 09 CSRF-discipline trade-off — see Plan 09 `## Notes`)

Locked UI strings the test asserts on (from CONTEXT D-02 + UI-SPEC):
- `"Import Backup"`                                      — landing-form heading + button label
- `"Backup Import — Preview"`                            — preview `<h1>` (em dash, U+2014)
- `"Backup Import — Confirm"`                            — confirm `<h1>`
- `"Schema version 1 matches."`                          — schema-match pill (preview)
- `"Proceed to Confirm"`                                 — preview primary CTA
- `"Execute Import"`                                     — confirm destructive CTA
- `"Cancel"`                                             — secondary CTA on both pages (button text inside a POST form per Plan 09)
- `"I am an admin and I understand all operational data will be deleted."`  — checkbox label (D-02#4)
- `"You must acknowledge the deletion warning to continue."`               — @AssertTrue field-error
- `"Validation succeeded. Import execution will be enabled in Phase 75."`  — stub-success Flash (D-02#5)
- `"Import canceled."`                                                     — Cancel-flow Flash (Plan 08)
- `"Replace all operational data? This cannot be undone."`                  — JS confirm() dialog text (D-10)

DOM selectors (from UI-SPEC §"Layout per page" + Plan 09 final shape):
- `input[type='file'][name='file']`                       — file input on landing
- `.card-grid > .card`                                    — preview cards (expect count = 24)
- `input[name='stagingId']` (hidden)                      — UUID on every staged form (preview, confirm, cancel forms all carry it)
- `form[action$='/import-cancel']`                        — Cancel POST form on preview AND confirm pages (Plan 09 sibling-form pattern inside `<div class="actions">`)
- `form[action$='/import-cancel'] button[type='submit']`  — Cancel submit button inside the cancel form
- `input#acknowledged[type='checkbox']`                   — confirm checkbox (`th:field="*{acknowledged}"`)
- `.field-error`                                          — inline validation error (class-only selector — Plan 09 renders as `<span class="field-error" role="alert">`, tag-agnostic for resilience)
- `.alert.alert-success` / `.alert.alert-error`           — Flash banners (rendered by `admin/layout.html`)

Service used by the test for fixture generation (constructor-injected by Spring):
```java
public class BackupArchiveService {
    public void writeZip(OutputStream out, Instant exportDate) throws IOException;
}
```

Configured staging-dir property (Plan 01):
```yaml
app:
  backup:
    staging-dir: data/${spring.profiles.active}/backup-staging   # dev → data/dev/backup-staging
```
The test reads this via `@Value("${app.backup.staging-dir}") Path stagingDir` to assert file presence/absence on disk.
</interfaces>

<e2e_profile_binding>
From `pom.xml` — `<profile id="e2e">` (verified):
```xml
<plugin>
  <artifactId>maven-failsafe-plugin</artifactId>
  <configuration>
    <includes>
      <include>**/e2e/**/*Test.java</include>
    </includes>
  </configuration>
</plugin>
```
Surefire excludes `**/e2e/**` so the test does NOT run under `./mvnw verify`. The executor command for this plan is `./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest`.
</e2e_profile_binding>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Author BackupImportE2ETest — happy path (full upload → preview → confirm → execute walkthrough)</name>
  <files>src/test/java/org/ctc/e2e/BackupImportE2ETest.java</files>
  <read_first>
    - src/test/java/org/ctc/e2e/PlaywrightConfig.java (entire file — abstract base class shape)
    - src/test/java/org/ctc/e2e/BackupExportE2ETest.java (entire file — structural twin: extends pattern, @BeforeEach/@AfterEach lifecycle, page.navigate/getByRole/locator idioms, AssertJ + PlaywrightAssertions imports)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-UI-SPEC.md §"Copywriting Contract" + §"Layout per page" (locked strings + selectors)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md D-02, D-08, D-10, D-18 (locked Flash strings + stub-execute contract + stateless re-parse)
  </read_first>
  <behavior>
    Test method `givenPhase73ExportZip_whenAdminUploadsAndConfirms_thenLandsOnBackupWithStubFlash`:
    - Given: a Phase-73 export ZIP generated at runtime into a `@TempDir` Path by calling `backupArchiveService.writeZip(Files.newOutputStream(zipPath), Instant.now())`.
    - When: navigate to `/admin/backup`, set the file on the landing `input[type='file']`, click `Import Backup`, observe preview page, click `Proceed to Confirm`, observe confirm page, accept the JS `confirm()` dialog via `page.onDialog(d -> d.accept())`, tick `#acknowledged`, click `Execute Import`.
    - Then: URL ends at `/admin/backup`, the `.alert.alert-success` Flash contains `"Validation succeeded. Import execution will be enabled in Phase 75."`, NO `.alert.alert-error` is visible, no error string `"mismatch"` / `"safety checks"` / `"too large"` is present anywhere on the page.
    - Additional intermediate assertions:
      * On `/admin/backup` (landing): the `Import Backup` heading/button is visible (Plan 09 added it).
      * On `/admin/backup/import-preview`: `<h1>` contains `"Backup Import"` and `"Preview"`; the header card contains the original filename text; the schema pill contains `"Schema version 1 matches."`; `.card-grid > .card` count equals **24** (BackupSchema.getExportOrder() — D-03/D-21).
      * On `/admin/backup/import-confirm`: `<h1>` contains `"Backup Import"` and `"Confirm"`; the warning callout `.alert.alert-warning` is visible; `#acknowledged` is initially unchecked; the recap card mentions the original filename.
  </behavior>
  <action>
    Create `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` with the structural twin layout of `BackupExportE2ETest`. Package `org.ctc.e2e`. The class:

    - `class BackupImportE2ETest extends PlaywrightConfig` — package-private (sister test is package-private).
    - Add Spring field injection (PlaywrightConfig is `@SpringBootTest` so `@Autowired` resolves):
      * `@Autowired BackupArchiveService backupArchiveService;` — for runtime fixture generation per D-25.
      * `@Value("${app.backup.staging-dir}") Path stagingDir;` — used by the cancel + cookie-jar tests; declared here once for class-level reuse.
    - `@BeforeEach void setUp() { setupPage(); page.onDialog(d -> d.accept()); }` — globally accept any `confirm()` dialog (D-10 JS-extra). Register on the freshly-built `page` AFTER `setupPage()`.
    - `@AfterEach void tearDown() { teardownPage(); }`.

    For this task (Task 1), implement ONLY `givenPhase73ExportZip_whenAdminUploadsAndConfirms_thenLandsOnBackupWithStubFlash`:

    1. Use JUnit `@TempDir Path tempDir;` (instance-level — JUnit 5 supports `@TempDir` on a non-static field for per-test cleanup). Build `Path fixtureZip = tempDir.resolve("ctc-backup-test.zip");`. Stream a real export into it: `try (OutputStream out = Files.newOutputStream(fixtureZip)) { backupArchiveService.writeZip(out, Instant.now()); }`.
    2. Assert `Files.size(fixtureZip) > 0L` with AssertJ.as(...) rationale "Fixture ZIP must contain bytes (BackupArchiveService.writeZip output)".
    3. `page.navigate(url("/admin/backup"));`
    4. `assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/admin/backup$"));`
    5. `assertThat(page.locator("h1")).containsText("Backup");`
    6. `assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Import Backup"))).isVisible();`
    7. Upload: `page.locator("input[type='file'][name='file']").setInputFiles(fixtureZip);`
    8. Click the Import Backup submit button — use `getByRole(AriaRole.BUTTON, setName("Import Backup")).click()`.
    9. Assert preview page: `assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/admin/backup/import-preview$"));`. (Per CONTEXT D-08: the preview is rendered directly by the controller; the path matches the POST URL because Spring's `RequestMappingHandlerAdapter` returns the view without redirect.) `assertThat(page.locator("h1")).containsText("Preview");` and `.containsText("Backup Import");`.
    10. Schema-match pill: `assertThat(page.locator(".alert.alert-success").first()).containsText("Schema version 1 matches.");`.
    11. Header block: `assertThat(page.locator(".card").first()).containsText("File:");` and `.containsText("Size:");` and `.containsText("Uploads:");` and `.containsText("Total imported rows:");`.
    12. Card grid count: `assertThat(page.locator(".card-grid > .card")).hasCount(24);` — uses Playwright's `LocatorAssertions.hasCount` (AssertJ-style); imports from `com.microsoft.playwright.assertions.PlaywrightAssertions`.
    13. Capture the staging UUID for cross-task use (Task 4 reuses it via a helper — for Task 1 just verify it exists): `String stagingUuid = page.locator("input[name='stagingId']").getAttribute("value");` and assert `org.assertj.core.api.Assertions.assertThat(stagingUuid).as("Preview form must carry the staging UUID per D-18").isNotBlank();`. Also parse as UUID to fail loud on malformed values: `java.util.UUID.fromString(stagingUuid);`.
    14. Click `Proceed to Confirm` — `page.getByRole(AriaRole.BUTTON, setName("Proceed to Confirm")).click();`.
    15. Confirm page assertions: URL ends `/admin/backup/import-confirm`; `<h1>` contains `"Confirm"` and `"Backup Import"`; `.alert.alert-warning` visible; `#acknowledged` not checked: `assertThat(page.locator("#acknowledged")).not().isChecked();` (Playwright `LocatorAssertions.not().isChecked()`).
    16. Tick: `page.locator("#acknowledged").check();`.
    17. Click `Execute Import` — `page.getByRole(AriaRole.BUTTON, setName("Execute Import")).click();`. (The pre-registered dialog handler from `@BeforeEach` auto-accepts the JS `confirm()`.)
    18. Land assertions: `assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/admin/backup$"));` (exact `/admin/backup`, not `/import-execute` — Plan 08 redirects).
    19. Success Flash: `assertThat(page.locator(".alert.alert-success")).containsText("Validation succeeded. Import execution will be enabled in Phase 75.");` — the locked D-02#5 string verbatim.
    20. Negative assertions (defense-in-depth): `assertThat(page.locator(".alert.alert-error")).hasCount(0);` and `org.assertj.core.api.Assertions.assertThat(page.content()).as("No reject-path strings on success").doesNotContain("Schema version mismatch", "safety checks", "Upload too large");`.

    Conventions: Given-When-Then naming (CLAUDE.md), `// given` / `// when` / `// then` block comments per BDD style. AssertJ `.as("...")` rationale on every non-trivial assertion. NO `Thread.sleep` — use Playwright's auto-waiting assertions (`assertThat(...)` from `PlaywrightAssertions` waits up to the default timeout). NO hard-coded ports — use `url("/admin/backup")` from the base class.

    Imports to add (single import block at top of file):
    - `com.microsoft.playwright.Page;`
    - `com.microsoft.playwright.options.AriaRole;`
    - `static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;` (Playwright assertions — page/locator)
    - `org.assertj.core.api.Assertions;` (NOT static — used as `Assertions.assertThat(...)` to avoid collision with PlaywrightAssertions.assertThat)
    - `org.junit.jupiter.api.{Test, BeforeEach, AfterEach, io.TempDir};`
    - `org.springframework.beans.factory.annotation.{Autowired, Value};`
    - `org.ctc.backup.service.BackupArchiveService;`
    - `java.io.OutputStream; java.nio.file.{Files, Path}; java.time.Instant; java.util.UUID; java.util.regex.Pattern;`

    DO NOT add `@SpringBootTest` / `@ActiveProfiles` on the class — they are inherited from `PlaywrightConfig`. DO NOT make `playwright` / `browser` fields — also inherited.

    Hard guards:
    - Branch: `gsd/v1.10-platform-and-backup` (current per gitStatus). Do not switch branches, do not `git stash`, do not `git reset`.
    - Implement ONLY Task 1's `@Test` method in this task. Tasks 2/3/4 add their own methods in a single file edit each; if you find yourself wanting to write more than the one method here, stop and report `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest#givenPhase73ExportZip_whenAdminUploadsAndConfirms_thenLandsOnBackupWithStubFlash -DfailIfNoTests=false</automated>
  </verify>
  <done>
    The single `@Test` method passes under `-Pe2e`. The class compiles. `git diff --stat` shows exactly one new file: `src/test/java/org/ctc/e2e/BackupImportE2ETest.java`. No production file modified.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Add cancel-cleanup test + missing-checkbox validation test (two more @Test methods in the same file)</name>
  <files>src/test/java/org/ctc/e2e/BackupImportE2ETest.java</files>
  <read_first>
    - src/test/java/org/ctc/e2e/BackupImportE2ETest.java (the file produced by Task 1 — extend it; do NOT rewrite Task 1's method)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md D-06 (Cancel CTA — rendered as `<form method="post">` to `/admin/backup/import-cancel` with hidden `stagingId` per Plan 09 CSRF trade-off) and D-16 (reject-paths delete staging file synchronously)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/09-PLAN.md `## Notes` "Cancel as POST trade-off" + "HTML5 nested-form constraint" (sibling forms inside `<div class="actions">`)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-UI-SPEC.md §"`admin/backup-confirm.html`" + Plan 09 Task 3 acceptance — field-error rendered as `<span class="field-error" role="alert">` (class-only selector required)
  </read_first>
  <behavior>
    Test method `givenPreviewPage_whenAdminClicksCancel_thenStagingFileDeletedAndRedirects`:
    - Given: a preview page reached via the same upload sequence as Task 1.
    - When: capture the staging UUID from the hidden input, then click the Cancel submit button inside the cancel POST form. The form-level submit triggers a POST navigation to `/admin/backup/import-cancel` with the hidden `stagingId` body field, which Plan 08 routes to `BackupImportService.deleteStagingFile(UUID)` + Flash `successMessage = "Import canceled."`.
    - Then: a POST request to `/import-cancel` was observed (via `page.waitForRequest`); URL is `/admin/backup`; the staging file `upload-{uuid}.zip` no longer exists in `stagingDir`; no `.alert.alert-error` visible. The `.alert.alert-success` Flash `Import canceled.` is asserted as the visible signal.

    Test method `givenConfirmPage_whenAdminSubmitsWithoutTickingCheckbox_thenSeesFieldError`:
    - Given: the confirm page reached via upload + Proceed-to-Confirm.
    - When: WITHOUT ticking `#acknowledged`, click `Execute Import`. (The pre-registered dialog handler auto-accepts the JS `confirm()` if it fires; but with native HTML5 form pre-submit the `@AssertTrue` server-side check is the authoritative gate per D-10.)
    - Then: URL still ends with `/admin/backup/import-confirm`; the `.field-error` element (class-only selector — Plan 09 renders `<span class="field-error">`, tag-agnostic) is visible near the checkbox containing `"You must acknowledge the deletion warning to continue."`; `.alert.alert-success` NOT visible (no stub flash because server rejected with BindingResult error).
  </behavior>
  <action>
    Open the file from Task 1 and APPEND two new `@Test` methods after Task 1's method. Do not modify Task 1's method, imports list, fields, `@BeforeEach`/`@AfterEach`, or class header.

    Both tests share the same upload-to-preview-page setup as Task 1; extract a small private helper inside the class to avoid duplication:

    ```java
    private void uploadFixtureAndOpenPreview(Path tempDir) throws IOException {
        Path fixtureZip = tempDir.resolve("ctc-backup-test.zip");
        try (OutputStream out = Files.newOutputStream(fixtureZip)) {
            backupArchiveService.writeZip(out, Instant.now());
        }
        page.navigate(url("/admin/backup"));
        page.locator("input[type='file'][name='file']").setInputFiles(fixtureZip);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Import Backup")).click();
        assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-preview$"));
    }

    private String currentStagingUuid() {
        String value = page.locator("input[name='stagingId']").getAttribute("value");
        Assertions.assertThat(value).as("Preview/Confirm form must carry stagingId per D-18").isNotBlank();
        UUID.fromString(value);  // fail loud on malformed
        return value;
    }
    ```

    Refactor Task 1's method to also call `uploadFixtureAndOpenPreview(tempDir)` + `currentStagingUuid()` so the three tests share the same staging-page setup. (This is a minor refactor INSIDE the test class — production code untouched. If you prefer not to refactor Task 1's method, inline the helper bodies in Tasks 2/3 instead — both are acceptable; pick the cleaner option.)

    **Cancel-cleanup test (`givenPreviewPage_whenAdminClicksCancel_thenStagingFileDeletedAndRedirects`):**
    1. `@TempDir Path tempDir;` parameter on the method (JUnit 5 supports per-method @TempDir injection).
    2. `uploadFixtureAndOpenPreview(tempDir);`
    3. `String stagingUuid = currentStagingUuid();`
    4. `Path stagingFile = stagingDir.resolve("upload-" + stagingUuid + ".zip");`
    5. Pre-assert: `Assertions.assertThat(Files.exists(stagingFile)).as("Staging file must exist on disk between preview and cancel").isTrue();`.
    6. Pre-assert: the cancel form is the POST form Plan 09 renders. `assertThat(page.locator("form[action$='/import-cancel']")).hasCount(1);` and `Assertions.assertThat(page.locator("form[action$='/import-cancel']").getAttribute("method")).as("Cancel must be a POST form per Plan 08 + Plan 09 CSRF trade-off").isEqualToIgnoringCase("post");` and `Assertions.assertThat(page.locator("form[action$='/import-cancel'] input[name='stagingId']").getAttribute("value")).as("Cancel form must carry the same staging UUID as the Proceed form").isEqualTo(stagingUuid);`.
    7. Wrap the click in a `page.waitForRequest(...)` so the test pins the request method as POST (not GET): use the two-arg overload — `page.waitForRequest(req -> req.method().equals("POST") && req.url().endsWith("/import-cancel"), () -> page.locator("form[action$='/import-cancel'] button[type='submit']").click());`. The lambda click selector targets the Cancel submit button INSIDE the cancel form (precise, unambiguous — the form has exactly one button per Plan 09 Task 2 acceptance). The locator-by-button-name alternative `page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancel"))` also works because Plan 09 renders `<button type="submit">Cancel</button>` inside the form — pick whichever is clearer; both resolve to the same DOM node. If the locator-by-name approach yields multiple matches across the preview-page DOM (it should not — only one Cancel button on preview), fall back to the form-scoped selector above.
    8. `assertThat(page).hasURL(Pattern.compile(".*/admin/backup$"));`
    9. Post-assert: `Assertions.assertThat(Files.exists(stagingFile)).as("Staging file must be deleted synchronously by import-cancel per D-16 + Plan 08 deleteStagingFile(UUID)").isFalse();`
    10. Positive Flash: `assertThat(page.locator(".alert.alert-success")).containsText("Import canceled.");` (Plan 08 Flash — locked in `<interfaces>`).
    11. Negative: `assertThat(page.locator(".alert.alert-error")).hasCount(0);`.

    **Missing-checkbox test (`givenConfirmPage_whenAdminSubmitsWithoutTickingCheckbox_thenSeesFieldError`):**
    1. `@TempDir Path tempDir;` parameter.
    2. `uploadFixtureAndOpenPreview(tempDir);`
    3. Click Proceed: `page.getByRole(AriaRole.BUTTON, setName("Proceed to Confirm")).click();`.
    4. Assert confirm page reached: `assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-confirm$"));`.
    5. Pre-assert: `assertThat(page.locator("#acknowledged")).not().isChecked();`.
    6. Click Execute WITHOUT ticking: `page.getByRole(AriaRole.BUTTON, setName("Execute Import")).click();`.
    7. Assert still on confirm page: `assertThat(page).hasURL(Pattern.compile(".*/admin/backup/import-confirm$"));` (server returned the view, no redirect, per Plan 08's `if (result.hasErrors()) return "admin/backup-confirm";`).
    8. Assert field-error visible with locked copy using the CLASS-ONLY selector `.field-error` (tag-agnostic — Plan 09 renders the element as `<span class="field-error" role="alert">`, but the selector is class-only so the test survives a future tag swap to `<small>` or `<div>` without churn): `assertThat(page.locator(".field-error")).isVisible();` and `assertThat(page.locator(".field-error")).containsText("You must acknowledge the deletion warning to continue.");`.
    9. Negative: `assertThat(page.locator(".alert.alert-success")).hasCount(0);` — no stub flash because server rejected.

    Hard guards: same as Task 1 (branch, no stash/reset, only this task's methods).
  </action>
  <verify>
    <automated>./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest#givenPreviewPage_whenAdminClicksCancel_thenStagingFileDeletedAndRedirects+givenConfirmPage_whenAdminSubmitsWithoutTickingCheckbox_thenSeesFieldError -DfailIfNoTests=false</automated>
  </verify>
  <done>
    Both new test methods pass under `-Pe2e`. Task 1's method still passes (regression-free refactor of the shared setup helper). The cancel test pins the HTTP method as POST (via `page.waitForRequest`) and confirms the cancel form selector resolves to exactly one element with `method="post"` and the matching `stagingId`. The missing-checkbox test asserts on the class-only `.field-error` selector (tag-agnostic). Class still compiles. `git diff` shows only the test file modified.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: Add stateless cookie-jar test (SC#5 proof) — the fourth @Test method</name>
  <files>src/test/java/org/ctc/e2e/BackupImportE2ETest.java</files>
  <read_first>
    - src/test/java/org/ctc/e2e/BackupImportE2ETest.java (the file produced by Tasks 1+2 — extend it; do not rewrite earlier methods)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md D-18 (stateless: no @SessionAttributes, no in-memory cache — pure re-read from staging file by UUID) and D-08 (execute stub re-reads via UUID from form)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md (cookie-jar reset technique — search for "cookie jar" or "clearCookies")
  </read_first>
  <behavior>
    Test method `givenStagingUuid_whenCookieJarIsClearedBetweenPreviewAndExecute_thenPagesStillFunction`:
    - Given: a preview page is rendered via the standard upload sequence; the staging UUID is captured from the hidden input; a fresh `BrowserContext` for the second half of the test (modelling a different session entirely — strongest possible proof of statelessness).
    - When: `context.clearCookies()` wipes the original context's cookies; then a SECOND fresh BrowserContext is opened (zero-cookie state). A new Page in the new context POSTs to `/admin/backup/import-confirm` with the same `stagingId` via `APIRequestContext.post(...)` (Playwright's HTTP client — bypasses the browser's cookie state for the request). Then the confirm page (returned as HTML body) is navigated to in the new page to assert it renders.
    - Then: the confirm-page response (whether read via API call or navigated to) contains the warning callout, the `#acknowledged` checkbox, and the staging UUID hidden field with the SAME value — proving the server re-read the staged ZIP by UUID, not by HttpSession. The execute POST in the cookie-wiped fresh context returns 3xx with Location ending `/admin/backup`. The visible Flash banner is intentionally NOT asserted in this test (see "Pragmatic compromise" below) — Task 1's happy path already covers the Flash render.
  </behavior>
  <action>
    Open the file from Tasks 1+2 and APPEND the fourth `@Test` method. Do not modify earlier methods.

    Implementation strategy — TWO viable approaches; pick (a) for clarity unless the test infrastructure prefers (b):

    **(a) PREFERRED: spawn a second BrowserContext, copy-free (true session isolation).** The cleanest proof: after wiping cookies in the original context, ALSO open a brand-new context with zero cookies and re-submit the confirm form via the new context's `APIRequestContext`. If the confirm page renders identically in the new context, statelessness is proven beyond doubt.

    **(b) FALLBACK: single-context cookie-jar reset.** `context.clearCookies();` then re-navigate to a re-POST endpoint. Works if approach (a) hits a Spring-Security CSRF token issue (dev profile has CSRF disabled per Phase 73 Security pattern — but verify).

    Recommended implementation using approach (a):

    1. `@TempDir Path tempDir;` parameter.
    2. `uploadFixtureAndOpenPreview(tempDir);` — original context + page. **Pre-condition for the SC#5 proof:** the preview page must have rendered correctly BEFORE the cookie wipe — this establishes that the staging file exists on disk and is keyed by UUID. The `uploadFixtureAndOpenPreview` helper already asserts URL `/import-preview`; this is the "preview rendered with correct content before the cookie wipe" baseline.
    3. `String stagingUuid = currentStagingUuid();` — capture the UUID; this is the SOLE handle the test will use to reconstruct the confirm page after the wipe.
    4. Wipe cookies on the original context to prove the FIRST page doesn't need them either: `context.clearCookies();`. The preview page is already in the DOM at this point (rendered in step 2 before the wipe) — the test does NOT re-fetch it (no GET endpoint for preview); the staging UUID alone must suffice.
    5. Open a fresh, isolated context: `BrowserContext freshContext = browser.newContext();` and `Page freshPage = freshContext.newPage();`. Register the same dialog handler: `freshPage.onDialog(d -> d.accept());`. Use `try { ... } finally { freshContext.close(); }` to guarantee cleanup.
    6. In `freshContext`, navigate first to `/admin/backup` to obtain a CSRF token if dev profile happens to require one (Phase 73's CSRF posture: disabled for dev/local, enabled for prod/docker — dev profile means CSRF is NOT enforced, so this is a safety net): `freshPage.navigate(url("/admin/backup"));`. Then `freshContext.clearCookies();` — wipe again so the API call below operates against a truly empty cookie jar. This is the "wipe between preview and execute" moment per SC#5.
    7. POST to `/admin/backup/import-confirm` with the same staging UUID via Playwright's `APIRequestContext` to bypass form-render and directly request the confirm view:
       ```java
       APIResponse confirmResp = freshContext.request().post(
           url("/admin/backup/import-confirm"),
           RequestOptions.create()
               .setForm(FormData.create().set("stagingId", stagingUuid))
       );
       Assertions.assertThat(confirmResp.status())
           .as("Confirm page must render successfully in a session-wiped fresh context — proves stateless re-parse (SC#5)")
           .isEqualTo(200);
       String confirmBody = confirmResp.text();
       Assertions.assertThat(confirmBody)
           .as("Confirm page in fresh context must contain the same staging UUID — proves server reads by UUID, not session")
           .contains(stagingUuid)
           .contains("Backup Import")
           .contains("Confirm")
           .contains("I am an admin and I understand all operational data will be deleted.");
       ```
    8. Drive the execute stub via API in the same fresh, cookie-wiped context:
       ```java
       APIResponse executeResp = freshContext.request().post(
           url("/admin/backup/import-execute"),
           RequestOptions.create()
               .setForm(FormData.create().set("stagingId", stagingUuid).set("acknowledged", "true"))
       );
       Assertions.assertThat(executeResp.status())
           .as("Execute stub must redirect (302/303) when called from a session-wiped fresh context")
           .isBetween(300, 399);
       Assertions.assertThat(executeResp.headers().get("location"))
           .as("Execute stub must redirect to /admin/backup per D-08")
           .endsWith("/admin/backup");
       ```
    9. **Intentionally omitted: rendered-Flash assertion.** Spring's Flash uses an HttpSession-backed handover across the redirect. After the cookie wipe in step 6 there is no session cookie to carry the Flash key, so a follow-up GET in `freshContext` would NOT render the success banner — this is correct architectural behaviour (Flash is by design session-coupled, while the IMPORT staging file is UUID-coupled). The test must NOT assert on the rendered Flash here; the strong-and-narrow assertion is steps 7+8 (confirm-render 200 with matching UUID body, execute redirect 3xx Location `/admin/backup`). Task 1's happy path already proves the Flash renders in a normal session — this test proves the orthogonal axis (re-parse works without any session at all).

    **Pragmatic compromise (committed):** this test commits to the narrow, robust assertion path:
    - Preview page rendered correctly BEFORE the cookie wipe (step 2 — implicit in `uploadFixtureAndOpenPreview`).
    - After wiping cookies, the API POST to `/import-confirm` with only the staging UUID returns HTTP 200 with the confirm page body containing the same UUID and the locked checkbox label.
    - The API POST to `/import-execute` in the same wiped context returns 3xx with Location `/admin/backup`.
    - The visible `.alert.alert-success` Flash banner is NOT asserted in this test (deliberate — Flash is session-coupled by Spring design; Task 1 covers the rendered-Flash path in a normal session).

    10. Clean up the fresh context in `finally`. Original page/context tear down via the inherited `@AfterEach`.

    Add these imports to the file (extend the existing import block):
    - `com.microsoft.playwright.BrowserContext;` (already inherited but explicit local use)
    - `com.microsoft.playwright.APIResponse;`
    - `com.microsoft.playwright.options.RequestOptions;`
    - `com.microsoft.playwright.options.FormData;`

    Hard guards: same as previous tasks. If the dev profile DOES enforce CSRF (verify by reading `application-dev.yml` or `application.yml` for `csrf.disable` or by checking the existing Phase 73 `BackupControllerSecurityIT` dev-profile section), the test must first GET `/admin/backup`, parse the CSRF meta tag, and include `_csrf` in the form data. **Phase 73 security tests confirm dev disables CSRF, so this is the expected baseline — but verify by greping for `csrf` in `org/ctc/SecurityConfig.java` before running.** If CSRF is enforced on dev unexpectedly, STOP and report `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest#givenStagingUuid_whenCookieJarIsClearedBetweenPreviewAndExecute_thenPagesStillFunction -DfailIfNoTests=false</automated>
  </verify>
  <done>
    The fourth `@Test` method passes. All four methods pass together under `./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest`. The class compiles, JaCoCo line coverage at the test-class boundary remains irrelevant (test classes are excluded from coverage), and the overall Phase 74 coverage threshold (≥ 82% per CLAUDE.md) is unaffected. `git diff` shows only the one test file modified across Tasks 1+2+3.
  </done>
</task>

</tasks>

## Verification

**Must-haves all four E2E tests must satisfy together:**

1. `./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest` exits 0 with 4 tests run, 0 failures, 0 errors, 0 skipped.
2. The Surefire phase of the standard build (`./mvnw verify` without `-Pe2e`) does NOT pick up this class (excluded by Surefire's `**/e2e/**` exclude — Phase 73 confirmed pattern).
3. `BackupImportE2ETest.class` extends `PlaywrightConfig` and is package-private, matching `BackupExportE2ETest`'s shape.
4. The fixture ZIP is generated at runtime via `BackupArchiveService.writeZip` (no binary blob committed under `src/test/resources/`).
5. Each test uses `@TempDir Path tempDir;` for the fixture (per-test cleanup; no leaked tempfiles).
6. SC#1 proven: the happy-path test asserts the 24-card grid is visible with the locked schema-match string.
7. SC#5 proven: the cookie-jar test asserts the confirm-page re-renders identically in a freshly-spawned BrowserContext with cleared cookies, proving the server reads the staged ZIP by UUID rather than via HttpSession.
8. No `Thread.sleep`, no hard-coded port — the test uses Playwright's auto-waiting assertions and the inherited `url(String)` helper from `PlaywrightConfig`.
9. All Locked D-02 / UI-SPEC strings asserted character-for-character in `.containsText(...)` calls — no paraphrasing.
10. `git diff --stat` shows exactly one new file: `src/test/java/org/ctc/e2e/BackupImportE2ETest.java`. No production code touched, no other test files touched.

**Manual smoke (optional, not part of automation):** the executor MAY run `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` in a sidecar shell and `playwright-cli open http://localhost:9090/admin/backup` to visually verify the import form is rendered as expected by Plan 09. This is NOT a CI gate, just a sanity check per `feedback_playwright_cli.md`.

## Success Criteria

Phase 74 Plan 10 is complete when:

- [ ] `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` exists, compiles, and contains exactly 4 `@Test` methods.
- [ ] All 4 tests pass under `./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest`.
- [ ] All 4 tests use Given-When-Then naming + block comments.
- [ ] The class extends `PlaywrightConfig` (no `@SpringBootTest` / `@ActiveProfiles` repetition on the class — inherited).
- [ ] Spring fields are constructor-free, `@Autowired` / `@Value` annotated, declared once at the class top.
- [ ] No committed binary fixture ZIP; the test ZIP is generated at runtime via `BackupArchiveService.writeZip` (D-25).
- [ ] No `Thread.sleep`, no hard-coded ports, no string-concatenated log messages.
- [ ] No production-side files modified.
- [ ] Final `./mvnw verify` (no `-Pe2e`) still passes — the new test class is invisible to Surefire.
- [ ] Final `./mvnw verify -Pe2e` passes — all E2E tests pass together (no flake introduced by the new class).
- [ ] `git status` clean except for the one new test file.
- [ ] The cancel test pins the request method as POST (via `page.waitForRequest`) and uses a form-scoped button selector — matches Plan 08's `POST /admin/backup/import-cancel` and Plan 09's `<form method="post"><button>Cancel</button></form>` shape.
- [ ] The missing-checkbox test uses the class-only `.field-error` selector (tag-agnostic) — matches Plan 09's `<span class="field-error">` rendering and survives a future tag swap without churn.

## Notes

### Playwright lifecycle (inherited from PlaywrightConfig)

`PlaywrightConfig` owns the `Playwright` and `Browser` lifecycle via `@BeforeAll` / `@AfterAll`. Each test subclass owns the `BrowserContext` + `Page` lifecycle via `setupPage()` / `teardownPage()` in `@BeforeEach` / `@AfterEach`. Tests in this plan additionally register a per-page `page.onDialog(d -> d.accept())` handler in `@BeforeEach` (after `setupPage()`) to auto-accept the JS `confirm()` dialog from the Execute Import button (D-10).

### Cancel as POST (Plan 08 + Plan 09 reality)

Plan 08 defines `POST /admin/backup/import-cancel` (CSRF discipline forbids mutating GET endpoints). Plan 09 renders Cancel as a `<form method="post">` with a hidden `stagingId` input and a `<button type="submit">Cancel</button>`, placed as a SIBLING form inside `<div class="actions">` next to the Proceed/Execute form (HTML5 forbids nested forms — Plan 09 `## Notes`). Task 2's cancel-cleanup test therefore:
- selects the cancel button via the form-scoped selector `form[action$='/import-cancel'] button[type='submit']` (precise, unambiguous);
- pins the request method as POST via `page.waitForRequest(req -> req.method().equals("POST") && req.url().endsWith("/import-cancel"), ...)`;
- asserts the post-cancel Flash `Import canceled.` (Plan 08 `successMessage`).

### Field-error selector (class-only)

Plan 09 renders the inline validation error as `<span class="field-error" role="alert" th:errors="*{acknowledged}">` (the `<span>` tag was chosen over `<small>` to avoid the browser default 80%-font-size compounding — Plan 09 `## Notes`). Task 2's missing-checkbox test uses the class-only selector `.field-error` (NOT `small.field-error` or `span.field-error`) so the test:
- matches Plan 09's actual `<span>` rendering;
- remains resilient to a future tag swap (e.g. to `<small>` or `<div>`) without churn;
- aligns with the CSS rule `.form-group .field-error` (admin.css:318 — class-based, tag-agnostic).

### Cookie-jar trick (SC#5 proof)

The standard cookie-clear approach (`context.clearCookies()`) wipes only HTTP cookies, not other browser-storage layers like `localStorage`. Phase 74 server-side uses neither `@SessionAttributes` nor `HttpSession` for backup-import state per D-18, so HTTP cookies are the only persistence layer that could carry session state across the preview→execute transition. Clearing cookies is therefore the necessary AND sufficient proof.

For maximum rigor, the cookie-jar test in Task 3 spawns a SECOND BrowserContext (not just `clearCookies()` on the first) — this is the strongest possible isolation: a brand-new context shares nothing with the original, including its `User-Agent`/`Accept-Language`/`localStorage`. If the server can still re-render the confirm page in the new context using only the staging UUID, statelessness is proven beyond doubt.

**Trade-off committed in Task 3:** the rendered `.alert.alert-success` Flash banner is intentionally NOT asserted in Task 3 because Spring's Flash uses an HttpSession-backed handover across the redirect — after the cookie wipe there is no session cookie to carry the Flash key, which is correct architectural behaviour. Task 3's strong-and-narrow assertions are: (a) preview page rendered with correct content before the cookie wipe (proven via `uploadFixtureAndOpenPreview` URL/content check); (b) after the wipe, the API POST to `/import-confirm` returns HTTP 200 with the confirm page body containing the same staging UUID; (c) the API POST to `/import-execute` in the wiped context returns 3xx with Location `/admin/backup`. The Flash render is covered orthogonally by Task 1's happy path in a normal session.

### Failsafe binding

`pom.xml` `<profile id="e2e">` configures Failsafe with `<include>**/e2e/**/*Test.java</include>`. The standard Surefire binding excludes `**/e2e/**`. So:
- `./mvnw verify` runs Surefire (skips this class) + Failsafe with NO `-Pe2e` (skips this class — no include pattern matches).
- `./mvnw verify -Pe2e` runs Surefire (skips this class) + Failsafe with the `-Pe2e` include pattern (RUNS this class).

The `-Dit.test=BackupImportE2ETest` flag narrows Failsafe to this single class during local iteration — use it during executor iteration to keep feedback loops fast.

### Playwright Chromium install

Per CLAUDE.md "Commands": `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"` must be run once per dev environment. CI (.github/workflows) handles this automatically per the Phase 73 BackupExportE2ETest precedent — no per-test setup needed in this class.

### Screenshots on failure

Playwright in headless mode does NOT auto-capture screenshots by default. If the executor wants per-failure screenshot capture (recommended for diagnosing flakes in CI), add a JUnit 5 `TestWatcher` extension that on `testFailed` calls `page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(".screenshots/", testName + ".png")))` — per `feedback_screenshots_folder.md`, the project convention is to write to `.screenshots/`, not `target/playwright-screenshots/` (the planner-context wording was an approximation). This is OPTIONAL — the four tests as written should be deterministic enough not to need it.

### Coverage impact

Test classes are excluded from JaCoCo's instrumented set (Surefire-only coverage). Adding this E2E test does NOT change the production coverage percentage. The Phase 74 ≥82% threshold (CLAUDE.md) is unaffected by Plan 10.

<output>
After completion, create `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-10-SUMMARY.md` summarizing: 4 tests written, all green under `-Pe2e`, SC#1 + SC#5 proven, IMPORT-01..04 covered end-to-end. Include the full `./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest` console output as an appendix for downstream UAT reference.
</output>
</content>
</invoke>