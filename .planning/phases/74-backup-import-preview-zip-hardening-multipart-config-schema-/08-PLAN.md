---
id: "08"
title: "BackupController — 4 import endpoints + security/validation ITs"
phase: 74
plan: "08"
type: execute
wave: 3
depends_on: ["03", "05", "06"]
requirements: [IMPORT-01, IMPORT-02, IMPORT-04, SECU-04]
files_modified:
  - src/main/java/org/ctc/backup/BackupController.java
  - src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java
  - src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java
autonomous: true

must_haves:
  truths:
    - "`POST /admin/backup/import-preview` accepts a multipart `file` upload, stages it via `BackupImportService.stage(MultipartFile)`, adds the resulting `BackupImportPreview` as `model.addAttribute(\"preview\", ...)`, and returns view name `\"admin/backup-preview\"`."
    - "`POST /admin/backup/import-confirm` accepts `stagingId` UUID, re-parses via `BackupImportService.reparse(UUID)`, adds `preview` + a fresh `BackupImportConfirmForm` (with `stagingId` pre-set) and returns view name `\"admin/backup-confirm\"`."
    - "`POST /admin/backup/import-execute` is a Phase-74 STUB: `@Valid`-binds `BackupImportConfirmForm`, re-renders `\"admin/backup-confirm\"` on `BindingResult.hasErrors()`, otherwise re-parses via `reparse(UUID)` (D-09 defense-in-depth) and redirects `/admin/backup` with locked Flash `successMessage = \"Validation succeeded. Import execution will be enabled in Phase 75.\"`."
    - "`POST /admin/backup/import-cancel` deletes the staging file via `BackupImportService.deleteStagingFile(UUID)` and redirects to `/admin/backup` with locked Flash `successMessage = \"Import canceled.\"`."
    - "All four new endpoints catch `BackupArchiveException` and emit the D-02 Flash string for the routed `Reason` via private `mapReason(Reason)` helper; `IOException` from staging emits the D-02#3 safety-checks string."
    - "Phase-74 stub does NOT delete the staging file on successful execute — Phase 75 inherits this responsibility per D-08."
    - "All four endpoints inherit Phase-73 profile-conditional auth + CSRF from `SecurityConfig` (prod/docker) and `OpenSecurityConfig` (dev/local) — no endpoint-specific `requestMatchers` or `@PreAuthorize` is added."
    - "The existing Phase-73 `GET /admin/backup` and `POST /admin/backup/export` endpoints remain byte-identical (lines 51-109 of `BackupController.java` are unchanged)."
  artifacts:
    - path: "src/main/java/org/ctc/backup/BackupController.java"
      provides: "4 new `@PostMapping` methods + 1 private `mapReason(Reason)` helper appended to the existing class."
      contains: "@PostMapping(\"/import-preview\")"
    - path: "src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java"
      provides: "Two-`@Nested`-class profile matrix (`ProdProfileSecurityTest` + `DevProfileSecurityTest`) covering anonymous + CSRF + authenticated paths for all 4 endpoints."
      contains: "@Nested"
    - path: "src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java"
      provides: "Spring binding chain IT for `@AssertTrue Boolean acknowledged` field — proves the re-render-with-field-error path AND the success-redirect path."
      contains: "BackupImportConfirmFormValidationIT"
  key_links:
    - from: "src/main/java/org/ctc/backup/BackupController.java"
      to: "src/main/java/org/ctc/backup/service/BackupImportService.java"
      via: "constructor-injected `BackupImportService backupImportService` field; calls `stage(...)`, `reparse(...)`, `deleteStagingFile(...)`"
      pattern: "backupImportService\\.(stage|reparse|deleteStagingFile)"
    - from: "src/main/java/org/ctc/backup/BackupController.java"
      to: "src/main/java/org/ctc/backup/exception/BackupArchiveException.java"
      via: "`catch (BackupArchiveException ex)` + `mapReason(ex.reason())` routing to D-02 Flash strings"
      pattern: "catch \\(BackupArchiveException"
    - from: "src/main/java/org/ctc/backup/BackupController.java"
      to: "src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java"
      via: "`@Valid @ModelAttribute(\"backupImportConfirmForm\") BackupImportConfirmForm form` on `/import-execute`"
      pattern: "@Valid @ModelAttribute\\(\"backupImportConfirmForm\"\\)"
---

<objective>
Extend the existing Phase-73 `BackupController` (`src/main/java/org/ctc/backup/BackupController.java` lines 51-109 — `GET /admin/backup` and `POST /admin/backup/export` are byte-identical-preserved) with the four import endpoints locked by D-22:

1. `POST /admin/backup/import-preview` — multipart upload entry point. Delegates to `BackupImportService.stage(MultipartFile)`, renders `admin/backup-preview` on success, redirects to `/admin/backup` with a D-02-routed Flash on any `BackupArchiveException` or staging `IOException`.
2. `POST /admin/backup/import-confirm` — preview → confirm page transition. Re-parses the staged ZIP via `BackupImportService.reparse(UUID)` (stateless per D-18), renders `admin/backup-confirm` with a fresh `BackupImportConfirmForm` (stagingId pre-filled).
3. `POST /admin/backup/import-execute` — Phase-74 STUB per D-08. `@Valid`-binds `BackupImportConfirmForm`; on `BindingResult.hasErrors()` re-renders the confirm page with field errors; otherwise re-runs the validation chain via `reparse(UUID)` (D-09 defense-in-depth) and redirects to `/admin/backup` with locked Flash `Validation succeeded. Import execution will be enabled in Phase 75.` (D-02#5). The staging file is intentionally NOT deleted — Phase 75 inherits it.
4. `POST /admin/backup/import-cancel` — abandon flow + synchronous staging-file cleanup. Calls `BackupImportService.deleteStagingFile(UUID)`, redirects `/admin/backup` with `successMessage = "Import canceled."`.

A private `mapReason(BackupArchiveException.Reason)` helper routes every reject reason to one of the two locked D-02 Flash strings:
- `Reason.SCHEMA_MISMATCH` → the templated D-02#2 string with `{actual}` / `{current}` filled from `BackupArchiveException.getMessage()` substitution (Plan 02 ships the Reason; Plan 05 throws the exception with the formatted message — Plan 08 just routes).
- All other reject reasons (`PATH_TRAVERSAL`, `ENTRY_TOO_LARGE`, `TOTAL_TOO_LARGE`, `TOO_MANY_ENTRIES`, `MANIFEST_MISSING`, `MANIFEST_INVALID`, `NOT_A_ZIP`) → D-02#3 generic safety-checks string.

CSRF + auth are inherited (no endpoint-specific annotations) from the existing Phase-73 wiring: `SecurityConfig` enforces authenticated + CSRF on `/admin/**` for `prod`/`docker`; `OpenSecurityConfig` permits-all + disables CSRF for `dev`/`local`. Plan 73-04 (`BackupControllerSecurityIT`) already proved this for `POST /admin/backup/export`; this plan adds the matrix for the 4 new endpoints.

Two new ITs ship in this plan:
- `BackupImportControllerSecurityIT` — mirror of `BackupControllerSecurityIT`'s two-`@Nested` profile matrix; covers all 4 endpoints under prod (401 anonymous, 403 anonymous-no-CSRF, 403 authenticated-no-CSRF, 200/302 authenticated-with-CSRF) and dev (200/302 anonymous, CSRF disabled).
- `BackupImportConfirmFormValidationIT` — Spring binding chain test for `BackupImportConfirmForm`'s `@AssertTrue Boolean acknowledged` field; proves the re-render-with-field-error path (acknowledged=false AND acknowledged-missing) AND the success-redirect path (acknowledged=true → D-02#5 Flash).

Purpose: deliver IMPORT-01 (preview endpoint + staging path consumers wired), IMPORT-02 (schema-version gate routed to D-02#2 Flash), IMPORT-04 (confirm dialog with mandatory checkbox), and SECU-04 (the `MaxUploadSizeExceededException` Flash path is owned by Plan 06's `BackupUploadExceptionHandler`; Plan 08 consumes the same `errorMessage` Flash key in the `/admin/backup` landing — the contract is two-way).

Output: 1 extended controller (3 new task changes — DI field add + 4 endpoints + helper) + 2 new IT classes.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-UI-SPEC.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md
@.planning/REQUIREMENTS.md

<interfaces>
<!-- Key types and contracts the executor will consume. Extracted from prior-plan outputs + existing code. Executor should use these directly — no codebase exploration needed. -->

From `src/main/java/org/ctc/backup/BackupController.java` (existing — Phase 73 export endpoint; THIS PLAN EXTENDS, does NOT replace):
- Class is `@Slf4j @Controller @RequestMapping("/admin/backup") @RequiredArgsConstructor`.
- Existing field: `private final BackupArchiveService backupArchiveService;` (Lombok constructor picks up new `final` fields automatically — just add `private final BackupImportService backupImportService;`).
- Existing methods (PRESERVE byte-identical): `showForm(Model)` at line 62-66 returns `"admin/backup"`; `export()` at line 68-104 returns `ResponseEntity<StreamingResponseBody>`; `isoSafeFilename(Instant)` private static at line 106-108.

From Plan 05 (`BackupImportService` — `src/main/java/org/ctc/backup/service/BackupImportService.java`):
- `BackupImportPreview stage(MultipartFile file) throws IOException` — saves staging file, parses manifest, hardening checks. Throws `BackupArchiveException` for the schema-mismatch + ZIP-safety reject paths; throws `IOException` for filesystem failures during staging.
- `BackupImportPreview reparse(UUID stagingId)` — re-runs validation against the on-disk staging file. Same exception contract. Used by `import-confirm` (preview → confirm page transition) AND by `import-execute` (D-09 defense-in-depth re-validation).
- `void deleteStagingFile(UUID stagingId)` — used by `/import-cancel` (D-16-style synchronous delete). Idempotent (missing file → no-op).
- The service is `@Service @Transactional(readOnly = true)`; the controller is NOT `@Transactional` (per CONTEXT D-22 closing note "`@Transactional` is NOT on the controller").

From Plan 03 (`BackupImportConfirmForm` — `src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java`):
- `@Getter @Setter @NoArgsConstructor public class BackupImportConfirmForm` with two fields:
  - `@NotNull private UUID stagingId;` — hidden-input bound from the confirm page form.
  - `@NotNull @AssertTrue(message = "You must acknowledge the deletion warning to continue.") private Boolean acknowledged;` — checkbox-bound. **MUST be `Boolean` (boxed), not `boolean`**, so `@NotNull` fires on missing field.

From Plan 03 (`BackupImportPreview` — `src/main/java/org/ctc/backup/dto/BackupImportPreview.java`):
- `public record BackupImportPreview(UUID stagingId, String originalFilename, long fileSizeBytes, int schemaVersion, int currentSchemaVersion, boolean schemaMatches, List<EntityRowCount> entityCounts, int uploadFileCount, long totalImportedRows)`.
- Templates bind to this record; no entity lookups in Thymeleaf (D-21).

From Plan 02 (`BackupArchiveException` — `src/main/java/org/ctc/backup/exception/BackupArchiveException.java`):
- `public class BackupArchiveException extends RuntimeException` with `public Reason reason()` accessor.
- `Reason` enum (Plan 02 final order, 8 values):
  `PATH_TRAVERSAL, ENTRY_TOO_LARGE, TOTAL_TOO_LARGE, TOO_MANY_ENTRIES, MANIFEST_MISSING, MANIFEST_INVALID, SCHEMA_MISMATCH, NOT_A_ZIP`.
- Two constructors: `(Reason, String)` and `(Reason, String, Throwable)`.
- **Coordination note for Plan 05:** for `SCHEMA_MISMATCH`, Plan 05 MUST throw with the message already formatted per D-02#2: `"Schema version mismatch: backup=" + actual + ", expected=" + current + ". Cannot import."` — Plan 08 routes `getMessage()` verbatim into the Flash. Plan 05's SCHEMA_MISMATCH throw site is the formatter; Plan 02's two-arg constructor is sufficient (no extra `int actual, int expected` carrier fields needed because the message is pre-formatted at throw time).

From Plan 06 (`BackupUploadExceptionHandler` — `src/main/java/org/ctc/backup/exception/BackupUploadExceptionHandler.java`):
- `@ControllerAdvice` class with one `@ExceptionHandler(MaxUploadSizeExceededException.class)` method returning `"redirect:/admin/backup"` with `errorMessage = "Upload too large — maximum is 100 MB."` (D-02#1). Plan 08 does NOT need to handle this — Spring routes the exception to Plan 06's advice BEFORE the multipart binding ever reaches `import-preview`.

From `src/main/java/org/ctc/admin/controller/CarController.java:50-58` (Flash-redirect analog pattern — copy this shape into all 4 endpoints):
```
@PostMapping("/{id}/image")
public String uploadImage(@PathVariable UUID id,
                          @RequestParam("image") MultipartFile image,
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

From `src/main/java/org/ctc/admin/controller/MatchdayController.java:94-114` (`@Valid` + `BindingResult` re-render-form analog):
```
@PostMapping("/save")
public String save(@Valid @ModelAttribute("form") MatchdayForm form,
                   BindingResult result,
                   RedirectAttributes redirectAttributes,
                   Model model) {
    if (result.hasErrors()) {
        // ...populate model...
        return "admin/matchday-form";
    }
    // success: addFlashAttribute("successMessage", ...) + redirect
}
```

From `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java` (existing Phase 73 — the structural analog for `BackupImportControllerSecurityIT`):
- Two `@Nested` classes inside an outer non-public test class:
  - `ProdProfileSecurityTest` — `@Nested @SpringBootTest(properties = {...H2 isolated bksectest DB...}) @AutoConfigureMockMvc @ActiveProfiles("prod")` — 4 tests per endpoint shape: `givenAnonymous_whenPost_thenUnauthorized` (with CSRF, isolates auth from CSRF), `givenAnonymousNoCsrf_whenPost_thenForbidden`, `givenAuthenticatedNoCsrf_whenPost_thenForbidden` (`@WithMockUser`), `givenAuthenticatedWithCsrf_whenPost_thenOk` (`@WithMockUser` + `.with(csrf())`).
  - `DevProfileSecurityTest` — `@Nested @SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")` — 1 test per endpoint shape: `givenAnonymous_whenPost_thenOk`.
- Imports needed: `SecurityMockMvcRequestPostProcessors.{anonymous, csrf}`, `MockMvcRequestBuilders.{get, post, multipart}`, `MockMvcResultMatchers.{status, header, request, view, redirectedUrl, flash, model}`.

REQUIREMENTS.md acceptance criteria for this plan's requirement IDs (verbatim, German strings overridden by D-01/D-02 English locks):
- **IMPORT-01** (L41): `POST /admin/backup/import-preview` multipart stages ZIP under `data/{profile}/backup-staging/upload-{uuid}.zip`, renders preview with per-table counts. Plan 08 owns the endpoint; Plan 05 owns the service; Plan 09 owns the template.
- **IMPORT-02** (L42): Schema-version check BEFORE any DB write; on mismatch HTTP 400 + Flash redirect; DB unchanged. Plan 08 contract: the Flash key is `errorMessage`, the value is the D-02#2 string (already formatted by Plan 05 at throw time), the redirect target is `/admin/backup`. (HTTP 400 is loose interpretation — Spring's redirect-with-Flash returns HTTP 302; the IMPORT-02 intent is "the request is rejected before any write" which is satisfied; CONTEXT D-09 confirms the rejection path is "Flash redirect to `/admin/backup`".)
- **IMPORT-04** (L44): `POST /admin/backup/import-execute` renders confirm dialog with mandatory checkbox; server-side `@AssertTrue` enforcement (D-10). Plan 08 wires the `@Valid`-binding; the JS-`confirm` dialog is template-side (Plan 09).
- **SECU-04** (L55): `MaxUploadSizeExceededException` mapped to readable Flash. Plan 06 owns the `@ControllerAdvice`; Plan 08 inherits the `errorMessage` Flash on `/admin/backup`. No code change in this plan for SECU-04 — the requirement is `requirements`-listed because the controller is the consumer of the contract Plan 06 provides.

</interfaces>
</context>

<tasks>

<task type="auto" id="74-08-01">
  <name>Task 1: Extend `BackupController` with 4 import endpoints + `mapReason(Reason)` helper</name>
  <files>
    src/main/java/org/ctc/backup/BackupController.java
  </files>
  <read_first>
    - src/main/java/org/ctc/backup/BackupController.java (the full existing file lines 1-109 — Phase 73 export endpoint; the new import endpoints append below, NEVER modify lines 1-109).
    - src/main/java/org/ctc/admin/controller/CarController.java:50-58 — Flash-redirect-after-multipart pattern. Copy the `try { ... addFlashAttribute("successMessage", ...) } catch { addFlashAttribute("errorMessage", ...) } return "redirect:..."` shape.
    - src/main/java/org/ctc/admin/controller/CarController.java:62-77 — `@Valid` + `BindingResult` save-then-redirect pattern with `Model` model population on error.
    - src/main/java/org/ctc/admin/controller/MatchdayController.java:94-114 — `@Valid @ModelAttribute("form") + BindingResult result + RedirectAttributes redirectAttributes + Model model` parameter order; on `result.hasErrors()` re-render with model attributes, otherwise redirect with `successMessage` Flash.
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md §D-08 (execute stub Flash), §D-09 (defense-in-depth re-validation at execute), §D-10 (server-side `@AssertTrue` is authoritative), §D-18 (stateless — no `@SessionAttributes`), §D-22 (4 endpoints + CSRF + auth identical to Phase 73).
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md §"Final English strings (locked, terse style)" D-02 lines 21-31 — the 5 locked Flash strings (Plan 08 emits #3 and #5; Plan 06 emits #1; Plan 05 formats #2 at throw site; D-02#4 is the checkbox label rendered in the template by Plan 09).
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md §"`org.ctc.backup.BackupController` (controller, request-response + multipart file-I/O)" — the multipart `RedirectAttributes` + `Model` parameter pattern, plus the `@Valid + BindingResult` re-render snippet.
  </read_first>
  <action>
    Open `src/main/java/org/ctc/backup/BackupController.java` and apply ONLY additive changes. The Phase-73 lines (1-109) MUST remain byte-identical except for new import statements (which the executor inserts in the existing alphabetized import block) and the new `private final BackupImportService backupImportService;` field below the existing `private final BackupArchiveService backupArchiveService;` field. Lombok's `@RequiredArgsConstructor` picks up the new `final` field automatically — no manual constructor needed.

    Add imports (alphabetized, grouped with existing imports — Java first, then third-party):
    - `java.io.IOException` (already present — verify line 3; no duplicate).
    - `java.util.UUID` (NEW).
    - `org.ctc.backup.dto.BackupImportConfirmForm` (NEW).
    - `org.ctc.backup.dto.BackupImportPreview` (NEW).
    - `org.ctc.backup.exception.BackupArchiveException` (NEW).
    - `org.ctc.backup.exception.BackupArchiveException.Reason` (NEW — static-import optional; if not static-imported, use `BackupArchiveException.Reason.SCHEMA_MISMATCH` qualified form).
    - `org.ctc.backup.service.BackupImportService` (NEW).
    - `jakarta.validation.Valid` (NEW).
    - `org.springframework.validation.BindingResult` (NEW).
    - `org.springframework.web.bind.annotation.ModelAttribute` (NEW).
    - `org.springframework.web.bind.annotation.RequestParam` (NEW).
    - `org.springframework.web.multipart.MultipartFile` (NEW).
    - `org.springframework.web.servlet.mvc.support.RedirectAttributes` (NEW).

    Add the new field directly below the existing `private final BackupArchiveService backupArchiveService;` declaration (line 60 in current file):

    `private final BackupImportService backupImportService;`

    Append the four new endpoints AFTER the existing `export()` method (after line 104, before the private `isoSafeFilename` helper at line 106). Insert in this exact order: `importPreview` → `importConfirm` → `importExecute` → `importCancel` → (then the existing `isoSafeFilename` stays at the bottom unchanged). Then append the private `mapReason(Reason)` helper as the very last method in the class, below `isoSafeFilename`.

    ### Endpoint 1: `POST /admin/backup/import-preview`

    Method signature:

    `public String importPreview(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes, Model model)`

    Body specification (no fenced code in `<action>` per planner rules — describe the body in directive prose):

    The method MUST be wrapped in a single `try { ... } catch (BackupArchiveException ex) { ... } catch (IOException ex) { ... }` block. On the happy path, call `BackupImportPreview preview = backupImportService.stage(file);` and on success add THREE model attributes in order: (a) `model.addAttribute("title", "Backup");` so the sidebar's `Backup` link auto-highlights via the existing `admin/layout.html` `title.contains('Backup')` predicate; (b) `model.addAttribute("preview", preview);` for the template binding; (c) return the literal view name string `"admin/backup-preview"`.

    On `BackupArchiveException ex`: log via `log.warn("Backup import-preview rejected: reason={}, msg={}", ex.reason(), ex.getMessage());`, then call `redirectAttributes.addFlashAttribute("errorMessage", mapReason(ex));` (the `mapReason` helper takes the full exception so it can use `ex.getMessage()` for the SCHEMA_MISMATCH case — see helper spec below), return `"redirect:/admin/backup"`.

    On `IOException ex`: log via `log.error("Backup import-preview I/O failure", ex);` and emit the generic D-02#3 string verbatim: `redirectAttributes.addFlashAttribute("errorMessage", "Backup archive failed safety checks (size or path) and was rejected.");` and return `"redirect:/admin/backup"`. Rationale: a true filesystem `IOException` during staging (disk full, permission denied, partially written upload) is an external-attacker-indistinguishable signal — emit the same safety-checks Flash to avoid leaking server-internal state via the Flash text.

    ### Endpoint 2: `POST /admin/backup/import-confirm`

    Method signature:

    `public String importConfirm(@RequestParam("stagingId") UUID stagingId, RedirectAttributes redirectAttributes, Model model)`

    Body specification: wrap in a single `try { ... } catch (BackupArchiveException ex) { ... }` block. On success, call `BackupImportPreview preview = backupImportService.reparse(stagingId);` (stateless re-read per D-18), then construct `BackupImportConfirmForm form = new BackupImportConfirmForm(); form.setStagingId(stagingId);` (pre-fill the hidden-input UUID so the template's `th:object="${backupImportConfirmForm}"` + `th:field="*{stagingId}"` round-trips correctly). Add THREE model attributes: (a) `model.addAttribute("title", "Backup");`, (b) `model.addAttribute("preview", preview);`, (c) `model.addAttribute("backupImportConfirmForm", form);`. Return the literal view name `"admin/backup-confirm"`.

    On `BackupArchiveException ex`: identical to endpoint 1 — `log.warn("Backup import-confirm rejected: reason={}, msg={}", ex.reason(), ex.getMessage());`, `redirectAttributes.addFlashAttribute("errorMessage", mapReason(ex));`, return `"redirect:/admin/backup"`. **Why no `IOException` catch:** `reparse(UUID)` operates on an already-staged file; its only failure modes are the typed `BackupArchiveException` branches (file missing → `MANIFEST_MISSING`, malformed → `MANIFEST_INVALID`, schema drift → `SCHEMA_MISMATCH`, etc.). Plan 05's signature does not declare `throws IOException` on `reparse`; if Plan 05 changes this, executor adjusts.

    ### Endpoint 3: `POST /admin/backup/import-execute` (STUB — D-08)

    Method signature:

    `public String importExecute(@Valid @ModelAttribute("backupImportConfirmForm") BackupImportConfirmForm form, BindingResult bindingResult, RedirectAttributes redirectAttributes, Model model)`

    **Parameter order is load-bearing:** Spring requires `BindingResult` to IMMEDIATELY follow the `@Valid`-annotated model attribute — any other parameter between them breaks the binding and `BindingResult.hasErrors()` returns `false` even on field-validation failures. Verified by `MatchdayController.java:94-97` analog.

    Body specification, branching on `bindingResult.hasErrors()`:

    **Validation failure branch** (`bindingResult.hasErrors()` returns `true`): re-render the confirm page with the field-error annotations Spring auto-injects via the BindingResult. Re-populate the `preview` model attribute so the page renders the header block + recap line correctly. Wrap in `try { BackupImportPreview preview = backupImportService.reparse(form.getStagingId()); model.addAttribute("preview", preview); model.addAttribute("title", "Backup"); return "admin/backup-confirm"; } catch (BackupArchiveException ex) { log.warn("Backup import-execute reparse-on-error failed: reason={}", ex.reason()); redirectAttributes.addFlashAttribute("errorMessage", mapReason(ex)); return "redirect:/admin/backup"; }`. Rationale: if validation fails AND the staging file vanished/was tampered with between confirm-page-render and execute-submit, the only honest UX is to redirect with the routed Flash rather than render a half-populated confirm page.

    **Validation success branch** (`bindingResult.hasErrors()` returns `false`): wrap in `try { ... } catch (BackupArchiveException ex) { ... }`. Inside try, call `backupImportService.reparse(form.getStagingId());` (D-09 defense-in-depth — the schema-version gate re-runs even though preview-time already passed; result is discarded — we only care that no exception is thrown). Then `redirectAttributes.addFlashAttribute("successMessage", "Validation succeeded. Import execution will be enabled in Phase 75.");` (D-02#5, locked verbatim). Return `"redirect:/admin/backup"`. On `BackupArchiveException ex`: `log.warn("Backup import-execute defense-in-depth re-validation failed: reason={}", ex.reason());`, `redirectAttributes.addFlashAttribute("errorMessage", mapReason(ex));`, return `"redirect:/admin/backup"`.

    **The staging file MUST NOT be deleted in this Phase-74 stub.** Per D-08 + CONTEXT specifics: "The execute-stub does NOT call `deleteStagingFile` — Phase 75 will (D-08). The staging file stays on disk after a successful Phase-74 execute." The executor MUST NOT add a `deleteStagingFile` call on the success path; this is enforced by the acceptance-criteria grep (see below).

    ### Endpoint 4: `POST /admin/backup/import-cancel`

    Method signature:

    `public String importCancel(@RequestParam("stagingId") UUID stagingId, RedirectAttributes redirectAttributes)`

    Body specification: no try/catch — `deleteStagingFile(UUID)` is idempotent per Plan 05 contract (missing file → no-op, no exception). Single statement sequence: `backupImportService.deleteStagingFile(stagingId);` then `redirectAttributes.addFlashAttribute("successMessage", "Import canceled.");` then `return "redirect:/admin/backup";`. Log via `log.info("Backup import canceled: stagingId={}", stagingId);` BEFORE the delete (so even if the JVM crashes between log + delete, the audit trail captures the user intent).

    The "Import canceled." Flash text is NOT one of the locked D-02 strings (D-02 covers reject/success paths; Cancel is a neutral abandonment). Per D-01 (all UI text English) and the existing CTC convention of terse success messages (`"Car deleted"`, `"Image updated"` in `CarController`), `"Import canceled."` is the planner-discretion English string for this neutral action. It is rendered via the existing `admin/layout.html` `successMessage` Flash slot.

    ### Private helper: `mapReason(BackupArchiveException ex)`

    Place this method as the last method in the class, after `isoSafeFilename`. Signature:

    `private static String mapReason(BackupArchiveException ex)`

    Behaviour: switch on `ex.reason()`:
    - `case SCHEMA_MISMATCH` → return `ex.getMessage()` directly. Plan 05 formats the message at throw time per D-02#2: `"Schema version mismatch: backup=" + actual + ", expected=" + current + ". Cannot import."`. The controller does NOT re-format. **Defensive fallback:** if `ex.getMessage()` is null or blank, return the generic D-02#3 string instead (`"Backup archive failed safety checks (size or path) and was rejected."`) — this never happens in production because Plan 05 always supplies a message, but the defensive branch prevents a `null` from reaching the template's `th:text` (which renders `null` as the literal string "null").
    - All other cases (`PATH_TRAVERSAL`, `ENTRY_TOO_LARGE`, `TOTAL_TOO_LARGE`, `TOO_MANY_ENTRIES`, `MANIFEST_MISSING`, `MANIFEST_INVALID`, `NOT_A_ZIP`) → return the D-02#3 literal: `"Backup archive failed safety checks (size or path) and was rejected."`. Per D-22 closing note: "`MANIFEST_MISSING` / `MANIFEST_INVALID` → folded into the same Flash; SCHEMA_MISMATCH is the only specially-formatted one."

    Use a Java 25 switch expression (`return switch (ex.reason()) { case SCHEMA_MISMATCH -> ...; case PATH_TRAVERSAL, ENTRY_TOO_LARGE, TOTAL_TOO_LARGE, TOO_MANY_ENTRIES, MANIFEST_MISSING, MANIFEST_INVALID, NOT_A_ZIP -> ...; };`) — exhaustive over the enum so the compiler enforces the 8-case coverage. If `BackupArchiveException.Reason` gains a 9th value in a future plan, this switch becomes a compile error — which is the intent (the new value MUST get an explicit Flash routing decision).

    ### Class-level structural guarantees

    - The existing `@PreAuthorize` is **absent** on `BackupController` (Phase 73 verified — auth is inherited from `SecurityConfig` `anyRequest().authenticated()` on `/admin/**`). Do NOT add `@PreAuthorize` to any new endpoint — the Phase-73 inherited model is the locked contract per D-22.
    - CSRF is inherited from Spring Security's global config — do NOT add `.csrf().disable()` or per-endpoint annotations. Verified by Phase-73 `BackupControllerSecurityIT` against the export endpoint; the 4 new endpoints inherit the same behaviour.
    - The class is NOT `@Transactional` (per CONTEXT D-22 closing note); the read-only transaction lives on `BackupImportService` itself (Plan 05).
    - Lombok `@RequiredArgsConstructor` produces a 2-arg constructor `BackupController(BackupArchiveService, BackupImportService)` automatically. Do NOT write the constructor manually (the explicit-constructor form is only needed when constructor-parameter-level annotations like `@Qualifier` are required — none are needed here).
  </action>
  <verify>
    <automated>./mvnw -q -Dtest='BackupControllerIT,BackupControllerSecurityIT' test</automated>
  </verify>
  <acceptance_criteria>
    - File `src/main/java/org/ctc/backup/BackupController.java` exists, compiles, and is annotated `@Slf4j @Controller @RequestMapping("/admin/backup") @RequiredArgsConstructor` (verify via `grep -nE '^@(Slf4j|Controller|RequestMapping|RequiredArgsConstructor)' src/main/java/org/ctc/backup/BackupController.java` returns exactly 4 lines).
    - Existing Phase-73 lines preserved: `grep -cE '"admin/backup"|"/export"|StreamingResponseBody' src/main/java/org/ctc/backup/BackupController.java` returns at least 3 (the GET view name, the export `@PostMapping`, the streaming type).
    - Exactly 4 new `@PostMapping` annotations exist for the import endpoints — verify via `grep -cE '@PostMapping\("/(import-preview|import-confirm|import-execute|import-cancel)"\)' src/main/java/org/ctc/backup/BackupController.java` returns `4` (one per endpoint URI).
    - The execute-stub does NOT delete the staging file on the success branch: `grep -c 'deleteStagingFile' src/main/java/org/ctc/backup/BackupController.java` returns exactly `1` (the lone occurrence is inside `importCancel`). If this grep returns `2` or more, the executor mistakenly wired the Phase-75 delete behaviour into Phase 74 — fix.
    - The D-02#5 locked string appears exactly once: `grep -c 'Validation succeeded. Import execution will be enabled in Phase 75.' src/main/java/org/ctc/backup/BackupController.java` returns `1`.
    - The D-02#3 locked string appears at least once (inside `mapReason` AND the `IOException` catch on `importPreview` — but the executor MAY extract the string to a `private static final String` constant to satisfy DRY, in which case the grep counts the constant declaration plus any inline reference): `grep -c 'Backup archive failed safety checks (size or path) and was rejected.' src/main/java/org/ctc/backup/BackupController.java` returns at least `1`.
    - "Import canceled." appears exactly once: `grep -c '"Import canceled\."' src/main/java/org/ctc/backup/BackupController.java` returns `1`.
    - No `@PreAuthorize`, no `@Secured`, no `.csrf` mutation: `grep -cE '@PreAuthorize|@Secured|csrf\(\)|requestMatchers' src/main/java/org/ctc/backup/BackupController.java | grep -v '^#'` returns `0` (filter comment lines).
    - The `mapReason` switch is exhaustive — compiles without an `error: a switch expression should cover all possible input values` (Java 25 exhaustiveness check); enforce by `./mvnw -q -DskipTests compile` exiting `0`.
    - Phase-73 `BackupControllerIT` and `BackupControllerSecurityIT` still pass (they exercise the byte-identical-preserved Phase-73 endpoints; if either fails, the executor mutated existing lines — revert).
  </acceptance_criteria>
  <done>
    `./mvnw -q compile` exits `0`; `./mvnw -q -Dtest='BackupControllerIT,BackupControllerSecurityIT' test` passes (Phase-73 regression intact); 4 new `@PostMapping` annotations present; `deleteStagingFile` appears exactly once (inside `importCancel`).
  </done>
</task>

<task type="auto" id="74-08-02">
  <name>Task 2: `BackupImportControllerSecurityIT` — profile-matrix anonymous + CSRF + auth for all 4 endpoints</name>
  <files>
    src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java
  </files>
  <read_first>
    - src/test/java/org/ctc/backup/BackupControllerSecurityIT.java (the full Phase-73 file — copy the two-`@Nested`-class structure verbatim, swap URIs).
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md §"`BackupImportControllerSecurityIT`" — analog instructions.
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md §D-22 (CSRF + auth identical to Phase 73).
    - src/main/java/org/ctc/backup/BackupController.java (just edited in Task 1) — verify endpoint URIs match the test paths.
  </read_first>
  <action>
    Create `src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java` in package `org.ctc.backup` (non-public outer class, two public-in-package `@Nested` inner classes — mirrors `BackupControllerSecurityIT` exactly).

    Outer class declaration: `class BackupImportControllerSecurityIT { ... }` (package-private, no `public`). Add a class-level Javadoc citing CONTEXT §D-22 + the analog citation `extends BackupControllerSecurityIT pattern` (Phase 73-04).

    Imports (alphabetized; static imports last):
    - `java.nio.file.Files` (for the multipart-body byte array in the prod-CSRF-success test).
    - `java.util.UUID` (for synthetic `stagingId` values in tests).
    - `org.hamcrest.Matchers` (for substring assertions if needed; optional).
    - `org.junit.jupiter.api.Nested`, `org.junit.jupiter.api.Test`.
    - `org.springframework.beans.factory.annotation.Autowired`.
    - `org.springframework.boot.test.context.SpringBootTest`.
    - `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
    - `org.springframework.mock.web.MockMultipartFile`.
    - `org.springframework.security.test.context.support.WithMockUser`.
    - `org.springframework.test.context.ActiveProfiles`.
    - `org.springframework.test.web.servlet.MockMvc`.
    - Static imports: `SecurityMockMvcRequestPostProcessors.{anonymous, csrf}`, `MockMvcRequestBuilders.{multipart, post}`, `MockMvcResultMatchers.status`.

    ### `@Nested ProdProfileSecurityTest`

    Annotations on the inner class (copy verbatim from Phase 73-04):

    ```
    @Nested
    @SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bkimpsectest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration",
        "logging.config=classpath:logback-test.xml"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("prod")
    class ProdProfileSecurityTest { ... }
    ```

    **Note the DB name change:** use `bkimpsectest` (NOT `bksectest` — that's Phase 73's name; if both ITs run in the same Surefire-Failsafe pass, an H2-memory-DB collision could in theory occur. Different name = different schema instance = full isolation.)

    `@Autowired private MockMvc mockMvc;` field.

    Test methods (Given-When-Then names per CLAUDE.md). For each of the 4 endpoints — `/import-preview` (multipart), `/import-confirm` (form POST), `/import-execute` (form POST), `/import-cancel` (form POST) — produce 4 tests in the prod profile + 1 test in the dev profile, totalling **17 tests** (4 endpoints × 4 prod-tests + 4 endpoints × 1 dev-test + 1 extra dev anonymous-CSRF sanity test).

    The 4 prod-profile tests per endpoint:

    1. **`givenAnonymousWithCsrf_when{Endpoint}_thenUnauthorized()`** — `.with(anonymous()).with(csrf())`, expect `status().isUnauthorized()` (401). Rationale: with CSRF passed, the auth filter is the rejecting filter (cleanly isolates the auth path; otherwise CSRF filter would 403 first).
    2. **`givenAnonymousNoCsrf_when{Endpoint}_thenForbidden()`** — `.with(anonymous())` only (no CSRF), expect `status().isForbidden()` (403). CSRF filter rejects before auth filter — the most common attacker scenario.
    3. **`@WithMockUser` `givenAuthenticatedNoCsrf_when{Endpoint}_thenForbidden()`** — no `.with(csrf())`, expect `status().isForbidden()`. Locks the CSRF-default-on contract.
    4. **`@WithMockUser` `givenAuthenticatedWithCsrf_when{Endpoint}_then{ExpectedSuccessStatus}()`** — `.with(csrf())`. Expected status per endpoint:
       - `/import-preview`: For an invalid multipart (empty file, no real staging path), the controller's `try { backupImportService.stage(file); ... } catch (BackupArchiveException ex)` will fire — Plan 05's `stage` throws `NOT_A_ZIP` or similar on an empty/invalid file. The redirect-to-`/admin/backup` returns `status().is3xxRedirection()`. Use `MockMultipartFile file = new MockMultipartFile("file", "x.zip", "application/zip", new byte[0]);` to keep the test self-contained. The test does NOT assert the Flash text (that's covered by `BackupImportConfirmFormValidationIT` and `BackupImportServiceIT`); it asserts that auth+CSRF passed (otherwise the response would be 401/403 rather than 302). Expected: `status().is3xxRedirection()`.
       - `/import-confirm`: `stagingId=<random-UUID>` — no real staging file exists, so `reparse(UUID)` throws (likely `MANIFEST_MISSING` from Plan 05's contract). Controller catches and redirects with Flash. Expected: `status().is3xxRedirection()`.
       - `/import-execute`: POST with `stagingId=<random-UUID>` AND `acknowledged=false` (so `@AssertTrue` validation fails and we hit the bindingResult.hasErrors branch). That branch tries `reparse(form.getStagingId())` which throws (missing file), so the catch-on-error redirects. Expected: `status().is3xxRedirection()`. (Even if the binding were valid, the same redirect happens via the catch in the success branch — both branches converge to a redirect for a non-existent stagingId.)
       - `/import-cancel`: `stagingId=<random-UUID>` — `deleteStagingFile` is idempotent (no-op on missing file). Expected: `status().is3xxRedirection()` (the redirect to `/admin/backup`).

    Each `{Endpoint}` produces a test name with the camel-case endpoint identifier — `whenPostImportPreview`, `whenPostImportConfirm`, `whenPostImportExecute`, `whenPostImportCancel`. Example test method skeleton (DO NOT include this as fenced code in the test file; it's illustrative only — the executor writes idiomatic Java):

    `mockMvc.perform(multipart("/admin/backup/import-preview").file(new MockMultipartFile("file", "x.zip", "application/zip", new byte[0])).with(anonymous()).with(csrf())).andExpect(status().isUnauthorized());`

    For `/import-confirm`, `/import-execute`, `/import-cancel`, use `post(...).param("stagingId", UUID.randomUUID().toString())` (and for `/import-execute` also `.param("acknowledged", "false")`).

    **Total prod tests:** 4 endpoints × 4 scenarios = **16 prod tests**.

    ### `@Nested DevProfileSecurityTest`

    Annotations:

    ```
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    class DevProfileSecurityTest { ... }
    ```

    `@Autowired private MockMvc mockMvc;`

    Test methods — **one anonymous-OK test per endpoint, total 4 dev tests**:

    1. `givenAnonymous_whenPostImportPreview_thenRedirectsToBackup()` — empty `MockMultipartFile`, no CSRF (dev profile has CSRF disabled per `OpenSecurityConfig`). Expected: `status().is3xxRedirection()`.
    2. `givenAnonymous_whenPostImportConfirm_thenRedirectsToBackup()` — random `stagingId`. Expected: `status().is3xxRedirection()`.
    3. `givenAnonymous_whenPostImportExecute_thenRedirectsToBackup()` — random `stagingId` + `acknowledged=false`. Expected: `status().is3xxRedirection()`.
    4. `givenAnonymous_whenPostImportCancel_thenRedirectsToBackup()` — random `stagingId`. Expected: `status().is3xxRedirection()`.

    All dev tests assert ONLY the redirection status (not the Flash content) — the dev profile is about proving "no auth/CSRF gate exists"; the actual functional behaviour (Flash text, view name on happy path) is covered by `BackupImportConfirmFormValidationIT` (Task 3) and `BackupImportServiceIT` (Plan 05). Keeping these dev tests minimal (status-only) makes them fast and protects against test fragility when the underlying service throws different exception types.

    ### Total

    16 prod tests + 4 dev tests = **20 tests in `BackupImportControllerSecurityIT`**.

    ### Givens, Whens, Thens

    Every test body MUST follow the BDD comment shape per CLAUDE.md "Test Naming":

    ```
    // given
    MockMultipartFile file = new MockMultipartFile(...);
    // when / then
    mockMvc.perform(...).andExpect(...);
    ```

    For tests where the `// given` is trivial (e.g. just a hardcoded `UUID stagingId = UUID.randomUUID();`), keep the `// given` block anyway — consistency over brevity.

    Use AssertJ for any non-MockMvc assertion (none expected here — all assertions are via `andExpect(status()...)`).
  </action>
  <verify>
    <automated>./mvnw -q -Dit.test='BackupImportControllerSecurityIT' verify -DskipUTs</automated>
  </verify>
  <acceptance_criteria>
    - File `src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java` exists, compiles, and is package-`org.ctc.backup`.
    - Exactly two `@Nested` inner classes: `ProdProfileSecurityTest` and `DevProfileSecurityTest`. Verify via `grep -cE '@Nested$|@Nested[[:space:]]*$' src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java | head -1` — returns `2`.
    - Prod nested class uses `@ActiveProfiles("prod")` AND a non-default H2 datasource URL (`bkimpsectest`) to avoid collision with Phase 73's `bksectest`: `grep -c 'bkimpsectest' src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java` returns at least `1`.
    - Dev nested class uses `@ActiveProfiles("dev")`: `grep -c 'ActiveProfiles("dev")' src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java` returns `1`.
    - Test count: `grep -c '@Test' src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java` returns `20` (16 prod + 4 dev).
    - All 4 endpoint URIs appear in tests: `grep -cE '/admin/backup/(import-preview|import-confirm|import-execute|import-cancel)' src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java` returns at least `20` (each test hits at least one URI).
    - `./mvnw -q -Dit.test='BackupImportControllerSecurityIT' verify -DskipUTs` exits `0`; all 20 tests green.
    - Failsafe picks up the `*IT` suffix automatically — no `pom.xml` change needed.
  </acceptance_criteria>
  <done>
    `BackupImportControllerSecurityIT` ships with 20 passing tests (16 prod + 4 dev), uses an isolated H2 schema name (`bkimpsectest`), and proves the profile-conditional auth + CSRF matrix matches Phase-73 expectations for all 4 new endpoints.
  </done>
</task>

<task type="auto" id="74-08-03">
  <name>Task 3: `BackupImportConfirmFormValidationIT` — Spring binding chain for `@AssertTrue Boolean acknowledged`</name>
  <files>
    src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java
  </files>
  <read_first>
    - src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java (Plan 03 output) — verify the `@NotNull @AssertTrue Boolean acknowledged` annotation chain.
    - src/main/java/org/ctc/backup/BackupController.java (Task 1 output) — verify the `@Valid @ModelAttribute("backupImportConfirmForm") BackupImportConfirmForm form, BindingResult bindingResult, ...` parameter order on `/import-execute`.
    - src/main/java/org/ctc/backup/service/BackupImportService.java (Plan 05 output) — verify the `stage(MultipartFile)` and `reparse(UUID)` signatures + behavior on a real ZIP.
    - src/test/java/org/ctc/backup/BackupControllerIT.java — full-context `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")` shape + MockMvc usage patterns.
    - src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java — for the existing in-repo pattern of building a real Phase-73-shaped ZIP fixture in-test (used to obtain a valid `stagingId`).
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md §D-10 (server-side `@AssertTrue` is authoritative), §D-02#5 (locked stub-success Flash).
  </read_first>
  <action>
    Create `src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java` in package `org.ctc.backup`. Class header:

    ```
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    class BackupImportConfirmFormValidationIT { ... }
    ```

    Use the dev profile because (a) CSRF is disabled (test doesn't need `.with(csrf())`), (b) anonymous access is permitted, (c) the H2 in-memory DB is auto-provisioned via the existing dev profile's `spring.datasource.url` — no per-test datasource override needed, the dev profile already isolates from prod.

    Inject:
    - `@Autowired private MockMvc mockMvc;`
    - `@Autowired private BackupImportService backupImportService;` — used in the `@BeforeEach` to stage a real ZIP so the test has a valid `stagingId`.
    - `@Autowired private BackupArchiveService backupArchiveService;` — used to write a Phase-73-shaped ZIP into a `ByteArrayOutputStream` that becomes the `MockMultipartFile` body.

    ### Shared fixture in `@BeforeEach`

    A method `void setUp() throws Exception { ... }`:

    1. Build a Phase-73-shape ZIP in-memory: `ByteArrayOutputStream baos = new ByteArrayOutputStream(); backupArchiveService.writeZip(baos, Instant.now()); byte[] zipBytes = baos.toByteArray();`.
    2. Wrap in a `MockMultipartFile zip = new MockMultipartFile("file", "fixture.zip", "application/zip", zipBytes);`.
    3. Stage via `BackupImportPreview preview = backupImportService.stage(zip);` and store `this.stagingId = preview.stagingId();`.

    This produces a valid `stagingId` pointing at a real on-disk staging file — the controller's `reparse(stagingId)` (called by both branches of `/import-execute`) will succeed.

    Add an `@AfterEach` method that calls `backupImportService.deleteStagingFile(this.stagingId)` to keep the test directory clean (defense against Plan 07's startup-sweep racing the next test — also avoids leaving zombie files in `data/dev/backup-staging/`).

    Add a field `private UUID stagingId;` to hold the staging UUID across the BeforeEach/test boundary.

    ### Test 1: `givenAcknowledgedFalse_whenPostImportExecute_thenReRendersConfirmWithFieldError()`

    ```
    // given (BeforeEach stages a real ZIP; stagingId is valid)
    // when
    mockMvc.perform(post("/admin/backup/import-execute")
            .param("stagingId", stagingId.toString())
            .param("acknowledged", "false"))
    // then
        .andExpect(status().isOk())
        .andExpect(view().name("admin/backup-confirm"))
        .andExpect(model().attributeHasFieldErrors("backupImportConfirmForm", "acknowledged"))
        .andExpect(model().attributeExists("preview"));
    ```

    Assertion rationale:
    - `status().isOk()` (200) — the bindingResult.hasErrors() branch re-renders the page in-place; no redirect.
    - `view().name("admin/backup-confirm")` — the re-render view name MUST be the confirm template (Plan 09 owns the template).
    - `attributeHasFieldErrors("backupImportConfirmForm", "acknowledged")` — Spring auto-binds the field-level error from `@AssertTrue`; the model attribute key is exactly the one declared in `@ModelAttribute("backupImportConfirmForm")` on the controller.
    - `attributeExists("preview")` — Task 1's spec requires `model.addAttribute("preview", ...)` in the validation-failure branch so the page header block renders.

    ### Test 2: `givenAcknowledgedMissing_whenPostImportExecute_thenReRendersConfirmWithFieldError()`

    Same as Test 1, but omit the `acknowledged` parameter entirely (`@NotNull` fires instead of `@AssertTrue`, but both produce a field-level error on the same field). Same assertion set:

    ```
    mockMvc.perform(post("/admin/backup/import-execute")
            .param("stagingId", stagingId.toString())
            // no acknowledged param at all
    ).andExpect(status().isOk())
     .andExpect(view().name("admin/backup-confirm"))
     .andExpect(model().attributeHasFieldErrors("backupImportConfirmForm", "acknowledged"))
     .andExpect(model().attributeExists("preview"));
    ```

    ### Test 3: `givenAcknowledgedTrue_whenPostImportExecute_thenRedirectsToBackupWithStubFlash()`

    ```
    mockMvc.perform(post("/admin/backup/import-execute")
            .param("stagingId", stagingId.toString())
            .param("acknowledged", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/backup"))
        .andExpect(flash().attribute("successMessage", "Validation succeeded. Import execution will be enabled in Phase 75."));
    ```

    Assertion rationale:
    - `status().is3xxRedirection()` (302) — the success branch redirects.
    - `redirectedUrl("/admin/backup")` — locked target per D-22.
    - `flash().attribute(...)` — the exact D-02#5 string, verbatim. This is the load-bearing assertion: if anyone in the future "improves" the wording, this test fails and the breaking change is surfaced.

    ### Test 4 (extra — staging-file-persistence after success): `givenAcknowledgedTrue_whenPostImportExecute_thenStagingFileRemainsOnDisk()`

    Per D-08: "The staging file is **not deleted** by the stub — Phase 75 inherits it and deletes after successful execute." This is a critical Phase-74-to-Phase-75 seam contract. Add a test that proves it:

    ```
    // given (BeforeEach: real ZIP staged, stagingId valid)
    Path stagingFileBefore = resolveStagingFile(stagingId); // helper: data/dev/backup-staging/upload-{uuid}.zip
    assertThat(stagingFileBefore).exists();

    // when
    mockMvc.perform(post("/admin/backup/import-execute")
            .param("stagingId", stagingId.toString())
            .param("acknowledged", "true"))
        .andExpect(status().is3xxRedirection());

    // then
    assertThat(stagingFileBefore).as("Phase 74 stub MUST NOT delete the staging file — Phase 75 inherits it (D-08)").exists();
    ```

    Implement `resolveStagingFile` as a `private Path resolveStagingFile(UUID id)` helper that uses Spring's `Environment` (`@Autowired Environment env;`) to read `app.backup.staging-dir` and build `Paths.get(stagingDir).resolve("upload-" + id + ".zip")`. Inject the Environment field at the class level alongside `MockMvc`.

    **Note:** because the `@AfterEach` deletes the staging file, this test's `assertThat(stagingFileBefore).exists()` after the POST runs BEFORE the AfterEach — `@AfterEach` runs after the test method body completes, so the assertion sees the file still present from the test's perspective. The cleanup happens between tests.

    ### Givens, Whens, Thens

    Every test follows the `// given` / `// when` / `// then` comment shape per CLAUDE.md.

    ### Total

    4 tests in `BackupImportConfirmFormValidationIT`.

    ### Imports

    - `java.nio.file.{Path, Paths}`, `java.time.Instant`, `java.util.UUID`, `java.io.ByteArrayOutputStream`.
    - `org.junit.jupiter.api.{AfterEach, BeforeEach, Test}`.
    - `org.springframework.beans.factory.annotation.Autowired`.
    - `org.springframework.boot.test.context.SpringBootTest`.
    - `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
    - `org.springframework.core.env.Environment`.
    - `org.springframework.mock.web.MockMultipartFile`.
    - `org.springframework.test.context.ActiveProfiles`.
    - `org.springframework.test.web.servlet.MockMvc`.
    - `org.ctc.backup.dto.BackupImportPreview`, `org.ctc.backup.service.BackupArchiveService`, `org.ctc.backup.service.BackupImportService`.
    - Static imports: `org.assertj.core.api.Assertions.assertThat`, `MockMvcRequestBuilders.post`, `MockMvcResultMatchers.{status, view, model, redirectedUrl, flash}`.
  </action>
  <verify>
    <automated>./mvnw -q -Dit.test='BackupImportConfirmFormValidationIT' verify -DskipUTs</automated>
  </verify>
  <acceptance_criteria>
    - File `src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java` exists, compiles, and is package-`org.ctc.backup`.
    - 4 `@Test`-annotated methods present: `grep -c '@Test' src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java` returns `4`.
    - `@BeforeEach` and `@AfterEach` lifecycle methods present (the BeforeEach stages a real ZIP, the AfterEach cleans up): `grep -cE '@BeforeEach|@AfterEach' src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java` returns `2`.
    - The locked D-02#5 string is asserted verbatim: `grep -c 'Validation succeeded\. Import execution will be enabled in Phase 75\.' src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java` returns at least `1`.
    - The D-08 staging-file-survives test is present: `grep -c 'Phase 74 stub MUST NOT delete' src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java` returns `1` (the AssertJ `as(...)` rationale string).
    - All 4 tests green via `./mvnw -q -Dit.test='BackupImportConfirmFormValidationIT' verify -DskipUTs`.
  </acceptance_criteria>
  <done>
    `BackupImportConfirmFormValidationIT` ships with 4 passing tests covering the `@AssertTrue` re-render path (2 tests: false + missing), the success-redirect-with-Flash path (1 test), and the staging-file-survives-success contract from D-08 (1 test).
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| HTTP client → `BackupController` | Multipart upload + form POSTs cross from an untrusted (or admin-authenticated, but human-controlled) browser into the controller's parameter-binding layer. |
| `BackupController` → `BackupImportService` | Trusted boundary — the controller hands an opaque `MultipartFile` or `UUID` to the service; the service owns all filesystem + ZIP hardening (Plans 02/04/05). |
| Form-bound `BackupImportConfirmForm.acknowledged` | The `@AssertTrue Boolean` field is the authoritative server-side gate for the destructive action; client-side JS `confirm()` (Plan 09) is decorative only. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-74-08-01 | Spoofing | `/admin/backup/import-*` endpoints under prod profile | mitigate | Inherited from `SecurityConfig` on `/admin/**`: `anyRequest().authenticated()` + `csrf().enable()`. `BackupImportControllerSecurityIT.ProdProfileSecurityTest` asserts 401 anonymous + 403 missing-CSRF for every endpoint (16 tests). |
| T-74-08-02 | Tampering | `BackupImportConfirmForm.acknowledged` field | mitigate | Server-side `@NotNull @AssertTrue Boolean` (Plan 03) — the form value `acknowledged=false` OR a missing `acknowledged` field both produce a `BindingResult` field error. `BackupImportConfirmFormValidationIT` Tests 1+2 prove the gate fires. Client-side JS `confirm()` is decorative; an attacker bypassing JS still hits the server gate. |
| T-74-08-03 | Tampering | `stagingId` UUID parameter | accept | A user can POST with an arbitrary UUID to `/import-confirm` / `/import-execute` / `/import-cancel`. Worst case: `BackupArchiveException(Reason.MANIFEST_MISSING)` → Flash redirect (`/import-confirm`/`/import-execute`) or silent no-op (`/import-cancel` — `deleteStagingFile` is idempotent on missing files per Plan 05). No information leaks because the routed Flash text is the generic D-02#3 string (not the stagingId or filesystem path). Accept the risk — UUID guessing is infeasible (122 bits of entropy). |
| T-74-08-04 | Information Disclosure | Exception message in `mapReason` | mitigate | Only the `SCHEMA_MISMATCH` `Reason` propagates `ex.getMessage()` to the Flash — and Plan 05 formats that message with only the actual + expected integer schema versions (both already public via `BackupSchema.SCHEMA_VERSION`). All other reasons collapse to the generic D-02#3 string; entry names, paths, byte counts are NOT surfaced via the Flash. Stack traces never reach the user (controller swallows the exception). |
| T-74-08-05 | Denial of Service | Missing `import-execute` resource cleanup (D-08 stub) | accept | The Phase-74 stub intentionally does NOT delete the staging file on successful execute (D-08 — Phase 75 inherits). Without Phase 75, a malicious admin could repeatedly upload + execute to fill the staging directory. Mitigated by Plan 07's `BackupStagingCleanup` (`ApplicationReadyEvent` listener, D-17): the directory is swept on every JVM restart. Daily-restart admin operations (the project's deployment cadence) bound the worst-case growth to ~24 hours. Phase 75 closes this gap fully. |
| T-74-08-06 | Repudiation | No audit row written for Phase-74 stub | accept | Per D-08 + CONTEXT scope: "no DB row, no file system mutation outside the staging dir" in Phase 74. Audit-row writing is Phase 75 (IMPORT-07). `log.info("Backup import canceled: stagingId={}", ...)` provides a partial trail via SLF4J for the cancel path; preview/confirm/execute log at `info` / `warn` similarly. Full audit trail in `data_import_audit` ships in Phase 75. |
| T-74-08-07 | Elevation of Privilege | Inherited `SecurityConfig` rule on `/admin/**` | mitigate | The four new endpoints match `/admin/backup/import-*`, which falls under `/admin/**`. `SecurityConfig.anyRequest().authenticated()` covers them automatically; no per-endpoint `requestMatchers` rule is needed. The `BackupImportControllerSecurityIT.ProdProfileSecurityTest.givenAnonymousWithCsrf_when{Endpoint}_thenUnauthorized` test family (4 tests) proves anonymous POSTs are rejected for every endpoint. |
</threat_model>

<verification>
After all three tasks, run the full focused-IT bundle:

`./mvnw -q -Dit.test='BackupImportControllerSecurityIT,BackupImportConfirmFormValidationIT' verify -DskipUTs`

Expected: BUILD SUCCESS, both IT classes green (20 + 4 = 24 tests).

Then run the full Phase-73 regression to prove the existing export endpoint is byte-identical-preserved:

`./mvnw -q -Dtest='BackupControllerIT,BackupControllerSecurityIT' test`

Expected: all Phase-73 tests still green (no `/export` regression).

Finally, run the unit-test classes that this plan's controller touches (none directly — `BackupController` has no unit-test class, only ITs). Spot-grep verification: the existing export endpoint's view return `return "admin/backup";` (line 64) and its `@PostMapping("/export")` (line 68) are still present:

`grep -E 'return "admin/backup"|@PostMapping\("/export"\)' src/main/java/org/ctc/backup/BackupController.java`

Both must match.

Cross-reference REQUIREMENTS coverage:
- IMPORT-01 satisfied: `import-preview` endpoint wired, calls `backupImportService.stage()`, renders `admin/backup-preview` (template ships in Plan 09).
- IMPORT-02 satisfied: schema-mismatch path → `mapReason(SCHEMA_MISMATCH)` → D-02#2 Flash → redirect `/admin/backup` (no DB write reached). Plan 05 owns the throw site; Plan 08 owns the routing.
- IMPORT-04 satisfied: `import-execute` `@Valid`-binds the confirm form with `@AssertTrue Boolean acknowledged`; `BackupImportConfirmFormValidationIT` proves the gate fires server-side.
- SECU-04 satisfied: `errorMessage` Flash slot on `/admin/backup` consumed by both Plan 06's `MaxUploadSizeExceededException` advice AND Plan 08's reject paths; same key, same template hook (layout `<div th:if="${errorMessage}">`).
</verification>

<success_criteria>
- `src/main/java/org/ctc/backup/BackupController.java` contains 4 new `@PostMapping` endpoints (`/import-preview`, `/import-confirm`, `/import-execute`, `/import-cancel`) AND 1 private `mapReason(BackupArchiveException)` helper, in the order specified in Task 1.
- Existing Phase-73 lines 1-109 of `BackupController.java` are byte-identical with the pre-edit state EXCEPT for (a) new imports added to the alphabetized import block, (b) one new `private final BackupImportService backupImportService;` field below the existing field. Verified by `git diff src/main/java/org/ctc/backup/BackupController.java` showing only additive hunks (no deletions or modifications inside `showForm`, `export`, or `isoSafeFilename`).
- `mapReason` uses a Java 25 exhaustive switch expression over `BackupArchiveException.Reason.values()`; the compiler enforces 8-case coverage. If the enum gains a 9th value in a future plan, the controller fails to compile until the case is routed (intentional surface).
- `grep -c 'deleteStagingFile' src/main/java/org/ctc/backup/BackupController.java` returns exactly `1` (the `importCancel` call site) — proves the D-08 contract that Phase-74 stub does NOT delete on execute-success.
- `grep -c 'Validation succeeded\. Import execution will be enabled in Phase 75\.' src/main/java/org/ctc/backup/BackupController.java` returns `1` (the D-02#5 stub Flash, exactly once).
- `BackupImportControllerSecurityIT` ships with 20 tests (16 prod-profile + 4 dev-profile), uses isolated H2 schema (`bkimpsectest`), and proves the auth/CSRF matrix for all 4 endpoints.
- `BackupImportConfirmFormValidationIT` ships with 4 tests covering the `@AssertTrue` field-error re-render (acknowledged=false AND acknowledged-missing), the success-Flash-redirect (D-02#5 verbatim), and the D-08 staging-file-survives-success seam contract.
- All Phase-73 regression tests (`BackupControllerIT`, `BackupControllerSecurityIT`) still pass — `./mvnw -q -Dtest='BackupControllerIT,BackupControllerSecurityIT' test` exits `0`.
- All 24 new tests pass — `./mvnw -q -Dit.test='BackupImportControllerSecurityIT,BackupImportConfirmFormValidationIT' verify -DskipUTs` exits `0`.
- Coverage on `BackupController.java` ≥ 90 % line coverage (JaCoCo) after Wave 3 — the 4 happy paths are exercised by `BackupImportConfirmFormValidationIT` + Plan 10's E2E, the reject paths by `BackupImportServiceIT` (Plan 05) calling through MockMvc, and the redirect-on-non-existent-stagingId paths by `BackupImportControllerSecurityIT`. Phase-wide ≥ 82 % JaCoCo gate (`pom.xml`) is the binding constraint.
</success_criteria>

<output>
After completion, create `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-08-SUMMARY.md` per `templates/summary.md`. Capture:

- The 4 new endpoint URIs + method signatures (parameter order + types — Plan 09 needs the exact `@ModelAttribute("backupImportConfirmForm")` key for the template's `th:object`).
- The `mapReason(BackupArchiveException)` Reason→Flash routing table (one row per `Reason` value, 8 rows).
- The seam contract for Phase 75: the staging file is NOT deleted on `import-execute` success (D-08); Phase 75's `execute(UUID)` MUST be the deletion site. Cite this verbatim in the summary so Phase 75's planner reads it.
- The 24 new test method names (20 in `BackupImportControllerSecurityIT` + 4 in `BackupImportConfirmFormValidationIT`) — Plan 10's E2E test should NOT duplicate these scenarios.
- Test runtime in seconds (sanity check — full-context ITs typically run 3-10 s each).
- Any deviation from PATTERNS / UI-SPEC / CONTEXT (none expected — the planning_context is explicit and exhaustive).
</output>

## Notes

- **Reason enum is the routing key.** Plan 02 finalized 8 `Reason` values: `PATH_TRAVERSAL, ENTRY_TOO_LARGE, TOTAL_TOO_LARGE, TOO_MANY_ENTRIES, MANIFEST_MISSING, MANIFEST_INVALID, SCHEMA_MISMATCH, NOT_A_ZIP`. The `mapReason` switch routes `SCHEMA_MISMATCH` to the D-02#2 templated string (pre-formatted by Plan 05 at throw time, NOT re-formatted by the controller) and folds the other 7 into D-02#3 generic safety-checks. This matches the planning_context's `mapReason` block verbatim — `MANIFEST_MISSING` and `MANIFEST_INVALID` collapse into the generic Flash per the closing note.

- **No `BackupArchiveException` carrier-fields needed for SCHEMA_MISMATCH.** The planning_context originally hinted "exception must carry both ints" — but Plan 02 settled on a `(Reason, String)` two-arg constructor (no `int actual, int expected` carriers, no `Object[] reasonArgs`). Plan 05's throw site formats the message inline: `throw new BackupArchiveException(Reason.SCHEMA_MISMATCH, "Schema version mismatch: backup=" + actual + ", expected=" + current + ". Cannot import.");`. The controller's `mapReason` returns `ex.getMessage()` verbatim for SCHEMA_MISMATCH. This is simpler than the carrier-field design and produces byte-identical Flash output.

- **D-02 string `IDENTITY` — the Phase-74 contract:** D-02 strings are LOCKED. Any change to the 5 D-02 strings is a Phase-74 contract breach requiring a CONTEXT revision. The IT assertions (`flash().attribute(...)` with verbatim strings) are the executable lock. If a future phase wants to reword them, the failing IT is the surface that catches it before it ships.

- **No `@SessionAttributes`** (D-18). The controller binds the `stagingId` as a hidden `@RequestParam` (preview/confirm/execute/cancel each take it as input). Stateless. The form's `BackupImportConfirmForm.stagingId` field is bound via `th:field="*{stagingId}"` in the template (Plan 09). No session state, no in-memory cache, no `@SessionAttributes("backupImportConfirmForm")`.

- **Why `@ModelAttribute("backupImportConfirmForm")` not `@ModelAttribute("form")`:** the model-attribute key name controls (a) the template's `th:object` binding, (b) Spring's `BindingResult` lookup pattern (`org.springframework.validation.BindingResult.backupImportConfirmForm`), AND (c) the `model().attributeHasFieldErrors("backupImportConfirmForm", ...)` assertion in `BackupImportConfirmFormValidationIT`. Plan 09 will use the same key in the template's `th:object`. This is load-bearing — if a later refactor renames the key, the template binding silently breaks (the form fields render as readonly) and the test catches it.

- **`@Transactional` is intentionally absent** on the controller (per CONTEXT D-22 closing note). `BackupImportService` carries the `@Transactional(readOnly = true)` boundary (Plan 05). Adding `@Transactional` to the controller would extend the read-only transaction to the redirect-handling, which is unnecessary and risks fighting OSIV's view-render transaction boundary.

- **Test execution mode:** `BackupImportControllerSecurityIT` is `*IT`-suffixed → Failsafe picks it up. `BackupImportConfirmFormValidationIT` is also `*IT`-suffixed → Failsafe. Surefire ignores both. To run both in one shot: `./mvnw -q -Dit.test='BackupImport*IT' verify -DskipUTs`.

- **No Playwright/E2E** in this plan. Plan 10 owns the Playwright E2E walkthrough that exercises the full chain (upload → preview → confirm → execute) against a real browser. Plan 08 covers MockMvc-level binding + security; Plan 10 covers UI-level user journey.

## PLAN COMPLETE 08
