# Phase 74: Backup Import Preview + ZIP Hardening + Multipart Config + Schema-Version Gate — Pattern Map

**Mapped:** 2026-05-12
**Files analyzed:** 22 (11 new prod + 4 extended prod + 2 new templates + 1 edited template + 1 edited config + 10 new tests)
**Analogs found:** 22 / 22 (every file has at least one strong analog in the repo)

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `org.ctc.backup.service.BackupImportService` *(new)* | Service | file-I/O + transform | `src/main/java/org/ctc/backup/service/BackupArchiveService.java` | role-match (sister service, opposite direction) |
| `org.ctc.backup.service.BackupStagingCleanup` *(new ApplicationReadyEvent listener)* | Service (lifecycle) | event-driven (startup) | `src/main/java/org/ctc/admin/DevDataSeeder.java` | role-match (only startup-runner in repo; differs in event type but same `@Component` + `@Slf4j` shape) |
| `org.ctc.backup.service.BackupImportLimits` *(new constants holder)* | Utility (constants) | n/a | `FileStorageService.MAX_FILE_SIZE` (`FileStorageService.java:25`) | partial (project has no standalone constants class; closest is the `static final long` block inside `FileStorageService`) |
| `org.ctc.backup.dto.BackupImportPreview` *(new record)* | DTO | request-response (Controller→Template) | `src/main/java/org/ctc/backup/schema/BackupManifest.java` | exact (record in same `org.ctc.backup` tree) |
| `org.ctc.backup.dto.EntityRowCount` *(new record)* | DTO | request-response | `src/main/java/org/ctc/backup/schema/EntityRef.java` | exact (small immutable record adjacent to schema) |
| `org.ctc.backup.dto.BackupImportConfirmForm` *(new form DTO)* | Form DTO | request-response | `src/main/java/org/ctc/admin/dto/MatchdayForm.java` | role-match (canonical Lombok form DTO with `@NotNull`) |
| `org.ctc.backup.exception.BackupArchiveException` *(new — or reuse `BusinessRuleException`)* | Exception | event-driven (throw/catch) | `src/main/java/org/ctc/domain/exception/BusinessRuleException.java` | exact |
| `org.ctc.backup.exception.BackupUploadExceptionHandler` *(new @ControllerAdvice)* | Controller-advice | request-response (redirect-flash) | `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` | role-match (existing handler returns `ModelAndView`; D-14 forces a new redirect-string handler) |
| `org.ctc.backup.security.PathTraversalGuard` *(new — optional / inline)* | Utility (security) | transform | `FileStorageService.validatePathWithinUploadDir` (`FileStorageService.java:153-158`) | exact (extract of existing idiom) |
| `org.ctc.backup.BackupController` *(extends — 4 endpoints)* | Controller | request-response + file-I/O upload | self (existing `BackupController.java`) + `src/main/java/org/ctc/dataimport/CsvImportController.java` | exact (extend) + role-match (staging-path) |
| `org.ctc.backup.service.BackupArchiveService` *(extends — 3 read methods)* | Service | file-I/O streaming read | self (existing `writeZip`) | exact (extend with read counterpart) |
| `org.ctc.admin.controller.GlobalExceptionHandler` *(non-modification — see below)* | n/a | n/a | n/a | D-14 spawns a **new** sibling `@ControllerAdvice` class instead of extending this one — the existing class is left untouched |
| `src/main/resources/templates/admin/backup-preview.html` *(new)* | Template | request-response (SSR) | `src/main/resources/templates/admin/backup.html` + `admin/import-preview.html` | exact (Phase 73 sister template) + partial (preview-shape reference, NOT layout copy) |
| `src/main/resources/templates/admin/backup-confirm.html` *(new)* | Template | request-response (SSR + form) | `src/main/resources/templates/admin/backup.html` + `admin/matchday-form.html` (form-with-`@Valid`-error pattern) | role-match |
| `src/main/resources/templates/admin/backup.html` *(extends — adds import form)* | Template | request-response (SSR) | self | exact (extend) |
| `src/main/resources/application.yml` *(edited)* | Config | n/a | self | exact (extend) |
| `BackupImportServiceIT` *(new test)* | Integration test | request-response | `src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java` | exact |
| `BackupImportSchemaVersionMismatchIT` *(new test)* | Integration test | request-response | `BackupArchiveServiceIT` + `BackupRoundTripIT` | exact |
| `BackupImportZipSlipIT` / `BackupImportZipBombIT` *(new tests)* | Integration test | file-I/O | `BackupArchiveServiceIT` | role-match |
| `BackupImportMultipartLimitIT` *(new test)* | Integration test | request-response (HTTP) | `BackupControllerIT.java` | role-match (MockMvc full-context IT) |
| `BackupImportConfirmFormValidationIT` *(new test)* | Integration test | request-response | `BackupControllerIT.java` | role-match |
| `BackupImportControllerSecurityIT` *(new test)* | Integration test | security matrix | `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java` | exact |
| `BackupStagingCleanupIT` *(new test)* | Integration test | event-driven | `BackupArchiveServiceIT` | role-match (no startup-listener IT exists yet — adapt `@SpringBootTest`+`@TempDir` shape) |
| `BackupArchiveServiceReadIT` *(new test)* | Integration test | file-I/O | `BackupArchiveServiceIT` | exact |
| `BackupImportE2ETest` *(Playwright)* | E2E test | request-response (UI) | `src/test/java/org/ctc/e2e/BackupExportE2ETest.java` | exact (sister Playwright test) |

---

## Pattern Assignments

### `org.ctc.backup.service.BackupImportService` (service, file-I/O + transform)

**Analog:** `src/main/java/org/ctc/backup/service/BackupArchiveService.java` (the write-side sister service)

**Class header pattern** (`BackupArchiveService.java:54-74`):

```java
@Slf4j
@Service
@Transactional(readOnly = true)
public class BackupArchiveService {

    private final BackupExportService backupExportService;
    private final BackupSchema backupSchema;
    private final ObjectMapper backupObjectMapper;
    private final String appVersion;

    public BackupArchiveService(
            BackupExportService backupExportService,
            BackupSchema backupSchema,
            @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper,
            @Value("${app.version:dev}") String appVersion
    ) {
        this.backupExportService = backupExportService;
        this.backupSchema = backupSchema;
        this.backupObjectMapper = backupObjectMapper;
        this.appVersion = appVersion;
    }
```

Copy: `@Slf4j` + `@Service` + `@Transactional(readOnly = true)`, explicit constructor (NOT `@RequiredArgsConstructor`) so the `@Qualifier("backupObjectMapper")` annotation can sit on the constructor parameter. Add `@Value("${app.backup.staging-dir:data/${spring.profiles.active}/backup-staging}") String stagingDir` per D-15.

**Logging pattern** (`BackupArchiveService.java:94-95` and `135-136`):

```java
log.info("Backup export started: schemaVersion={}, appVersion={}, entities={}",
        BackupSchema.SCHEMA_VERSION, appVersion, exportOrder.size());
// ...
log.info("Backup export completed: dataEntries={}, uploadEntries={}, skippedTraversal={}",
        dataEntries, uploadEntries, skippedTraversal);
```

Use the same parameterized-`{}` info-logging at the start and end of `stage(...)`, including `stagingId`, `originalFilename`, `fileSizeBytes`, `schemaVersion`. CLAUDE.md "Logging" + CONVENTIONS.md L57-67 require this style.

**File-I/O staging pattern** (`FileStorageService.java:30-46` and `:153-158`):

```java
private final Path uploadDir;

public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
    this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
}

// ...
Path target = raceDir.resolve(filename);
validatePathWithinUploadDir(target);
file.transferTo(target);

// ...
private void validatePathWithinUploadDir(Path target) {
    Path normalized = target.toAbsolutePath().normalize();
    if (!normalized.startsWith(uploadDir)) {
        log.warn("Attempted path traversal: {}", target);
        throw new IllegalArgumentException("Path traversal detected: " + target);
    }
}
```

Copy: resolve `Paths.get(stagingDir).toAbsolutePath().normalize()` once in the constructor, store as `Path`. Build the staging file as `stagingDir.resolve("upload-" + uuid + ".zip")` and re-validate via the same `startsWith(stagingDir)` predicate. The new `BackupArchiveService.readManifest/countDataEntries/countUploadFiles` reuse the same predicate per-`ZipEntry.getName()` (D-11).

**Reject-path try/finally pattern** (cross-reference D-16, not a literal copy because no analog uses `try/finally` for staging delete):

```java
// Pattern derived from BackupController:74-93 (catch + rethrow with context-log)
// plus FileStorageService:60-62 (Files.deleteIfExists + WARN on failure).
try {
    runHardeningChecks(stagingFile);
    return buildPreview(stagingFile);
} catch (BackupArchiveException ex) {
    try {
        Files.deleteIfExists(stagingFile);
    } catch (IOException io) {
        log.warn("Failed to delete rejected staging file: {}", stagingFile, io);
    }
    throw ex;
}
```

---

### `org.ctc.backup.service.BackupStagingCleanup` (service, event-driven startup)

**Analog:** `src/main/java/org/ctc/admin/DevDataSeeder.java` (only startup-runner in repo)

**Header pattern** (`DevDataSeeder.java:10-23`):

```java
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final TestDataService testDataService;
    private final SiteGeneratorService siteGeneratorService;

    @Override
    public void run(String... args) {
        testDataService.seed();
        generateSite();
    }
```

**Adaptation for Phase 74:** drop `@Profile("dev")` (cleanup runs in every profile — D-17 is unconditional), swap `CommandLineRunner` for an `@EventListener(ApplicationReadyEvent.class)`-annotated method (the listener pattern is preferred for filesystem touchups that should run *after* the app is ready, not during context init). Keep `@Slf4j`, `@Component`, `@RequiredArgsConstructor`.

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupStagingCleanup {

    private final Path stagingDir; // via @Value in @Configuration or constructor param

    @EventListener(ApplicationReadyEvent.class)
    public void cleanStaleStagingFiles() {
        // Files.walk + filter("upload-*.zip") + delete; log.info("Cleared {N} stale staging files")
    }
}
```

**Logging pattern** (`DevDataSeeder.java:28`): `log.info("Site generated: {} pages, {} errors", ...)` → mirror with `log.info("Cleared {} stale staging files", count)`.

---

### `org.ctc.backup.dto.BackupImportPreview` (DTO, request-response)

**Analog:** `src/main/java/org/ctc/backup/schema/BackupManifest.java` (sister record in same module)

**Record pattern** (`BackupManifest.java:34-40`):

```java
public record BackupManifest(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("app_version") String appVersion,
        @JsonProperty("export_date") Instant exportDate,
        @JsonProperty("table_counts") Map<String, Long> tableCounts
) {
}
```

**Adaptation:** drop the `@JsonProperty` annotations (Phase 74 preview is template-bound, not serialized over the wire). Keep the record shape exactly as written in CONTEXT D-21. No Lombok on records (records are already immutable). Same package convention (`org.ctc.backup.dto` is new; `org.ctc.backup.schema` already exists as a sibling).

---

### `org.ctc.backup.dto.EntityRowCount` (DTO, request-response)

**Analog:** `src/main/java/org/ctc/backup/schema/EntityRef.java`

EntityRef is the smallest record in the codebase and is the structural model for `EntityRowCount`. Copy the record-only-no-annotations style. Field order per CONTEXT D-21: `(String tableName, String humanLabel, long currentRows, long importedRows)`.

---

### `org.ctc.backup.dto.BackupImportConfirmForm` (form DTO, request-response)

**Analog:** `src/main/java/org/ctc/admin/dto/MatchdayForm.java`

**Form DTO pattern** (`MatchdayForm.java:1-23`):

```java
package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class MatchdayForm {

    private UUID id;

    @NotBlank
    private String label;

    private int sortIndex;

    @NotNull
    private UUID seasonId;
}
```

**Adaptation:**

```java
package org.ctc.backup.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class BackupImportConfirmForm {

    @NotNull
    private UUID stagingId;

    @NotNull
    @AssertTrue(message = "You must acknowledge the deletion warning to continue.")
    private Boolean acknowledged;
}
```

Note: `Boolean` (NOT primitive `boolean`) so `@NotNull` works — `@AssertTrue` alone passes on `null`. The message is the locked UI-SPEC string.

---

### `org.ctc.backup.exception.BackupArchiveException` (exception)

**Analog:** `src/main/java/org/ctc/domain/exception/BusinessRuleException.java`

```java
package org.ctc.domain.exception;

public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
```

**Adaptation (D-12 discretion: reason-code enum):**

```java
package org.ctc.backup.exception;

public class BackupArchiveException extends RuntimeException {

    public enum Reason {
        SCHEMA_VERSION_MISMATCH,
        ZIP_SLIP,
        ENTRY_TOO_LARGE,
        TOTAL_TOO_LARGE,
        TOO_MANY_ENTRIES,
        MANIFEST_PARSE_FAILED,
        MANIFEST_MISSING
    }

    private final Reason reason;

    public BackupArchiveException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
```

Reason codes route to the locked Flash strings in `BackupImportService.stage(...)` / `BackupController.importPreview(...)`.

**Caller catch pattern** (`CarController.java:54-58`):

```java
try {
    carService.uploadImage(id, image);
    redirectAttributes.addFlashAttribute("successMessage", "Image updated");
} catch (BusinessRuleException e) {
    redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
}
return "redirect:/admin/cars/" + id + "/edit";
```

Copy this `try/catch + addFlashAttribute + redirect:` shape into `BackupController.importPreview`, branching by `Reason` to pick the locked D-02 string.

---

### `org.ctc.backup.exception.BackupUploadExceptionHandler` (controller advice)

**Analog:** `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java`

**Existing handler pattern** (`GlobalExceptionHandler.java:17-49`):

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    // ...

    @ExceptionHandler(BusinessRuleException.class)
    public ModelAndView handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return buildErrorView(HttpStatus.CONFLICT, "Business Rule Violation", ex);
    }
```

**Adaptation (D-14 — new sibling `@ControllerAdvice` returning redirect-string instead of `ModelAndView`):**

```java
@Slf4j
@ControllerAdvice
public class BackupUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleTooLarge(MaxUploadSizeExceededException ex, RedirectAttributes redirectAttributes) {
        log.warn("Backup upload too large: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                "Upload too large — maximum is 100 MB.");
        return "redirect:/admin/backup";
    }
}
```

**Why a separate class** (not adding a method to `GlobalExceptionHandler`): D-14 + RESEARCH §"Alternatives Considered" — mixing `ModelAndView` and `String "redirect:..."` return types in one `@ControllerAdvice` works at runtime, but a sibling advice keeps the redirect-flash concern isolated to the Backup feature (lower blast radius, simpler test).

---

### `org.ctc.backup.security.PathTraversalGuard` (security utility — D-11 discretion)

**Analog:** `FileStorageService.validatePathWithinUploadDir` (`FileStorageService.java:153-158`)

**Exact lines to copy** (`FileStorageService.java:153-158`):

```java
private void validatePathWithinUploadDir(Path target) {
    Path normalized = target.toAbsolutePath().normalize();
    if (!normalized.startsWith(uploadDir)) {
        log.warn("Attempted path traversal: {}", target);
        throw new IllegalArgumentException("Path traversal detected: " + target);
    }
}
```

**Adaptation:** lift into a package-private static helper class `PathTraversalGuard` with signature `static void guard(Path root, String entryName)` so both `FileStorageService` (existing) and the new `BackupArchiveService.readManifest/...` share semantics. Replace `IllegalArgumentException` with `BackupArchiveException(Reason.ZIP_SLIP, ...)` on the ZIP-read side. Per D-11, planner may keep it inline with a comment cross-referencing `FileStorageService.java:153-158` — discretion call.

---

### `org.ctc.backup.BackupController` (controller, request-response + multipart file-I/O)

**Analog:** self (existing `BackupController.java`) + `org.ctc.dataimport.CsvImportController.java`

**Existing controller header** (`BackupController.java:51-66`):

```java
@Slf4j
@Controller
@RequestMapping("/admin/backup")
@RequiredArgsConstructor
public class BackupController {

    private static final DateTimeFormatter ISO_COMPACT_INSTANT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final BackupArchiveService backupArchiveService;

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("title", "Backup");
        return "admin/backup";
    }
```

**Adaptation:** keep the class header + the existing `showForm` + `export` methods exactly as-is. Add a `private final BackupImportService backupImportService;` field (Lombok will pick it up). Add 4 new endpoints under `/admin/backup`.

**Multipart-upload + Preview + RedirectAttributes pattern** (`CsvImportController.java:38-62`):

```java
@PostMapping("/preview")
public String preview(@RequestParam("file") MultipartFile file,
                      @RequestParam UUID seasonId,
                      @RequestParam(required = false) String matchdayLabel,
                      @RequestParam(required = false) UUID matchdayId,
                      @RequestParam(required = false) UUID playoffMatchupId,
                      Model model) {
    try {
        var metadata = new CsvImportService.ImportMetadata(seasonId, matchdayLabel, null, null, playoffMatchupId, matchdayId);
        var preview = csvImportService.parseAndPreview(file.getInputStream(), metadata);

        csvImportService.checkDuplicate(preview);
        model.addAttribute("preview", preview);
        // ...
        return "admin/import-preview";
    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
        log.error("Error parsing CSV", e);
        addCommonAttributes(model);
        model.addAttribute("errorMessage", "Error reading CSV: " + e.getMessage());
        return "admin/import";
    }
}
```

**Adaptation for `POST /admin/backup/import-preview`:**

```java
@PostMapping("/import-preview")
public String importPreview(@RequestParam("file") MultipartFile file,
                            RedirectAttributes redirectAttributes,
                            Model model) {
    try {
        BackupImportPreview preview = backupImportService.stage(file);
        model.addAttribute("preview", preview);
        model.addAttribute("title", "Backup");
        return "admin/backup-preview";
    } catch (BackupArchiveException ex) {
        log.warn("Backup import rejected: reason={}, msg={}", ex.reason(), ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                resolveFlashStringFor(ex.reason()));  // routes by Reason → locked D-02 strings
        return "redirect:/admin/backup";
    } catch (IOException e) {
        log.error("I/O while staging backup upload", e);
        redirectAttributes.addFlashAttribute("errorMessage",
                "Backup archive failed safety checks (size or path) and was rejected.");
        return "redirect:/admin/backup";
    }
}
```

**`@Valid` form-binding + `BindingResult` pattern** (`MatchdayController.java:94-114`):

```java
@PostMapping("/save")
public String save(@Valid @ModelAttribute("form") MatchdayForm form,
                   BindingResult result,
                   RedirectAttributes redirectAttributes,
                   Model model) {
    if (result.hasErrors()) {
        // re-render form view with model
        return "admin/matchday-form";
    }
    // success: addFlashAttribute("successMessage", ...) + redirect
}
```

**Adaptation for `POST /admin/backup/import-execute`** (stub, D-08):

```java
@PostMapping("/import-execute")
public String importExecute(@Valid @ModelAttribute("confirmForm") BackupImportConfirmForm form,
                            BindingResult result,
                            RedirectAttributes redirectAttributes,
                            Model model) {
    if (result.hasErrors()) {
        model.addAttribute("title", "Backup");
        return "admin/backup-confirm";  // re-render with @AssertTrue field-error
    }
    try {
        backupImportService.reparse(form.getStagingId());  // D-09 defense-in-depth
        redirectAttributes.addFlashAttribute("successMessage",
                "Validation succeeded. Import execution will be enabled in Phase 75.");
        return "redirect:/admin/backup";
    } catch (BackupArchiveException ex) {
        redirectAttributes.addFlashAttribute("errorMessage", resolveFlashStringFor(ex.reason()));
        return "redirect:/admin/backup";
    }
}
```

**Cancel/Cleanup endpoint pattern** (`CarController.java:78-87`):

```java
@PostMapping("/{id}/delete")
public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        carService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Car deleted");
    } catch (BusinessRuleException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/admin/cars";
}
```

**Adaptation for `POST /admin/backup/import-cancel`:** mirror the structure verbatim (UUID path param replaced by `@RequestParam UUID stagingId`); delegate `backupImportService.deleteStagingFile(stagingId)` and redirect to `/admin/backup` without a Flash message (silent cancel, per D-06).

---

### `org.ctc.backup.service.BackupArchiveService` — extension (existing class, 3 new read methods)

**Analog:** self (the existing `writeZip` and its `writeJson` helper)

**Existing streaming-write pattern** (`BackupArchiveService.java:92-138`):

```java
public void writeZip(OutputStream out, Instant exportDate) throws IOException {
    List<EntityRef> exportOrder = backupSchema.getExportOrder();
    log.info("Backup export started: schemaVersion={}, ...", BackupSchema.SCHEMA_VERSION, appVersion, exportOrder.size());

    try (ZipOutputStream zip = new ZipOutputStream(out)) {
        zip.setLevel(Deflater.DEFAULT_COMPRESSION);

        // Step 1 — manifest.json must be entry #0 (wire contract D-14).
        Map<String, Long> tableCounts = backupExportService.countRowsPerTable();
        BackupManifest manifest = new BackupManifest(
                BackupSchema.SCHEMA_VERSION, appVersion, exportDate, tableCounts);
        zip.putNextEntry(new ZipEntry("manifest.json"));
        writeJson(zip, manifest, /* pretty= */ true);
        zip.closeEntry();
        // ...
    }
}
```

**Read-side adaptation** (inverse — `readManifest`, `countDataEntries`, `countUploadFiles`):

```java
public BackupManifest readManifest(Path zipPath) throws IOException {
    try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipPath))) {
        ZipEntry first = zin.getNextEntry();
        if (first == null || !"manifest.json".equals(first.getName())) {
            throw new BackupArchiveException(Reason.MANIFEST_MISSING,
                    "Expected manifest.json as entry #0");
        }
        guardPath(zipPath.getParent(), first.getName());  // D-11 (ZIP-Slip)
        // wrap with LimitedInputStream(MAX_ENTRY_BYTES) for D-12 ZipBomb defense
        InputStream limited = new LimitedInputStream(zin, MAX_ENTRY_BYTES);
        return backupObjectMapper.readValue(limited, BackupManifest.class);
    }
}
```

Copy: the `try (ZipOutputStream...)` → `try (ZipInputStream...)` symmetry, the `@Qualifier("backupObjectMapper")` reuse (already constructor-injected), the `log.info` start/end pattern, the per-entry assertion against `BackupSchema.SCHEMA_VERSION`.

Reference also: `BackupArchiveService.java:120-131` (export-side ZIP-Slip skip-with-WARN) — the read side is the mirror image: detect via `Paths.get(stagingDir).resolve(name).normalize().startsWith(stagingDir)` and **throw** (not skip), because malicious read input must fail loud.

---

### `src/main/resources/templates/admin/backup-preview.html` (new template)

**Analog:** `src/main/resources/templates/admin/backup.html` (Phase 73 sister template — exact layout-fragment shape)

**Existing template** (entire file, `admin/backup.html:1-21`):

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Backup', ~{::section})}">
<body>
<section>
    <h1>Backup</h1>
    <div class="card">
        <p class="text-dim mb-md">
            Exports the full league database (all 24 entities) plus every uploaded file
            into a single ZIP archive. The download starts immediately and may take a few
            seconds for large leagues.
        </p>
        <form th:action="@{/admin/backup/export}" method="post">
            <button type="submit" class="btn btn-primary btn-lg">
                Export Backup
            </button>
        </form>
    </div>
</section>
</body>
</html>
```

**Adaptation for `backup-preview.html`:** copy the `<!DOCTYPE>` + `<html th:replace="~{admin/layout :: layout('Backup', ~{::section})}">` + `<section>` wrapper exactly. Inside `<section>` follow the UI-SPEC §"Layout per page" skeleton — header card, schema-match `.alert.alert-success.mb-md`, `.card-grid` of 24 `.card.card--compact` per `${preview.entityCounts}`, hidden-input form to `/admin/backup/import-confirm`.

**CSS verification (from `admin.css` grep):**
- `.card-grid` at `admin.css:1692`
- `.card--compact` at `admin.css:1699`
- `.form-check` at `admin.css:335`
- All three exist; no new CSS introduced.

**Secondary analog — preview-shape only, NOT layout copy:** `templates/admin/import-preview.html` (Thymeleaf preview-page shape reference). Do NOT copy its CSV-table layout — Phase 74 uses a card grid per D-03/UI-SPEC.

---

### `src/main/resources/templates/admin/backup-confirm.html` (new template)

**Analog:** `admin/backup.html` (layout shell) + `admin/matchday-form.html` (`@Valid` + `BindingResult` + `.field-error` shape)

Follow the UI-SPEC §"`admin/backup-confirm.html`" skeleton verbatim. Key bits to copy from the existing form-error idiom (any of the `*-form.html` templates use this same shape):

```html
<small th:if="${#fields.hasErrors('acknowledged')}"
       th:errors="*{acknowledged}" class="field-error">
    You must acknowledge the deletion warning to continue.
</small>
```

The `.field-error` class lives at `admin.css:318`; the `.alert-warning` (for the destructive callout) at `admin.css:161`.

---

### `src/main/resources/templates/admin/backup.html` — extension (adds Import form)

**Analog:** self

**Adaptation:** preserve lines 1-19 byte-identically. Insert a second `<div class="card">` block **after** the existing one but **before** `</section>` (UI-SPEC §"`admin/backup.html` (existing — Phase 74 extends)" skeleton). Two stacked `.card` blocks; no side-by-side, no shared card.

---

### `src/main/resources/application.yml` — extension

**Analog:** self (the existing `spring.servlet.multipart` block at lines 8-11)

**Existing block:**

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

**Adaptation per D-13:** raise both limits to `100MB`; add the `server.tomcat` block (no Tomcat block exists yet — Phase 74 introduces it); add `app.backup.staging-dir` under the existing `app:` key (line 1-3). Resulting YAML preserves all other keys unchanged.

---

## Test Pattern Assignments

### `BackupImportServiceIT`, `BackupImportZipSlipIT`, `BackupImportZipBombIT`, `BackupArchiveServiceReadIT`

**Analog:** `src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java`

**Test class header pattern** (`BackupArchiveServiceIT.java:48-62`):

```java
@SpringBootTest
@ActiveProfiles("dev")
class BackupArchiveServiceIT {

    @Autowired
    private BackupArchiveService archiveService;

    @Autowired
    private BackupSchema backupSchema;

    @Autowired
    @Qualifier("backupObjectMapper")
    private ObjectMapper backupObjectMapper;
```

**Test-method naming pattern** (`BackupArchiveServiceIT.java:63-77`):

```java
@Test
void givenDevFixture_whenWriteZip_thenManifestIsFirstEntry() throws Exception {
    // given
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    // when
    archiveService.writeZip(out, Instant.now());

    // then
    List<String> entryNames = readEntryNames(out.toByteArray());
    assertThat(entryNames).isNotEmpty();
    assertThat(entryNames.get(0))
            .as("manifest.json must be ZipEntry #0 — RESEARCH §L-72.D-14")
            .isEqualTo("manifest.json");
}
```

Copy: `givenContext_whenAction_thenExpectedResult` naming + `// given` / `// when` / `// then` comments + AssertJ `assertThat(...).as("...")` rationale annotations (CLAUDE.md "Test Naming" requires this BDD shape).

---

### `BackupImportControllerSecurityIT`

**Analog:** `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java`

**Pattern** (`BackupControllerSecurityIT.java:41-126`): the two-`@Nested`-classes shape (prod vs dev profile) with `@WithMockUser` + `.with(csrf())` + `.with(anonymous())` matrix. The prod inner class uses property-overrides:

```java
@Nested
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bksectest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration",
        "logging.config=classpath:logback-test.xml"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileSecurityTest {
    // 4 tests: anonymous (401), anonymous-no-CSRF (403), authenticated-no-CSRF (403), authenticated-with-CSRF (200)
}
```

Mirror this verbatim — change the `mockMvc.perform(post("/admin/backup/export")...)` URLs to `import-preview`, `import-confirm`, `import-execute`, `import-cancel`. Multipart endpoints use `multipart(...)` instead of `post(...)`.

---

### `BackupImportMultipartLimitIT`, `BackupImportSchemaVersionMismatchIT`, `BackupImportConfirmFormValidationIT`

**Analog:** `src/test/java/org/ctc/backup/BackupControllerIT.java`

**Pattern** (`BackupControllerIT.java:42-58`):

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BackupControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenAuthenticatedAdmin_whenGetBackup_thenViewRendersWithLockedUiSpecStrings() throws Exception {
        mockMvc.perform(get("/admin/backup"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/backup"))
                .andExpect(content().string(Matchers.containsString("Export Backup")))
                .andExpect(content().string(Matchers.containsString("all 24 entities")));
    }
```

For multipart upload tests use Spring's `MockMultipartFile` (RESEARCH cites `CsvImportControllerTest.java:52` as an in-repo example):

```java
MockMultipartFile file = new MockMultipartFile("file", "x.zip", "application/zip", bytes);
mockMvc.perform(multipart("/admin/backup/import-preview").file(file))
       .andExpect(status().is3xxRedirection())  // reject paths redirect to /admin/backup
       .andExpect(flash().attribute("errorMessage", containsString("safety checks")));
```

For schema-mismatch IT, assert DB row counts before/after via `Repository.count()` to prove zero writes (D-24).

---

### `BackupStagingCleanupIT`

**Analog:** `BackupArchiveServiceIT.java` (the `@SpringBootTest` + `@ActiveProfiles("dev")` shape — no startup-listener IT exists yet in the repo)

**Adaptation:** use `@TempDir` to seed 3 stale `upload-*.zip` files into a temp staging dir, override `app.backup.staging-dir` via `@SpringBootTest(properties = "app.backup.staging-dir=${tempdir}")`, publish an `ApplicationReadyEvent` (or rely on the `@SpringBootTest` lifecycle to fire it naturally), assert all 3 files are gone + one `log.info` line was emitted (use `OutputCaptureExtension`).

---

### `BackupImportE2ETest` (Playwright)

**Analog:** `src/test/java/org/ctc/e2e/BackupExportE2ETest.java`

**Pattern** (`BackupExportE2ETest.java:44-89`):

```java
class BackupExportE2ETest extends PlaywrightConfig {

    @BeforeEach
    void setUp() {
        setupPage();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
    }

    @Test
    void givenAdminUI_whenClickBackupSidebarThenExport_thenZipDownloadsWithIsoFilenameAndManifestFirst()
            throws Exception {
        page.navigate(url("/admin"));

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Backup").setExact(true))
                .first()
                .click();

        assertThat(page).hasURL(Pattern.compile(".*/admin/backup$"));
        assertThat(page.locator("h1")).containsText("Backup");
        assertThat(page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Export Backup"))).isVisible();
        // ... waitForDownload + manifest-first assertion
    }
}
```

**Adaptation for `BackupImportE2ETest`:**
- Same `extends PlaywrightConfig` + `setupPage()/teardownPage()` lifecycle.
- Same `*Test` suffix (E2E profile picks it up via `**/e2e/**/*Test.java`).
- Upload flow: `page.setInputFiles("input[type=file]", phase73ExportZip)` + `getByRole(BUTTON, name="Import Backup").click()`.
- Assert preview-card visibility, `Proceed to Confirm` click, checkbox tick, `Execute Import` click, landing-page Flash text contains `Validation succeeded.`.

---

## Shared Patterns

### Pattern A — `@Slf4j` + parameterized logging
**Source:** `BackupArchiveService.java:94, 135-136`, `FileStorageService.java:55, 156`
**Apply to:** every new `@Service`, `@Component`, and `@ControllerAdvice` in this phase

Always `log.info("event: key={}, key2={}", v1, v2)` (state changes) and `log.warn(...)` (rejections). NEVER string-concat in log calls. CLAUDE.md "Logging" + CONVENTIONS.md L57-67 require this verbatim.

### Pattern B — Constructor-injection via `@RequiredArgsConstructor`
**Source:** `BackupController.java:54`, `CarController.java:21`, `MatchdayController.java`, `DevDataSeeder.java:13`
**Apply to:** `BackupController` (already in use), `BackupStagingCleanup`, `BackupImportService` (note: if `@Qualifier("backupObjectMapper")` is needed, use explicit constructor — see `BackupArchiveService.java:64-74`)

### Pattern C — Flash attributes `successMessage` / `errorMessage`
**Source:** `CarController.java:55, 57, 70, 73, 82, 84`, `MatchdayController.java:108, 119`, `CsvImportController.java:146, 162, 170, 203, 208`
**Apply to:** every redirect in `BackupController.importPreview/importConfirm/importExecute/importCancel` and the new `BackupUploadExceptionHandler`

Always `redirectAttributes.addFlashAttribute("successMessage", "...")` or `errorMessage`. CLAUDE.md "Controller & DTO Patterns" requires these exact keys; the layout (`admin/layout.html` lines just before `<div th:replace="${content}"></div>`) auto-renders them.

### Pattern D — Path-traversal defense reuse (D-11)
**Source:** `FileStorageService.java:153-158` (`startsWith(uploadDir.toRealPath())` predicate)
**Apply to:** `BackupArchiveService.readManifest/countDataEntries/countUploadFiles` per-`ZipEntry.getName()`; staging-file path construction in `BackupImportService.stage(...)`

Either extract into `org.ctc.backup.security.PathTraversalGuard.guard(Path root, String entryName)` or inline with a `// See FileStorageService.java:153-158` comment (D-11 explicit discretion).

### Pattern E — Given/When/Then BDD test naming
**Source:** `BackupArchiveServiceIT.java:64`, `BackupControllerSecurityIT.java:61, 87`, `BackupControllerIT.java:51, 61`
**Apply to:** every test method in this phase

`void givenContext_whenAction_thenExpectedResult()` with `// given` / `// when` / `// then` comments. CLAUDE.md "Test Naming" enforces this.

### Pattern F — `@Valid` + `BindingResult` form-binding
**Source:** `MatchdayController.java:94-105`, `CarController.java:63-66`
**Apply to:** `BackupController.importExecute` for `BackupImportConfirmForm`

```java
public String importExecute(@Valid @ModelAttribute("confirmForm") BackupImportConfirmForm form,
                            BindingResult result,
                            RedirectAttributes redirectAttributes,
                            Model model) {
    if (result.hasErrors()) {
        return "admin/backup-confirm";  // re-render with @AssertTrue field-error
    }
    // ...
}
```

### Pattern G — Lombok form DTO shape
**Source:** `MatchdayForm.java:11-23`, `SeasonPhaseForm.java`
**Apply to:** `BackupImportConfirmForm`

`@Getter @Setter @NoArgsConstructor` on the class; `@NotNull`, `@AssertTrue` on fields; `private` fields. Box `Boolean` so `@NotNull` works (primitive `boolean` defaults to `false` and bypasses `@NotNull`).

### Pattern H — Thymeleaf layout fragment
**Source:** `admin/backup.html:2-3`, every `templates/admin/*.html`
**Apply to:** `admin/backup-preview.html`, `admin/backup-confirm.html`

```html
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('<Page Title>', ~{::section})}">
<body>
<section>
  <!-- page content -->
</section>
</body>
</html>
```

The title MUST contain the substring `"Backup"` so `admin/layout.html` line `th:classappend="${title.contains('Backup') ? 'active' : ''}"` highlights the correct sidebar entry.

---

## No Analog Found

Every file has at least one analog. The one partial-match worth flagging:

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `BackupStagingCleanup` (`ApplicationReadyEvent` listener) | Service (lifecycle) | event-driven | The only startup-runner pattern in the repo is `DevDataSeeder` (`CommandLineRunner`). The shape is close (`@Slf4j @Component @RequiredArgsConstructor`) but Phase 74 should use `@EventListener(ApplicationReadyEvent.class)` rather than `CommandLineRunner` so cleanup runs *after* context is fully initialised. Spring docs idiom (`[CITED]` from RESEARCH §"ApplicationReadyEvent listener (chosen)"). Planner cites this gap explicitly. |
| `BackupImportLimits` (constants holder) | Utility (constants) | n/a | No standalone constants class exists in the repo. Closest pattern is the inline `static final long MAX_FILE_SIZE = 10 * 1024 * 1024;` in `FileStorageService.java:25`. Planner discretion (D-12) is to either inline the three constants on `BackupImportService` or create a tiny `final class BackupImportLimits { private BackupImportLimits() {} public static final long MAX_ENTRY_BYTES = ...; }` — both are acceptable. |
| `LimitedInputStream` (ZIP-bomb defense, named in RESEARCH §Pattern 1) | Utility (streaming) | streaming transform | No `FilterInputStream` extension exists in the repo. RESEARCH §Pattern 1 provides the ~25-LOC hand-rolled implementation; planner copies it verbatim. |

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/backup/**`
- `src/main/java/org/ctc/admin/controller/**`
- `src/main/java/org/ctc/admin/dto/**`
- `src/main/java/org/ctc/domain/service/**` (FileStorageService specifically)
- `src/main/java/org/ctc/domain/exception/**`
- `src/main/java/org/ctc/dataimport/CsvImportController.java`
- `src/main/resources/templates/admin/**`
- `src/main/resources/static/admin/css/admin.css`
- `src/main/resources/application.yml`
- `src/test/java/org/ctc/backup/**`
- `src/test/java/org/ctc/e2e/BackupExportE2ETest.java`

**Files scanned:** ~30 (Java + Thymeleaf + YAML + CSS)

**Pattern extraction date:** 2026-05-12

**Cross-referenced canonical files** (CONTEXT.md `<canonical_refs>`):
- `FileStorageService.java:65` → actually `:153-158` (`validatePathWithinUploadDir`) — exact line numbers cited above
- `BackupArchiveService.java:121-132` (export-side ZIP-Slip skip-with-WARN) — read-side mirror documented above
- `admin/layout.html:75-76` (sidebar Data group) — verified intact, no Phase 74 modification needed
- `BackupSchema.SCHEMA_VERSION = 1` (`BackupSchema.java:33`) — the integer constant `BackupImportService.stage(...)` compares against

## PATTERN MAPPING COMPLETE
